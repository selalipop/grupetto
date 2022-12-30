package com.spop.poverlay.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.SelectBuilder
import kotlinx.coroutines.selects.select
import kotlin.math.abs

suspend fun <T> CoroutineScope.selectForever(selectBuilder: SelectBuilder<T>.()->Unit){
    while (isActive){
        select(selectBuilder)
    }
}

/**
 * Smooth a sensor value using a Kalman filter
 *
 * If the sensor hasn't reported a change in at least parameterTimeout milliseconds,
 * the parameters for the Kalman filter are reset
 *
 * Without this timeout, the filter would get slower and slower to respond to changes in values
 */
fun Flow<Float>.smoothSensorValue(
    processNoise: Float = 20f,
    sensorNoise: KalmanSmoothFactor = KalmanSmoothFactor.Minimal,
    estimatedError: Float = 20f,
    parameterTimeout : Long = 30000L
): Flow<Float> {
    var lastNonZeroMs = System.currentTimeMillis()
    val kalmanFilter = KalmanFilter(processNoise, sensorNoise, estimatedError)
    return map { sensorValue: Float ->
        if (System.currentTimeMillis() - lastNonZeroMs > parameterTimeout) {
            kalmanFilter.resetParameters()
        }
        if (abs(sensorValue) > 0.01f) {
            lastNonZeroMs = System.currentTimeMillis()
        }

        kalmanFilter.update(sensorValue)
    }
}