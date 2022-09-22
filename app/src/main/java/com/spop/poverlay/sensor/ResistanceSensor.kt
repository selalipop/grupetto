package com.spop.poverlay.sensor

import android.os.IBinder

class ResistanceSensor(binder: IBinder) : RepeatingFloatV1Sensor(Command.GetResistanceRepeating, binder) {
    override fun mapFloat(value: Float): Float  = value / 10
}