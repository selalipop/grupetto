package com.spop.poverlay.sensor

import android.os.IBinder

class RpmSensor(binder: IBinder) : Sensor(Command.GetRpmRepeating, binder) {
    override fun mapValue(value: Float) = value
}