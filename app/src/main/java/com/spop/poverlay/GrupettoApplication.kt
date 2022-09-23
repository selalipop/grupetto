package com.spop.poverlay

import timber.log.Timber
import timber.log.Timber.*

class GrupettoApplication : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        if(BuildConfig.DEBUG){
            Timber.plant(DebugTree())
        }
    }
}