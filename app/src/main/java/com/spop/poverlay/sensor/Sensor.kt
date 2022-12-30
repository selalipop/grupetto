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
abstract class Sensor(private val command: Command, private val binder: IBinder) {
    companion object {
        // Sent to Peloton service to uniquely identify a request
        const val RequestIdBundleKey = "requestId"

        // Contains raw serial response for each sensor update sent by Peloton service
        const val HexResponseBundleKey = "responseHexString"

        // Contains epoch time of each sensor update sent by Peloton service
        const val TimeBundleKey = "time"

        // Pre-parsed value for sensor sent by Peloton service
        const val SensorValueBundleKey = "data"

        // Sent by Peloton service when a sensor has timed out
        const val SensorTimeoutString = "TIME_OUT"
    }

    private var mostRecentMessageTimestamp = 0L

    private val sensorThread =
        HandlerThread("GrupettoSensorThread-${command.name}-${UUID.randomUUID()}")

    private val mutableSensorValue = MutableSharedFlow<Float>(
        replay = 1,
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Exposes any exceptions that occur downstream, use with retry/retryWhen
     */
    val sensorValue = mutableSensorValue
        .asSharedFlow()

    fun start() {
        sensorThread.start()
        val handler = object : Handler(sensorThread.looper) {
            override fun handleMessage(message: Message) {
                processSensorServiceMessage(message)
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

    /**
     * Each message should contain
     * - Float sensor data in "data"
     * - Long epoch time in "time"
     * - Raw serial response in "responseHexString"
     */
    private fun processSensorServiceMessage(message: Message) {
        if (message.what < 0) {
            Timber.w("invalid service response, stopping sensor ${command.name}")
            stop()
        }

        val reply = message.data

        if (reply.getString(HexResponseBundleKey) == SensorTimeoutString) {
            Timber.e("sensor command timed out: $command")
            return
        }

        val messageTimestamp = reply.getLong(TimeBundleKey)
        if (messageTimestamp < mostRecentMessageTimestamp) {
            Timber.w("discarding stale sensor response for ${command.name}")
            return
        } else {
            mostRecentMessageTimestamp = messageTimestamp
        }

        if (!reply.containsKey(SensorValueBundleKey)) {
            Timber.e("missing sensor value in response for command ${command.name}")
            return
        }

        val sensorValue = mapValue(reply.getFloat(SensorValueBundleKey))
        mutableSensorValue.tryEmit(sensorValue)
    }

    private fun stop() {
        sensorThread.looper.quitSafely()
    }


    protected abstract fun mapValue(value: Float): Float
}