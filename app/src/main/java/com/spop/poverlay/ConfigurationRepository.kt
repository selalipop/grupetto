package com.spop.poverlay

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow

class ConfigurationRepository(context : Context) {
    enum class Preferences(val key : String){
        ShowTimerWhenMinimized("showTimerWhenMinimized")
    }

    companion object{
        const val SharedPrefsName = "configuration"
    }

    private val mutableShowTimerWhenMinimized = MutableStateFlow(true)
    val showTimerWhenMinimized = mutableShowTimerWhenMinimized.asSharedFlow()

    private val sharedPreferences : SharedPreferences

    // Must be kept as reference, unowned lambda would be garbage collected
    private val sharedPreferencesListener =
        object: SharedPreferences.OnSharedPreferenceChangeListener{
            override fun onSharedPreferenceChanged(
                sharedPreferences: SharedPreferences?,
                key: String?
            ) {
                updateFromSharedPrefs()
            }

        }

    init{
        sharedPreferences = context.getSharedPreferences(SharedPrefsName, Context.MODE_PRIVATE)
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferencesListener)
        updateFromSharedPrefs()
    }

    fun setShowTimerWhenMinimized(isShown : Boolean){
        sharedPreferences.edit {
            putBoolean(Preferences.ShowTimerWhenMinimized.key, isShown)
        }
    }
    private fun updateFromSharedPrefs() {
        mutableShowTimerWhenMinimized.value =
            sharedPreferences
                .getBoolean(Preferences.ShowTimerWhenMinimized.key, true)

    }

}