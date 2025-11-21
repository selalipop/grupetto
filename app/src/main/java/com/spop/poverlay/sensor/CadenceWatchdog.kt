package com.spop.poverlay.sensor

import com.spop.poverlay.sensor.interfaces.SensorInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.hours

/**
 * Monitors cadence and triggers a restart if no cadence is detected for a specified duration.
 * This helps address BLE issues that occur after extended running time.
 */
class CadenceWatchdog(
    private val sensorInterface: SensorInterface,
    override val coroutineContext: CoroutineContext,
    private val inactivityThreshold: kotlin.time.Duration = 1.hours
) : CoroutineScope {

    private val mutableRestartTriggered = MutableSharedFlow<Unit>(replay = 0)
    
    /**
     * Emits when the watchdog determines a restart is needed
     */
    val restartTriggered = mutableRestartTriggered.asSharedFlow()

    private var lastCadenceTime: Long = System.currentTimeMillis()
    private var monitoringJob: Job? = null
    private var cadenceCollectionJob: Job? = null
    
    companion object {
        private const val CADENCE_THRESHOLD = 1.0f // RPM threshold to consider as "active"
    }

    fun start() {
        stop() // Ensure no duplicate jobs
        
        lastCadenceTime = System.currentTimeMillis()
        
        // Monitor cadence updates
        cadenceCollectionJob = launch(Dispatchers.IO) {
            sensorInterface.cadence.collect { cadence ->
                if (cadence >= CADENCE_THRESHOLD) {
                    lastCadenceTime = System.currentTimeMillis()
                    Timber.d("Watchdog: Cadence detected: $cadence RPM")
                }
            }
        }
        
        // Check periodically for inactivity
        monitoringJob = launch(Dispatchers.IO) {
            while (true) {
                delay(60_000) // Check every minute
                
                val inactivityDuration = System.currentTimeMillis() - lastCadenceTime
                val thresholdMillis = inactivityThreshold.inWholeMilliseconds
                
                Timber.d("Watchdog: Inactivity duration: ${inactivityDuration / 1000}s / ${thresholdMillis / 1000}s")
                
                if (inactivityDuration >= thresholdMillis) {
                    Timber.w("Watchdog: Inactivity threshold reached. Triggering restart.")
                    mutableRestartTriggered.emit(Unit)
                    // Only trigger once, then stop monitoring
                    stop()
                    break
                }
            }
        }
        
        Timber.i("Cadence watchdog started with ${inactivityThreshold.inWholeMinutes} minute threshold")
    }

    fun stop() {
        cadenceCollectionJob?.cancel()
        cadenceCollectionJob = null
        monitoringJob?.cancel()
        monitoringJob = null
        Timber.i("Cadence watchdog stopped")
    }
}
