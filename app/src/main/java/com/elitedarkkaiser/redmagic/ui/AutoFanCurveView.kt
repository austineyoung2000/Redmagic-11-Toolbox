package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Color
import android.view.View
import com.elitedarkkaiser.redmagic.HardwareController

/** Displays the automatic controller's actual temperature-to-level thresholds. */
class AutoFanCurveView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val left = 38f * d
        val right = width - 8f * d
        val top = 10f * d
        val bottom = height - 25f * d
        if (right <= left || bottom <= top) return
        paint.textSize = 10f * d
        paint.strokeWidth = d
        paint.color = AppTheme.borderColor
        for (i in 0..5) {
            val y = bottom - (bottom - top) * i / 5f
            canvas.drawLine(left, y, right, y, paint)
            paint.color = AppTheme.textSecondary
            canvas.drawText(if (i == 0) "0" else "${i * 4}k", 2f * d, y + 3f * d, paint)
            paint.color = AppTheme.borderColor
        }
        val min = 86f
        val max = 140f
        fun x(t: Float) = left + (right - left) * (t - min) / (max - min)
        fun y(level: Int) = bottom - (bottom - top) * level / 5f
        val thresholds = floatArrayOf(95f, 104f, 113f, 122f, 131f)
        val path = Path().apply { moveTo(x(min), y(HardwareController.chooseAutoFanLevelForTempF(min))) }
        thresholds.forEach { t ->
            path.lineTo(x(t), y(HardwareController.chooseAutoFanLevelForTempF(t - 0.1f)))
            path.lineTo(x(t), y(HardwareController.chooseAutoFanLevelForTempF(t)))
        }
        path.lineTo(x(max), y(HardwareController.chooseAutoFanLevelForTempF(max)))
        val fill = Path(path).apply { lineTo(x(max), bottom); lineTo(x(min), bottom); close() }
        paint.color = Color.argb(50, Color.red(AppTheme.accentColor), Color.green(AppTheme.accentColor), Color.blue(AppTheme.accentColor))
        paint.style = Paint.Style.FILL
        canvas.drawPath(fill, paint)
        paint.color = AppTheme.accentColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * d
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        thresholds.forEach { t -> canvas.drawCircle(x(t), y(HardwareController.chooseAutoFanLevelForTempF(t)), 2.5f * d, paint) }
        paint.color = AppTheme.textSecondary
        listOf(86, 104, 122, 140).forEach { t ->
            canvas.drawText(t.toString(), x(t.toFloat()) - 8f * d, height - 5f * d, paint)
        }
    }
}
