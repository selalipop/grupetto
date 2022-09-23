package com.spop.poverlay.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.spop.poverlay.util.LineChart
import kotlin.math.abs
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

//This is the percentage of the overlay that is offscreen when hidden
const val PercentVisibleWhenHidden = .14f
const val VisibilityChangeDuration = 150
val OverlayCornerRadius = 25.dp
val StatCardWidth = 105.dp
val PowerChartWidth = 160.dp
@Composable
fun Overlay(
    viewModel: OverlayViewModel,
    height: Dp,
    location: OverlayLocation,
    dragCallback: (Int) -> Unit,
    offsetCallback: (Int, Int) -> Unit
) {
    val placeholderText = "-"

    val power by viewModel.powerValue.collectAsState(initial = placeholderText)

    val powerGraph = remember { viewModel.powerGraph }
    val rpm by viewModel.rpmValue.collectAsState(initial = placeholderText)
    val resistance by viewModel.resistanceValue.collectAsState(initial = placeholderText)
    val speed by viewModel.speedValue.collectAsState(initial = placeholderText)
    val speedLabel by viewModel.speedLabel.collectAsState(initial = "")

    val visible by viewModel.isVisible.collectAsState(initial = true)

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

    val heightPx = with(LocalDensity.current) {
        height.roundToPx()
    }

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

    val hideOffset = visibilityOffset.y
    val remainingVisibleHeight = abs(heightPx - (abs(hideOffset)))
    offsetCallback(visibilityOffset.y, remainingVisibleHeight)

    var dragOffset by remember { mutableStateOf(0f) }

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
        .background(
            color = backgroundColor,
            shape = backgroundShape,
        )
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { viewModel.onOverlayPressed() },
                onLongPress = { viewModel.onOverlayLongPress() }
            )
        }
        .draggable(
            orientation = Orientation.Horizontal,
            state = rememberDraggableState { delta ->
                dragOffset += delta
                dragCallback(dragOffset.roundToInt())
            }
        )
        .wrapContentWidth(unbounded = false)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val rowAlignment = when (location) {
                OverlayLocation.Top -> Alignment.Top
                OverlayLocation.Bottom -> Alignment.Bottom
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 9.dp)
                    .padding(bottom = 5.dp),
                verticalAlignment = rowAlignment,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val statCardModifier = Modifier.width(StatCardWidth)
                StatCard("Power", power, "watts", statCardModifier)
                StatCard("Cadence", rpm, "rpm", statCardModifier)
                LineChart(
                    data = powerGraph,
                    maxValue = 250f,
                    modifier = Modifier
                        .requiredWidth(PowerChartWidth)
                        .requiredHeight(100.dp)
                        .padding(bottom = 10.dp)
                        .padding(horizontal = 5.dp),
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

}
