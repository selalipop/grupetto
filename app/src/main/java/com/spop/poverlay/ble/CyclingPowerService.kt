package com.spop.poverlay.ble

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService

@Suppress("DEPRECATION")
class CyclingPowerService(server: BleServer) : BaseBleService(server) {
    private val measurementCharacteristic = BluetoothGattCharacteristic(
        CyclingPowerConstants.MeasurementUUID,
        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ
    ).apply {
        addDescriptor(
            BluetoothGattDescriptor(
                CyclingPowerConstants.ClientCharacteristicConfigurationUUID,
                BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ
            )
        )
    }

    private val featureCharacteristic = BluetoothGattCharacteristic(
        CyclingPowerConstants.FeatureUUID,
        BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ
    ).apply {
        val flags = CyclingPowerConstants.FeatureFlags.CrankRevolutionDataSupported or
                CyclingPowerConstants.FeatureFlags.WheelRevolutionDataSupported
        value = byteArrayOf(
            (flags and 0xFF).toByte(),
            (flags shr 8 and 0xFF).toByte(),
            (flags shr 16 and 0xFF).toByte(),
            (flags shr 24 and 0xFF).toByte()
        )
    }

    private val sensorLocationCharacteristic = BluetoothGattCharacteristic(
        CyclingPowerConstants.SensorLocationUUID,
        BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ
    ).apply { value = byteArrayOf(0x05) } // Left Crank

    override val service = BluetoothGattService(
        CyclingPowerConstants.ServiceUUID,
        BluetoothGattService.SERVICE_TYPE_PRIMARY
    ).apply {
        addCharacteristic(measurementCharacteristic)
        addCharacteristic(featureCharacteristic)
        addCharacteristic(sensorLocationCharacteristic)
    }

    override fun onSensorDataUpdated(cadence: Float, power: Float, speed: Float, resistance: Float) {
        // Flags: we include wheel and crank revolution data
        val flags = CyclingPowerConstants.MeasurementFlags.WheelRevolutionDataPresent or
                CyclingPowerConstants.MeasurementFlags.CrankRevolutionDataPresent

        val powerValue = power.toInt()

        // Use shared CSC counters from server (1/1024s timers, LE wrapping)
        val wheelRevs = server.cscCumulativeWheelRev
        val wheelTime = server.cscLastWheelEvtTime and 0xFFFF
        val crankRevs = server.cscCumulativeCrankRev and 0xFFFF
        val crankTime = server.cscLastCrankEvtTime and 0xFFFF

        // Build payload: Flags(2) + Power(2) + Wheel Pair(6) + Crank Pair(4)
        val bytes = byteArrayOf(
            (flags and 0xFF).toByte(),
            ((flags shr 8) and 0xFF).toByte(),
            (powerValue and 0xFF).toByte(),
            ((powerValue shr 8) and 0xFF).toByte(),
            // Wheel revolutions (uint32 LE)
            (wheelRevs and 0xFF).toByte(),
            ((wheelRevs shr 8) and 0xFF).toByte(),
            ((wheelRevs shr 16) and 0xFF).toByte(),
            ((wheelRevs shr 24) and 0xFF).toByte(),
            // Last wheel event time (uint16 LE)
            (wheelTime and 0xFF).toByte(),
            ((wheelTime shr 8) and 0xFF).toByte(),
            // Crank revolutions (uint16 LE)
            (crankRevs and 0xFF).toByte(),
            ((crankRevs shr 8) and 0xFF).toByte(),
            // Last crank event time (uint16 LE)
            (crankTime and 0xFF).toByte(),
            ((crankTime shr 8) and 0xFF).toByte()
        )

        measurementCharacteristic.setValue(bytes)
        for (device in connectedDevices) {
            server.notifyCharacteristicChanged(device, measurementCharacteristic, false)
        }
    }
}
