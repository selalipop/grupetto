package com.spop.poverlay.sensor

import android.os.IBinder
import timber.log.Timber

private const val SpuriousReadingThreshold = 40
private const val SpuriousReadingDelta = 100
private const val SpuriousReadingMaxRejections = 30

class PowerSensor(binder: IBinder) : RepeatingFloatV1Sensor(Command.GetPowerRepeating, binder) {
    var lastSensorPower = 0f
    var consecutiveRejections = 0

    override fun mapFloat(value: Float): Float {
        val power = value / 10f

        //At low speeds the sensor occasionally sends an incorrect spike in values
        //This filters for such cases
        return if (lastSensorPower < SpuriousReadingThreshold
            && power - lastSensorPower > SpuriousReadingDelta
            && consecutiveRejections < SpuriousReadingMaxRejections
        ) {
            consecutiveRejections++
            Timber.w("Ignoring spurious sensor data #$consecutiveRejections, $power")
            lastSensorPower
        } else {
            consecutiveRejections = 0
            lastSensorPower = power
            power
        }
    }
}