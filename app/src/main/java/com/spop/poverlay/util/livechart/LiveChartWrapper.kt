@file:Suppress("unused")

package com.spop.poverlay.util.livechart


import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.yabu.livechart.model.Dataset
import com.yabu.livechart.view.LiveChartStyle
import com.yabu.livechart.view.LiveChartTouchOverlay

/**
 * A wrapper to enable patching a bug in LiveChart library
 *
 * I wish I had documented what bug that was.
 */
class LiveChart(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    /**
     * [LiveChartView] reference.
     * Pass the attributes to the chart.
     */
    private val livechart = LiveChartView(context, attrs).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT)
    }

    /**
     * [LiveChartTouchOverlay] reference.
     * Pass the attributes to the view.
     */
    private val overlay = LiveChartTouchOverlay(context, attrs).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT)
    }

    /**
     * Flag to disable touch overlay.
     */
    private var disableTouchOverlay: Boolean = false

    init {
        clipToOutline = false
        clipToPadding = false
        clipChildren = false

        // Add the views to the layout.
        this.addView(livechart)
        this.addView(overlay)
    }

    /**
     * Set the [dataset] of this chart.
     */
    
    fun setDataset(dataset: Dataset): LiveChart {
        livechart.setDataset(dataset)
        overlay.setDataset(dataset)
        return this
    }

    /**
     * Set the Second [dataset] of this chart.
     */
    
    fun setSecondDataset(dataset: Dataset): LiveChart {
        livechart.setSecondDataset(dataset)
        overlay.setSecondDataset(dataset)
        return this
    }

    /**
     * Set the style object [LiveChartStyle] to this chart.
     */
    
    fun setLiveChartStyle(style: LiveChartStyle): LiveChart {
        livechart.setLiveChartStyle(style)
        overlay.setLiveChartStyle(style)
        return this
    }

    /**
     * Draw baseline flag.
     */
    @Suppress("UNUSED")
    
    fun drawBaselineConditionalColor(): LiveChart {
        livechart.drawBaselineConditionalColor()

        return this
    }

    /**
     * Draw baseline flag.
     */
    
    fun drawBaseline(): LiveChart {
        livechart.drawBaseline()

        return this
    }

    /**
     * Draw baseline automatically from first point.
     */
    @Suppress("UNUSED")
    
    fun drawBaselineFromFirstPoint(): LiveChart {
        livechart.drawBaselineFromFirstPoint()

        return this
    }

    /**
     * Draw Fill flag.
     */
    
    fun drawFill(withGradient: Boolean = true): LiveChart {
        livechart.drawFill(withGradient)
        return this
    }

    /**
     * Draw smooth path flag.
     */
    
    fun drawSmoothPath(): LiveChart {
        livechart.drawSmoothPath()
        overlay.drawSmoothPath()
        return this
    }

    
    fun drawStraightPath(): LiveChart {
        livechart.drawStraightPath()
        overlay.drawStraightPath()
        return this
    }

    /**
     * Disable Fill flag.
     */
    @Suppress("UNUSED")
    
    fun disableFill(): LiveChart {
        livechart.disableFill()
        return this
    }

    /**
     * Draw Y bounds flag.
     */
    
    fun drawYBounds(): LiveChart {
        livechart.drawYBounds()
        overlay.drawYBounds()
        return this
    }

    /**
     * Draw last point label flag.
     */
    @Suppress("UNUSED")
    
    fun drawLastPointLabel(): LiveChart {
        livechart.drawLastPointLabel()

        return this
    }

    /**
     * Set [baseline] data point manually instead of determining from first dataset point.
     */
    @Suppress("UNUSED")
    
    fun setBaselineManually(baseline: Float): LiveChart {
        livechart.setBaselineManually(baseline)

        return this
    }

    @Suppress("UNUSED")
    
    fun setOnTouchCallbackListener(listener: com.yabu.livechart.view.LiveChart.OnTouchCallback): LiveChart {
        overlay.setOnTouchCallbackListener(listener)

        return this
    }

    /**
     * Disable the touch overlay component.
     * This is useful for small charts that do not benefit from showing the touch event
     * or as an optimization if you require less overhead on your View.
     */
    @Suppress("UNUSED")
    
    fun disableTouchOverlay(): LiveChart {
        overlay.visibility = View.GONE
        disableTouchOverlay = true
        return this
    }

    /**
     * Draw vertical guidelines
     * @param steps Number of guidelines
     */
    @Suppress("UNUSED")
    
    fun drawVerticalGuidelines(steps: Int): LiveChart {
        livechart.drawVerticalGuidelines(steps)

        return this
    }

    /**
     * Draw horizontal guidelines
     * @param steps Number of guidelines
     */
    @Suppress("UNUSED")
    
    fun drawHorizontalGuidelines(steps: Int): LiveChart {
        livechart.drawHorizontalGuidelines(steps)

        return this
    }

    /**
     * Show Overlay by default, not just on touch.
     */
    
    fun drawTouchOverlayAlways(): LiveChart {
        disableTouchOverlay = false
        overlay.alwaysDisplay()

        return this
    }

    /**
     * Draw on chart and bind overlay to dataset.
     */
    
    fun drawDataset() {
        livechart.drawDataset()
        if (!disableTouchOverlay) {
            overlay.bindToDataset()
        }
    }
}
