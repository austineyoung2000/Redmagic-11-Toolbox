package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import com.elitedarkkaiser.redmagic.TemperatureHistory
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class ThermalHistoryView(
    context: Context,
    private val compact: Boolean = false
) : View(context) {
    private val density =
        resources.displayMetrics.density

    private val gridPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {
        color = context.getColor(
            com.elitedarkkaiser.redmagic.R.color.redmagic_graph_grid
        )
        strokeWidth = density
    }

    private val linePaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {
        color = if (compact) AppTheme.accentColor else context.getColor(
            com.elitedarkkaiser.redmagic.R.color.redmagic_graph_line
        )
        if (compact) alpha = 150
        strokeWidth = 2.25f * density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {
        style = Paint.Style.FILL
    }

    private val pointPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {
        color = context.getColor(
            com.elitedarkkaiser.redmagic.R.color.redmagic_graph_point
        )
        style = Paint.Style.FILL
        if (compact) alpha = 180
    }

    private val labelPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {
        color = AppTheme.textSecondary
        textSize = 11f *
            resources.configuration.fontScale *
            density
    }

    private val valuePaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {
        color = AppTheme.textPrimary
        textSize = 12f *
            resources.configuration.fontScale *
            density
    }

    private var samples:
        List<TemperatureHistory.Sample> = emptyList()
    private var useFahrenheit = true

    init {
        minimumHeight = ((if (compact) 32f else 180f) * density).toInt()
        contentDescription =
            "Thermal history is collecting samples"
    }

    fun setHistory(
        samples: List<TemperatureHistory.Sample>,
        useFahrenheit: Boolean
    ) {
        this.samples = samples
        this.useFahrenheit = useFahrenheit
        updateContentDescription()
        invalidate()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        val desiredHeight = ((if (compact) 32f else 180f) * density).toInt()
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(
                desiredHeight,
                heightMeasureSpec
            )
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val left = (if (compact) 4f else 8f) * density
        val right = viewWidth - (if (compact) 4f else 8f) * density
        val top = (if (compact) 4f else 32f) * density
        val bottom = viewHeight - (if (compact) 4f else 28f) * density

        if (!compact) drawGrid(canvas, left, right, top, bottom)

        if (samples.isEmpty()) {
            val message =
                "Collecting temperature history…"
            if (!compact) canvas.drawText(
                message,
                (
                    viewWidth -
                        labelPaint.measureText(message)
                ) / 2f,
                viewHeight / 2f,
                labelPaint
            )
            return
        }

        val temperatures = samples.map {
            displayTemperature(it.temperatureC)
        }
        val actualMin = temperatures.minOrNull()
            ?: return
        val actualMax = temperatures.maxOrNull()
            ?: return
        val (graphMin, graphMax) = if (compact) {
            ThermalGraphBounds.compact(actualMin, actualMax, useFahrenheit)
        } else {
            val padding = if (useFahrenheit) 3f else 2f
            min(actualMin - padding, displayTemperature(35f)) to
                max(actualMax + padding, displayTemperature(55f))
        }
        val range = (graphMax - graphMin)
            .coerceAtLeast(1f)

        val firstTime = samples.first()
            .elapsedRealtimeMs
        val lastTime = samples.last()
            .elapsedRealtimeMs
        val timeRange = (lastTime - firstTime)
            .coerceAtLeast(1L)

        val linePath = Path()
        val fillPath = Path()
        var lastX = right
        var lastY = bottom

        samples.forEachIndexed { index, sample ->
            val x = if (samples.size == 1) {
                right
            } else {
                left +
                    ((
                        sample.elapsedRealtimeMs -
                            firstTime
                    ).toFloat() / timeRange) *
                    (right - left)
            }
            val value = displayTemperature(
                sample.temperatureC
            )
            val y = bottom -
                ((value - graphMin) / range) *
                (bottom - top)

            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, bottom)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }

            lastX = x
            lastY = y
        }

        fillPath.lineTo(lastX, bottom)
        fillPath.close()

        fillPaint.shader = LinearGradient(
            0f,
            top,
            0f,
            bottom,
            if (compact) Color.argb(55, 243, 53, 77) else Color.argb(100, 143, 163, 191),
            if (compact) Color.argb(3, 243, 53, 77) else Color.argb(8, 143, 163, 191),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)
        canvas.drawCircle(
            lastX,
            lastY,
            3.5f * density,
            pointPaint
        )

        if (compact) return

        val current = temperatures.last()
        val summary = String.format(
            Locale.US,
            "Now %.1f%s   Min %.1f%s   Max %.1f%s",
            current,
            unitSuffix(),
            actualMin,
            unitSuffix(),
            actualMax,
            unitSuffix()
        )
        canvas.drawText(
            summary,
            left,
            18f * density,
            valuePaint
        )

        val span = formatSpan(lastTime - firstTime)
        canvas.drawText(
            span,
            right - labelPaint.measureText(span),
            viewHeight - 8f * density,
            labelPaint
        )
    }

    private fun drawGrid(
        canvas: Canvas,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float
    ) {
        for (index in 0..3) {
            val y = top +
                ((bottom - top) * index / 3f)
            canvas.drawLine(
                left,
                y,
                right,
                y,
                gridPaint
            )
        }

        for (index in 0..4) {
            val x = left +
                ((right - left) * index / 4f)
            canvas.drawLine(
                x,
                top,
                x,
                bottom,
                gridPaint
            )
        }
    }

    private fun displayTemperature(
        temperatureC: Float
    ): Float {
        return if (useFahrenheit) {
            temperatureC * 9f / 5f + 32f
        } else {
            temperatureC
        }
    }

    private fun unitSuffix(): String {
        return if (useFahrenheit) "°F" else "°C"
    }

    private fun formatSpan(spanMs: Long): String {
        val seconds = spanMs / 1_000L
        return when {
            seconds < 60L -> "Last ${seconds}s"
            else -> "Last ${seconds / 60L}m"
        }
    }

    private fun updateContentDescription() {
        val values = samples.map {
            displayTemperature(it.temperatureC)
        }
        val current = values.lastOrNull()

        contentDescription = if (current == null) {
            "Thermal history is collecting samples"
        } else {
            String.format(
                Locale.US,
                "Thermal history. Current %.1f%s, " +
                    "minimum %.1f%s, maximum %.1f%s",
                current,
                unitSuffix(),
                values.minOrNull() ?: current,
                unitSuffix(),
                values.maxOrNull() ?: current,
                unitSuffix()
            )
        }
    }
}
