package com.spop.poverlay.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.spop.poverlay.MainActivity
import com.spop.poverlay.R
import com.spop.poverlay.sensor.DummySensorInterface
import com.spop.poverlay.sensor.PelotonV1SensorInterface
import com.spop.poverlay.util.LifecycleEnabledService
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

        val OverlayWidthDp = 650.dp
        val OverlayHeightDp = 100.dp

        //Increases the size of the touch target during the hidden state
        const val HiddenTouchTargetMarginPx = 20

        //The percentage up or down a vertical drag must go before the overlay is relocated
        //Defined relative to the height of the screen
        const val VerticalMoveDragThreshold = .6f
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
            PelotonV1SensorInterface(this)
        } else {
            DummySensorInterface()
        }

        val sensorViewModel = OverlaySensorViewModel(
            this@OverlayService.application,
            sensorInterface
        )

        val dialogViewModel = OverlayDialogViewModel(screenSize)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
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
        )

        val touchTargetParams = LayoutParams().apply {
            copyFrom(composeParams)
        }

        val touchTargetView = FrameLayout(this).apply {
            lifecycleViaService()
            setOnClickListener {
                sensorViewModel.onOverlayPressed()
            }
        }

        val overlayView = ComposeView(this).apply {
            lifecycleViaService()
            setContent {
                Overlay(
                    sensorViewModel,
                    OverlayHeightDp,
                    dialogViewModel.dialogLocation.collectAsState(),
                    dialogViewModel::processHorizontalDrag,
                    dialogViewModel::processVerticalDrag,
                    dialogViewModel::processHideProgress
                )
            }
            alpha = 0.9f
            isFocusable = false
            clipChildren = false
            clipToOutline = false
        }

        wm.addView(overlayView, composeParams)
        wm.addView(touchTargetView, touchTargetParams)

        //Subscribe to Dialog view model and update views
        lifecycleScope.launchWhenResumed {
            combine(
                dialogViewModel.dialogOrigin,
                dialogViewModel.dialogGravity,
                dialogViewModel.partialOverlayFlags,
                dialogViewModel.touchTargetHeight,
                dialogViewModel.touchTargetVisiblity
            ){ origin, gravity, overlayFlags, touchTargetHeight, touchTargetVisibility ->
                composeParams.x = origin.x.roundToInt()
                composeParams.y = origin.y.roundToInt()
                composeParams.flags = DefaultOverlayFlags or overlayFlags
                composeParams.gravity = gravity

                touchTargetParams.x = origin.x.roundToInt()
                touchTargetParams.y = origin.y.roundToInt()
                touchTargetParams.gravity = gravity
                touchTargetParams.height = touchTargetHeight.roundToInt()

                touchTargetView.visibility = touchTargetVisibility

                wm.updateViewLayout(overlayView, composeParams)
                wm.updateViewLayout(touchTargetView, touchTargetParams)
            }.collect{}
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

