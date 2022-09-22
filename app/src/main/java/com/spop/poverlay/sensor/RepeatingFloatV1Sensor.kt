package com.spop.poverlay.sensor

import android.os.Bundle
import android.os.IBinder
import timber.log.Timber

//Most values follow the same pattern on a V1 bike
abstract class RepeatingFloatV1Sensor(private val command: Command, binder: IBinder) :
    Sensor<Float>(command, binder) {

    override fun mapData(data: Bundle): Float? {
        val hexString = data.getString(SensorBundleKey.HexResponse.bundleKey)
        if (hexString == null) {
            Timber.i("ignoring missing float sensor data for command $command")
            return null
        }
        if (hexString == "TIME_OUT") {
            throw SensorException("sensor command timed out: $command")
        }

        return parseResponseV1(hexString)?.let { mapFloat(it) }
            ?: throw SensorException("cannot parse sensor output $hexString for command $command")
    }

    protected abstract fun mapFloat(value: Float) : Float

    //Repeating command, never completes
    override fun isComplete() = false
}