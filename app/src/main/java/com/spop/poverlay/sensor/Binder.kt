package com.spop.poverlay.sensor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun getBinder(context: Context) = suspendCoroutine<IBinder> { ctx ->
    context.bindService(
        Intent("android.intent.action.peloton.SensorData").apply {
            setPackage("com.peloton.service.SensorData")
        }, object : ServiceConnection {
            override fun onServiceConnected(p0: ComponentName?, iBinder: IBinder?) {
                Timber.i("sensor service connected $p0")
                if(iBinder == null){
                    ctx.resumeWithException(Exception("sensor service resolution failed"))
                }else{
                    ctx.resume(iBinder)
                }
            }

            override fun onBindingDied(name: ComponentName?) {
                super.onBindingDied(name)
                Timber.i("sensor service binding died $name")
            }
            override fun onNullBinding(name: ComponentName?) {
                Timber.i("sensor service null binding $name")
            }

            override fun onServiceDisconnected(p0: ComponentName?) {
                Timber.i("sensor service disconnected $p0")
            }

        }, Context.BIND_AUTO_CREATE)
}