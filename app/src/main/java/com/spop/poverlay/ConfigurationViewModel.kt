package com.spop.poverlay

import android.app.Application
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.spop.poverlay.overlay.OverlayService
import timber.log.Timber

class ConfigurationViewModel(
    application: Application,
    private val configurationRepository: ConfigurationRepository
) : AndroidViewModel(application) {
    val finishActivity = MutableLiveData<Unit>()
    val requestOverlayPermission = MutableLiveData<Unit>()
    val showPermissionInfo = mutableStateOf(false)
    val infoPopup = MutableLiveData<String>()

    val showTimerWhenMinimized
        get() = configurationRepository.showTimerWhenMinimized

    init {
        updatePermissionState()
    }

    private fun updatePermissionState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            showPermissionInfo.value = !Settings.canDrawOverlays(getApplication())
        } else {
            showPermissionInfo.value = false
        }
    }

    fun onShowTimerWhenMinimizedClicked(isChecked: Boolean) {
        configurationRepository.setShowTimerWhenMinimized(isChecked)
    }

    fun onStartServiceClicked() {
        Timber.i("Starting service")
        ContextCompat.startForegroundService(
            getApplication(),
            Intent(getApplication(), OverlayService::class.java)
        )
        finishActivity.value = Unit
    }

    fun onGrantPermissionClicked() {
        requestOverlayPermission.value = Unit
    }

    fun onResume() {
        updatePermissionState()
    }

    fun onOverlayPermissionRequestCompleted(wasGranted: Boolean) {
        updatePermissionState()
        val prompt = if (wasGranted) {
            "Permission granted, click 'Start Overlay' to get started"
        } else {
            "Without this permission the app cannot function"
        }
        infoPopup.postValue(prompt)
    }

}