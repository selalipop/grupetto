package com.spop.poverlay.overlay.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.spop.poverlay.overlay.PowerChartFullWidth
import com.spop.poverlay.overlay.PowerChartShrunkWidth
import com.spop.poverlay.overlay.StatCard
import com.spop.poverlay.overlay.StatCardWidth
import com.spop.poverlay.util.LineChart


@Composable
fun OverlayMainContent(
    modifier: Modifier,
    rowAlignment: Alignment.Vertical,
    power: String,
    rpm: String,
    powerGraph: List<Float>,
    resistance: String,
    speed: String,
    speedLabel: String,
    calories: String,
    pauseChart : Boolean,
    onSpeedClicked : ()->Unit,
    onChartClicked : ()->Unit
) {
    var shrinkChart by remember { mutableStateOf(false) }
    Row(
        modifier = modifier,
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
        val chartPadding = if (shrinkChart) {
            15.dp
        } else {
            8.dp
        }
        LineChart(
            data = powerGraph,
            maxValue = 250f,
            pauseChart = pauseChart,
            modifier = Modifier
                .requiredWidth(chartWidth)
                .requiredHeight(100.dp)
                .padding(horizontal = chartPadding)
                .padding(bottom = 10.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onChartClicked() },
                        onLongPress = { shrinkChart = !shrinkChart }
                    )
                },
            fillColor = Color(android.graphics.Color.parseColor("#FF3348")),
            lineColor = Color(android.graphics.Color.parseColor("#D9182B")),
        )
        StatCard("Resistance", resistance, "", statCardModifier)

        StatCard("Speed", speed, speedLabel, statCardModifier.clickable {
            onSpeedClicked()
        })

        StatCard("Calories", calories, "kcal", statCardModifier)

    }
}


