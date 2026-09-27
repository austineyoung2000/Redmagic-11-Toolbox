package com.elitedarkkaiser.redmagic.ui.components

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.ui.AppTheme

class MainBottomNavigation(
    private val activity: Activity,
    private val onTabSelected: (String) -> Unit
) {
    private val tabViews = linkedMapOf<String, LinearLayout>()

    fun createView(): LinearLayout {
        tabViews.clear()

        val tabs = listOf(
            Tab("home", "⌂", "Home"),
            Tab("cooling", "❄", "Cooling"),
            Tab("controls", "⌘", "Controls"),
            Tab("hardware", "⌥", "Hardware"),
            Tab("lighting", "✦", "Lighting")
        )

        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundedTopBar()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            tabs.forEach { tab ->
                val view = navItem(tab)
                tabViews[tab.id] = view
                addView(
                    view,
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                )
            }
        }
    }

    fun select(tabId: String) {
        tabViews.forEach { (id, view) ->
            val selected = id == tabId
            view.background = roundedFill(
                if (selected) {
                    AppTheme.panelPressed
                } else {
                    Color.TRANSPARENT
                },
                18
            )
            view.alpha = if (selected) 1f else 0.72f
        }
    }

    private fun navItem(tab: Tab): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
            isClickable = true
            isFocusable = true
            contentDescription = tab.label
            setOnClickListener {
                onTabSelected(tab.id)
            }

            addView(TextView(activity).apply {
                text = tab.icon
                textSize = 20f
                setTextColor(AppTheme.textPrimary)
                gravity = Gravity.CENTER
            })

            addView(TextView(activity).apply {
                text = tab.label
                textSize = 12f
                setTextColor(AppTheme.textPrimary)
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun roundedTopBar(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(AppTheme.panelColor)
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), AppTheme.borderColor)
        }
    }

    private fun roundedFill(
        fill: Int,
        radiusDp: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
        }
    }

    private fun dp(value: Int): Int {
        return (
            value * activity.resources.displayMetrics.density
        ).toInt()
    }

    private data class Tab(
        val id: String,
        val icon: String,
        val label: String
    )
}
