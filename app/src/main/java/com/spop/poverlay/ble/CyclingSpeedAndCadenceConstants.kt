package com.spop.poverlay.ble

import java.util.UUID

object CyclingSpeedAndCadenceConstants {
    val ServiceUUID: UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")
    val MeasurementUUID: UUID = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb")
    val FeatureUUID: UUID = UUID.fromString("00002a5c-0000-1000-8000-00805f9b34fb")
    val ClientCharacteristicConfigurationUUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    object MeasurementFlags {
        const val WheelRevolutionDataPresent = 0x01
        const val CrankRevolutionDataPresent = 0x02
    }

    object FeatureFlags {
        const val WheelRevolutionDataSupported = 0x01
        const val CrankRevolutionDataSupported = 0x02
        const val MultipleSensorLocationsSupported = 0x04
    }
}
