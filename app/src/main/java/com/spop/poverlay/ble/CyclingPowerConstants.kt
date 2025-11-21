package com.spop.poverlay.ble

import java.util.UUID

object CyclingPowerConstants{
    val ServiceUUID: UUID = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb")
    val MeasurementUUID: UUID = UUID.fromString("00002a63-0000-1000-8000-00805f9b34fb")
    val FeatureUUID: UUID = UUID.fromString("00002a65-0000-1000-8000-00805f9b34fb")
    val SensorLocationUUID: UUID = UUID.fromString("00002a5d-0000-1000-8000-00805f9b34fb")
    val ClientCharacteristicConfigurationUUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    object MeasurementFlags {
        const val PedalPowerBalancePresent = 0x0001
        const val PedalPowerBalanceReference = 0x0002
        const val AccumulatedTorquePresent = 0x0004
        const val AccumulatedTorqueSource = 0x0008
        const val WheelRevolutionDataPresent = 0x0010
        const val CrankRevolutionDataPresent = 0x0020
        const val ExtremeForceMagnitudesPresent = 0x0040
        const val ExtremeTorqueMagnitudesPresent = 0x0080
        const val ExtremeAnglesPresent = 0x0100
        const val TopDeadSpotAnglePresent = 0x0200
        const val BottomDeadSpotAnglePresent = 0x0400
        const val AccumulatedEnergyPresent = 0x0800
        const val OffsetCompensationIndicator = 0x1000
    }

    object FeatureFlags {
        const val PedalPowerBalanceSupported = 0x00000001
        const val AccumulatedTorqueSupported = 0x00000002
        const val WheelRevolutionDataSupported = 0x00000004
        const val CrankRevolutionDataSupported = 0x00000008
        const val ExtremeMagnitudesSupported = 0x00000010
        const val ExtremeAnglesSupported = 0x00000020
        const val TopAndBottomDeadSpotAnglesSupported = 0x00000040
        const val AccumulatedEnergySupported = 0x00000080
        const val OffsetCompensationIndicatorSupported = 0x00000100
        const val OffsetCompensationSupported = 0x00000200
        const val CyclingPowerMeasurementCharacteristicContentMaskingSupported = 0x00000400
        const val MultipleSensorLocationsSupported = 0x00000800
        const val CrankLengthAdjustmentSupported = 0x00001000
        const val ChainLengthAdjustmentSupported = 0x00002000
        const val ChainWeightAdjustmentSupported = 0x00004000
        const val SpanLengthAdjustmentSupported = 0x00008000
        const val SensorMeasurementContext = 0x00010000
        const val InstantaneousMeasurementDirectionSupported = 0x00020000
        const val FactoryCalibrationDateSupported = 0x00040000
        const val EnhancedOffsetCompensationSupported = 0x00080000
        const val DistributedSystemSupport = 0x00300000
    }
}
