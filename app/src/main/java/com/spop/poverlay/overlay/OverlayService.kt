package com.spop.poverlay.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.spop.poverlay.ConfigurationRepository
import com.spop.poverlay.GrupettoApplication
import com.spop.poverlay.MainActivity
import com.spop.poverlay.R

import com.spop.poverlay.sensor.CadenceWatchdog
import com.spop.poverlay.sensor.DeadSensorDetector
import com.spop.poverlay.sensor.interfaces.DummySensorInterface
import com.spop.poverlay.sensor.interfaces.PelotonBikeSensorInterfaceV1New
import com.spop.poverlay.sensor.interfaces.PelotonBikePlusSensorInterface
import com.spop.poverlay.util.IsBikePlus
import com.spop.poverlay.util.IsG700CrossTrainer
import com.spop.poverlay.util.IsRunningOnPeloton
import com.spop.poverlay.util.LifecycleEnabledService
import com.spop.poverlay.util.disableAnimations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import timber.log.Timber
import kotlin.math.roundToInt


class OverlayService : LifecycleEnabledService() {
    companion object {
        const val ActionMinimizeOverlay = "com.spop.poverlay.action.MINIMIZE_OVERLAY"
        const val ActionRestoreOverlay = "com.spop.poverlay.action.RESTORE_OVERLAY"
        private const val DefaultOverlayFlags = (LayoutParams.FLAG_NOT_TOUCH_MODAL
                or LayoutParams.FLAG_NOT_FOCUSABLE
                or LayoutParams.FLAG_LAYOUT_NO_LIMITS)


        private const val OverlayServiceId = 2032
        private const val OverlayNotificationChannelId = "grupetto_overlay_service"
        private const val WakeLockTimeoutMs = 15 * 60 * 1000L
        private const val WakeLockRenewIntervalMs = 10 * 60 * 1000L

        val OverlayHeightDp = 110.dp

        //Increases the size of the touch target during the hidden state
        const val HiddenTouchTargetMarginPx = 40

        //The percentage up or down a vertical drag must go before the overlay is relocated
        //Defined relative to the height of the screen
        const val VerticalMoveDragThreshold = .5f

        // Replace with DeadSensorInterface to simulate a dead sensor
        val EmulatorSensorInterface by lazy { DummySensorInterface() }

        private val mutableIsRunning = MutableStateFlow(false)
        val isRunning = mutableIsRunning.asStateFlow()
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockRefreshJob: Job? = null
    private var overlayView: View? = null
    private var touchTargetView: View? = null
    private var windowManager: WindowManager? = null
    private var sensorViewModel: OverlaySensorViewModel? = null
    private var minimizedStateBeforeConfiguration: Boolean? = null
    private val bleServer by lazy { (application as GrupettoApplication).bleServer }

    override fun onCreate() {
        super.onCreate()
        mutableIsRunning.value = true
        syncBackgroundExecutionGuards()
        val notification = prepareNotification(NotificationManagerCompat.from(this))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                OverlayServiceId, 
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                OverlayServiceId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(OverlayServiceId, notification)
        }
        buildDialog()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("overlay service received intent")
        when (intent?.action) {
            ActionMinimizeOverlay -> {
                sensorViewModel?.let { viewModel ->
                    if (minimizedStateBeforeConfiguration == null) {
                        minimizedStateBeforeConfiguration = viewModel.isMinimized.value
                    }
                    viewModel.minimizeOverlay()
                }
            }
            ActionRestoreOverlay -> {
                minimizedStateBeforeConfiguration?.let { previousState ->
                    sensorViewModel?.setMinimized(previousState)
                    minimizedStateBeforeConfiguration = null
                }
            }
        }
        syncBackgroundExecutionGuards()
        return START_STICKY
    }

    override fun onDestroy() {
        mutableIsRunning.value = false
        removeOverlayViews()
        releaseWakeLock()
        sensorViewModel = null
        super.onDestroy()
    }

    private fun buildDialog() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val screenSize = Size(
            resources.displayMetrics.widthPixels.toFloat(),
            resources.displayMetrics.heightPixels.toFloat()
        )

