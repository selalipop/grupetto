package com.spop.poverlay.ble

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService

@Suppress("DEPRECATION")
class CyclingSpeedAndCadenceService(server: BleServer) : BaseBleService(server) {

    // State for crank data
    private var cumulativeCrankRevolutions: Int = 0
    private var lastCrankEventTime1024: Int = 0 // uint16 in 1/1024s
    private var lastUpdateElapsedMs: Long = android.os.SystemClock.elapsedRealtime()
    private var crankFractionalRevs: Double = 0.0

    private val measurementCharacteristic = BluetoothGattCharacteristic(
        CyclingSpeedAndCadenceConstants.MeasurementUUID,
        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ
    ).apply {
        addDescriptor(
            BluetoothGattDescriptor(
                CyclingSpeedAndCadenceConstants.ClientCharacteristicConfigurationUUID,
                BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ
            )
        )
    }

    private val featureCharacteristic = BluetoothGattCharacteristic(
        CyclingSpeedAndCadenceConstants.FeatureUUID,
        BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ
    ).apply {
        // Match C++ server: advertise wheel and crank support
        val flags = CyclingSpeedAndCadenceConstants.FeatureFlags.WheelRevolutionDataSupported or
                CyclingSpeedAndCadenceConstants.FeatureFlags.CrankRevolutionDataSupported
        setValue(
            byteArrayOf(
                (flags and 0xFF).toByte(),
                (flags shr 8 and 0xFF).toByte()
            )
        )
    }

    override val service = BluetoothGattService(
        CyclingSpeedAndCadenceConstants.ServiceUUID,
        BluetoothGattService.SERVICE_TYPE_PRIMARY
    ).apply {
        addCharacteristic(measurementCharacteristic)
        addCharacteristic(featureCharacteristic)
    }

    override fun onSensorDataUpdated(cadence: Float, power: Float, speed: Float, resistance: Float) {
        // Build measurement from server's shared counters
        val hasWheel = server.cscLastWheelEvtTime != 0 || server.cscCumulativeWheelRev != 0L
        val hasCrank = server.cscLastCrankEvtTime != 0 || server.cscCumulativeCrankRev != 0

        var flags = 0
        if (hasWheel) flags = flags or CyclingSpeedAndCadenceConstants.MeasurementFlags.WheelRevolutionDataPresent
        if (hasCrank) flags = flags or CyclingSpeedAndCadenceConstants.MeasurementFlags.CrankRevolutionDataPresent

        val bytes = ArrayList<Byte>(1 + (if (hasWheel) 6 else 0) + (if (hasCrank) 4 else 0))
        bytes.add(flags.toByte())
        if (hasWheel) {
            val wheelRevs = server.cscCumulativeWheelRev
            val wheelTime = server.cscLastWheelEvtTime
            bytes.add((wheelRevs and 0xFF).toByte())
            bytes.add(((wheelRevs shr 8) and 0xFF).toByte())
            bytes.add(((wheelRevs shr 16) and 0xFF).toByte())
            bytes.add(((wheelRevs shr 24) and 0xFF).toByte())
            bytes.add((wheelTime and 0xFF).toByte())
            bytes.add(((wheelTime shr 8) and 0xFF).toByte())
        }
        if (hasCrank) {
            val crankRevs = server.cscCumulativeCrankRev
            val crankTime = server.cscLastCrankEvtTime
            bytes.add((crankRevs and 0xFF).toByte())
            bytes.add(((crankRevs shr 8) and 0xFF).toByte())
            bytes.add((crankTime and 0xFF).toByte())
            bytes.add(((crankTime shr 8) and 0xFF).toByte())
        }
        measurementCharacteristic.setValue(bytes.toByteArray())

        for (device in connectedDevices) {
            server.notifyCharacteristicChanged(device, measurementCharacteristic, false)
        }
    }
}
