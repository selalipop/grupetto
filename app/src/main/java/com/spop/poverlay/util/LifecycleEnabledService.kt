package com.spop.poverlay.util

import android.app.Service
import android.view.View
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

/**
 * Allows a service to act as a LifecycleOwner
 * Mainly intended for use with WindowManager
 */
abstract class LifecycleEnabledService : Service(), LifecycleOwner, ViewModelStoreOwner,
    SavedStateRegistryOwner, CoroutineScope {

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override val coroutineContext: CoroutineContext
        get() = coroutineScope.coroutineContext
    /**
     * Must be called on views before this service will provide lifecycle access
     */
    protected fun View.lifecycleViaService() {
        ViewTreeLifecycleOwner.set(this, this@LifecycleEnabledService)
        ViewTreeViewModelStoreOwner.set(this, this@LifecycleEnabledService)
        setViewTreeSavedStateRegistryOwner(this@LifecycleEnabledService)
    }

    private val serviceViewModelStore = ViewModelStore()
    override fun getViewModelStore(): ViewModelStore = serviceViewModelStore

    private val serviceSavedStateRegistry by lazy {
        SavedStateRegistryController.create(this)
    }

    override val savedStateRegistry: SavedStateRegistry
        get() = serviceSavedStateRegistry.savedStateRegistry

    private val lifecycleRegistry: LifecycleRegistry by lazy {
        LifecycleRegistry(this)
    }

    override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry
    }


    private fun handleLifecycleEvent(event: Lifecycle.Event) =
        lifecycleRegistry.handleLifecycleEvent(event)

    override fun onCreate() {
        super.onCreate()
        serviceSavedStateRegistry.performRestore(null)
        handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        handleLifecycleEvent(Lifecycle.Event.ON_START)
        handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDestroy() {
        super.onDestroy()
        handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        coroutineScope.cancel()
    }
}