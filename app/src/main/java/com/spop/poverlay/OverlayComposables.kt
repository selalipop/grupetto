package com.spop.poverlay

import android.view.ViewGroup.LayoutParams
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import com.spop.poverlay.util.LineChart
import com.yabu.livechart.model.DataPoint
import com.yabu.livechart.model.Dataset
import com.yabu.livechart.view.LiveChart
import com.yabu.livechart.view.LiveChartStyle
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

@Composable
fun Overlay(viewModel: OverlayViewModel) {
    val placeholderText = "-"

    val power by viewModel.powerValue.collectAsState(initial = placeholderText)

    val powerGraph = remember { viewModel.powerGraph }
    val rpm by viewModel.rpmValue.collectAsState(initial = placeholderText)
    val resistance by viewModel.resistanceValue.collectAsState(initial = placeholderText)
    val speed by viewModel.speedValue.collectAsState(initial = placeholderText)

    val visible by viewModel.isVisible.collectAsState(initial = true)
    var rowSize by remember { mutableStateOf(IntSize.Zero) }

    val backgroundColor by animateColorAsState(
        if (visible) {
            Color(20, 20, 20)
        } else {
            Color(252, 93, 72)
        }, animationSpec = TweenSpec(150, 0)
    )

    val visibilityOffset by animateIntOffsetAsState(
        if (visible) {
            IntOffset.Zero
        } else {
            IntOffset(0, (rowSize.height * .9f).roundToInt())
        },
        animationSpec = TweenSpec(150, 0, LinearEasing)
    )

    Row(
        modifier = Modifier
            .offset { visibilityOffset }
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            )
            .padding(horizontal = 48.dp)
            .wrapContentSize(unbounded = true)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { viewModel.onOverlayPressed() },
                    onLongPress = { viewModel.onOverlayLongPress() }
                )
            }
            .onGloballyPositioned { coordinates ->
                rowSize = coordinates.size
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatCard("Power", power, "watts")
        StatCard("Cadence", rpm, "rpm")
        LineChart(
            powerGraph,
            modifier = Modifier
                .width(175.dp)
                .height(120.dp),
            fillColor = Color(AndroidColor.parseColor("#FF3348")),
            lineColor = Color(AndroidColor.parseColor("#D9182B")),
        )
        StatCard("Resistance", resistance, "")
        StatCard("Speed", speed, "mph")
    }

}


@Composable
private fun StatCard(name: String, value: String, unit: String) {
    Column(
        modifier = Modifier
            .width(140.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = name,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
        )
        Text(
            text = value,
            color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = unit,
            fontSize = 12.sp,
            color = Color.White,
        )
    }
}

