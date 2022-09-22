package com.spop.poverlay.util

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.spop.poverlay.OverlayViewModel
import com.yabu.livechart.model.DataPoint
import com.yabu.livechart.model.Dataset
import com.yabu.livechart.view.LiveChart
import com.yabu.livechart.view.LiveChartStyle

@Composable
fun LineChart(
    input: Collection<Float>,
    modifier: Modifier,
    fillColor: Color,
    lineColor: Color,
) {
    val graph = remember { input }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            LiveChart(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
                clipChildren = false
            }.setLiveChartStyle(LiveChartStyle().apply {
                textColor = android.graphics.Color.BLUE
                textHeight = 30f
                mainColor = lineColor.toArgb()
                mainFillColor = fillColor.toArgb()
                baselineColor = android.graphics.Color.BLUE
                pathStrokeWidth = 4f
                baselineStrokeWidth = 6f
                mainCornerRadius = 40f
                secondColor = android.graphics.Color.TRANSPARENT
            }).disableTouchOverlay()

        },
        update = { view ->
            view.setDataset(Dataset(graph.mapIndexed { index, value ->
                //Start axis at 1f to keep line visible at all times
                DataPoint(index.toFloat(), value + 1f)
            }.toMutableList()))
                .setSecondDataset(
                    Dataset(
                        mutableListOf(
                            DataPoint(0f, 0f),
                            DataPoint(OverlayViewModel.GraphMaxDataPoints.toFloat(), 200f)
                        )
                    )
                ).drawSmoothPath()
                .drawFill(withGradient = true)
                .drawDataset()
        }
    )
}