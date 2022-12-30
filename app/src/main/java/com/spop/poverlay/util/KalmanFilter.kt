package com.spop.poverlay.util

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

    private val initialSensorNoise = sensorNoise
    private val initialProcessNoise = processNoise
    private val initialEstimatedError = estimatedError

    fun resetParameters() {
        sensorNoise = initialSensorNoise
        processNoise = initialProcessNoise
        estimatedError = initialEstimatedError
    }

    fun update(value: Float): Float {
        estimatedError += processNoise
        kalmanGain = estimatedError / (estimatedError + sensorNoise.noise)
        currentValue += kalmanGain * (value - currentValue)
        estimatedError *= (1 - kalmanGain)
        return currentValue
    }
}
