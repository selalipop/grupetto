package com.spop.poverlay.util

import android.os.Build
import kotlin.math.pow
import kotlin.math.sqrt


private const val PelotonBrand = "Peloton"

val IsRunningOnPeloton = Build.BRAND == PelotonBrand

fun calculateSpeedFromPelotonV1Power(power: Float) =
    if (power < 0.1f) {
        0f
    } else {
        //https://ihaque.org/posts/2020/12/25/pelomon-part-ib-computing-speed/
        val pwrSqrt = sqrt(power)
        if (power < 26f) {
            0.057f - (0.172f * pwrSqrt) + (0.759f * pwrSqrt.pow(2)) - (0.079f * pwrSqrt.pow(3))
        } else {
            -1.635f + (2.325f * pwrSqrt) - (0.064f * pwrSqrt.pow(2)) + (0.001f * pwrSqrt.pow(3))
        }
    }