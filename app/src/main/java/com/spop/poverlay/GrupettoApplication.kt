package com.spop.poverlay

import com.spop.poverlay.util.IsRunningOnPeloton
import timber.log.Timber
import timber.log.Timber.*

class GrupettoApplication : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        if(BuildConfig.DEBUG || IsRunningOnPeloton){
            Timber.plant(DebugTree())
        }
    }
}