package com.spop.poverlay.sensor.interfaces

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 * Used to generate fake data on the emulator, creates a sin wave of values
 */
@OptIn(ExperimentalTime::class)
class DummySensorInterface : SensorInterface {
    override val power: Flow<Float>
        get() = dummyValueFlow(200f)
    override val cadence: Flow<Float>
        get() = dummyValueFlow(150f)
    override val resistance: Flow<Float>
        get() = dummyValueFlow(100f)


    private fun dummyValueFlow(magnitude : Float) = flow {
        val sineValues = generateSequence(0..360 step 10) { it }.flatten().map {
            (sin(Math.toRadians(it.toDouble())) + 1) * (magnitude / 2)
        }
        for(value in sineValues){
            delay(Duration.milliseconds(100))
            emit(value.toFloat())
        }
    }
}
