package com.spop.poverlay.ble
import android.os.Build
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.spop.poverlay.dircon.DirConGattBridge
import com.spop.poverlay.dircon.DirConServer
import com.spop.poverlay.dircon.toDirConService
import com.spop.poverlay.sensor.heartrate.HeartRateManager
import com.spop.poverlay.sensor.interfaces.SensorInterface
import java.util.LinkedList
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import kotlin.math.abs

interface TimeProvider {
    fun elapsedRealtime(): Long
}

class SystemTimeProvider : TimeProvider {
    override fun elapsedRealtime() = android.os.SystemClock.elapsedRealtime()
}

// Base class for all BLE services
abstract class BaseBleService(val server: BleServer) {
    abstract val service: BluetoothGattService
    abstract fun onSensorDataUpdated(cadence: Float, power: Float, speed: Float, resistance: Float)
    protected val connectedDevices = mutableSetOf<BluetoothDevice>()

    fun hasConnectedDevices(): Boolean = connectedDevices.isNotEmpty()

    open fun onConnected(device: BluetoothDevice) {
        connectedDevices.add(device)
    }

    open fun onDisconnected(device: BluetoothDevice) {
        connectedDevices.remove(device)
    }

    open fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
    ) {
        if (responseNeeded) {
            server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }
    }

    @Suppress("DEPRECATION")
    open fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
    ) {
        descriptor.value = value
        if (responseNeeded) {
            server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }
    }

    @Suppress("DEPRECATION")
    open fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
    ) {
        server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, descriptor.value)
    }
}

