package com.spop.poverlay.overlay

import android.app.Application
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spop.poverlay.ConfigurationRepository
import com.spop.poverlay.MainActivity
import com.spop.poverlay.sensor.SensorInterface
import com.spop.poverlay.util.tickerFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


private const val MphToKph = 1.60934

//For a yet unknown reason, removing val from configurationRepository breaks the class.
@Suppress("CanBeParameter")
class OverlaySensorViewModel(
    application: Application,
    private val sensorInterface: SensorInterface,
    private val configurationRepository: ConfigurationRepository
) :
    AndroidViewModel(application) {
    companion object {
        //The sensor does not necessarily return new value this quickly
        val GraphUpdatePeriod = 200.milliseconds

        //Max number of points before data starts to shift
        const val GraphMaxDataPoints = 300
    }

    val showTimerWhenMinimized
        get() = configurationRepository.showTimerWhenMinimized

    private val mutableIsVisible = MutableStateFlow(true)
    val isVisible = mutableIsVisible.asStateFlow()

    fun onOverlayPressed() {
        mutableIsVisible.apply { value = !value }
    }

    fun onOverlayDoubleTap() {
        getApplication<Application>().apply {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
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

    private fun toggleTimer() {
        mutableTimerPaused.value = !mutableTimerPaused.value
    }


    private var useMph = MutableStateFlow(true)

    val powerValue = sensorInterface.power
        .map { "%.0f".format(it) }
    val rpmValue = sensorInterface.cadence
        .map { "%.0f".format(it) }

    val resistanceValue = sensorInterface.resistance
        .map { "%.0f".format(it) }

    val speedValue = combine(
        sensorInterface.speed, useMph
    ) { speed, isMph ->
        val value = if (isMph) {
            speed
        } else {
            speed * MphToKph
        }
        "%.1f".format(value)
    }
    val speedLabel = useMph.map {
        if (it) {
            "mph"
        } else {
            "kph"
        }
    }

    fun onClickedSpeed() {
        viewModelScope.launch {
            useMph.emit(!useMph.value)
        }
    }

    val powerGraph = mutableStateListOf<Float>()

    init {
        setupPowerGraphData()
    }

    private fun setupPowerGraphData() {
        viewModelScope.launch(Dispatchers.IO) {
            //Sensor value is read every tick and added to graph
            combine(
                sensorInterface.power,
                tickerFlow(GraphUpdatePeriod)
            ) { sensorValue, _ -> sensorValue }.collect { value ->
                withContext(Dispatchers.Main) {
                    powerGraph.add(value)
                    if (powerGraph.size > GraphMaxDataPoints) {
                        powerGraph.removeFirst()
                    }
                }
            }
        }
    }

}

