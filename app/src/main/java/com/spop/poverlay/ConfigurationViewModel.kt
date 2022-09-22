package com.spop.poverlay

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.spop.poverlay.sensor.PowerSensor
import com.spop.poverlay.sensor.SensorException
import com.spop.poverlay.sensor.getBinder
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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