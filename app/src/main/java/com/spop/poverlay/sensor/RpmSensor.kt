package com.spop.poverlay.sensor

import android.os.Bundle
import android.os.IBinder
import timber.log.Timber

class RpmSensor(binder: IBinder) : Sensor<Float>(Command.GetRpmRepeating, binder) {
    override fun mapData(data: Bundle): Float? {
        val hexString = data.getString(SensorBundleKey.HexResponse.bundleKey)
        if (hexString == null){
            Timber.i("ignoring missing rpm sensor data")
            return null
        }
        if (hexString == "TIME_OUT"){
            throw SensorException("rpm sensor timeout")
        }

        return parseResponseV1(hexString)?.let { it / 10f }
            ?: throw SensorException("cannot parse rpm sensor output $hexString")
    }

    //Repeating command, never completes
    override fun isComplete() = false
}