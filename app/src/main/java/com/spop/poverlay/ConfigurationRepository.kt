package com.spop.poverlay

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class ConfigurationRepository(context : Context) : CoroutineScope {
    enum class Preferences(val key : String){
        ShowTimerWhenMinimized("showTimerWhenMinimized")
    }

    private val scope : CoroutineScope = MainScope()
    override val coroutineContext: CoroutineContext
        get() = scope.coroutineContext + Dispatchers.IO
    companion object{
        const val SharedPrefsName = "configuration"
    }

    private val mutableShowTimerWhenMinimized = MutableSharedFlow<Boolean>(replay = 1)
    val showTimerWhenMinimized = mutableShowTimerWhenMinimized.asSharedFlow()

    private val sharedPreferences : SharedPreferences

    // Must be kept as reference, unowned lambda would be garbage collected
    private val sharedPreferencesListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
           launch {
               updateFromSharedPrefs()
           }
        }

    init{
        sharedPreferences = context.getSharedPreferences(SharedPrefsName, Context.MODE_PRIVATE)
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferencesListener)
        launch {
            updateFromSharedPrefs()
        }
    }

    fun setShowTimerWhenMinimized(isShown : Boolean){
        sharedPreferences.edit {
            putBoolean(Preferences.ShowTimerWhenMinimized.key, isShown)
        }
    }
    private suspend fun updateFromSharedPrefs() {
        mutableShowTimerWhenMinimized.emit(
            sharedPreferences
                .getBoolean(Preferences.ShowTimerWhenMinimized.key, true)
        )
    }

}