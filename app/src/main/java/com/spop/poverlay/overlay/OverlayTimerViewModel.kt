package com.spop.poverlay.overlay

import android.app.Application
import android.text.format.DateUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spop.poverlay.ConfigurationRepository
import com.spop.poverlay.util.tickerFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

open class OverlayTimerViewModel(
    application: Application,
    private val configurationRepository: ConfigurationRepository,
    powerFlow: Flow<Float>
) : AndroidViewModel(application) {
    companion object {
        // Power threshold to consider the user is actively pedaling (in watts)
        private const val POWER_THRESHOLD = 5f
    }
    
    val showTimerWhenMinimized
        get() = configurationRepository.showTimerWhenMinimized
    private val timerEnabled = MutableStateFlow(false)
    private val mutableTimerPaused = MutableStateFlow(false)
    val timerPaused = mutableTimerPaused.asSharedFlow()
    
    // Expose elapsed seconds for calories calculation
    val elapsedSeconds = timerEnabled.flatMapLatest {
        if (it) {
            tickerFlow(period = 1.seconds)
                .filter { !mutableTimerPaused.value }
                .runningFold(0L) { acc, _ -> acc + 1L }
        } else {
            flow {
                emit(0L)
            }
        }
    }
    
    val timerLabel = elapsedSeconds.map { seconds ->
        if (timerEnabled.value) {
            DateUtils.formatElapsedTime(seconds)
        } else {
            "‒ ‒:‒ ‒"
        }
    }
    
    init {
        // Auto-start/stop timer based on power output
        viewModelScope.launch(Dispatchers.IO) {
            powerFlow.collect { power ->
                if (power > POWER_THRESHOLD) {
                    // User is pedaling - ensure timer is running
                    if (!timerEnabled.value) {
                        timerEnabled.value = true
                    }
                    if (mutableTimerPaused.value) {
                        mutableTimerPaused.value = false
                    }
                } else {
                    // Power is zero - pause the timer
                    if (timerEnabled.value && !mutableTimerPaused.value) {
                        mutableTimerPaused.value = true
                    }
                }
            }
        }
    }

    fun onTimerTap() {
        // Allow manual pause/resume override
        if (timerEnabled.value) {
            mutableTimerPaused.value = !mutableTimerPaused.value
        }
    }

    fun onTimerLongPress() {
        // Long press to reset timer to zero
        stopTimer()
    }

    private fun stopTimer() {
        timerEnabled.value = false
        mutableTimerPaused.value = false
    }
}
