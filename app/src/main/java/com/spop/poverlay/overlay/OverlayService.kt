package com.spop.poverlay.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.spop.poverlay.ConfigurationRepository
import com.spop.poverlay.MainActivity
import com.spop.poverlay.R
import com.spop.poverlay.sensor.DummySensorInterface
import com.spop.poverlay.sensor.PelotonV1SensorInterface
import com.spop.poverlay.util.LifecycleEnabledService
import com.spop.poverlay.util.disableAnimations
import kotlinx.coroutines.flow.combine
import timber.log.Timber
import java.util.*
import kotlin.math.roundToInt


private const val PelotonBrand = "Peloton"

class OverlayService : LifecycleEnabledService() {
    companion object {
        private const val DefaultOverlayFlags = (LayoutParams.FLAG_NOT_TOUCH_MODAL
                or LayoutParams.FLAG_NOT_FOCUSABLE
                or LayoutParams.FLAG_LAYOUT_NO_LIMITS)


        private const val OverlayServiceId = 2032

        val OverlayHeightDp = 100.dp

        //Increases the size of the touch target during the hidden state
        const val HiddenTouchTargetMarginPx = 40

        //The percentage up or down a vertical drag must go before the overlay is relocated
        //Defined relative to the height of the screen
        const val VerticalMoveDragThreshold = .5f
    }


    override fun onCreate() {
        super.onCreate()

        val notificationManager = NotificationManagerCompat.from(this)
        startForeground(OverlayServiceId, prepareNotification(notificationManager))

        buildDialog()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("overlay service received intent")
        return START_STICKY
    }

    private fun buildDialog() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val screenSize = Size(
            resources.displayMetrics.widthPixels.toFloat(),
            resources.displayMetrics.heightPixels.toFloat()
        )


        val sensorInterface = if (Build.BRAND == PelotonBrand) {
            PelotonV1SensorInterface(this).also {
                lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) {
                        it.stop()
                    }
                })
            }
        } else {
            DummySensorInterface()
        }
        val sensorViewModel = OverlaySensorViewModel(
            application,
            sensorInterface,
            ConfigurationRepository(applicationContext, this)
        )

        val dialogViewModel = OverlayDialogViewModel(screenSize)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            LayoutParams.TYPE_SYSTEM_ALERT
        }


        val defaultFlags = (LayoutParams.FLAG_NOT_TOUCH_MODAL
                or LayoutParams.FLAG_NOT_FOCUSABLE
                or LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        val composeParams = LayoutParams(
            screenSize.width.roundToInt(),
            LayoutParams.WRAP_CONTENT,
            layoutFlag,
            defaultFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            disableAnimations()
        }

        val touchTargetParams = LayoutParams().apply {
            copyFrom(composeParams)
            disableAnimations()
        }

        val touchTargetView = FrameLayout(this).apply {
            lifecycleViaService()
            setOnClickListener {
                sensorViewModel.onOverlayPressed()
            }
            layoutParams = ViewGroup.LayoutParams(100, 100)
        }

        val overlayView = ComposeView(this).apply {
            lifecycleViaService()
            setViewCompositionStrategy(
                ViewCompositionStrategy
                    .DisposeOnLifecycleDestroyed(this@OverlayService)
            )
            setContent {
                Overlay(
                    sensorViewModel,
                    OverlayHeightDp,
                    dialogViewModel.dialogLocation.collectAsState(),
                    dialogViewModel::processHorizontalDrag,
                    dialogViewModel::processVerticalDrag,
                    dialogViewModel::processHideProgress,
                    dialogViewModel::onOverlayLayout
                )
            }
            alpha = 0.9f
            isFocusable = false
            clipToPadding = false
            clipChildren = false
            clipToOutline = false
        }
        wm.addView(overlayView, composeParams)

        wm.addView(touchTargetView, touchTargetParams)
        touchTargetView.clipChildren = false
        touchTargetView.clipToPadding = false
        //Subscribe to Dialog view model and update views
        lifecycleScope.launchWhenResumed {
            combine(
                dialogViewModel.dialogOrigin,
                dialogViewModel.dialogGravity,
                dialogViewModel.partialOverlayFlags,
                dialogViewModel.touchTargetHeight,
                dialogViewModel.touchTargetVisiblity
            ) { origin, gravity, overlayFlags, touchTargetHeight, touchTargetVisibility ->
                composeParams.x = origin.x.roundToInt()
                composeParams.y = origin.y.roundToInt()
                composeParams.flags = DefaultOverlayFlags or overlayFlags
                composeParams.gravity = gravity

                touchTargetParams.x = origin.x.roundToInt()
                touchTargetParams.y = origin.y.roundToInt()
                touchTargetParams.gravity = gravity
                touchTargetParams.height = touchTargetHeight.roundToInt()

                touchTargetView.visibility = View.VISIBLE
                disableClipOnParents(overlayView)
                wm.updateViewLayout(overlayView, composeParams)
                wm.updateViewLayout(touchTargetView, touchTargetParams)
            }.collect {}
        }
    }

    fun disableClipOnParents(v: View) {
        if (v.parent == null) {
            return
        }
        if (v is ViewGroup) {
            v.clipChildren = false
        }
        if (v.parent is View) {
            disableClipOnParents(v.parent as View)
        }
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
                @Suppress("DEPRECATION")
                NotificationCompat.Builder(this)
            }

        notificationBuilder
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        notificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        return notificationBuilder.build()
    }
}