class BleServer(
        private val context: Context,
        private val bluetoothManager: BluetoothManager,
        private val sensorInterface: SensorInterface,
        private val timeProvider: TimeProvider = SystemTimeProvider()
) : BluetoothGattServerCallback(), CoroutineScope {

    override val coroutineContext = SupervisorJob() + Dispatchers.IO
    private var sensorDataJob: Job? = null
    private var watchdogJob: Job? = null
    private var heartRateServiceWatcherJob: Job? = null

    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private val registeredServices = mutableListOf<BaseBleService>()
    private var dirConServer: DirConServer? = null
    private var dirConTransportEnabled = true
    private var isDirConOnlyStarted = false
    private val servicesToRegister = LinkedList<BaseBleService>()
    private var currentlyRegisteringService: BaseBleService? = null
    private var serviceAddTimeoutJob: Job? = null
    
    // Advertising state tracking
    private var isAdvertising = false
    private var lastAdvertisingStartTime = 0L
    private var lastAdvertisingFailureCode: Int? = null
    private var isServerStarted = false
    private var heartRateServiceEnabled = false

    // CCCD UUID for checking notification subscriptions
    private val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    
    // Track notification subscriptions: Device Address -> Set of Characteristic UUIDs
    private val notificationSubscriptions = mutableMapOf<String, MutableSet<UUID>>()
    
    // Watchdog configuration
    companion object {
        private const val WATCHDOG_INITIAL_DELAY_MS = 60_000L // 1 minute
        private const val WATCHDOG_CHECK_INTERVAL_MS = 120_000L // 2 minutes
        private const val SERVICE_ADD_TIMEOUT_MS = 2_000L
        
        // Standard BLE sensors typically update at 1Hz. 
        // We use a slight offset to avoid aliasing with sensor sampling rates.
        private const val SENSOR_UPDATE_INTERVAL_MS = 1_000L
    }
    
    // Bluetooth adapter state receiver
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                handleBluetoothStateChange(state)
            }
        }
    }
    
    // Bond state receiver for diagnostics
    private val bondStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val device = getBluetoothDeviceExtra(intent)
                val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                val prev = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)
                val name = try {
                    device?.name
                } catch (e: SecurityException) {
                    "Unknown"
                }
                Timber.d("Bond state changed: ${device?.address} $name $prev -> $state")
            }
        }
    }

    private fun getBluetoothDeviceExtra(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    @Suppress("DEPRECATION")
    private fun characteristicValue(characteristic: BluetoothGattCharacteristic): ByteArray {
        return characteristic.value ?: ByteArray(0)
    }

    //ADD OR EDIT SERVICES HERE
    private fun setupServices() {
        servicesToRegister.addAll(baseServices(heartRateEnabled = heartRateServiceEnabled))
        registerNextService()
    }

    private val dirConBridge = object : DirConGattBridge {
        override fun services() = registeredServices.map { it.service.toDirConService() }

        override fun readCharacteristic(uuid: UUID): ByteArray? {
            val characteristic = findGattCharacteristic(uuid) ?: return null
            return characteristicValue(characteristic)
        }

        override fun writeCharacteristic(uuid: UUID, value: ByteArray): Boolean {
            val characteristic = findGattCharacteristic(uuid) ?: return false
            val writable = characteristic.properties and
                (BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            if (!writable) return false

            @Suppress("DEPRECATION")
            characteristic.value = value
            return true
        }
    }

    private fun baseServices(heartRateEnabled: Boolean): List<BaseBleService> {
        val services = mutableListOf<BaseBleService>(
            FitnessMachineService(this),
            CyclingPowerService(this),
            CyclingSpeedAndCadenceService(this),
            DeviceInformationService(this)
        )
        if (heartRateEnabled) {
            services.add(HeartRateService(this))
        }
        return services
    }

    fun start() {
        if (isServerStarted) {
            Timber.d("BLE server already started, ignoring duplicate start()")
            return
        }
        if (isDirConOnlyStarted) {
            stopDirConOnly()
        }
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Timber.e("Bluetooth adapter is null")
            return
        }

        val localAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (localAdvertiser == null) {
            Timber.e("Failed to create advertiser")
            return
        }
        advertiser = localAdvertiser

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnectPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasConnectPermission) {
                Timber.w("Cannot start BLE server: missing BLUETOOTH_CONNECT permission")
                return
            }
        } else {
            val hasBluetoothPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasBluetoothPermission) {
                Timber.w("Cannot start BLE server: missing BLUETOOTH permission")
                return
            }
        }

        try {
            val server = bluetoothManager.openGattServer(context, this)
            if (server == null) {
                Timber.e("Failed to open GATT server (returned null)")
                return
            }
            gattServer = server
            
            // Register Bluetooth state change receiver
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            context.registerReceiver(bluetoothStateReceiver, filter)
            Timber.d("Registered Bluetooth state change receiver")
            
            // Register bond state receiver
            val bondFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            context.registerReceiver(bondStateReceiver, bondFilter)
            heartRateServiceEnabled = HeartRateManager.connectedDevice.value != null
            setupServices()
            startWatchdog()
            startHeartRateServiceWatcher()
            
            isServerStarted = true
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to open GATT server due to missing Bluetooth permission")
            stop() // Clean up partial initialization
        } catch (e: Exception) {
            Timber.e(e, "Failed to start BLE server")
            stop() // Clean up partial initialization
        }
    }

    private fun registerNextService() {
        if (servicesToRegister.isEmpty()) {
            currentlyRegisteringService = null
            serviceAddTimeoutJob?.cancel()
            serviceAddTimeoutJob = null
            startAdvertising()
            startDirCon()
            startSensorDataUpdates()
        } else {
            currentlyRegisteringService = servicesToRegister.pop()
            try {
                val added = gattServer?.addService(currentlyRegisteringService!!.service) == true
                if (!added) {
                    currentlyRegisteringService = null
                    registerNextService()
                    return
                }
                serviceAddTimeoutJob?.cancel()
                serviceAddTimeoutJob = launch {
                    delay(SERVICE_ADD_TIMEOUT_MS)
                    val pending = currentlyRegisteringService
                    if (pending != null) {
                        currentlyRegisteringService = null
                        registerNextService()
                    }
                }
            } catch (e: SecurityException) {
                Timber.e(e, "Failed to add service ${currentlyRegisteringService!!.service.uuid}")
                currentlyRegisteringService = null
                servicesToRegister.clear()
            }
        }
    }

    fun stop() {
        try {
            isServerStarted = false
            isDirConOnlyStarted = false
            stopWatchdog()
            stopSensorDataUpdates()
            stopHeartRateServiceWatcher()
            stopDirCon()
            stopAdvertising()
            
            // Unregister Bluetooth state change receiver
            try {
                context.unregisterReceiver(bluetoothStateReceiver)
                Timber.d("Unregistered Bluetooth state change receiver")
            } catch (e: IllegalArgumentException) {
                // Receiver was not registered, this is normal if stop() called without start()
                Timber.d("Bluetooth state receiver was not registered (normal if not started)")
            }
            try {
                context.unregisterReceiver(bondStateReceiver)
            } catch (e: IllegalArgumentException) {
                Timber.d("Bond state receiver was not registered")
            }
            
            gattServer?.clearServices()
            gattServer?.close()
            gattServer = null
            registeredServices.clear()
            servicesToRegister.clear()
            currentlyRegisteringService = null
            heartRateServiceEnabled = false
            
            // Reset state tracking
            isAdvertising = false
            lastAdvertisingStartTime = 0L
            lastAdvertisingFailureCode = null
            
            // Reset smoothing and CSC state so a restart begins fresh
            smoothedCadence = null
            smoothedPower = null
            smoothedSpeedMph = null
            smoothedResistance = null
            cscCrankResidual = 0.0
            cscWheelResidual = 0.0
            cscCumulativeWheelRev = 0L
            cscLastWheelEvtTime = 0
            cscCumulativeCrankRev = 0
            cscLastCrankEvtTime = 0
            
            synchronized(notificationSubscriptions) {
                notificationSubscriptions.clear()
            }
        } catch (e: SecurityException) {
            Timber.e(e, "Missing bluetooth permissions")
        }
    }

    fun setDirConTransportEnabled(enabled: Boolean) {
        dirConTransportEnabled = enabled
        if (enabled) {
            if (isServerStarted) {
                startDirCon()
            } else {
                startDirConOnly()
            }
        } else if (isDirConOnlyStarted) {
            stopDirConOnly()
        } else {
            stopDirCon()
        }
    }

    fun startDirConOnly() {
        if (!dirConTransportEnabled || isServerStarted || isDirConOnlyStarted) {
            return
        }

        heartRateServiceEnabled = HeartRateManager.connectedDevice.value != null
        registeredServices.clear()
        registeredServices.addAll(baseServices(heartRateEnabled = heartRateServiceEnabled))
        startDirCon()
        startSensorDataUpdates()
        isDirConOnlyStarted = true
        Timber.i("DIRCON-only transport started")
    }

    fun stopDirConOnly() {
        if (!isDirConOnlyStarted) {
            stopDirCon()
            return
        }

        stopDirCon()
        stopSensorDataUpdates()
        registeredServices.clear()
        isDirConOnlyStarted = false
        heartRateServiceEnabled = false
        Timber.i("DIRCON-only transport stopped")
    }

    private fun startHeartRateServiceWatcher() {
        heartRateServiceWatcherJob?.cancel()
        heartRateServiceWatcherJob = launch {
            HeartRateManager.connectedDevice
                .collect { connectedDevice ->
                    val shouldEnable = connectedDevice != null
                    if (!isServerStarted || shouldEnable == heartRateServiceEnabled) {
                        return@collect
                    }
                    updateHeartRateServiceRegistration(shouldEnable)
                }
        }
    }

    private fun stopHeartRateServiceWatcher() {
        heartRateServiceWatcherJob?.cancel()
        heartRateServiceWatcherJob = null
    }

    private fun updateHeartRateServiceRegistration(enable: Boolean) {
        heartRateServiceEnabled = enable
        Timber.i("Heart rate BLE service ${if (enable) "enabled" else "disabled"}")

        stopSensorDataUpdates()
        stopDirCon()
        stopAdvertising()
        gattServer?.clearServices()
        registeredServices.clear()
        servicesToRegister.clear()
        currentlyRegisteringService = null

        servicesToRegister.addAll(baseServices(heartRateEnabled = enable))
        registerNextService()
    }

    private fun startDirCon() {
        if (!dirConTransportEnabled || dirConServer != null || registeredServices.isEmpty()) {
            return
        }

        dirConServer = DirConServer(
            context = context,
            bridge = dirConBridge,
            serialNumberProvider = { serialNumber() }
        ).also { it.start() }
    }

    private fun stopDirCon() {
        dirConServer?.stop()
        dirConServer = null
    }

    fun notifyCharacteristicChanged(
            device: BluetoothDevice,
            characteristic: BluetoothGattCharacteristic,
            confirm: Boolean
    ) {
        // Convention compliance: Only notify if subscribed (handled by tracking CCCD writes)
        val isSubscribed = synchronized(notificationSubscriptions) {
            notificationSubscriptions[device.address]?.contains(characteristic.uuid) == true
        }

        if (!isSubscribed) {
             return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gattServer?.notifyCharacteristicChanged(
                    device,
                    characteristic,
                    confirm,
                    characteristicValue(characteristic)
                )
            } else {
                @Suppress("DEPRECATION")
                gattServer?.notifyCharacteristicChanged(device, characteristic, confirm)
            }
        } catch (e: SecurityException) {
            Timber.e(e, "Missing bluetooth permissions")
        }
    }

    fun notifyDirConCharacteristicChanged(characteristic: BluetoothGattCharacteristic) {
        dirConServer?.notifyCharacteristicChanged(
            characteristic.uuid,
            characteristicValue(characteristic)
        )
    }

    fun sendResponse(
            device: BluetoothDevice?,
            requestId: Int,
            status: Int,
            offset: Int,
            value: ByteArray?
    ) {
        try {
            gattServer?.sendResponse(device, requestId, status, offset, value)
        } catch (e: SecurityException) {
            Timber.e(e, "Missing bluetooth permissions")
        }
    }
    
    private fun handleBluetoothStateChange(state: Int) {
        when (state) {
            BluetoothAdapter.STATE_OFF -> {
                Timber.w("Bluetooth turned off, stopping advertising")
                isAdvertising = false
                // Don't call stopAdvertising() as Bluetooth is already off
            }
            BluetoothAdapter.STATE_ON -> {
                Timber.i("Bluetooth turned on, attempting to restart advertising")
                if (isServerStarted) {
                    restartGattAndAdvertising("Bluetooth turned on")
                }
            }
            BluetoothAdapter.STATE_TURNING_OFF -> {
                Timber.d("Bluetooth turning off")
                isAdvertising = false
            }
            BluetoothAdapter.STATE_TURNING_ON -> {
                Timber.d("Bluetooth turning on")
            }
        }
    }
    
    private fun startWatchdog() {
        stopWatchdog()
        watchdogJob = launch {
            // Wait a bit before starting watchdog to allow initial setup
            delay(WATCHDOG_INITIAL_DELAY_MS)
            
            while (isActive && isServerStarted) {
                try {
                    checkAndRestartAdvertising()
                } catch (e: Exception) {
                    Timber.e(e, "Error in advertising watchdog")
                }
                // Check every 2 minutes
                delay(WATCHDOG_CHECK_INTERVAL_MS)
            }
        }
        Timber.d("Started advertising watchdog")
    }
    
    private fun stopWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
        Timber.d("Stopped advertising watchdog")
    }
    
    private fun hasConnectedDevices(): Boolean {
        return registeredServices.any { it.hasConnectedDevices() }
    }
    
    private fun checkAndRestartAdvertising() {
        val bluetoothAdapter = bluetoothManager.adapter
        
        if (bluetoothAdapter == null) {
            Timber.w("Watchdog: Bluetooth adapter is null")
            return
        }
        
        if (!bluetoothAdapter.isEnabled) {
            Timber.d("Watchdog: Bluetooth is disabled; skipping auto-enable to avoid interfering with other apps")
            isAdvertising = false
            return
        }
        
        // Check if we should be advertising but aren't
        if (!isAdvertising && registeredServices.isNotEmpty()) {
            // With multi-client support, advertising should be active even with connected devices
            // Only skip if we very recently had a connection state change (give it time to restart)
            val timeSinceLastStart = System.currentTimeMillis() - lastAdvertisingStartTime
            
            if (hasConnectedDevices() && timeSinceLastStart < 5000) {
                // Recent connection, advertising is being restarted automatically
                Timber.d("Watchdog: Recent connection detected, waiting for automatic advertising restart")
                return
            }
            
            val reason = if (lastAdvertisingStartTime == 0L) {
                "never started"
            } else if (lastAdvertisingFailureCode != null) {
                "last failed with code $lastAdvertisingFailureCode"
            } else if (hasConnectedDevices()) {
                "stopped despite connected clients (${timeSinceLastStart / 1000}s ago)"
            } else {
                "stopped (${timeSinceLastStart / 1000}s ago)"
            }
            
            Timber.w("Watchdog: Advertising is not active, reason: $reason. Restarting...")
            
            // If we have connected devices, just restart advertising (don't reset GATT server)
            if (hasConnectedDevices()) {
                Timber.i("Restarting advertising only (preserving connections)")
                startAdvertising()
            } else {
                // No connections, safe to do full restart
                restartGattAndAdvertising("Watchdog detected inactive advertising")
            }
        } else if (isAdvertising) {
            val timeSinceStart = System.currentTimeMillis() - lastAdvertisingStartTime
            Timber.d("Watchdog: Advertising active for ${timeSinceStart / 1000}s")
        }
    }
    
    private fun restartGattAndAdvertising(reason: String) {
        Timber.i("Restarting GATT and advertising: $reason")
        
        try {
            // Stop current advertising
            stopAdvertising()
            
            // Close and reopen GATT server
            gattServer?.clearServices()
            gattServer?.close()
            gattServer = null
            
            val bluetoothAdapter = bluetoothManager.adapter
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                Timber.e("Cannot restart: Bluetooth not available")
                return
            }
            
            advertiser = bluetoothAdapter.bluetoothLeAdvertiser
            if (advertiser == null) {
                Timber.e("Cannot restart: Failed to get advertiser")
                return
            }
            
            gattServer = bluetoothManager.openGattServer(context, this)
            if (gattServer == null) {
                Timber.e("Cannot restart: Failed to open GATT server")
                return
            }
            
            // Re-register all services
            val savedServices = registeredServices.toList()
            registeredServices.clear()
            servicesToRegister.clear()
            servicesToRegister.addAll(savedServices)
            currentlyRegisteringService = null
            
            registerNextService()
            
        } catch (e: SecurityException) {
            Timber.e(e, "Missing bluetooth permissions during restart")
        } catch (e: Exception) {
            Timber.e(e, "Error during GATT and advertising restart")
        }
    }

    private fun startAdvertising() {
        if (isAdvertising) {
            return
        }
        val serviceUuids = registeredServices.map { ParcelUuid(it.service.uuid) }
        if (serviceUuids.isEmpty()) {
            return
        }
        try {
            val settings =
                    AdvertiseSettings.Builder()
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                            .setConnectable(true)
                            .build()

            // Primary advertising data: keep it lean (just service UUIDs) to avoid 31-byte limit
            val advDataBuilder = AdvertiseData.Builder()
            for (uuid in serviceUuids) {
                advDataBuilder.addServiceUuid(uuid)
            }

            // Scan response: include device name and manufacturer specific data
            val scanResponseBuilder = AdvertiseData.Builder()
                .setIncludeDeviceName(true)

            // Manufacturer data (use a test/manufacturer ID; replace with your assigned company ID if available)
            val manufacturerId = 0xFFFF // 16-bit Company Identifier (testing)
            val sn = serialNumber()
            // Keep payload concise to fit scan response size constraints
            val manufacturerData = "GRUP-$sn".toByteArray(Charsets.UTF_8)
            scanResponseBuilder.addManufacturerData(manufacturerId, manufacturerData)

            advertiser?.startAdvertising(
                settings,
                advDataBuilder.build(),
                scanResponseBuilder.build(),
                advertisingCallback
            )
        } catch (e: SecurityException) {
            Timber.e(e, "Missing bluetooth permissions")
        } catch (e: IllegalArgumentException) {
            // Thrown if advertise data exceeds the allowed size
            Timber.e(e, "Invalid advertise data: %s", e.message)
        }
    }

    private fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advertisingCallback)
            isAdvertising = false
            Timber.d("Stopped advertising")
        } catch (e: SecurityException) {
            Timber.e(e, "Missing bluetooth permissions")
        }
    }

    private val advertisingCallback =
            object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    isAdvertising = true
                    lastAdvertisingStartTime = System.currentTimeMillis()
                    lastAdvertisingFailureCode = null
                    Timber.i("BLE advertising started successfully")
                }

                override fun onStartFailure(errorCode: Int) {
                    isAdvertising = false
                    lastAdvertisingFailureCode = errorCode
                    val errorMessage = when (errorCode) {
                        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data too large"
                        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers"
                        AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "Already started"
                        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal error"
                        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                        else -> "Unknown error"
                    }
                    Timber.e("BLE advertising failed: $errorCode ($errorMessage)")
                    if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED) {
                        isAdvertising = true
                    }
                }
            }

    override fun onServiceAdded(status: Int, service: BluetoothGattService) {
        serviceAddTimeoutJob?.cancel()
        serviceAddTimeoutJob = null
        if (currentlyRegisteringService?.service?.uuid != service.uuid) {
            Timber.e(
                    "Mismatched service added callback! Expected ${currentlyRegisteringService?.service?.uuid}, got ${service.uuid}"
            )
            servicesToRegister.clear()
            currentlyRegisteringService = null
            return
        }

        if (status == BluetoothGatt.GATT_SUCCESS) {
            Timber.d("Service added ${service.uuid}")
            registeredServices.add(currentlyRegisteringService!!)
        } else {
            Timber.e("Failed to add service ${service.uuid}, status: $status")
        }
        registerNextService()
    }

    override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
        if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
            device?.let { 
                registeredServices.forEach { it.onConnected(device) }
                Timber.d("Device connected: ${device.address}")
                
                // Restart advertising to allow additional clients to connect (support multiple connections)
                if (!isAdvertising && isServerStarted) {
                    Timber.i("Device connected, restarting advertising to allow more clients")
                    launch {
                        // Small delay to ensure connection is fully established
                        delay(500)
                        if (!isAdvertising && isServerStarted) {
                            startAdvertising()
                        }
                    }
                }
            }
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            // Clean up subscriptions
            synchronized(notificationSubscriptions) {
                notificationSubscriptions.remove(device?.address)
            }
            device?.let { 
                registeredServices.forEach { it.onDisconnected(device) }
                Timber.d("Device disconnected: ${device.address}")
                
                // Restart advertising if no devices are connected anymore
                if (!hasConnectedDevices() && !isAdvertising && isServerStarted) {
                    Timber.i("Last device disconnected, restarting advertising")
                    launch {
                        // Small delay to ensure disconnect is fully processed
                        delay(500)
                        if (!hasConnectedDevices() && !isAdvertising) {
                            startAdvertising()
                        }
                    }
                }
            }
        }
    }

    override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
        Timber.d("onMtuChanged: $mtu for ${device?.address}")
    }

    private fun findServiceForCharacteristic(uuid: UUID?): BaseBleService? {
        return registeredServices.firstOrNull { it.service.uuid == uuid }
    }

    private fun findGattCharacteristic(uuid: UUID): BluetoothGattCharacteristic? {
        return registeredServices
            .asSequence()
            .flatMap { it.service.characteristics.asSequence() }
            .firstOrNull { it.uuid == uuid }
    }

    override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
    ) {
        findServiceForCharacteristic(characteristic.service.uuid)
                ?.onCharacteristicWriteRequest(
                        device,
                        requestId,
                        characteristic,
                        preparedWrite,
                        responseNeeded,
                        offset,
                        value
                )
    }

    override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
    ) {
        val service = findServiceForCharacteristic(characteristic.service.uuid)
        if (service == null) {
            sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
            return
        }
        sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristicValue(characteristic))
    }

    override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
    ) {
        findServiceForCharacteristic(descriptor.characteristic.service.uuid)
                ?.onDescriptorReadRequest(device, requestId, offset, descriptor)
    }

    override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
    ) {
        // Convention compliance: Track CCCD state
        if (descriptor.uuid == CLIENT_CHARACTERISTIC_CONFIG && value != null) {
            val isEnabled = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                           value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
            val isDisabled = value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)

            synchronized(notificationSubscriptions) {
                val deviceAddress = device.address
                if (isEnabled) {
                    val uuidSet = notificationSubscriptions.getOrPut(deviceAddress) { mutableSetOf() }
                    uuidSet.add(descriptor.characteristic.uuid)
                    Timber.d("Notifications enabled for ${descriptor.characteristic.uuid} on $deviceAddress")
                } else if (isDisabled) {
                    notificationSubscriptions[deviceAddress]?.remove(descriptor.characteristic.uuid)
                    if (notificationSubscriptions[deviceAddress]?.isEmpty() == true) {
                        notificationSubscriptions.remove(deviceAddress)
                    }
                    Timber.d("Notifications disabled for ${descriptor.characteristic.uuid} on $deviceAddress")
                }
            }
        }

        findServiceForCharacteristic(descriptor.characteristic.service.uuid)
                ?.onDescriptorWriteRequest(
                        device,
                        requestId,
                        descriptor,
                        preparedWrite,
                        responseNeeded,
                        offset,
                        value
                )
    }

    // Data class to hold four lists of sensor values
    private data class SensorData(
            val cadence: List<Float>,
            val power: List<Float>,
            val speed: List<Float>,
            val resistance: List<Float>
    )

    private fun startSensorDataUpdates() {
        sensorDataJob?.cancel()
        sensorDataJob = launch {
            val mutex = Mutex()
            val cadenceBuffer = mutableListOf<Float>()
            val powerBuffer = mutableListOf<Float>()
            val speedBuffer = mutableListOf<Float>()
            val resistanceBuffer = mutableListOf<Float>()

            launch {
                combine(
                                sensorInterface.cadence,
                                sensorInterface.power,
                                sensorInterface.speed,
                                sensorInterface.resistance
                        ) { cadence, power, speed, resistance ->
                            mutex.withLock {
                                cadenceBuffer.add(cadence)
                                powerBuffer.add(power)
                                speedBuffer.add(speed)
                                resistanceBuffer.add(resistance)
                            }
                        }
                        .collect()
            }

            launch {
                while (isActive) {
                    // Use a standard interval (~1Hz) to be consistent with typical clients.
                    // The slight offset (e.g. 1003ms) prevents phase-locking/aliasing with incoming sensor data loops.
                    delay(SENSOR_UPDATE_INTERVAL_MS)
                    
                    val buffers =
                            mutex.withLock {
                                if (cadenceBuffer.isEmpty()) null
                                else
                                        SensorData(
                                                cadenceBuffer.toList(),
                                                powerBuffer.toList(),
                                                speedBuffer.toList(),
                                                resistanceBuffer.toList()
                                        )
                                                .also {
                                                    cadenceBuffer.clear()
                                                    powerBuffer.clear()
                                                    speedBuffer.clear()
                                                    resistanceBuffer.clear()
                                                }
                            }
                    buffers?.let { data ->
                        // Robust outlier filtering per metric, then light exponential smoothing
                        val rCadence = robustAverage(data.cadence)
                        val rPower = robustAverage(data.power)
                        val rSpeedMph = robustAverage(data.speed) // mph
                        val rResistance = robustAverage(data.resistance)

                        val sCadence = smoothCadence(rCadence)
                        val sPower = smoothPower(rPower)
                        val sSpeedMph = smoothSpeed(rSpeedMph)
                        val sResistance = smoothResistance(rResistance)

                        // Convert mph -> km/h for wheel calculations
                        val sSpeedKmh = sSpeedMph * 1.60934f
                        // Update shared CSC counters using km/h for wheel and RPM for crank
                        updateWheelAndCrankRev(sSpeedKmh, sCadence)
                        // Notify services with smoothed values (speed remains mph; services handle their unit needs)
                        registeredServices.forEach {
                            it.onSensorDataUpdated(sCadence, sPower, sSpeedMph, sResistance)
                        }
                    }
                }
            }
        }
    }

    private fun stopSensorDataUpdates() {
        sensorDataJob?.cancel()
    }

    // --- Robust filtering and smoothing helpers ---
    // Median of a non-empty Float list
    private fun medianOf(values: List<Float>): Float {
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else ((sorted[n / 2 - 1] + sorted[n / 2]) / 2f)
    }

    // Compute a robust average by removing outliers using a MAD-based threshold
    private fun robustAverage(values: List<Float>): Float {
        val finite = values.filter { it.isFinite() }
        if (finite.isEmpty()) return 0f
        if (finite.size < 3) return finite.average().toFloat()

        val med = medianOf(finite)
        val deviations = finite.map { abs(it - med) }
        val mad = medianOf(deviations)

        // If MAD is ~0 (stable), fall back to light trimming of extremes
        if (mad == 0f) {
            val sorted = finite.sorted()
            return when {
                sorted.size >= 5 -> sorted.subList(1, sorted.size - 1).average().toFloat()
                else -> sorted.average().toFloat()
            }
        }

        // 3-sigma rule on MAD with consistency constant
        val threshold = 3.0f * 1.4826f * mad
        val inliers = finite.filter { abs(it - med) <= threshold }
        return if (inliers.isNotEmpty()) inliers.average().toFloat() else med
    }

    // Exponential smoothing states and helpers
    private var smoothedCadence: Float? = null
    private var smoothedPower: Float? = null
    private var smoothedSpeedMph: Float? = null
    private var smoothedResistance: Float? = null

    private fun smooth(prev: Float?, value: Float, alpha: Float): Float =
        if (prev == null) value else (alpha * value + (1f - alpha) * prev)

    // With 1Hz updates, we increase alpha (reduce smoothing) to maintain responsiveness.
    // The previous 1-second buffering already provides significant noise reduction.
    private fun smoothCadence(v: Float, alpha: Float = 0.7f): Float {
        smoothedCadence = smooth(smoothedCadence, v, alpha)
        return smoothedCadence!!
    }

    private fun smoothPower(v: Float, alpha: Float = 0.7f): Float {
        smoothedPower = smooth(smoothedPower, v, alpha)
        return smoothedPower!!
    }

    private fun smoothSpeed(vMph: Float, alpha: Float = 0.7f): Float {
        smoothedSpeedMph = smooth(smoothedSpeedMph, vMph, alpha)
        return smoothedSpeedMph!!
    }

    private fun smoothResistance(v: Float, alpha: Float = 0.7f): Float {
        smoothedResistance = smooth(smoothedResistance, v, alpha)
        return smoothedResistance!!
    }

    // CSC shared state (used by multiple services)
    // Wheel values: cumulative (uint32) and last event time (uint16, 1/1024s). Only updated if
    // speed provided.
    var cscCumulativeWheelRev: Long = 0L
        private set
    var cscLastWheelEvtTime: Int = 0 // uint16 ticks (wrap at 65536)
        private set
    // Crank values: cumulative (uint16) and last event time (uint16, 1/1024s)
    var cscCumulativeCrankRev: Int = 0
        private set
    var cscLastCrankEvtTime: Int = 0 // uint16 ticks (wrap at 65536)
        private set

    // Update CSC wheel and crank revolutions using the C++ algorithm
    // speedKmh: if provided, wheel data will be updated; cadenceRpm always used for crank
    private var cscLastUpdateMs: Long = timeProvider.elapsedRealtime()
    private var cscCrankResidual: Double = 0.0
    private var cscWheelResidual: Double = 0.0
    fun updateWheelAndCrankRev(speedKmh: Float?, cadenceRpm: Float) {
        val now = timeProvider.elapsedRealtime()
        val deltaMs = (now - cscLastUpdateMs).coerceAtLeast(0)
        cscLastUpdateMs = now

        // Wheel
        val wheelSizeMeters = 2.127f // 700c x 28, typical
        // speedKmh must be in km/h; convert to m/s for wheel RPM calculation
        val speedMps = speedKmh?.let { it / 3.6f }
        if (speedMps != null && speedMps > 0f) {

            val wheelRpm = (speedMps / wheelSizeMeters) * 60f
            if (wheelRpm > 0f) {
                val wheelRevPeriodTicks = (60.0 * 1024.0) / wheelRpm
                val wheelRevsDelta = wheelRpm * (deltaMs / 60000.0)
                cscWheelResidual += wheelRevsDelta
                    val toAdd = kotlin.math.floor(cscWheelResidual).toInt()
                    if (toAdd > 0) {
                        cscWheelResidual -= toAdd
                        cscCumulativeWheelRev = (cscCumulativeWheelRev + toAdd) and 0xFFFF_FFFFL
                        val ticksAdd = (wheelRevPeriodTicks * toAdd).toInt().coerceAtLeast(1)
                        cscLastWheelEvtTime = (cscLastWheelEvtTime + ticksAdd) and 0xFFFF
                    }
                }
            }

        // Crank
        if (cadenceRpm > 0f) {
            val crankRevPeriodTicks = (60.0 * 1024.0) / cadenceRpm
            val crankRevsDelta = cadenceRpm * (deltaMs / 60000.0)
            cscCrankResidual += crankRevsDelta
            val toAdd = kotlin.math.floor(cscCrankResidual).toInt()
            if (toAdd > 0) {
                cscCrankResidual -= toAdd
                cscCumulativeCrankRev = (cscCumulativeCrankRev + toAdd) and 0xFFFF
                val ticksAdd = (crankRevPeriodTicks * toAdd).toInt().coerceAtLeast(1)
                cscLastCrankEvtTime = (cscLastCrankEvtTime + ticksAdd) and 0xFFFF
            }
        }
    }


    //Function checks userprefs to see if serial number has been generated on previous ones, and if not, It creates one.
    fun serialNumber(): String {
        // Use the same shared preferences as ConfigurationRepository
        val prefs = context.getSharedPreferences(
            com.spop.poverlay.ConfigurationRepository.SharedPrefsName,
            Context.MODE_PRIVATE
        )
        val key = com.spop.poverlay.ConfigurationRepository.Preferences.SerialNumber.key
        var existing = prefs.getString(key, null)
        if (existing.isNullOrEmpty()) {
            val value = kotlin.random.Random.nextInt(0x10000)
            existing = value.toString(16).padStart(4, '0').uppercase()
            prefs.edit { putString(key, existing) }
        }
        return existing
    }
    
}
