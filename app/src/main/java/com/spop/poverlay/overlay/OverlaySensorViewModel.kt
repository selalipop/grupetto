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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


private const val MphToKph = 1.60934


class OverlaySensorViewModel(
    application: Application,
    private val sensorInterface: SensorInterface,
    private val configurationRepository: ConfigurationRepository
) :
    AndroidViewModel(application) {

    companion object {
        // The sensor does not necessarily return new value this quickly
        val GraphUpdatePeriod = 200.milliseconds

        // Max number of points before data starts to shift
        const val GraphMaxDataPoints = 300

        // Max time between sensor updates before the user is shown the dead sensor message
        val DeadSensorTimeout = 15.seconds

        // Once the dead sensor warning has been shown,
        // wait at least this long before showing it again
        val DeadSensorWarningInterval = 5.minutes
    }

    private val resetTimeoutChannel = Channel<Unit>()

    init {
        setupTimeoutReset()
        setupDeadSensorDetection()
        setupPowerGraphData()
    }

    // Whenever a value is received for a sensor, reset the dead sensor timeout
    private fun setupTimeoutReset() {
        viewModelScope.launch(Dispatchers.IO) {
            sensorInterface.power.collect {
                resetTimeoutChannel.trySend(Unit)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            sensorInterface.resistance.collect {
                resetTimeoutChannel.trySend(Unit)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            sensorInterface.cadence.collect {
                resetTimeoutChannel.trySend(Unit)
            }
        }
    }

    private var lastDeadSensorMessageMillis: Long? = null

    // When the Bike is left on for a few days, occasionally the system stops being able to respond
    // If DeadSensorTimeout passes without any messages being received, show an error message
    private fun setupDeadSensorDetection() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                select<Unit> {
                    resetTimeoutChannel.onReceive { }  // Do nothing if the timeout was reset

                    onTimeout(DeadSensorTimeout.inWholeMilliseconds) {
                        // Check 'DeadSensorWarningInterval' has passed
                        // since the last error message
                        val canShowMessage = lastDeadSensorMessageMillis?.let {
                            System.currentTimeMillis() - it >
                                    DeadSensorWarningInterval.inWholeMilliseconds
                        } ?: true

                        if (canShowMessage) {
                            lastDeadSensorMessageMillis = System.currentTimeMillis()
                            mutableErrorMessage
                                .tryEmit(
                                    "The sensors seem to have stopped responding," +
                                            " you may need to restart the bike by removing the " +
                                            " power adapter momentarily"
                                )
                        }
                    }
                }

            }
        }

    }


    val showTimerWhenMinimized
        get() = configurationRepository.showTimerWhenMinimized

    private val mutableIsVisible = MutableStateFlow(true)
    val isVisible = mutableIsVisible.asStateFlow()

    private val mutableErrorMessage = MutableStateFlow<String?>(null)
    val errorMessage = mutableErrorMessage.asStateFlow()

    fun onDismissErrorPressed() {
        mutableErrorMessage.tryEmit(null)
    }

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