        val sensorInterface = if (IsRunningOnPeloton) {
            if (IsG700CrossTrainer || IsBikePlus) {
                PelotonBikePlusSensorInterface(this).also {
                    lifecycle.addObserver(LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_DESTROY) {
                            it.stop()
                        }
                    })
                }
            } else {
                PelotonBikeSensorInterfaceV1New(this).also {
                    lifecycle.addObserver(LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_DESTROY) {
                            it.stop()
                        }
                    })
                }
            }

        } else {
            EmulatorSensorInterface
        }

        val timerViewModel = OverlayTimerViewModel(
            application,
            ConfigurationRepository(applicationContext, this),
            sensorInterface.power
        )

        val sensorViewModel = OverlaySensorViewModel(
            application,
            sensorInterface,
            DeadSensorDetector(sensorInterface, this.coroutineContext),
            timerViewModel
        )
        this.sensorViewModel = sensorViewModel
        // Wire up timer to auto-start/pause based on movement
        timerViewModel.observeMovement(sensorViewModel.isMoving, sensorViewModel.sessionReset)

        val dialogViewModel = OverlayDialogViewModel(screenSize, sensorViewModel.isMinimized)

        // Initialize and start watchdog (always enabled)
        val watchdogThreshold = 30.minutes
        val watchdog = CadenceWatchdog(sensorInterface, this.coroutineContext, watchdogThreshold)
        watchdog.start()
        
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                watchdog.stop()
            }
        })
        
        // Handle watchdog restart trigger
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                watchdog.restartTriggered.collect {
                    Timber.w(
                        "Watchdog triggered restart - no cadence detected for ${watchdogThreshold.inWholeMinutes} minutes"
                    )
                    restartToOverlay()
                }
            }
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            LayoutParams.TYPE_SYSTEM_ALERT
        }


        val overlayParams = LayoutParams(
            200,
            LayoutParams.WRAP_CONTENT,
            layoutFlag,
            DefaultOverlayFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            disableAnimations()
        }

        val touchTargetParams = LayoutParams().apply {
            copyFrom(overlayParams)
            disableAnimations()
        }

        touchTargetView = FrameLayout(this).apply {
            lifecycleViaService()
            setOnClickListener {
                sensorViewModel.onOverlayPressed()
            }
            layoutParams = ViewGroup.LayoutParams(100, 100)
        }

        overlayView = ComposeView(this).apply {
            lifecycleViaService()
            setViewCompositionStrategy(
                ViewCompositionStrategy
                    .DisposeOnLifecycleDestroyed(this@OverlayService)
            )
            setContent {
                Overlay(
                    sensorViewModel,
                    timerViewModel,
                    OverlayHeightDp,
                    dialogViewModel.dialogLocation.collectAsState(),
                    dialogViewModel::processHorizontalDrag,
                    dialogViewModel::processVerticalDrag,
                    dialogViewModel::processHideProgress,
                    dialogViewModel::onOverlayLayout,
                    dialogViewModel::onTimerOverlayLayout
                )
            }
            alpha = 0.9f
            isFocusable = false
            clipToPadding = false
            clipChildren = false
            clipToOutline = false
        }
        val overlay = overlayView!!
        val touchTarget = touchTargetView!!
        wm.addView(overlay, overlayParams)

        wm.addView(touchTarget, touchTargetParams)
        //touchTarget.clipChildren = false
        //touchTarget.clipToPadding = false
        //Subscribe to Dialog view model and update views
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                combine(
                    dialogViewModel.dialogOrigin,
                    dialogViewModel.dialogGravity,
                    dialogViewModel.partialOverlayFlags,
                    dialogViewModel.touchTargetHeight,
                    dialogViewModel.dialogSizeParams,
                    dialogViewModel.minimizedDialogSizeParams
                ) { values ->
                    val origin = values[0] as Offset
                    val gravity = values[1] as Int
                    val overlayFlags = values[2] as Int
                    val touchTargetHeight = values[3] as Float
                    val (width, height)  = values[4] as Pair<Int,Int>
                    val (mWidth, mHeight)  = values[5] as Pair<Int,Int>
                    overlayParams.x = origin.x.roundToInt()
                    overlayParams.y = origin.y.roundToInt()
                    overlayParams.flags = DefaultOverlayFlags or overlayFlags
                    overlayParams.gravity = gravity
                    overlayParams.width = width
                    overlayParams.height = if(sensorViewModel.isMinimized.value){
                        mHeight
                    }else{
                        height
                    }
                    touchTargetParams.x = origin.x.roundToInt()
                    touchTargetParams.y = origin.y.roundToInt()
                    touchTargetParams.gravity = gravity
                    touchTargetParams.width = mWidth
                    touchTargetParams.height = touchTargetHeight.roundToInt()
                    val currentOverlay = overlayView
                    val currentTouchTarget = touchTargetView
                    if (currentOverlay == null || currentTouchTarget == null) {
                        Timber.d("Overlay views cleared before update; skipping layout application")
                        return@combine
                    }
                    currentTouchTarget.visibility = if (touchTargetHeight > 0f){
                        View.VISIBLE
                    }else{
                        View.GONE
                    }
                    disableClipOnParents(currentOverlay)
                    wm.updateViewLayout(currentOverlay, overlayParams)
                    wm.updateViewLayout(currentTouchTarget, touchTargetParams)
                }.collect {}
            }
        }
    }

    private fun disableClipOnParents(v: View) {
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

    private fun restartToOverlay() {
        Timber.i("Restarting Grupetto to overlay due to watchdog trigger")
        
        // Stop the current service
        stopSelf()
        
        // Start the overlay service again
        val restartIntent = Intent(this, OverlayService::class.java)
        ContextCompat.startForegroundService(this, restartIntent)
        
        // Exit the process to ensure clean restart
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            Runtime.getRuntime().exit(0)
        }, 500)
    }

    private fun prepareNotification(notificationManager: NotificationManagerCompat): Notification {
        val channelId = OverlayNotificationChannelId

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
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.overlay_notification))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        notificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        return notificationBuilder.build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = ContextCompat.getSystemService(this, PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Grupetto:OverlayWakelock").apply {
            setReferenceCounted(false)
            acquire(WakeLockTimeoutMs)
        }
        wakeLockRefreshJob?.cancel()
        wakeLockRefreshJob = lifecycleScope.launch {
            while (isActive) {
                delay(WakeLockRenewIntervalMs)
                wakeLock?.let {
                    if (it.isHeld) {
                        it.acquire(WakeLockTimeoutMs)
                    }
                }
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLockRefreshJob?.cancel()
        wakeLockRefreshJob = null
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun syncBackgroundExecutionGuards() {
        val bleEnabled = isBleTxEnabled()
        val dirConEnabled = isDirConEnabled()
        val shouldRunBle = bleEnabled && hasBleRuntimePermissions()

        if (shouldRunBle) {
            bleServer.setDirConTransportEnabled(dirConEnabled)
            bleServer.start()
        } else {
            bleServer.stop()
            bleServer.setDirConTransportEnabled(dirConEnabled)
        }

        if (shouldRunBle || dirConEnabled) {
            acquireWakeLock()
        } else {
            releaseWakeLock()
        }
    }

    private fun isBleTxEnabled(): Boolean {
        val prefs = getSharedPreferences(ConfigurationRepository.SharedPrefsName, MODE_PRIVATE)
        return prefs.getBoolean(ConfigurationRepository.Preferences.BleTxEnabled.key, true)
    }

    private fun isDirConEnabled(): Boolean {
        val prefs = getSharedPreferences(ConfigurationRepository.SharedPrefsName, MODE_PRIVATE)
        return prefs.getBoolean(ConfigurationRepository.Preferences.DirConEnabled.key, true)
    }

    private fun hasBleRuntimePermissions(): Boolean {
        val hasBaseBluetooth = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.BLUETOOTH
        ) == PackageManager.PERMISSION_GRANTED

        val hasBluetoothAdmin = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.BLUETOOTH_ADMIN
        ) == PackageManager.PERMISSION_GRANTED

        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasAdvertise = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED
            val hasConnect = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            return hasBaseBluetooth && hasBluetoothAdmin && hasFineLocation && hasAdvertise && hasConnect
        }

        return hasBaseBluetooth && hasBluetoothAdmin && hasFineLocation
    }

    private fun removeOverlayViews() {
        val wm = windowManager
        val hasViews = overlayView != null || touchTargetView != null
        if (wm != null && hasViews) {
            overlayView?.let {
                runCatching { wm.removeViewImmediate(it) }
                    .onFailure { ex -> Timber.w(ex, "Failed to remove overlay view") }
            }
            touchTargetView?.let {
                runCatching { wm.removeViewImmediate(it) }
                    .onFailure { ex -> Timber.w(ex, "Failed to remove touch target view") }
            }
        } else if (wm == null && hasViews) {
            Timber.e("WindowManager unavailable during cleanup; overlay views may remain attached and leak")
        }
        overlayView = null
        touchTargetView = null
        windowManager = null
    }
}
