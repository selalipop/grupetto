package com.spop.poverlay.ble

import java.util.UUID

object DeviceInformationConstants{
    val ServiceUUID: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    val ManufacturerNameUUID: UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
    val ModelNumberUUID: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
    val SerialNumberUUID: UUID = UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb")
    val HardwareRevisionUUID: UUID = UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb")
    val FirmwareRevisionUUID: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
    val SoftwareRevisionUUID: UUID = UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")
}
