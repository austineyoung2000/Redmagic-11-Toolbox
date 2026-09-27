package com.elitedarkkaiser.redmagic.ui.components

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.google.android.material.button.MaterialButton
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

/**
 * Builds the shared, stateless view primitives used by the main activity.
 *
 * Keeping these factories outside the activity leaves the activity focused on
 * lifecycle, state, navigation, and feature orchestration. This class does not
 * own services or device behavior.
 */
class MainActivityUiKit(
    private val activity: Activity
) {
    private val typeface: Typeface = Typeface.SANS_SERIF

    fun scrollTabContainer(): LinearLayout {
        val inner = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            addView(ScrollView(activity).apply {
                setBackgroundColor(AppTheme.bgColor)
                addView(inner)
            })

            tag = inner
        }
    }

    fun subtitleText(text: String): TextView {
        return TextView(activity).apply {
            this.text = text
            textSize = 13f
            setTextColor(AppTheme.textSecondary)
            setPadding(0, dp(4), 0, 0)
        }
    }

    fun ledTitleText(text: String): TextView {
        return TextView(activity).apply {
            this.text = text
            textSize = 20f
            setTextColor(AppTheme.textPrimary)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(14), 0, 0, 0)
            setShadowLayer(
                dp(6).toFloat(),
                0f,
                0f,
                AppTheme.accentColor
            )

            val titleView = this
            val animator = ValueAnimator.ofObject(
                ArgbEvaluator(),
                AppTheme.accentColor,
                AppTheme.textPrimary,
                AppTheme.accentColor
            ).apply {
                duration = 1800L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator()
                addUpdateListener { animation ->
                    val color = animation.animatedValue as Int
                    titleView.setTextColor(color)
                    titleView.setShadowLayer(
                        dp(8).toFloat(),
                        0f,
                        0f,
                        color
                    )
                }
            }

            addOnAttachStateChangeListener(
                object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(view: View) {
                        if (!animator.isStarted) {
                            animator.start()
                        }
                    }

                    override fun onViewDetachedFromWindow(view: View) {
                        animator.cancel()
                    }
                }
            )
        }
    }

    fun bodyText(text: String): TextView {
        return TextView(activity).apply {
            this.text = text
            textSize = 13f
            setTextColor(AppTheme.textSecondary)
            setPadding(0, dp(4), 0, 0)
        }
    }

    fun infoRow(
        label: String,
        valueView: TextView
    ): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, 0, 0, dp(6))

            val labelView = TextView(activity).apply {
                text = label
                textSize = 13f
                setTextColor(AppTheme.textSecondary)
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    dp(64),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            valueView.layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )

            addView(labelView)
            addView(valueView)
        }
    }

    fun infoValue(): TextView {
        return TextView(activity).apply {
            text = "--"
            textSize = 13f
            setTextColor(AppTheme.textPrimary)
            setLineSpacing(0f, 1.1f)
            isSingleLine = false
            maxLines = 4
        }
    }

    fun sectionHeader(
        icon: String,
        text: String
    ): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))

            val iconView = TextView(activity).apply {
                this.text = icon
                textSize = 11f
                setTextColor(AppTheme.textSecondary)
                gravity = Gravity.CENTER
                background = roundedFill(
                    AppTheme.panelPressed,
                    10
                )
                setPadding(dp(7), dp(5), dp(7), dp(5))
            }

            val labelView = TextView(activity).apply {
                this.text = text
                textSize = 11f
                setTextColor(AppTheme.accentColor)
                setTypeface(typeface, Typeface.BOLD)
                letterSpacing = 0.06f
                setPadding(dp(8), 0, 0, 0)
            }

            addView(iconView)
            addView(labelView)
        }
    }

    fun sectionPanel(): LinearLayout {
        val shapeModel = ShapeAppearanceModel.builder()
            .setAllCornerSizes(dp(22).toFloat())
            .build()

        val panelBackground = MaterialShapeDrawable(shapeModel).apply {
            initializeElevationOverlay(activity)
            fillColor = ColorStateList.valueOf(AppTheme.panelColor)
            strokeWidth = dp(1).toFloat()
            strokeColor = ColorStateList.valueOf(AppTheme.borderColor)
            elevation = dp(2).toFloat()
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = panelBackground
            elevation = dp(2).toFloat()
            clipToOutline = true

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14)
            }
        }
    }

    fun statusChip(text: String): TextView {
        return TextView(activity).apply {
            this.text = text
            setTextColor(AppTheme.textPrimary)
            textSize = 10f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            isSingleLine = true
            minHeight = dp(40)
            minimumWidth = dp(80)
            includeFontPadding = false
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = roundedFill(AppTheme.chipOnColor, 999)
        }
    }

    fun updateSelectableButton(
        button: Button,
        selected: Boolean
    ) {
        button.isSelected = selected

        if (button is MaterialButton) {
            button.backgroundTintList = ColorStateList.valueOf(
                if (selected) {
                    AppTheme.panelPressed
                } else {
                    Color.TRANSPARENT
                }
            )
            button.strokeColor = ColorStateList.valueOf(
                if (selected) {
                    AppTheme.highlightBorder
                } else {
                    AppTheme.borderColor
                }
            )
        }
    }

    fun subtleLabel(text: String): TextView {
        return TextView(activity).apply {
            this.text = text
            textSize = 12f
            setTextColor(AppTheme.textSecondary)
            setPadding(0, dp(4), 0, 0)
        }
    }

    fun segmentedChip(
        label: String,
        selected: Boolean,
        onClick: () -> Unit
    ): Button {
        return MaterialButton(
            activity,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setTextColor(AppTheme.textPrimary)
            strokeWidth = dp(1)
            rippleColor =
                ColorStateList.valueOf(AppTheme.rippleColor)
            cornerRadius = dp(20)

            insetTop = 0
            insetBottom = 0
            minHeight = dp(48)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            updateSelectableButton(this, selected)
            setOnClickListener { onClick() }
        }
    }

    fun actionButton(
        text: String,
        isDanger: Boolean = false,
        onClick: () -> Unit
    ): Button {
        return MaterialButton(activity).apply {
            this.text = text
            textSize = 13f
            isAllCaps = false
            setTextColor(AppTheme.textPrimary)

            backgroundTintList = ColorStateList.valueOf(
                if (isDanger) {
                    AppTheme.dangerColor
                } else {
                    AppTheme.panelPressed
                }
            )
            rippleColor = ColorStateList.valueOf(
                if (isDanger) {
                    AppTheme.highlightBorder
                } else {
                    AppTheme.rippleColor
                }
            )
            cornerRadius = dp(16)

            insetTop = 0
            insetBottom = 0
            minHeight = dp(48)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { onClick() }
        }
    }

    fun row(
        left: Button,
        right: Button
    ): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }

            left.layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = dp(6)
            }
            right.layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = dp(6)
            }

            addView(left)
            addView(right)
        }
    }

    fun singleRow(button: Button): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }

            button.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(button)
        }
    }

    fun roundedBg(
        fill: Int,
        stroke: Int,
        radiusDp: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            setStroke(dp(1), stroke)
        }
    }

    fun roundedFill(
        fill: Int,
        radiusDp: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
        }
    }

    fun space(width: Int): TextView {
        return TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(width, 1)
        }
    }

    fun spacer(height: Int): TextView {
        return TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
        }
    }

    fun getStatusBarHeight(): Int {
        val resourceId = activity.resources.getIdentifier(
            "status_bar_height",
            "dimen",
            "android"
        )
        return if (resourceId > 0) {
            activity.resources.getDimensionPixelSize(resourceId)
        } else {
            dp(24)
        }
    }

    fun dp(value: Int): Int {
        return (
            value * activity.resources.displayMetrics.density
        ).toInt()
    }

    fun smallActionButton(
        label: String,
        isDanger: Boolean = false,
        onClick: () -> Unit
    ): Button {
        return MaterialButton(activity).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setTextColor(AppTheme.textPrimary)

            backgroundTintList = ColorStateList.valueOf(
                if (isDanger) {
                    AppTheme.dangerColor
                } else {
                    AppTheme.panelPressed
                }
            )
            rippleColor = ColorStateList.valueOf(
                if (isDanger) {
                    AppTheme.highlightBorder
                } else {
                    AppTheme.rippleColor
                }
            )
            cornerRadius = dp(14)

            insetTop = 0
            insetBottom = 0
            minHeight = dp(44)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { onClick() }
        }
    }

    fun flowRow(vararg views: View): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(6))
            views.forEachIndexed { index, view ->
                val params = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
                if (index > 0) {
                    params.marginStart = dp(6)
                }
                addView(view, params)
            }
        }
    }
}
