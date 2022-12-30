package com.spop.poverlay.overlay

import android.app.Application
import android.text.format.DateUtils
import androidx.lifecycle.AndroidViewModel
import com.spop.poverlay.ConfigurationRepository
import com.spop.poverlay.util.tickerFlow
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.seconds

open class OverlayTimerViewModel(
    application: Application,
    private val configurationRepository: ConfigurationRepository
) : AndroidViewModel(application) {
    val showTimerWhenMinimized
        get() = configurationRepository.showTimerWhenMinimized
    private val timerEnabled = MutableStateFlow(false)
    private val mutableTimerPaused = MutableStateFlow(false)
    val timerPaused = mutableTimerPaused.asSharedFlow()
    val timerLabel = timerEnabled.flatMapLatest {
        if (it) {
            tickerFlow(period = 1.seconds)
                .filter { !mutableTimerPaused.value }
                .runningFold(0L) { acc, _ -> acc + 1L }
                .map { seconds ->
                    DateUtils.formatElapsedTime(seconds)
                }
        } else {
            flow {
                emit("‒ ‒:‒ ‒")
            }
        }
    }

    fun onTimerTap() {
        if (timerEnabled.value) {
            toggleTimer()
        } else {
            resumeTimer()
        }
    }

    fun onTimerLongPress() {
        if (timerEnabled.value) {
            stopTimer()
        } else {
            resumeTimer()
        }
    }

    private fun stopTimer() {
        timerEnabled.value = false
        mutableTimerPaused.value = false
    }

    private fun resumeTimer() {
        mutableTimerPaused.value = false
        timerEnabled.value = true
    }

    private fun toggleTimer() {
        mutableTimerPaused.value = !mutableTimerPaused.value
    }
}