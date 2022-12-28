package com.spop.poverlay.util

//https://web.archive.org/web/20120724084346/http://interactive-matter.eu/blog/2009/12/18/filtering-sensor-data-with-a-kalman-filter/
//https://github.com/bachagas/Kalman/blob/master/Kalman.h
class KalmanFilter(
    var processNoise: Float = 0.125f,
    var sensorNoise: KalmanSmoothFactor = KalmanSmoothFactor.Normal,
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
