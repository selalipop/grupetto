package com.spop.poverlay.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spop.poverlay.overlay.composables.OverlayMainContent
import com.spop.poverlay.overlay.composables.OverlayMinimizedContent
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

const val VisibilityChangeDurationMs = 150
val OverlayCornerRadius = 25.dp
val StatCardWidth = 105.dp
val PowerChartFullWidth = 200.dp
val PowerChartShrunkWidth = 120.dp
val BackgroundColorDefault = Color(20, 20, 20)

// Shown when a sensor hasn't reported a value yet
const val SensorValuePlaceholderText = "-"

@Composable
fun Overlay(
    sensorViewModel: OverlaySensorViewModel,
    timerViewModel: OverlayTimerViewModel,
    height: Dp,
    locationState: State<OverlayLocation>,
    horizontalDragCallback: (Float) -> Float,
    verticalDragCallback: (Float) -> Float,
    offsetCallback: (Float, Float) -> Unit,
    onLayout: (IntSize) -> Unit,
    onTimerLayout: (IntSize) -> Unit
) {
    val power by sensorViewModel.powerValue.collectAsStateWithLifecycle(initialValue = SensorValuePlaceholderText)

    val powerGraph = remember { sensorViewModel.powerGraph }
    val rpm by sensorViewModel.rpmValue.collectAsStateWithLifecycle(initialValue = SensorValuePlaceholderText)
    val resistance by sensorViewModel.resistanceValue.collectAsStateWithLifecycle(initialValue = SensorValuePlaceholderText)
    val speed by sensorViewModel.speedValue.collectAsStateWithLifecycle(initialValue = SensorValuePlaceholderText)
    val speedLabel by sensorViewModel.speedLabel.collectAsStateWithLifecycle(initialValue = "")
    val calories by sensorViewModel.caloriesValue.collectAsStateWithLifecycle(initialValue = SensorValuePlaceholderText)
    val timerLabel by timerViewModel.timerLabel.collectAsStateWithLifecycle(initialValue = "")
    val isTimerPaused by timerViewModel.timerPaused.collectAsStateWithLifecycle(initialValue = false)
    val errorMessage by sensorViewModel.errorMessage.collectAsStateWithLifecycle(initialValue = null)

    var isCurrentlyAnimating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        sensorViewModel.isMinimized
            .drop(1) // Ignore the initial value since animations only happen after new updates
            .collect {
                isCurrentlyAnimating = true
            }
    }

    val minimized by sensorViewModel.isMinimized.collectAsStateWithLifecycle(initialValue = false)
    val location by remember { locationState }
    val size = remember { mutableStateOf(IntSize.Zero) }


    val mainContentHeight = with(LocalDensity.current) {
        height.roundToPx()
    }

    val timerAlpha by animateFloatAsState(
        if (minimized) .5f else 1f,
        animationSpec = TweenSpec(VisibilityChangeDurationMs, 0, LinearEasing)
    )

    val visibilityOffset by animateIntOffsetAsState(
        if (minimized) {
            when (location) {
                // When the main content is hidden, move it off screen completely
                OverlayLocation.Top -> IntOffset(0, -mainContentHeight)
                OverlayLocation.Bottom -> IntOffset(0, mainContentHeight)
            }
        } else {
            IntOffset.Zero
        },
        animationSpec = TweenSpec(VisibilityChangeDurationMs, 0, LinearEasing),
        finishedListener = {
            isCurrentlyAnimating = false
        }
    )

    offsetCallback(visibilityOffset.y.toFloat(), size.value.height.toFloat())

    var horizontalDragOffset by remember { mutableStateOf(0f) }
    var verticalDragOffset by remember { mutableStateOf(0f) }

    val backgroundShape = when (location) {
        OverlayLocation.Top -> RoundedCornerShape(
            bottomStart = OverlayCornerRadius, bottomEnd = OverlayCornerRadius
        )
        OverlayLocation.Bottom -> RoundedCornerShape(
            topStart = OverlayCornerRadius, topEnd = OverlayCornerRadius
        )
    }
    val timer = @Composable {
        val showTimerWhenMinimizedFlow = remember {
            timerViewModel.showTimerWhenMinimized.onEach {
                Timber.i("Show Timer: $it")
            }
        }
        val showTimerWhenMinimized by showTimerWhenMinimizedFlow
            .collectAsStateWithLifecycle(initialValue = true)

        OverlayMinimizedContent(
            isMinimized = minimized,
            timerPaused = isTimerPaused,
            showTimerWhenMinimized = showTimerWhenMinimized,
            location = location,
            powerLabel = power,
            contentAlpha = timerAlpha,
            timerLabel = timerLabel,
            cadenceLabel = rpm,
            speedLabel = speed,
            resistanceLabel = resistance,
            onTap = { timerViewModel.onTimerTap() },
            onLongPress = { timerViewModel.onTimerLongPress() },
            onLayout = onTimerLayout
        )
    }
    val mainContent = @Composable {
        Box(modifier = Modifier
            .requiredHeight(height)
            .wrapContentWidth(unbounded = true)
            .onSizeChanged {
                if (it.width != size.value.width || it.height != size.value.height) {
                    size.value = it
                    onLayout(size.value)
                }
            }
            .background(
                color = BackgroundColorDefault,
                shape = backgroundShape,
            )
            .pointerInput(Unit) {
                detectDragGestures(onDrag = { change, offset ->
                    change.consume()
                    horizontalDragOffset += offset.x
                    horizontalDragOffset = horizontalDragCallback(horizontalDragOffset)

                    verticalDragOffset += offset.y
                    verticalDragOffset = verticalDragCallback(verticalDragOffset)
                }, onDragEnd = {
                    verticalDragOffset = 0f
                })
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { sensorViewModel.onOverlayPressed() },
                    onLongPress = { sensorViewModel.onOverlayDoubleTap() })
            }) {


            val rowAlignment = when (location) {
                OverlayLocation.Top -> Alignment.Top
                OverlayLocation.Bottom -> Alignment.Bottom
            }

            OverlayMainContent(
                modifier = Modifier
                    .wrapContentWidth(unbounded = true)
                    .padding(horizontal = 9.dp)
                    .padding(bottom = 5.dp),
                rowAlignment = rowAlignment,
                power = power,
                rpm = rpm,
                pauseChart = isCurrentlyAnimating,
                powerGraph = powerGraph,
                resistance = resistance,
                speed = speed,
                speedLabel = speedLabel,
                calories = calories,
                onSpeedClicked = { sensorViewModel.onClickedSpeed() },
                onChartClicked = { sensorViewModel.onOverlayPressed() }
            )
        }
    }


    Box(
        modifier = Modifier
            .wrapContentSize(unbounded = true)
    ) {
        errorMessage?.let {
            Snackbar(
                action = {
                    Button(onClick = { sensorViewModel.onDismissErrorPressed() }) {
                        Text("Dismiss")
                    }
                },
                containerColor = Color.White,
                modifier = Modifier
                    .padding(8.dp)
                    .zIndex(1f)
            ) {
                Text(it, color = Color.Black)
            }
            return@Box
        }
        Column(
            modifier = Modifier
                .wrapContentSize()
                .offset { visibilityOffset },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            when (location) {
                OverlayLocation.Top -> {
                    mainContent()

                    timer()
                }
                OverlayLocation.Bottom -> {
                    timer()
                    mainContent()

                }
            }
        }
    }

}
