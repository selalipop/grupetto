package com.spop.poverlay.util

/*
Captures predefined values for Kalman filter implementation sensor noise
Heavier smoothing values result in an output that reacts to changes slower
 */
sealed class KalmanSmoothFactor(val noise: Float) {
    object Heavy : KalmanSmoothFactor(32f)
    object Normal : KalmanSmoothFactor(16f)
    object Light : KalmanSmoothFactor(8f)
    object Minimal : KalmanSmoothFactor(4f)
    class Custom(smoothNoise: Float) : KalmanSmoothFactor(smoothNoise)
}