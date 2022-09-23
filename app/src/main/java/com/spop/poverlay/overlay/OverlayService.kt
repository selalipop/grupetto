package com.spop.poverlay.overlay

import android.app.*
import android.content.Intent
import android.content.res.Resources
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.spop.poverlay.MainActivity
import com.spop.poverlay.R
import com.spop.poverlay.sensor.DummySensorInterface
import com.spop.poverlay.sensor.PelotonV1SensorInterface
import timber.log.Timber
import java.util.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt


private const val PelotonBrand = "Peloton"

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    companion object {
        private const val OverlayServiceId = 2032

        val OverlayWidthDp = 650.dp
        val OverlayHeightDp = 100.dp

        //If the overlay is dragged within this many pixels of the center of the screen
        //snap to the center of the screen
        val OverlayCenterSnapRangePx = 20

        //Increases the size of the touch target during the hidden state
        const val HiddenTouchTargetMarginPx = 20
    }

    private var lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedState.savedStateRegistry

    override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry
    }


    private fun handleLifecycleEvent(event: Lifecycle.Event) =
        lifecycleRegistry.handleLifecycleEvent(event)


    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        handleLifecycleEvent(Lifecycle.Event.ON_START)
        handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val notificationManager = NotificationManagerCompat.from(this)
        startForeground(OverlayServiceId, prepareNotification(notificationManager))

        buildDialog(OverlayLocation.Bottom)
    }

    override fun onDestroy() {
        super.onDestroy()
        handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }


    private val store = ViewModelStore()
    override fun getViewModelStore(): ViewModelStore = store

    private val savedState = SavedStateRegistryController.create(this)

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("overlay service received intent")
        return START_STICKY
    }

    private fun buildDialog(location: OverlayLocation) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager


        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            LayoutParams.TYPE_SYSTEM_ALERT
        }

        val widthPx = (OverlayWidthDp.value * resources.displayMetrics.density).roundToInt()

        val defaultFlags = (LayoutParams.FLAG_NOT_TOUCH_MODAL
                or LayoutParams.FLAG_NOT_FOCUSABLE
                or LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        val composeParams = LayoutParams(
            widthPx,
            LayoutParams.WRAP_CONTENT,
            layoutFlag,
            defaultFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = location.gravity
        }

        val disabledTouchParams = LayoutParams().apply {
            copyFrom(composeParams)
        }

        val sensorInterface = if (Build.BRAND == PelotonBrand) {
            PelotonV1SensorInterface(this)
        } else {
            DummySensorInterface()
        }

        val overlayViewModel = OverlayViewModel(
            this@OverlayService.application,
            sensorInterface
        )

        val disabledTouchView = FrameLayout(this).apply {
            ViewTreeLifecycleOwner.set(this, this@OverlayService)
            ViewTreeViewModelStoreOwner.set(this, this@OverlayService)
            setOnClickListener {
                overlayViewModel.onOverlayPressed()
            }
            layoutParams = ViewGroup.LayoutParams(100, 100)
        }

        val composeView = ComposeView(this).apply {
            ViewTreeLifecycleOwner.set(this, this@OverlayService)
            ViewTreeViewModelStoreOwner.set(this, this@OverlayService)

            setContent {
                Overlay(
                    overlayViewModel,
                    OverlayHeightDp,
                    location,
                    { offset ->
                        if (offset > -OverlayCenterSnapRangePx && offset < OverlayCenterSnapRangePx) {
                            composeParams.x = 0
                        } else {
                            val overlayWidthPx =
                                OverlayWidthDp.value * Resources.getSystem().displayMetrics.density
                            val screenWidth = resources.displayMetrics.widthPixels
                            val dragRange =  ceil((screenWidth-overlayWidthPx) / 2).toInt()
                            composeParams.x = offset.coerceIn(-dragRange, dragRange)
                        }

                        wm.updateViewLayout(this, composeParams)
                    }
                ) { offset, remainingVisibleHeight ->
                    /**
                     * Views attached directly to the window manager block all touches regardless
                     * of if there is content beneath them
                     *
                     * But changing the height of the ComposeView results in janky animations.
                     *
                     * This solution disables touches on the ComposeView when it hides
                     * Then an invisible view appears to capture touches
                     */
                    /**
                     * Views attached directly to the window manager block all touches regardless
                     * of if there is content beneath them
                     *
                     * But changing the height of the ComposeView results in janky animations.
                     *
                     * This solution disables touches on the ComposeView when it hides
                     * Then an invisible view appears to capture touches
                     */
                    /**
                     * Views attached directly to the window manager block all touches regardless
                     * of if there is content beneath them
                     *
                     * But changing the height of the ComposeView results in janky animations.
                     *
                     * This solution disables touches on the ComposeView when it hides
                     * Then an invisible view appears to capture touches
                     */
                    /**
                     * Views attached directly to the window manager block all touches regardless
                     * of if there is content beneath them
                     *
                     * But changing the height of the ComposeView results in janky animations.
                     *
                     * This solution disables touches on the ComposeView when it hides
                     * Then an invisible view appears to capture touches
                     */
                    val isHidden = abs(offset) > 0f
                    disabledTouchView.visibility = if (isHidden) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                    disabledTouchParams.height = remainingVisibleHeight + HiddenTouchTargetMarginPx
                    wm.updateViewLayout(disabledTouchView, disabledTouchParams)

                    composeParams.flags = if (isHidden) {
                        defaultFlags or
                                LayoutParams.FLAG_NOT_TOUCHABLE
                    } else {
                        defaultFlags
                    }
                    wm.updateViewLayout(this, composeParams)
                }
            }

            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            alpha = 0.9f
            isFocusable = false
            clipChildren = false
            clipToOutline = false
        }

        wm.addView(composeView, composeParams)
        wm.addView(disabledTouchView, disabledTouchParams)
    }


    private fun prepareNotification(notificationManager: NotificationManagerCompat): Notification {
        val channelId = UUID.randomUUID().toString()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            notificationManager.getNotificationChannel(channelId) == null
        ) {
            val name: CharSequence = getString(R.string.overlay_notification)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance)
            channel.enableVibration(false)
            notificationManager.createNotificationChannel(channel)
        }
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val intentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            intentFlags
        )

        val notificationBuilder: NotificationCompat.Builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationCompat.Builder(this, channelId)
            } else {
                NotificationCompat.Builder(this)
            }

        notificationBuilder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        notificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        return notificationBuilder.build()
    }
}

