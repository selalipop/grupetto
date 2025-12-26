@file:OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)

package com.spop.poverlay.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Snackbar
import androidx.compose.material.Text
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
import com.spop.poverlay.overlay.composables.OverlayMainContent
import com.spop.poverlay.overlay.composables.OverlayMinimizedContent
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
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
    val power by sensorViewModel.powerValue.collectAsState(initial = SensorValuePlaceholderText)

    val selectedMetric by sensorViewModel.selectedMetric.collectAsState(initial = MetricType.POWER)
    val currentGraph = remember(selectedMetric) { sensorViewModel.getGraphForMetric(selectedMetric) }
    val rpm by sensorViewModel.rpmValue.collectAsState(initial = SensorValuePlaceholderText)
    val resistance by sensorViewModel.resistanceValue.collectAsState(initial = SensorValuePlaceholderText)
    val speed by sensorViewModel.speedValue.collectAsState(initial = SensorValuePlaceholderText)
    val speedLabel by sensorViewModel.speedLabel.collectAsState(initial = "")
    val timerLabel by timerViewModel.timerLabel.collectAsState(initial = "")
    val isTimerPaused by timerViewModel.timerPaused.collectAsState(initial = false)
    val errorMessage by sensorViewModel.errorMessage.collectAsState(initial = null)

    // Max values
    val maxPower by sensorViewModel.maxPower.collectAsState()
    val maxCadence by sensorViewModel.maxCadence.collectAsState()
    val maxResistance by sensorViewModel.maxResistance.collectAsState()
    val maxSpeed by sensorViewModel.maxSpeed.collectAsState()

    // Session totals and averages
    val totalEnergy by sensorViewModel.totalEnergy.collectAsState()
    val totalDistance by sensorViewModel.totalDistance.collectAsState()
    val avgCadence by sensorViewModel.avgCadence.collectAsState()
    val avgResistance by sensorViewModel.avgResistance.collectAsState()

    var isCurrentlyAnimating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        sensorViewModel.isMinimized
            .drop(1) // Ignore the initial value since animations only happen after new updates
            .collect(object : FlowCollector<Boolean> {
                override suspend fun emit(value: Boolean) {
                    isCurrentlyAnimating = true
                }
            })
    }

    val minimized by sensorViewModel.isMinimized.collectAsState(initial = false)
    val location by locationState
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
            .collectAsState(initial = true)

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
            onMinimizeToggle = { sensorViewModel.onOverlayPressed() },
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
                detectDragGestures(onDrag = { _, offset ->
                    horizontalDragOffset += offset.x
                    horizontalDragOffset = horizontalDragCallback(horizontalDragOffset)

                    verticalDragOffset += offset.y
                    verticalDragOffset = verticalDragCallback(verticalDragOffset)
                }, onDragEnd = {
                    verticalDragOffset = 0f
                })
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
                currentGraph = currentGraph,
                selectedMetric = selectedMetric,
                resistance = resistance,
                speed = speed,
                speedLabel = speedLabel,
                maxPower = "%.0f".format(maxPower),
                maxCadence = "%.0f".format(maxCadence),
                maxResistance = "%.0f".format(maxResistance),
                maxSpeed = "%.1f".format(maxSpeed),
                totalEnergy = "%.0f".format(totalEnergy),
                totalDistance = if (speedLabel == "mph") "%.2f".format(totalDistance) else "%.2f".format(totalDistance * 1.60934f),
                distanceUnit = if (speedLabel == "mph") "mi" else "km",
                avgCadence = "%.0f".format(avgCadence),
                avgResistance = "%.0f".format(avgResistance),
                onMetricSelected = { sensorViewModel.onMetricSelected(it) },
                onSpeedUnitClicked = { sensorViewModel.onClickedSpeedUnit() },
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
                backgroundColor = Color.White,
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
