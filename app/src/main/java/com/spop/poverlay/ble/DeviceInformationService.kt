package com.spop.poverlay.ble
import com.spop.poverlay.BuildConfig
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.os.Build
import android.health.connect.datatypes.Device

class DeviceInformationService(server: BleServer) : BaseBleService(server) {

    override val service =
            BluetoothGattService(
                            DeviceInformationConstants.ServiceUUID,
                            BluetoothGattService.SERVICE_TYPE_PRIMARY
                    )
                    .apply {
                        addCharacteristic(
                                BluetoothGattCharacteristic(
                                        DeviceInformationConstants.ManufacturerNameUUID,
                                        BluetoothGattCharacteristic.PROPERTY_READ,
                                        BluetoothGattCharacteristic.PERMISSION_READ
                                )
                        )
                        addCharacteristic(
                                BluetoothGattCharacteristic(
                                        DeviceInformationConstants.ModelNumberUUID,
                                        BluetoothGattCharacteristic.PROPERTY_READ,
                                        BluetoothGattCharacteristic.PERMISSION_READ
                                )
                        )
                        addCharacteristic(
                                BluetoothGattCharacteristic(
                                        DeviceInformationConstants.SerialNumberUUID,
                                        BluetoothGattCharacteristic.PROPERTY_READ,
                                        BluetoothGattCharacteristic.PERMISSION_READ
                                )
                        )
                        addCharacteristic(
                                BluetoothGattCharacteristic(
                                        DeviceInformationConstants.HardwareRevisionUUID,
                                        BluetoothGattCharacteristic.PROPERTY_READ,
                                        BluetoothGattCharacteristic.PERMISSION_READ
                                )
                        )
                        addCharacteristic(
                                BluetoothGattCharacteristic(
                                        DeviceInformationConstants.FirmwareRevisionUUID,
                                        BluetoothGattCharacteristic.PROPERTY_READ,
                                        BluetoothGattCharacteristic.PERMISSION_READ
                                )
                        )
                        addCharacteristic(
                                BluetoothGattCharacteristic(
                                        DeviceInformationConstants.SoftwareRevisionUUID,
                                        BluetoothGattCharacteristic.PROPERTY_READ,
                                        BluetoothGattCharacteristic.PERMISSION_READ
                                )
                        )
                    }
    override fun onSensorDataUpdated(
            cadence: Float,
            power: Float,
            speed: Float,
            resistance: Float
    ) {

        // Populate characteristic values (UTF-8 strings)
        service.getCharacteristic(DeviceInformationConstants.ManufacturerNameUUID)
                ?.setValue("Grupetto".toByteArray(Charsets.UTF_8))
        service.getCharacteristic(DeviceInformationConstants.ModelNumberUUID)
                ?.setValue("Grupetto FTMS".toByteArray(Charsets.UTF_8))
        service.getCharacteristic(DeviceInformationConstants.SerialNumberUUID)
                ?.setValue(server.serialNumber().toByteArray(Charsets.UTF_8))
        service.getCharacteristic(DeviceInformationConstants.HardwareRevisionUUID)
                ?.setValue("1.0".toByteArray(Charsets.UTF_8))
        service.getCharacteristic(DeviceInformationConstants.FirmwareRevisionUUID)
                ?.setValue(BuildConfig.VERSION_NAME.toByteArray(Charsets.UTF_8))
        service.getCharacteristic(DeviceInformationConstants.SoftwareRevisionUUID)
                ?.setValue(BuildConfig.VERSION_NAME.toByteArray(Charsets.UTF_8))
    }
}
