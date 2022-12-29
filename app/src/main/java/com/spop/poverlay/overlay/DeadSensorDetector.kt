package com.spop.poverlay.overlay

import com.spop.poverlay.sensor.SensorInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DeadSensorDetector(
    private val sensorInterface: SensorInterface,
    override val coroutineContext: CoroutineContext,
) : CoroutineScope {

    private val mutableDeadSensorDetected = MutableSharedFlow<Unit>()
    val deadSensorDetected = mutableDeadSensorDetected.asSharedFlow()

    companion object {
        // Max time between sensor updates before the user is shown the dead sensor message
        val DeadSensorTimeout = 15.seconds

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
    private fun setupDeadSensorDetection() {
        launch(Dispatchers.IO) {
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
                            mutableDeadSensorDetected.tryEmit(Unit)
                        }
                    }
                }

            }
        }

    }
}