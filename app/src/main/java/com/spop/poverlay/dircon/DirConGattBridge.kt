package com.spop.poverlay.dircon

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import java.util.UUID

data class DirConCharacteristic(
    val uuid: UUID,
    val properties: Int,
    val value: ByteArray
)

data class DirConService(
    val uuid: UUID,
    val characteristics: List<DirConCharacteristic>
)

interface DirConGattBridge {
    fun services(): List<DirConService>
    fun readCharacteristic(uuid: UUID): ByteArray?
    fun writeCharacteristic(uuid: UUID, value: ByteArray): Boolean
}

fun BluetoothGattService.toDirConService(): DirConService =
    DirConService(
        uuid = uuid,
        characteristics = characteristics.map { characteristic ->
            @Suppress("DEPRECATION")
            DirConCharacteristic(
                uuid = characteristic.uuid,
                properties = characteristic.properties.toDirConProperties(),
                value = characteristic.value ?: ByteArray(0)
            )
        }
    )

private fun Int.toDirConProperties(): Int {
    var properties = 0
    if (this and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
        properties = properties or DirConConstants.PropertyRead
    }
    if (
        this and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
        this and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
    ) {
        properties = properties or DirConConstants.PropertyWrite
    }
    if (this and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
        properties = properties or DirConConstants.PropertyNotify
    }
    if (this and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
        properties = properties or DirConConstants.PropertyIndicate
    }
    return properties
}
