package com.spop.poverlay.ble
import android.os.Build
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import androidx.core.content.edit
import com.spop.poverlay.sensor.interfaces.SensorInterface
import java.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import kotlin.math.abs

// Listener for sensor data updates
interface SensorDataListener {
    fun onSensorDataUpdated(cadence: Float, power: Float, speed: Float, resistance: Float)
}

// Base class for all BLE services
abstract class BaseBleService(val server: BleServer) : SensorDataListener {
    abstract val service: BluetoothGattService
    protected val connectedDevices = mutableSetOf<BluetoothDevice>()

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
        private val sensorInterface: SensorInterface
) : BluetoothGattServerCallback(), CoroutineScope {

    override val coroutineContext = SupervisorJob() + Dispatchers.IO
    private var sensorDataJob: Job? = null

    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private val registeredServices = mutableListOf<BaseBleService>()
    private val servicesToRegister = LinkedList<BaseBleService>()
    private var currentlyRegisteringService: BaseBleService? = null

    //ADD OR EDIT SERVICES HERE
    private fun setupServices() {
        servicesToRegister.addAll(
                listOf(
                        FitnessMachineService(this),
                        CyclingPowerService(this),
                        CyclingSpeedAndCadenceService(this),
                        DeviceInformationService(this)
                )
        )
        registerNextService()
    }

    fun start() {
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Timber.e("Bluetooth adapter is null")
            return
        }
        
        // Set the Bluetooth adapter name to "Grupetto"
        try {
            bluetoothAdapter.name = "Grupetto"
            Timber.d("Bluetooth adapter name set to Grupetto")
        } catch (e: SecurityException) {
            Timber.e(e, "Missing bluetooth permissions to set adapter name")
        } catch (e: Exception) {
            Timber.e(e, "Failed to set Bluetooth adapter name")
        }
        
        val localAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (localAdvertiser == null) {
            Timber.e("Failed to create advertiser")
            return
        }
        advertiser = localAdvertiser

        try {
            gattServer = bluetoothManager.openGattServer(context, this)
            setupServices()
        } catch (e: SecurityException) {
            Timber.e(e, "Missing bluetooth permissions")
        }
    }

    private fun registerNextService() {
        if (servicesToRegister.isEmpty()) {
            currentlyRegisteringService = null
            startAdvertising()
            startSensorDataUpdates()
        } else {
            currentlyRegisteringService = servicesToRegister.pop()
            try {
                gattServer?.addService(currentlyRegisteringService!!.service)
            } catch (e: SecurityException) {
                Timber.e(e, "Failed to add service ${currentlyRegisteringService!!.service.uuid}")
                currentlyRegisteringService = null
                servicesToRegister.clear()
            }
        }
    }

    fun stop() {
        try {
            stopSensorDataUpdates()
            stopAdvertising()
            gattServer?.clearServices()
            gattServer?.close()
            gattServer = null
            registeredServices.clear()
            servicesToRegister.clear()
            currentlyRegisteringService = null
        } catch (e: SecurityException) {
            Timber.e(e, "Missing bluetooth permissions")
        }
    }

    fun notifyCharacteristicChanged(
            device: BluetoothDevice,
            characteristic: BluetoothGattCharacteristic,
            confirm: Boolean
    ) {
        try {
            gattServer?.notifyCharacteristicChanged(device, characteristic, confirm)
        } catch (e: SecurityException) {
            Timber.e(e, "Missing bluetooth permissions")
        }
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

    private fun startAdvertising() {
        val serviceUuids = registeredServices.map { ParcelUuid(it.service.uuid) }
        try {
            val settings =
                    AdvertiseSettings.Builder()
                            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
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
        } catch (e: SecurityException) {
            Timber.e(e, "Missing bluetooth permissions")
        }
    }

    private val advertisingCallback =
            object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Timber.i("BLE advertising started")
                }

                override fun onStartFailure(errorCode: Int) {
                    Timber.e("BLE advertising failed: $errorCode")
                }
            }

    override fun onServiceAdded(status: Int, service: BluetoothGattService) {
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
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            device?.let { registeredServices.forEach { it.onConnected(device) } }
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            device?.let { registeredServices.forEach { it.onDisconnected(device) } }
        }
    }

    private fun findServiceForCharacteristic(uuid: UUID?): BaseBleService? {
        return registeredServices.firstOrNull { it.service.uuid == uuid }
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
        sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.value)
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
                    delay(300)
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

    private fun smoothCadence(v: Float, alpha: Float = 0.35f): Float {
        smoothedCadence = smooth(smoothedCadence, v, alpha)
        return smoothedCadence!!
    }

    private fun smoothPower(v: Float, alpha: Float = 0.25f): Float {
        smoothedPower = smooth(smoothedPower, v, alpha)
        return smoothedPower!!
    }

    private fun smoothSpeed(vMph: Float, alpha: Float = 0.4f): Float {
        smoothedSpeedMph = smooth(smoothedSpeedMph, vMph, alpha)
        return smoothedSpeedMph!!
    }

    private fun smoothResistance(v: Float, alpha: Float = 0.35f): Float {
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
    private var cscLastUpdateMs: Long = android.os.SystemClock.elapsedRealtime()
    private var cscCrankResidual: Double = 0.0
    private var cscWheelResidual: Double = 0.0
    fun updateWheelAndCrankRev(speedKmh: Float?, cadenceRpm: Float) {
        val now = android.os.SystemClock.elapsedRealtime()
        val deltaMs = (now - cscLastUpdateMs).coerceAtLeast(0)
        cscLastUpdateMs = now

        // Wheel
        val wheelSizeMeters = 2.127f // 700c x 28, typical
    // speedKmh must be in km/h; convert to m/s for wheel RPM calculation
    var speedMps = speedKmh?.let { it / 3.6f }
    if (speedMps != null && speedMps > 0f) {
        speedMps = speedMps.div(2)
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