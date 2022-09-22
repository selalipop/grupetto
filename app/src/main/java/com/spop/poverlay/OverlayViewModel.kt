package com.spop.poverlay

import android.app.Application
import android.content.Intent
import android.os.IBinder
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spop.poverlay.sensor.*
import com.spop.poverlay.util.calculateSpeedFromPower
import com.spop.poverlay.util.smooth
import com.spop.poverlay.util.tickerFlow
import com.spop.poverlay.util.windowed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

class OverlayViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        //The sensor does not necessarily return new value this quickly
        val GraphUpdatePeriod = 200.milliseconds
        //Max number of points before data starts to shift
        const val GraphMaxDataPoints = 300
    }

    private val mutableIsVisible = MutableStateFlow(true)
    val isVisible = mutableIsVisible.asStateFlow()
    fun onOverlayPressed() {
        mutableIsVisible.apply { value = !value }
    }

    fun onOverlayLongPress() {
        getApplication<Application>().apply {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }

    private val binder = MutableSharedFlow<IBinder>(replay = 1)
    private val powerSensor = binder.flatMapLatest {
        val powerSensor = PowerSensor(it)
        powerSensor.start()
        powerSensor.sensorValue
    }.shareIn(viewModelScope, SharingStarted.Eagerly)

    val powerValue = powerSensor.sensorValueToString("%.1f")


    val rpmValue = binder.filterNotNull().flatMapLatest {
        val rpmSensor = RpmSensor(it)
        rpmSensor.start()
        rpmSensor.sensorValue
    }.sensorValueToString("%.0f").shareIn(viewModelScope, SharingStarted.Eagerly)

    val speedValue = powerSensor
        .map { value -> value.map(::calculateSpeedFromPower) }
        .sensorValueToString("%.1f")


    private val resistanceSensor = binder.filterNotNull().flatMapLatest {
        val resistanceSensor = ResistanceSensor(it)
        resistanceSensor.start()
        resistanceSensor.sensorValue

    }

    val powerGraph = mutableStateListOf<Float>()
    init {
        setupPowerGraphData()
    }

    private fun setupPowerGraphData() {
        viewModelScope.launch(Dispatchers.IO) {
            //Sensor value is read every tick and added to graph
            combine(
                powerSensor.sensorValueToFloat().smooth(),
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

    val resistanceValue = resistanceSensor
        .sensorValueToString("%.0f", windowSize = 1)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val service = getBinder(application)
            binder.emit(service)
        }
    }

    private fun Flow<Result<Float?>>.sensorValueToString(
        formatString: String,
        windowSize: Int = 1,
        lowFloor: Float? = null
    ) = sensorValueToFloat()
        .windowed(windowSize, 1, partialWindows = true)
        .map { it.average().toFloat() }
        .map {
            if (lowFloor != null && it < lowFloor) {
                "Low"
            } else {
                formatString.format(it)
            }
        }

    private fun Flow<Result<Float?>>.sensorValueToFloat() =
        filterNotNull()
            .map { value -> value.getOrThrow() }
            .retry {
                if (it is SensorException) {
                    Timber.e(it, "sensor exception:")
                    return@retry true
                }
                return@retry false
            }
            .filterNotNull()

}

