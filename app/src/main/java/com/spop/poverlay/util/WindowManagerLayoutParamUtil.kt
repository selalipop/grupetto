package com.spop.poverlay.util

import android.view.WindowManager
import timber.log.Timber

/**
 * Disables a hidden animation flag set on all window manager layout parameters...
 * https://stackoverflow.com/a/63880503/3808828
 */
fun WindowManager.LayoutParams.disableAnimations(){
    try {
        val layoutParamsClass = WindowManager.LayoutParams::class.java
        val privateFlagField = layoutParamsClass.getField("privateFlags")
        val noAnimationFlag = layoutParamsClass.getField("PRIVATE_FLAG_NO_MOVE_ANIMATION")

        var privateFlagsValue = privateFlagField.getInt(this)
        val noAnimFlag = noAnimationFlag.getInt(this)
        privateFlagsValue = privateFlagsValue or noAnimFlag
        privateFlagField.setInt(this, privateFlagsValue)
    } catch (e: Exception) {
        Timber.e("Failed to disable WindowManager animations: ${e.localizedMessage}")
    }
}