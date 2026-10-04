package com.elitedarkkaiser.redmagic

import android.app.Activity
import android.content.res.ColorStateList
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/** Stage every slider value, but send hardware previews only when the drag ends. */
internal class LedBrightnessUi(activity: Activity, parent: LinearLayout, private val zone: String,
    private val getEffect: () -> String, private val getColor: () -> Int,
    private val setEffect: (String) -> Unit, private val preview: () -> Unit,
    textColor: Int, accent: Int, dp: (Int) -> Int) {
    private val label = TextView(activity).apply {
        setTextColor(textColor); textSize = 13f
        setPadding(0, dp(14), 0, dp(4))
    }
    private val slider = SeekBar(activity).apply {
        max = LedBrightness.MAX - LedBrightness.MIN
        progressTintList = ColorStateList.valueOf(accent)
        thumbTintList = ColorStateList.valueOf(accent)
        contentDescription = "LED brightness, 32 to 255"
    }
    private val note = TextView(activity).apply { setTextColor(textColor); textSize = 12f }
    init {
        parent.addView(label)
        parent.addView(slider, LinearLayout.LayoutParams(-1, dp(48)))
        parent.addView(note)
        refresh()
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val level = progress + LedBrightness.MIN
                    setEffect(LedBrightness.encode(level, getEffect()))
                    showLevel(level)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) { preview() }
        })
    }
    private fun showLevel(level: Int) {
        label.text = "Brightness: $level / 255 (${(level * 100 + 127) / 255}%)"
    }
    fun refresh() {
        val supported = LedBrightness.supported(zone, getEffect(), getColor())
        slider.isEnabled = supported
        slider.progress = LedBrightness.level(getEffect()) - LedBrightness.MIN
        showLevel(LedBrightness.level(getEffect()))
        note.text = if (supported) "32 is the lowest tested level. Off uses the enable switch."
            else "Dimming is not available for this effect in the test build. Uses full brightness."
    }
}
