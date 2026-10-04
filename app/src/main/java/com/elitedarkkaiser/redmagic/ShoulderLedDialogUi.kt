package com.elitedarkkaiser.redmagic

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.InputFilter
import android.text.method.DigitsKeyListener
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder

internal object ShoulderLedDialogUi {
    data class Deps(
        val textPrimary: Int,
        val textSecondary: Int,
        val panelColor: Int,
        val borderColor: Int,
        val panelPressed: Int,
        val accent: Int,
        val typeface: Typeface?,
        val dp: (Int) -> Int,
        val roundedBg: (Int, Int, Int) -> Drawable,
        val roundedFill: (Int, Int) -> Drawable,
        val space: (Int) -> View,
        val filterChip: (String, Boolean, () -> Unit) -> Button,
        val colorDotGeneric: (String, Boolean, () -> Unit) -> View,
        val colorDotDrawable: (String, Boolean) -> Drawable
    )

    fun showShoulderLedDialog(
        activity: MainActivity,
        originalEnabled: Boolean,
        originalEffect: String,
        originalColor: Int,
        currentEnabled: () -> Boolean,
        currentEffect: () -> String,
        currentColor: () -> Int,
        setEnabled: (Boolean) -> Unit,
        setEffect: (String) -> Unit,
        setColor: (Int) -> Unit,
        applyPreviewIfEnabled: () -> Unit,
        applyEffect: (String, Int) -> Unit,
        disableLed: () -> Unit,
        saveState: () -> Unit,
        startFanLedService: () -> Unit,
        stopFanLedService: () -> Unit,
        anyLedEnabled: () -> Boolean,
        setDialogRefresh: (((() -> Unit)?) -> Unit),
        deps: Deps
    ) {
        var dialogRefresh: (() -> Unit)? = null
        var bottomRgb = ShoulderLedSplit.decode(currentEffect())?.bottomRgb
            ?: ShoulderLedSplit.presetRgb(currentColor())
        fun selection() = ShoulderLedSplit.decode(currentEffect())
        fun baseEffect() = ShoulderLedSplit.baseEffect(currentEffect())
        fun previewAndRefresh() {
            applyPreviewIfEnabled()
            dialogRefresh?.invoke()
        }
        fun changeEffect(effect: String) {
            val split = selection()
            setEffect(if (split == null) effect else ShoulderLedSplit.encode(effect, split.topRgb, split.bottomRgb))
            previewAndRefresh()
        }
        fun changeRgb(top: Boolean, rgb: Int) {
            val split = selection() ?: return
            val upper = if (top) rgb else split.topRgb
            val lower = if (top) split.bottomRgb else rgb
            bottomRgb = lower
            setEffect(ShoulderLedSplit.encode(split.effect, upper, lower))
            previewAndRefresh()
        }
        fun label(text: String) = TextView(activity).apply {
            this.text = text
            textSize = 13f
            setTextColor(deps.textSecondary)
            setPadding(0, deps.dp(12), 0, deps.dp(6))
        }
        fun showColorPicker(top: Boolean) {
            val split = selection() ?: return
            val initial = if (top) split.topRgb else split.bottomRgb
            val picker = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(deps.dp(22), deps.dp(12), deps.dp(22), deps.dp(12))
            }
            val sample = label("Color preview").apply {
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setShadowLayer(2f, 0f, 0f, Color.BLACK)
            }
            val hexInput = EditText(activity).apply {
                setSingleLine(true)
                hint = "RRGGBB"
                keyListener = DigitsKeyListener.getInstance("0123456789abcdefABCDEF")
                filters = arrayOf(InputFilter.LengthFilter(6))
                setText(ShoulderLedSplit.hex(initial))
                setTextColor(deps.textPrimary)
            }
            val channels = intArrayOf((initial shr 16) and 255, (initial shr 8) and 255, initial and 255)
            fun refreshSample() {
                val rgb = (channels[0] shl 16) or (channels[1] shl 8) or channels[2]
                hexInput.setText(ShoulderLedSplit.hex(rgb))
                sample.background = deps.roundedFill(Color.rgb(channels[0], channels[1], channels[2]), 12)
            }
            picker.addView(sample, LinearLayout.LayoutParams(-1, deps.dp(40)))
            picker.addView(label("Hex color"))
            picker.addView(hexInput)
            listOf("Red", "Green", "Blue").forEachIndexed { index, name ->
                val channelLabel = label("$name: ${channels[index]}")
                picker.addView(channelLabel)
                picker.addView(SeekBar(activity).apply {
                    max = 255
                    progress = channels[index]
                    progressTintList = ColorStateList.valueOf(deps.accent)
                    thumbTintList = ColorStateList.valueOf(deps.accent)
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                            if (!fromUser) return
                            channels[index] = value
                            channelLabel.text = "$name: $value"
                            refreshSample()
                        }
                        override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                        override fun onStopTrackingTouch(bar: SeekBar?) = Unit
                    })
                })
            }
            refreshSample()
            val pickerDialog = MaterialAlertDialogBuilder(activity)
                .setTitle(if (top) "Top trigger color" else "Bottom trigger color")
                .setView(picker)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Use color", null)
                .create()
            pickerDialog.setOnShowListener {
                pickerDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    val rgb = ShoulderLedSplit.parseRgb(hexInput.text.toString())
                    if (rgb == null) hexInput.error = "Enter six hexadecimal digits"
                    else {
                        changeRgb(top, rgb)
                        pickerDialog.dismiss()
                    }
                }
            }
            pickerDialog.show()
        }

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(deps.dp(22), deps.dp(18), deps.dp(22), deps.dp(12))
        }
        container.addView(TextView(activity).apply {
            text = "Trigger LEDs"
            textSize = 20f
            setTextColor(deps.textPrimary)
            setTypeface(deps.typeface, Typeface.BOLD)
        })
        container.addView(label("Choose matching colors or customize each trigger separately."))
        val enableCheck = MaterialCheckBox(activity).apply {
            text = "Enable trigger LEDs"
            isChecked = currentEnabled()
            setTextColor(deps.textPrimary)
            buttonTintList = ColorStateList.valueOf(deps.accent)
            setOnCheckedChangeListener { _, checked ->
                ShoulderLedActions.setPreviewEnabled(checked, setEnabled, applyEffect, disableLed, currentEffect, currentColor)
            }
        }
        container.addView(enableCheck)
        val splitCheck = MaterialCheckBox(activity).apply {
            text = "Separate top and bottom colors"
            isChecked = selection() != null
            setTextColor(deps.textPrimary)
            buttonTintList = ColorStateList.valueOf(deps.accent)
            setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    setEffect(ShoulderLedSplit.encode(baseEffect(), ShoulderLedSplit.presetRgb(currentColor()), bottomRgb))
                } else {
                    selection()?.let { bottomRgb = it.bottomRgb }
                    setEffect(baseEffect())
                }
                previewAndRefresh()
            }
        }
        container.addView(splitCheck)
        container.addView(label("Effect"))
        val effectsRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val effectButtons = listOf("steady" to "Steady", "breathe" to "Breathe", "flashing" to "Flashing", "rapid" to "Rapid")
            .map { (value, title) ->
                val button = deps.filterChip(title, baseEffect() == value) { changeEffect(value) }
                if (effectsRow.childCount > 0) effectsRow.addView(deps.space(deps.dp(6)))
                effectsRow.addView(button, LinearLayout.LayoutParams(0, deps.dp(44), 1f))
                value to button
            }
        container.addView(effectsRow)
        val singlePanel = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val splitPanel = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val dotUpdates = mutableListOf<() -> Unit>()
        fun addPalette(parent: LinearLayout, top: Boolean?, title: String) {
            parent.addView(label(title))
            ShoulderLedSplit.colors.chunked(4).forEach { palette ->
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, deps.dp(6), 0, 0)
                }
                palette.forEachIndexed { index, color ->
                    val rgb = ShoulderLedSplit.parseRgb(color.hex)!!
                    fun selected(): Boolean = when (top) {
                        true -> selection()?.topRgb == rgb
                        false -> selection()?.bottomRgb == rgb
                        null -> currentColor() == color.id
                    }
                    if (index > 0) row.addView(deps.space(deps.dp(10)))
                    val dot = deps.colorDotGeneric(color.hex, selected()) {
                        if (top == null) {
                            setColor(color.id)
                            previewAndRefresh()
                        } else changeRgb(top, rgb)
                    }.apply { contentDescription = "$title: ${color.label}" }
                    row.addView(dot)
                    dotUpdates.add { dot.background = deps.colorDotDrawable(color.hex, selected()) }
                }
                parent.addView(row)
            }
        }
        addPalette(singlePanel, null, "Color")
        addPalette(splitPanel, true, "Top trigger")
        val topCustom = deps.filterChip("Custom top color", false) { showColorPicker(true) }
        splitPanel.addView(topCustom)
        addPalette(splitPanel, false, "Bottom trigger")
        val bottomCustom = deps.filterChip("Custom bottom color", false) { showColorPicker(false) }
        splitPanel.addView(bottomCustom)
        container.addView(singlePanel)
        container.addView(splitPanel)

        var saved = false
        fun restoreOriginal() {
            ShoulderLedActions.restoreOriginalState(originalEnabled, originalEffect, originalColor,
                setEnabled, setEffect, setColor, applyEffect, disableLed)
        }
        val scroll = ScrollView(activity).apply { addView(container) }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        fun refreshUi() {
            val split = selection()
            singlePanel.visibility = if (split == null) View.VISIBLE else View.GONE
            splitPanel.visibility = if (split == null) View.GONE else View.VISIBLE
            effectButtons.forEach { (value, button) ->
                val selected = baseEffect() == value
                button.isSelected = selected
                if (button is MaterialButton) {
                    button.backgroundTintList = ColorStateList.valueOf(if (selected) deps.panelPressed else Color.TRANSPARENT)
                    button.strokeColor = ColorStateList.valueOf(if (selected) deps.accent else deps.borderColor)
                }
            }
            dotUpdates.forEach { it() }
            topCustom.text = split?.let { "Custom: #${ShoulderLedSplit.hex(it.topRgb)}" } ?: "Custom top color"
            bottomCustom.text = split?.let { "Custom: #${ShoulderLedSplit.hex(it.bottomRgb)}" } ?: "Custom bottom color"
        }
        dialogRefresh = ::refreshUi
        setDialogRefresh(dialogRefresh)
        dialog.setOnDismissListener {
            setDialogRefresh(null)
            if (!saved) restoreOriginal()
        }
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).setOnClickListener { dialog.dismiss() }
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                ShoulderLedActions.commitState(currentEnabled(), currentEffect(), currentColor(), saveState,
                    applyEffect, disableLed, startFanLedService, stopFanLedService, anyLedEnabled)
                saved = true
                dialog.dismiss()
            }
        }
        refreshUi()
        dialog.show()
    }
}
