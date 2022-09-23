package com.spop.poverlay.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

//https://web.archive.org/web/20120724084346/http://interactive-matter.eu/blog/2009/12/18/filtering-sensor-data-with-a-kalman-filter/
//https://github.com/bachagas/Kalman/blob/master/Kalman.h
class KalmanFilter(
    private var processNoise: Float = 0.125f,
    private var sensorNoise: KalmanSmoothFactor = KalmanSmoothFactor.Normal,
    private var estimatedError: Float = 20f,
    initialValue: Float = 0f
) {
    var kalmanGain = 0f
    var currentValue = initialValue
    fun update(value: Float): Float {
        estimatedError += processNoise
        kalmanGain = estimatedError / (estimatedError + sensorNoise.noise)
        currentValue += kalmanGain * (value - currentValue)
        estimatedError *= (1 - kalmanGain)
        return currentValue
    }
}

fun Flow<Float>.smooth(
    processNoise: Float = 0.125f,
    sensorNoise: KalmanSmoothFactor = KalmanSmoothFactor.Normal,
    estimatedError: Float = 20f
): Flow<Float> {
    val kalmanFilter = KalmanFilter(processNoise, sensorNoise, estimatedError)
    return map {
        kalmanFilter.update(it)
    }
}