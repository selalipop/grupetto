package com.spop.poverlay.sensor

import android.os.Bundle
import android.os.IBinder
import timber.log.Timber

class ResistanceSensor(binder: IBinder) : Sensor<Float>(Command.GetResistanceRepeating, binder) {
    override fun mapData(data: Bundle): Float? {
        val hexString = data.getString(SensorBundleKey.HexResponse.bundleKey)
        if (hexString == null) {
            Timber.i("ignoring missing resistance sensor data")
            return null
        }
        if (hexString == "TIME_OUT") {
            throw SensorException("resistance sensor timeout")
        }

        return parseResponseV1(hexString)?.let { it / 10f }
            ?: throw SensorException("cannot parse sensor output $hexString")
    }

    //Repeating command, never completes
    override fun isComplete() = false
}