package com.spop.poverlay.sensor

import android.os.IBinder

class RpmSensor(binder: IBinder) : RepeatingFloatV1Sensor(Command.GetRpmRepeating, binder) {
    override fun mapFloat(value: Float) = value / 10f
}