package com.spop.poverlay.sensor.interfaces

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

/**
 * Used to generate fake data on the emulator
 * Power pattern: Ramps up to 80W, holds for 10s, ramps down, pauses for 10s, repeats
 * This simulates interval training for testing auto-start/stop timer
 */
class DummySensorInterface : SensorInterface {
    override val power: Flow<Float>
        get() = intervalPowerFlow()
    override val cadence: Flow<Float>
        get() = dummyValueFlow(150f)
    override val resistance: Flow<Float>
        get() = dummyValueFlow(110f)

    private fun intervalPowerFlow() = flow {
        while (true) {
            // Ramp up: 0 to 80W over 2 seconds
            for (i in 0..20) {
                emit(i * 4f)  // 0, 4, 8, 12, ... 80
                delay(100.milliseconds)
            }
            
            // Hold at 80W for 10 seconds
            for (i in 0..100) {
                emit(80f)
                delay(100.milliseconds)
            }
            
            // Ramp down: 80 to 0W over 2 seconds
            for (i in 20 downTo 0) {
                emit(i * 4f)  // 80, 76, 72, ... 0
                delay(100.milliseconds)
            }
            
            // Rest at 0W for 10 seconds
            for (i in 0..100) {
                emit(0f)
                delay(100.milliseconds)
            }
        }
    }

    private fun dummyValueFlow(magnitude : Float) = flow {
        val sineValues = generateSequence(0..360 step 10) { it }.flatten().map {
            (sin(Math.toRadians(it.toDouble())) + 1) * (magnitude / 2)
        }
        for(value in sineValues){
            delay(100.milliseconds)
            emit(value.toFloat())
        }
    }
}