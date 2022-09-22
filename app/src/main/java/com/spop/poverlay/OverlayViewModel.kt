package com.spop.poverlay

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spop.poverlay.sensor.SensorInterface
import com.spop.poverlay.util.smooth
import com.spop.poverlay.util.tickerFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds


private const val MphToKph = 1.60934

class OverlayViewModel(application: Application, private val sensorInterface: SensorInterface) :
    AndroidViewModel(application) {

    companion object {
        //The sensor does not necessarily return new value this quickly
        val GraphUpdatePeriod = 200.milliseconds

        //Max number of points before data starts to shift
        const val GraphMaxDataPoints = 300

        private const val SmoothingFactor = 8f
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

    var useMph = MutableStateFlow(true)
    val powerValue = sensorInterface.power
        .smooth(sensorNoise = SmoothingFactor).map { "%.1f".format(it) }
    val rpmValue = sensorInterface.cadence
        .smooth(sensorNoise = SmoothingFactor).map { "%.0f".format(it) }

    val speedValue = combine(
        sensorInterface.speed.smooth(sensorNoise = SmoothingFactor), useMph
    ) { speed, isMph ->
        val value = if (isMph) {
            speed
        } else {
            speed * MphToKph
        }
        "%.1f".format(value)
    }
    val speedLabel = useMph.map {
        if(it){
            "mph"
        }else{
            "kph"
        }
    }
    val resistanceValue = sensorInterface.resistance
        .smooth(sensorNoise = SmoothingFactor).map { "%.0f".format(it) }

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
                sensorInterface.power.smooth(),
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

