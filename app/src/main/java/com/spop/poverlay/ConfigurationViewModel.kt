package com.spop.poverlay

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.spop.poverlay.overlay.OverlayService
import timber.log.Timber

class ConfigurationViewModel(application: Application) : AndroidViewModel(application) {
    val finishActivity = MutableLiveData<Unit>()
    fun onStartService(){
        Timber.i("Starting service")
        ContextCompat.startForegroundService(
            getApplication(),
            Intent(getApplication(), OverlayService::class.java)
        )
        finishActivity.value = Unit
    }
}