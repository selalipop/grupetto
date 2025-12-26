package com.spop.poverlay.overlay.composables

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spop.poverlay.R
import com.spop.poverlay.overlay.MetricType
import com.spop.poverlay.overlay.PowerChartFullWidth
import com.spop.poverlay.overlay.PowerChartShrunkWidth
import com.spop.poverlay.overlay.StatCard
import com.spop.poverlay.overlay.StatCardWidth
import com.spop.poverlay.ui.theme.MetricCadenceColor
import com.spop.poverlay.ui.theme.MetricPowerColor
import com.spop.poverlay.ui.theme.MetricResistanceColor
import com.spop.poverlay.ui.theme.MetricSpeedColor
import com.spop.poverlay.util.LineChart


@Composable
fun OverlayMainContent(
    modifier: Modifier,
    rowAlignment: Alignment.Vertical,
    power: String,
    rpm: String,
    currentGraph: List<Float>,
    selectedMetric: MetricType,
    resistance: String,
    speed: String,
    speedLabel: String,
    pauseChart: Boolean,
    maxPower: String,
    maxCadence: String,
    maxResistance: String,
    maxSpeed: String,
    totalEnergy: String,
    totalDistance: String,
    distanceUnit: String,
    avgCadence: String,
    avgResistance: String,
    onMetricSelected: (MetricType) -> Unit,
    onSpeedUnitClicked: () -> Unit,
    onChartClicked: () -> Unit
) {
    var shrinkChart by remember { mutableStateOf(false) }

    val chartColor = when (selectedMetric) {
        MetricType.POWER -> MetricPowerColor
        MetricType.CADENCE -> MetricCadenceColor
        MetricType.RESISTANCE -> MetricResistanceColor
        MetricType.SPEED -> MetricSpeedColor
    }

    val chartLabel = when (selectedMetric) {
        MetricType.POWER -> "Power"
        MetricType.CADENCE -> "Cadence"
        MetricType.RESISTANCE -> "Resistance"
        MetricType.SPEED -> "Speed"
    }

    val chartMaxValue = when (selectedMetric) {
        MetricType.POWER -> 250f
        MetricType.CADENCE -> 160f
        MetricType.RESISTANCE -> 100f
        MetricType.SPEED -> 40f
    }

    Row(
        modifier = modifier,
        verticalAlignment = rowAlignment,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val statCardModifier = Modifier.requiredWidth(StatCardWidth)

        StatCard(
            name = "Power",
            value = power,
            unit = "watts",
            modifier = statCardModifier,
            iconDrawable = R.drawable.ic_power,
            maxValue = maxPower,
            totalValue = totalEnergy,
            totalUnit = "kJ",
            color = MetricPowerColor,
            onClick = { onMetricSelected(MetricType.POWER) }
        )

        StatCard(
            name = "Cadence",
            value = rpm,
            unit = "rpm",
            modifier = statCardModifier,
            iconDrawable = R.drawable.ic_cadence,
            maxValue = maxCadence,
            totalValue = avgCadence,
            totalUnit = "avg",
            color = MetricCadenceColor,
            onClick = { onMetricSelected(MetricType.CADENCE) }
        )

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

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onChartClicked() },
                        onLongPress = { shrinkChart = !shrinkChart }
                    )
                }
        ) {
            Text(
                text = chartLabel,
                color = chartColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            LineChart(
                data = currentGraph,
                maxValue = chartMaxValue,
                pauseChart = pauseChart,
                modifier = Modifier
                    .requiredWidth(chartWidth)
                    .requiredHeight(90.dp)
                    .padding(horizontal = chartPadding),
                fillColor = chartColor.copy(alpha = 0.6f),
                lineColor = chartColor,
            )
        }

        StatCard(
            name = "Resistance",
            value = resistance,
            unit = "%",
            modifier = statCardModifier,
            iconDrawable = R.drawable.ic_resistance,
            maxValue = maxResistance,
            totalValue = avgResistance,
            totalUnit = "avg",
            color = MetricResistanceColor,
            onClick = { onMetricSelected(MetricType.RESISTANCE) }
        )

        StatCard(
            name = "Speed",
            value = speed,
            unit = speedLabel,
            modifier = statCardModifier,
            iconDrawable = R.drawable.ic_speed,
            maxValue = maxSpeed,
            totalValue = totalDistance,
            totalUnit = distanceUnit,
            color = MetricSpeedColor,
            onClick = { onMetricSelected(MetricType.SPEED) },
            onUnitClick = onSpeedUnitClicked
        )

    }
}


