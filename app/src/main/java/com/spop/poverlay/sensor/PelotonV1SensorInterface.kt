package com.spop.poverlay.sensor

import android.content.Context
import android.os.IBinder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

class PelotonV1SensorInterface(context: Context) : SensorInterface, CoroutineScope{
    private val binder = MutableSharedFlow<IBinder>(replay = 1)

    init {
        launch(Dispatchers.IO) {
            val service = getBinder(context)
            binder.emit(service)
        }
    }

    override val coroutineContext: CoroutineContext
        get() = SupervisorJob()

    fun stop(){
        coroutineContext.cancelChildren()
    }

    override val power: Flow<Float>
        get() = binder.flatMapLatest {
            val powerSensor = PowerSensor(it)
            powerSensor.start()
            powerSensor.sensorValue
        }.mapV1SensorToFloat()

    override val cadence: Flow<Float>
        get() = binder.flatMapLatest {
            val rpmSensor = RpmSensor(it)
            rpmSensor.start()
            rpmSensor.sensorValue
        }.mapV1SensorToFloat()

    override val resistance: Flow<Float>
        get() = binder.flatMapLatest {
            val resistanceSensor = ResistanceSensor(it)
            resistanceSensor.start()
            resistanceSensor.sensorValue
        }.mapV1SensorToFloat()

    private fun Flow<Result<Float?>>.mapV1SensorToFloat() =
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