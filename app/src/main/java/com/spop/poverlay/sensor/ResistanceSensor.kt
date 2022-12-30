package com.spop.poverlay.sensor

import android.os.IBinder

class ResistanceSensor(binder: IBinder) : Sensor(Command.GetResistanceRepeating, binder) {
    override fun mapValue(value: Float) = value
}