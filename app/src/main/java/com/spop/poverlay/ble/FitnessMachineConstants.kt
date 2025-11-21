package com.spop.poverlay.ble

import java.util.UUID

object FitnessMachineConstants {
    val ServiceUUID: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    val IndoorBikeDataUUID: UUID = UUID.fromString("00002ad2-0000-1000-8000-00805f9b34fb")
    val FeatureUUID: UUID = UUID.fromString("00002acc-0000-1000-8000-00805f9b34fb")
    val ControlPointUUID: UUID = UUID.fromString("00002ad9-0000-1000-8000-00805f9b34fb")
    val SupportedResistanceRangeUUID: UUID = UUID.fromString("00002ad6-0000-1000-8000-00805f9b34fb")
    val TrainingStatusUUID: UUID = UUID.fromString("00002ad3-0000-1000-8000-00805f9b34fb")
    val ClientCharacteristicConfigurationUUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    object IndoorBikeDataFlags {
        const val MoreData = 1 shl 0
        const val AverageSpeedPresent = 1 shl 1
        const val InstantaneousCadencePresent = 1 shl 2
        const val AverageCadencePresent = 1 shl 3
        const val TotalDistancePresent = 1 shl 4
        const val ResistanceLevelPresent = 1 shl 5
        const val InstantaneousPowerPresent = 1 shl 6
        const val AveragePowerPresent = 1 shl 7
        const val ExpendedEnergyPresent = 1 shl 8
        const val HeartRatePresent = 1 shl 9
        const val MetabolicEquivalentPresent = 1 shl 10
        const val ElapsedTimePresent = 1 shl 11
        const val RemainingTimePresent = 1 shl 12
    }

    object FeatureFlags {
        const val AverageSpeedSupported = 1 shl 0
        const val CadenceSupported = 1 shl 1
        const val TotalDistanceSupported = 1 shl 2
        const val InclinationSupported = 1 shl 3
        const val ElevationGainSupported = 1 shl 4
        const val PaceSupported = 1 shl 5
        const val StepCountSupported = 1 shl 6
        const val ResistanceLevelSupported = 1 shl 7
        const val StrideCountSupported = 1 shl 8
        const val ExpendedEnergySupported = 1 shl 9
        const val HeartRateMeasurementSupported = 1 shl 10
        const val MetabolicEquivalentSupported = 1 shl 11
        const val ElapsedTimeSupported = 1 shl 12
        const val RemainingTimeSupported = 1 shl 13
        const val PowerMeasurementSupported = 1 shl 14
        const val ForceOnBeltAndPowerOutputSupported = 1 shl 15
        const val UserDataRetentionSupported = 1 shl 16
    }

    object TrainingStatus {
        const val Other = 0x00
        const val Idle = 0x01
        const val WarmingUp = 0x02
        const val LowIntensityInterval = 0x03
        const val HighIntensityInterval = 0x04
        const val RecoveryInterval = 0x05
        const val Isometric = 0x06
        const val HeartRateControl = 0x07
        const val FitnessTest = 0x08
        const val SpeedOutsideOfControlRegionLow = 0x09
        const val SpeedOutsideOfControlRegionHigh = 0x0A
        const val CoolDown = 0x0B
        const val WattControl = 0x0C
        const val ManualMode = 0x0D
        const val PreWorkout = 0x0E
        const val PostWorkout = 0x0F
    }

    object FitnessMachineControlPointResultCode {
        const val ReservedForFutureUse = 0x00
        const val Success = 0x01
        const val OpCodeNotSupported = 0x02
        const val InvalidParameter = 0x03
        const val OperationFailed = 0x04
        const val ControlNotPermitted = 0x05
        // Reserved for Future Use = 0x06-0xFF
    }

    object FitnessMachineTargetFlags {
        const val SpeedTargetSettingSupported = 1 shl 0
        const val InclinationTargetSettingSupported = 1 shl 1
        const val ResistanceTargetSettingSupported = 1 shl 2
        const val PowerTargetSettingSupported = 1 shl 3
        const val HeartRateTargetSettingSupported = 1 shl 4
        const val TargetedExpendedEnergyConfigurationSupported = 1 shl 5
        const val TargetedStepNumberConfigurationSupported = 1 shl 6
        const val TargetedStrideNumberConfigurationSupported = 1 shl 7
        const val TargetedDistanceConfigurationSupported = 1 shl 8
        const val TargetedTrainingTimeConfigurationSupported = 1 shl 9
        const val TargetedTimeTwoHeartRateZonesConfigurationSupported = 1 shl 10
        const val TargetedTimeThreeHeartRateZonesConfigurationSupported = 1 shl 11
        const val TargetedTimeFiveHeartRateZonesConfigurationSupported = 1 shl 12
        const val IndoorBikeSimulationParametersSupported = 1 shl 13
        const val WheelCircumferenceConfigurationSupported = 1 shl 14
        const val SpinDownControlSupported = 1 shl 15
        const val TargetedCadenceConfigurationSupported = 1 shl 16
    }

    object FitnessMachineControlPointProcedure {
        const val RequestControl = 0x00
        const val Reset = 0x01
        const val SetTargetSpeed = 0x02
        const val SetTargetInclination = 0x03
        const val SetTargetResistanceLevel = 0x04
        const val SetTargetPower = 0x05
        const val SetTargetHeartRate = 0x06
        const val StartOrResume = 0x07
        const val StopOrPause = 0x08
        const val SetIndoorBikeSimulationParameters = 0x11
        const val SetWheelCircumference = 0x12
        const val SpinDownControl = 0x13
        const val SetTargetedCadence = 0x14
        // Reserved for Future Use 0x15-0x7F
        const val ResponseCode = 0x80
        // Reserved for Future Use 0x81-0xFF
    }

    object FitnessMachineStatus {
        const val ReservedForFutureUse = 0x00
        const val Reset = 0x01
        const val StoppedOrPausedByUser = 0x02
        const val StoppedBySafetyKey = 0x03
        const val StartedOrResumedByUser = 0x04
        const val TargetSpeedChanged = 0x05
        const val TargetInclineChanged = 0x06
        const val TargetResistanceLevelChanged = 0x07
        const val TargetPowerChanged = 0x08
        const val TargetHeartRateChanged = 0x09
        const val TargetedExpendedEnergyChanged = 0x0A
        const val TargetedNumberofStepsChanged = 0x0B
        const val TargetedNumberofStridesChanged = 0x0C
        const val TargetedDistanceChanged = 0x0D
        const val TargetedTrainingTimeChanged = 0x0E
        const val TargetedTimeinTwoHeartRateZonesChanged = 0x0F
        const val TargetedTimeinThreeHeartRateZonesChanged = 0x10
        const val TargetedTimeinFiveHeartRateZonesChanged = 0x11
        const val IndoorBikeSimulationParametersChanged = 0x12
        const val WheelCircumferenceChanged = 0x13
        const val SpinDownStatus = 0x14
        const val TargetedCadenceChanged = 0x15
        // Reserved for Future Use 0x16-0xFE
        const val ControlPermissionLost = 0xFF
    }
}
