package com.spop.poverlay.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.spop.poverlay.util.LineChart
import android.graphics.Color as AndroidColor

//This is the percentage of the overlay that is offscreen when hidden
const val PercentVisibleWhenHidden = .14f
const val VisibilityChangeDuration = 150
val OverlayCornerRadius = 25.dp
val StatCardWidth = 105.dp
val PowerChartFullWidth = 200.dp
val PowerChartShrunkWidth = 120.dp


@Composable
fun Overlay(
    viewModel: OverlaySensorViewModel,
    height: Dp,
    locationState: State<OverlayLocation>,
    horizontalDragCallback: (Float) -> Float,
    verticalDragCallback: (Float) -> Float,
    offsetCallback: (Float, Float) -> Unit,
    onLayout: (IntSize) -> Unit
) {
    val placeholderText = "-"

    val power by viewModel.powerValue.collectAsState(initial = placeholderText)

    val powerGraph = remember { viewModel.powerGraph }
    val rpm by viewModel.rpmValue.collectAsState(initial = placeholderText)
    val resistance by viewModel.resistanceValue.collectAsState(initial = placeholderText)
    val speed by viewModel.speedValue.collectAsState(initial = placeholderText)
    val speedLabel by viewModel.speedLabel.collectAsState(initial = "")

    val visible by viewModel.isVisible.collectAsState(initial = true)
    val location by remember { locationState }
    val size = remember { mutableStateOf(IntSize.Zero) }
    var shrinkChart by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        if (visible) {
            Color(20, 20, 20)
        } else {
            Color(252, 93, 72)
        }, animationSpec = TweenSpec(VisibilityChangeDuration, 0)
    )

    val maxOffset = with(LocalDensity.current) {
        (height * (1 - PercentVisibleWhenHidden)).roundToPx()
    }

    val contentAlpha by animateFloatAsState(
        if (visible) {
            1f
        } else {
            0f
        },
        animationSpec = TweenSpec(VisibilityChangeDuration, 0, LinearEasing)
    )

    val visibilityOffset by animateIntOffsetAsState(
        if (visible) {
            IntOffset.Zero
        } else {
            when (location) {
                OverlayLocation.Top -> IntOffset(0, -maxOffset)
                OverlayLocation.Bottom -> IntOffset(0, maxOffset)
            }
        },
        animationSpec = TweenSpec(VisibilityChangeDuration, 0, LinearEasing)
    )

    offsetCallback(visibilityOffset.y.toFloat(), size.value.height.toFloat())

    var horizontalDragOffset by remember { mutableStateOf(0f) }
    var verticalDragOffset by remember { mutableStateOf(0f) }

    val backgroundShape = when (location) {
        OverlayLocation.Top -> RoundedCornerShape(
            bottomStart = OverlayCornerRadius,
            bottomEnd = OverlayCornerRadius
        )
        OverlayLocation.Bottom -> RoundedCornerShape(
            topStart = OverlayCornerRadius,
            topEnd = OverlayCornerRadius
        )
    }
    Box(modifier = Modifier
        .offset { visibilityOffset }
        .requiredHeight(height)
        .wrapContentWidth(unbounded = true)
        .onSizeChanged {
            if (it.width != size.value.width ||
                it.height != size.value.height
            ) {
                size.value = it
                onLayout(size.value)
            }
        }
        .background(
            color = backgroundColor,
            shape = backgroundShape,
        )
        .pointerInput(Unit) {
            detectDragGestures(
                onDrag = { change, offset ->
                    change.consume()
                    horizontalDragOffset += offset.x
                    horizontalDragOffset =
                        horizontalDragCallback(horizontalDragOffset)

                    verticalDragOffset += offset.y
                    verticalDragOffset =
                        verticalDragCallback(verticalDragOffset)
                },
                onDragEnd = {
                    verticalDragOffset = 0f
                }
            )
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { viewModel.onOverlayPressed() },
                onLongPress = { viewModel.onOverlayDoubleTap() }
            )
        }
    ) {

        val rowAlignment = when (location) {
            OverlayLocation.Top -> Alignment.Top
            OverlayLocation.Bottom -> Alignment.Bottom
        }
        Row(
            modifier = Modifier
                .wrapContentWidth(unbounded = true)
                .padding(horizontal = 9.dp)
                .padding(bottom = 5.dp)
                .alpha(contentAlpha),
            verticalAlignment = rowAlignment,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val statCardModifier = Modifier.requiredWidth(StatCardWidth)

            StatCard("Power", power, "watts", statCardModifier)

            StatCard("Cadence", rpm, "rpm", statCardModifier)
            val chartWidth = if (shrinkChart) {
                PowerChartShrunkWidth
            } else {
                PowerChartFullWidth
            }
            LineChart(
                data = powerGraph,
                maxValue = 250f,
                modifier = Modifier
                    .requiredWidth(chartWidth)
                    .requiredHeight(100.dp)
                    .padding(horizontal = 15.dp)
                    .padding(bottom = 10.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { viewModel.onOverlayPressed() },
                            onLongPress = { shrinkChart = !shrinkChart }
                        )
                    },
                fillColor = Color(AndroidColor.parseColor("#FF3348")),
                lineColor = Color(AndroidColor.parseColor("#D9182B")),
            )
            StatCard("Resistance", resistance, "", statCardModifier)

            StatCard("Speed", speed, speedLabel, statCardModifier.clickable {
                viewModel.onClickedSpeed()
            })

        }
    }
}


