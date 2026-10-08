package com.elitedarkkaiser.redmagic.ui.components

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.google.android.material.button.MaterialButton

class LedControlViewFactory(
    private val activity: Activity
) {
    fun filterChip(
        label: String,
        selected: Boolean,
        onClick: () -> Unit
    ): Button {
        return MaterialButton(
            activity,
            null,
            com.google.android.material.R.attr
                .materialButtonOutlinedStyle
        ).apply {
            text = label
            textSize = 11f
            isAllCaps = false
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            isSingleLine = true
            minWidth = 0
            minimumWidth = 0
            setTextColor(AppTheme.textPrimary)

            backgroundTintList = ColorStateList.valueOf(
                if (selected) {
                    AppTheme.panelPressed
                } else {
                    Color.TRANSPARENT
                }
            )
            strokeWidth = dp(1)
            strokeColor = ColorStateList.valueOf(
                if (selected) {
                    AppTheme.highlightBorder
                } else {
                    AppTheme.borderColor
                }
            )
            rippleColor =
                ColorStateList.valueOf(AppTheme.rippleColor)
            cornerRadius = dp(16)

            insetTop = 0
            insetBottom = 0
            minHeight = dp(40)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { onClick() }
        }
    }

    fun fanPresetBubble(
        colors: List<String>,
        selected: () -> Boolean,
        onClick: () -> Unit
    ): View {
        require(colors.size == 4) {
            "fanPresetBubble requires exactly 4 colors"
        }

        return object : View(activity) {
            private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(3).toFloat()
            }

            init {
                val size = dp(44)
                layoutParams = LinearLayout.LayoutParams(size, size)
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }
                elevation = dp(2).toFloat()
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)

                val pad = dp(1).toFloat()
                val rect = RectF(
                    pad,
                    pad,
                    width.toFloat() - pad,
                    height.toFloat() - pad
                )

                val saveCount = canvas.save()
                val clipPath = Path().apply {
                    addOval(rect, Path.Direction.CW)
                }
                canvas.clipPath(clipPath)

                val midX = rect.centerX()
                val midY = rect.centerY()

                fillPaint.color = Color.parseColor(colors[0])
                canvas.drawRect(
                    rect.left,
                    rect.top,
                    midX,
                    midY,
                    fillPaint
                )

                fillPaint.color = Color.parseColor(colors[1])
                canvas.drawRect(
                    midX,
                    rect.top,
                    rect.right,
                    midY,
                    fillPaint
                )

                fillPaint.color = Color.parseColor(colors[2])
                canvas.drawRect(
                    rect.left,
                    midY,
                    midX,
                    rect.bottom,
                    fillPaint
                )

                fillPaint.color = Color.parseColor(colors[3])
                canvas.drawRect(
                    midX,
                    midY,
                    rect.right,
                    rect.bottom,
                    fillPaint
                )

                canvas.restoreToCount(saveCount)

                if (selected()) {
                    ringPaint.strokeWidth = dp(3).toFloat()
                    ringPaint.color = Color.WHITE
                    canvas.drawOval(rect, ringPaint)
                }
            }
        }
    }

    fun colorDot(
        hex: String,
        selected: Boolean,
        onClick: () -> Unit
    ): View {
        return View(activity).apply {
            val size = dp(44)
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = colorDotDrawable(hex, selected)
            elevation = dp(2).toFloat()
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    fun colorDotDrawable(
        hex: String,
        selected: Boolean
    ): Drawable {
        val fill = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(hex))
            setStroke(dp(3), if (selected) Color.WHITE else Color.TRANSPARENT)
        }
        return fill
    }

    private fun dp(value: Int): Int {
        return (
            value * activity.resources.displayMetrics.density
        ).toInt()
    }
}
