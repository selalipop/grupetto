package com.spop.poverlay.ble

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import com.spop.poverlay.sensor.heartrate.HeartRateManager

@Suppress("DEPRECATION")
class HeartRateService(server: BleServer) : BaseBleService(server) {
    private val measurementCharacteristic = BluetoothGattCharacteristic(
        HeartRateConstants.MeasurementUUID,
        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ
    ).apply {
        addDescriptor(
            BluetoothGattDescriptor(
                HeartRateConstants.ClientCharacteristicConfigurationUUID,
                BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ
            )
        )
    }

    private val bodySensorLocationCharacteristic = BluetoothGattCharacteristic(
        HeartRateConstants.BodySensorLocationUUID,
        BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ
    ).apply {
        // Relay-only source; chest is broadly compatible as a default.
        value = byteArrayOf(HeartRateConstants.BodySensorLocation.Chest.toByte())
    }

    override val service = BluetoothGattService(
        HeartRateConstants.ServiceUUID,
        BluetoothGattService.SERVICE_TYPE_PRIMARY
    ).apply {
        addCharacteristic(measurementCharacteristic)
        addCharacteristic(bodySensorLocationCharacteristic)
    }

    override fun onSensorDataUpdated(cadence: Float, power: Float, speed: Float, resistance: Float) {
        val heartRate = HeartRateManager.heartRate.value ?: return
        if (heartRate <= 0) return

        // Flags (uint8):
        // bit0 = 0 => uint8 heart rate value format.
        val flags = 0x00
        measurementCharacteristic.setValue(
            byteArrayOf(
                flags.toByte(),
                (heartRate and 0xFF).toByte()
            )
        )
        server.notifyDirConCharacteristicChanged(measurementCharacteristic)

        for (device in connectedDevices) {
            server.notifyCharacteristicChanged(device, measurementCharacteristic, false)
        }
    }
}
