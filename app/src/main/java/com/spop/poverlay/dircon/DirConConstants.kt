package com.spop.poverlay.dircon

object DirConConstants {
    const val TcpPort = 8081
    const val MdnsServiceType = "_wahoo-fitness-tnp._tcp."
    const val MdnsServiceName = "Grupetto"

    const val HeaderLength = 6
    const val ProtocolVersion = 1

    const val PropertyRead = 0x01
    const val PropertyWrite = 0x02
    const val PropertyNotify = 0x04
    const val PropertyIndicate = 0x08

    const val MessageError = 0xFF
    const val MessageDiscoverServices = 0x01
    const val MessageDiscoverCharacteristics = 0x02
    const val MessageReadCharacteristic = 0x03
    const val MessageWriteCharacteristic = 0x04
    const val MessageEnableCharacteristicNotifications = 0x05
    const val MessageUnsolicitedCharacteristicNotification = 0x06

    const val ResponseSuccess = 0x00
    const val ResponseUnknownMessageType = 0x01
    const val ResponseUnexpectedError = 0x02
    const val ResponseServiceNotFound = 0x03
    const val ResponseCharacteristicNotFound = 0x04
    const val ResponseCharacteristicOperationNotSupported = 0x05
    const val ResponseCharacteristicWriteFailed = 0x06
    const val ResponseUnknownProtocol = 0x07
}
