package com.spop.poverlay.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.spop.poverlay.MainActivity
import com.spop.poverlay.R
import com.spop.poverlay.sensor.DummySensorInterface
import com.spop.poverlay.sensor.PelotonV1SensorInterface
import com.spop.poverlay.util.LifecycleEnabledService
import timber.log.Timber
import java.util.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt


private const val PelotonBrand = "Peloton"

class OverlayService : LifecycleEnabledService() {
    companion object {
        private const val OverlayServiceId = 2032

        val OverlayWidthDp = 650.dp
        val OverlayHeightDp = 100.dp

        //If the overlay is dragged within this many pixels of the center of the screen
        //snap to the center of the screen
        const val OverlayCenterSnapRangePx = 20

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
        val location = mutableStateOf(OverlayLocation.Bottom)
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val validHorizontalDragRange = calculateHorizontalDragRange()
        val screenHeight = resources.displayMetrics.heightPixels


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
        ).apply {
            gravity = location.value.gravity
        }

        val disabledTouchParams = LayoutParams().apply {
            copyFrom(composeParams)
        }

        val sensorInterface = if (Build.BRAND == PelotonBrand) {
            PelotonV1SensorInterface(this)
        } else {
            DummySensorInterface()
        }

        val overlayViewModel = OverlaySensorViewModel(
            this@OverlayService.application,
            sensorInterface
        )

        val disabledTouchView = FrameLayout(this).apply {
            lifecycleViaService()
            setOnClickListener {
                overlayViewModel.onOverlayPressed()
            }
            layoutParams = ViewGroup.LayoutParams(100, 100)
        }

        val composeView = ComposeView(this).apply {
            lifecycleViaService()

            setContent {
                Overlay(
                    overlayViewModel,
                    OverlayHeightDp,
                    location,
                    { offset ->
                        var wasClamped = false
                        if (offset > -OverlayCenterSnapRangePx && offset < OverlayCenterSnapRangePx) {
                            composeParams.x = 0
                        } else {
                            composeParams.x = offset.coerceIn(validHorizontalDragRange)

                            if(!validHorizontalDragRange.contains(offset)){
                                wasClamped = true
                            }
                        }
                        wm.updateViewLayout(this, composeParams)

                        //Relocate hidden touch target as well
                        disabledTouchParams.copyFrom(composeParams)
                        wm.updateViewLayout(disabledTouchView, disabledTouchParams)
                        /*
                        If the drag gesture offset has fallen out of sync with composeParams, either
                        a) the view as dragged too far off the screen and clamped to the side
                        b) the view is being snapped to the center
                        If it's being snapped, allow the gesture to continue
                        Otherwise force the drag back into sync with the view position
                        */
                        if (wasClamped) {
                            composeParams.x // Sync gesture to view location
                        } else {
                            offset // Continue gesture if snapped or synced
                        }
                    },
                    { delta ->

                        // If the user has dragged halfway up the screen, cancel the drag gesture and
                        // toggle the location
                        if (abs(delta) > screenHeight * VerticalMoveDragThreshold) {
                            location.value = if (location.value == OverlayLocation.Bottom) {
                                OverlayLocation.Top
                            } else {
                                OverlayLocation.Bottom
                            }
                            composeParams.gravity = location.value.gravity
                            wm.updateViewLayout(this, composeParams)

                            disabledTouchParams.copyFrom(composeParams)
                            wm.updateViewLayout(disabledTouchView, disabledTouchParams)
                            true // Reset the drag gesture as we've handled it
                        } else {
                            false // Continue drag gesture
                        }

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
            alpha = 0.9f
            isFocusable = false
            clipChildren = false
            clipToOutline = false
        }

        wm.addView(composeView, composeParams)
        wm.addView(disabledTouchView, disabledTouchParams)
    }

    private fun calculateHorizontalDragRange(): IntRange {
        val overlayWidthPx =
            OverlayWidthDp.value * Resources.getSystem().displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val dragRange = ceil((screenWidth - overlayWidthPx) / 2).toInt()
        return -dragRange..dragRange
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

