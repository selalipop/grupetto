package com.spop.poverlay.util

import kotlin.math.pow
import kotlin.math.sqrt

fun calculateSpeedFromPower(it: Float) =
    if (it < 0.1f) {
        0f
    } else {
        //https://ihaque.org/posts/2020/12/25/pelomon-part-ib-computing-speed/
        val r = sqrt(it)
        if (it < 26f) {
            0.057f - (0.172f * r) + (0.759f * r.pow(2)) - (0.079f * r.pow(3))
        } else {
            -1.635f + (2.325f * r) - (0.064f * r.pow(2)) + (0.001f * r.pow(3))
        }
    }