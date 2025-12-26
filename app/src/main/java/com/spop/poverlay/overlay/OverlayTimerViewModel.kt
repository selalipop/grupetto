package com.spop.poverlay.overlay

import android.app.Application
import android.text.format.DateUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spop.poverlay.ConfigurationRepository
import com.spop.poverlay.util.tickerFlow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
open class OverlayTimerViewModel(
    application: Application,
    private val configurationRepository: ConfigurationRepository
) : AndroidViewModel(application) {
    val showTimerWhenMinimized
        get() = configurationRepository.showTimerWhenMinimized

    // Accumulated seconds (persists across pause/resume)
    private var accumulatedSeconds = 0L
    private val mutableAccumulatedSeconds = MutableStateFlow(0L)

    // Timer is running when moving
    private val mutableTimerRunning = MutableStateFlow(false)
    val timerPaused = mutableTimerRunning.map { !it }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        true
    )

    // Timer has started at least once this session
    private val mutableTimerStarted = MutableStateFlow(false)

    val timerLabel = combine(mutableTimerStarted, mutableAccumulatedSeconds) { started, seconds ->
        if (started) {
            DateUtils.formatElapsedTime(seconds)
        } else {
            "‒ ‒:‒ ‒"
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "‒ ‒:‒ ‒")

    init {
        // Tick every second when timer is running
        viewModelScope.launch {
            tickerFlow(period = Duration.seconds(1)).collect {
                if (mutableTimerRunning.value) {
                    accumulatedSeconds++
                    mutableAccumulatedSeconds.value = accumulatedSeconds
                }
            }
        }
    }

    /**
     * Called by OverlayService to observe movement state
     */
    fun observeMovement(isMoving: StateFlow<Boolean>, sessionReset: StateFlow<Long>) {
        viewModelScope.launch {
            isMoving.collect { moving ->
                if (moving) {
                    // Start/resume timer when movement starts
                    mutableTimerStarted.value = true
                    mutableTimerRunning.value = true
                } else {
                    // Pause timer when movement stops
                    mutableTimerRunning.value = false
                }
            }
        }

        viewModelScope.launch {
            sessionReset.drop(1).collect {
                // Reset timer on session reset (5-minute inactivity)
                resetTimer()
            }
        }
    }

    private fun resetTimer() {
        accumulatedSeconds = 0L
        mutableAccumulatedSeconds.value = 0L
        mutableTimerRunning.value = false
        mutableTimerStarted.value = false
    }

    fun onTimerTap() {
        // Manual tap toggles pause/resume
        if (mutableTimerStarted.value) {
            mutableTimerRunning.value = !mutableTimerRunning.value
        }
    }

    fun onTimerLongPress() {
        // Long press resets the timer
        resetTimer()
    }
}
