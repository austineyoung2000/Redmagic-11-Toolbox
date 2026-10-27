package com.elitedarkkaiser.redmagic.ui.components

import android.content.Context
import android.content.res.ColorStateList
import android.widget.Button
import com.elitedarkkaiser.redmagic.R
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider

/** Shared presentation only; callers retain their values and event handlers. */
object SelectionControlStyle {
    fun apply(button: Button, selected: Boolean) {
        val context = button.context
        button.isSelected = selected
        button.isAllCaps = false
        button.textSize = 11f
        button.maxLines = 1
        button.ellipsize = android.text.TextUtils.TruncateAt.END
        button.minWidth = 0
        button.minimumWidth = 0
        button.setTextColor(context.getColor(if (selected) R.color.redmagic_on_primary else R.color.redmagic_on_surface))
        if (button is MaterialButton) {
            button.backgroundTintList = ColorStateList.valueOf(context.getColor(
                if (selected) R.color.redmagic_primary else R.color.redmagic_surface_variant))
            button.strokeWidth = 0
            button.cornerRadius = AppTheme.dp(context, 16)
            button.rippleColor = ColorStateList.valueOf(context.getColor(R.color.redmagic_primary_container))
            button.insetTop = 0
            button.insetBottom = 0
            button.minHeight = AppTheme.dp(context, 48)
            button.setPadding(AppTheme.dp(context, 8), 0, AppTheme.dp(context, 8), 0)
        }
        if (button.tag == "cooling_preset") {
            button.setSingleLine(false)
            button.maxLines = 2
            button.ellipsize = null
            button.textSize = 12f
            button.minHeight = AppTheme.dp(context, 100)
            button.setPadding(AppTheme.dp(context, 3), AppTheme.dp(context, 8), AppTheme.dp(context, 3), AppTheme.dp(context, 8))
            if (button is MaterialButton) {
                button.cornerRadius = AppTheme.dp(context, 12)
                button.strokeWidth = AppTheme.dp(context, 1)
                button.strokeColor = ColorStateList.valueOf(if (selected) AppTheme.accentColor else AppTheme.borderColor)
                button.backgroundTintList = ColorStateList.valueOf(if (selected) AppTheme.dangerColor else AppTheme.panelPressed)
            }
        }
    }

    fun slider(context: Context): Slider = Slider(context).apply {
        val primary = ColorStateList.valueOf(context.getColor(R.color.redmagic_primary))
        thumbTintList = primary
        isTickVisible = false
        thumbRadius = AppTheme.dp(context, 8)
        trackHeight = AppTheme.dp(context, 4)
        trackActiveTintList = primary
        trackInactiveTintList = ColorStateList.valueOf(context.getColor(R.color.redmagic_surface_variant))
    }
}
