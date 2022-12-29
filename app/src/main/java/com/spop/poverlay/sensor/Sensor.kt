package com.spop.poverlay.sensor

import android.os.*
import androidx.core.os.bundleOf
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.util.*

/**
 * Handles the flow of sending a message to the Sensor Service linked via Binder
 * And receiving a result on a HandlerThread
 */
abstract class Sensor<T>(private val command: Command, private val binder: IBinder) {
    companion object{
        // Each request should have a unique ID that represents it
        const val RequestIdBundleKey = "requestId"
    }

    private val sensorThread = HandlerThread("PelotonSensor-${command.name}-${UUID.randomUUID()}")

    private val mutableSensorValue = MutableSharedFlow<Result<T?>>(replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val sensorValue = mutableSensorValue
        .asSharedFlow()

    protected abstract fun mapData(data: Bundle): T?
    protected abstract fun isComplete(): Boolean

    fun start() {
        sensorThread.start()
        val handler = object : Handler(sensorThread.looper) {
            override fun handleMessage(message: Message) {
                if (isComplete()) {
                    Timber.w("discarding message for completed sensor $message")
                }
                try {
                    val mappedValue = mapData(message.data)

                    if (mappedValue == null) {
                        Timber.w("failed to map sensor value $message")
                        return
                    }

                    mutableSensorValue.tryEmit(Result.success(mappedValue))
                } catch (sensorException: RecoverableSensorException) {
                    mutableSensorValue.tryEmit(Result.failure(sensorException))
                }

                if (message.what < 0 || isComplete()) {
                    Timber.w("closing sensor thread after request " +
                            "What:${message.what} IsComplete:${isComplete()}")
                    stop()
                }
            }
        }

        Messenger(binder).send(Message().apply {
            what = command.id
            data = bundleOf(
                RequestIdBundleKey to UUID.randomUUID().toString()
            )
            replyTo = Messenger(handler)
        })
    }

    fun stop() {
        sensorThread.looper.quitSafely()
    }
}