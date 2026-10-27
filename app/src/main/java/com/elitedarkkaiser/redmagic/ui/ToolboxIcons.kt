package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.content.res.ColorStateList
import android.widget.ImageView
import com.elitedarkkaiser.redmagic.R

/** Consistent line icons for the dashboard and section destinations. */
internal object ToolboxIcons {
    fun create(context: Context, name: String): ImageView {
        val key = name.lowercase()
        val icon = when {
            "root" in key -> R.drawable.ic_toolbox_root
            "rpm" in key -> R.drawable.ic_toolbox_rpm
            "temp" in key -> R.drawable.ic_toolbox_temp
            "fan" in key || "cooling" in key -> R.drawable.ic_toolbox_fan
            "logo" in key -> R.drawable.ic_toolbox_logo
            "bar" in key -> R.drawable.ic_toolbox_bar
            "trigger" in key -> R.drawable.ic_toolbox_shoulder
            "call" in key -> R.drawable.ic_toolbox_call
            "notification" in key -> R.drawable.ic_toolbox_bell
            "charging" in key || "charge" in key -> R.drawable.ic_toolbox_charge
            "studio" in key -> R.drawable.ic_toolbox_palette
            "light" in key -> R.drawable.ic_toolbox_light
            ("game" in key || "gaming" in key) -> R.drawable.ic_toolbox_game
            "hardware" in key -> R.drawable.ic_toolbox_hardware
            "profile" in key || "automation" in key -> R.drawable.ic_toolbox_profiles
            "haptic" in key -> R.drawable.ic_toolbox_wave
            else -> R.drawable.ic_toolbox_system
        }
        return ImageView(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(AppTheme.accentColor)
            contentDescription = null
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
    }
}
