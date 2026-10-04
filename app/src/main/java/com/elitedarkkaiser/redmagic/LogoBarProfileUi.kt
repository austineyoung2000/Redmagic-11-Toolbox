package com.elitedarkkaiser.redmagic

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.elitedarkkaiser.redmagic.state.LedState
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Each physical area has its own controls; the tested effect remains shared. */
internal object LogoBarProfileUi {
    fun create(activity: Activity, initial: LedState, onlyBar: Boolean? = null,
        onPreview: ((LedState) -> Unit)? = null, onChanged: (LedState) -> Unit): LinearLayout {
        var selection = LogoBarSelection.from(initial)
        var logoColor = initial.color.takeIf { it in LogoBarSelection.colors } ?: 1
        fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
        fun publish(preview: Boolean = true) {
            val state = selection.state(logoColor)
            onChanged(state)
            if (preview) onPreview?.invoke(state)
        }
        fun label(text: String) = TextView(activity).apply {
            this.text = text
            setTextColor(AppTheme.textSecondary)
            textSize = 13f
            setPadding(0, dp(12), 0, dp(8))
        }
        val panel = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(label("Logo and GAME MODE bar share this effect"))
        val effects = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val buttons = mutableMapOf<String, MaterialButton>()
        LogoBarSelection.effects.forEach { effect ->
            val button = MaterialButton(activity, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = when (effect) { "breathe" -> "Breathe"; "flashing" -> "Flashing"; "rapid" -> "Rapid"; else -> "Steady" }
                isAllCaps = false
                textSize = 11f
                minWidth = 0
                minimumWidth = 0
                isSelected = effect == selection.effect
                setTextColor(AppTheme.textPrimary)
                strokeColor = ColorStateList.valueOf(if (isSelected) AppTheme.accentColor else AppTheme.borderColor)
                setOnClickListener {
                    selection = selection.copy(effect = effect)
                    buttons.forEach { (key, candidate) ->
                        candidate.isSelected = key == effect
                        candidate.strokeColor = ColorStateList.valueOf(if (key == effect) AppTheme.accentColor else AppTheme.borderColor)
                    }
                    publish()
                }
            }
            buttons[effect] = button
            effects.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        panel.addView(effects)
        fun area(bar: Boolean) {
            val name = if (bar) "GAME MODE bar" else "Logo LED"
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(12))
            }
            card.addView(label(name).apply { setTypeface(null, Typeface.BOLD); textSize = 16f })
            card.addView(MaterialCheckBox(activity).apply {
                text = "Enable $name"
                isChecked = if (bar) selection.barEnabled else selection.logoEnabled
                setTextColor(AppTheme.textPrimary)
                buttonTintList = ColorStateList.valueOf(AppTheme.accentColor)
                setOnCheckedChangeListener { _, checked ->
                    selection = if (bar) selection.copy(barEnabled = checked) else selection.copy(logoEnabled = checked)
                    publish()
                }
            })
            val colors = listOf(1 to "#FF0000", 3 to "#FF8C00", 4 to "#FFD600", 5 to "#00E676",
                6 to "#00E5FF", 7 to "#1565FF", 8 to "#A020F0", 9 to "#FF69B4")
            val colorButtons = mutableMapOf<Int, MaterialButton>()
            colors.chunked(4).forEach { rowColors ->
                val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                rowColors.forEach { (id, hex) ->
                    val button = MaterialButton(activity).apply {
                        text = if (id == (if (bar) selection.barColor else logoColor)) "✓" else ""
                        contentDescription = "$name ${when(id) { 1 -> "Red"; 3 -> "Orange"; 4 -> "Yellow"; 5 -> "Green"; 6 -> "Cyan"; 7 -> "Blue"; 8 -> "Purple"; else -> "Pink" }}"
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor(hex))
                        setTextColor(Color.WHITE)
                        minWidth = 0
                        minimumWidth = 0
                        cornerRadius = dp(24)
                        setOnClickListener {
                            if (bar) selection = selection.copy(barColor = id) else logoColor = id
                            colorButtons.forEach { (key, dot) -> dot.text = if (key == id) "✓" else "" }
                            publish()
                        }
                    }
                    colorButtons[id] = button
                    row.addView(button, LinearLayout.LayoutParams(0, dp(52), 1f))
                }
                card.addView(row)
            }
            val levelLabel = label("Brightness: ${if (bar) selection.barBrightness else selection.logoBrightness} / 255")
            card.addView(levelLabel)
            card.addView(SeekBar(activity).apply {
                max = 223
                progress = (if (bar) selection.barBrightness else selection.logoBrightness) - 32
                progressTintList = ColorStateList.valueOf(AppTheme.accentColor)
                thumbTintList = ColorStateList.valueOf(AppTheme.accentColor)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        val level = progress + 32
                        selection = if (bar) selection.copy(barBrightness = level) else selection.copy(logoBrightness = level)
                        levelLabel.text = "Brightness: $level / 255"
                        publish(preview = false)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) { publish() }
                })
            })
            panel.addView(card)
        }
        if (onlyBar == null) { area(false); area(true) } else area(onlyBar)
        return panel
    }

    fun show(activity: Activity, title: String, initial: LedState, onlyBar: Boolean? = null,
        onPreview: ((LedState) -> Unit)? = null, onSave: (LedState) -> Unit,
        onCancel: (() -> Unit)? = null) {
        var staged = initial
        var saved = false
        val editor = create(activity, initial, onlyBar, onPreview = onPreview) { staged = it }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(ScrollView(activity).apply { addView(editor) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ -> saved = true; onSave(staged) }
            .create()
        dialog.setOnDismissListener { if (!saved) onCancel?.invoke() }
        dialog.show()
    }
}
