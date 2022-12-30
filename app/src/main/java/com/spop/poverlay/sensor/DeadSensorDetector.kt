package com.spop.poverlay.sensor

import com.spop.poverlay.sensor.interfaces.SensorInterface
import com.spop.poverlay.util.selectForever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * At times the Peloton sensor service stops responding until the Bike is power cycled
 * This class monitors for this
 */
class DeadSensorDetector(
    private val sensorInterface: SensorInterface,
    override val coroutineContext: CoroutineContext,
) : CoroutineScope {

    // Must be a SharedFlow to ensure duplicate values are sent
    private val mutableDeadSensorDetected = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Emits when a dead sensor is detected
     * Guaranteed not to fire more often than DeadSensorWarningInterval
     */
    val deadSensorDetected = mutableDeadSensorDetected.asSharedFlow()

    companion object {
        // Max time between sensor updates before the user is shown the dead sensor message
        val DeadSensorTimeout = 10.seconds

        // Once the dead sensor warning has been shown,
        // wait at least this long before showing it again
        val DeadSensorWarningInterval = 5.minutes
    }

    private val resetTimeoutChannel = Channel<Unit>()
    private var lastDeadSensorMessageMillis: Long? = null

    init {
        setupTimeoutReset()
        setupDeadSensorDetection()
    }

    // Whenever a value is received for a sensor, reset the dead sensor timeout
    private fun setupTimeoutReset() {
        launch(Dispatchers.IO) {
            sensorInterface.power.collect {
                resetTimeoutChannel.trySend(Unit)
            }
        }
        launch(Dispatchers.IO) {
            sensorInterface.resistance.collect {
                resetTimeoutChannel.trySend(Unit)
            }
        }
        launch(Dispatchers.IO) {
            sensorInterface.cadence.collect {
                resetTimeoutChannel.trySend(Unit)
            }
        }
    }

    // When the Bike is left on for a few days, occasionally the system stops being able to respond
    // If DeadSensorTimeout passes without any messages being received, show an error message
    private fun setupDeadSensorDetection() =
        launch(Dispatchers.IO) {
            selectForever<Unit> {
                resetTimeoutChannel.onReceive { }  // Do nothing if the timeout was reset

                onTimeout(DeadSensorTimeout.inWholeMilliseconds) {
                    // Check 'DeadSensorWarningInterval' has passed
                    // since the last error emission
                    val canShowMessage = lastDeadSensorMessageMillis?.let {
                        System.currentTimeMillis() - it >
                                DeadSensorWarningInterval.inWholeMilliseconds
                    } ?: true

                    if (canShowMessage) {
                        lastDeadSensorMessageMillis = System.currentTimeMillis()
                        mutableDeadSensorDetected.tryEmit(Unit)
                    }
                }
            }
        }
}