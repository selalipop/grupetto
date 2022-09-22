package com.spop.poverlay.sensor

import android.os.Bundle
import android.os.IBinder
import timber.log.Timber
import kotlin.math.abs

private const val SpuriousReadingThreshold = 40
private const val SpuriousReadingDelta = 100
private const val SpuriousReadingMaxRejections = 30

class PowerSensor(binder: IBinder) : Sensor<Float>(Command.GetPowerRepeating, binder) {
    var lastSensorPower = 0f
    var consecutiveRejections = 0
    override fun mapData(data: Bundle): Float? {
        val hexString = data.getString(SensorBundleKey.HexResponse.bundleKey)
        if (hexString == null) {
            Timber.i("ignoring missing power sensor data")
            return null
        }
        if (hexString == "TIME_OUT") {
            throw SensorException("power sensor timeout")
        }

        return parseResponseV1(hexString)?.let {
            val power = it / 10f

            //At low speeds the sensor occasionally sends an incorrect spike in values
            //This filters for such cases
            if (power < SpuriousReadingThreshold
                && power - lastSensorPower > SpuriousReadingDelta
                && consecutiveRejections < SpuriousReadingMaxRejections
            ) {
                consecutiveRejections++
                Timber.w("Ignoring spurious sensor data #$consecutiveRejections, $power")
                return lastSensorPower
            } else {
                consecutiveRejections = 0
                lastSensorPower = power
                return power
            }
        }
            ?: throw SensorException("cannot parse sensor output $hexString")
    }

    //Repeating command, never completes
    override fun isComplete() = false
}