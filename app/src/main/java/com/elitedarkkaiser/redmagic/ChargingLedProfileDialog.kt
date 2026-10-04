package com.elitedarkkaiser.redmagic

import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import android.widget.LinearLayout
import android.widget.TextView

internal object ChargingLedProfileDialog {
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
        val colorDotGeneric: (String, Boolean, () -> Unit) -> View,
        val colorDotDrawable: (String, Boolean) -> Drawable,
        val fanPresetBubble: (String, String, String, String, String, () -> Boolean, () -> Unit) -> View
    )

    fun show(
        activity: MainActivity,
        title: String,
        subtitle: String,
        originalEnabled: Boolean,
        originalEffect: String,
        originalColor: Int,
        onSave: (enabled: Boolean, effect: String, color: Int) -> Unit,
        deps: Deps,
        showFanPresets: Boolean = false
    ) {
        var enabled = originalEnabled
        var effect = if (showFanPresets) {
            FanLedPalette.normalizeEffect(originalEffect)
        } else {
            originalEffect
        }
        var color = if (showFanPresets) {
            FanLedPalette.normalizeColor(originalEffect, originalColor)
        } else {
            originalColor
        }

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(deps.dp(22), deps.dp(18), deps.dp(22), deps.dp(12))
            background = deps.roundedBg(deps.panelColor, deps.borderColor, 22)
        }

        val titleView = TextView(activity).apply {
            text = title
            textSize = 20f
            setTextColor(deps.textPrimary)
            setTypeface(deps.typeface, Typeface.BOLD)
        }

        val subtitleView = TextView(activity).apply {
            text = subtitle
            textSize = 13f
            setTextColor(deps.textSecondary)
            setPadding(0, deps.dp(8), 0, deps.dp(10))
        }

        val enableCheck = MaterialCheckBox(activity).apply {
            text = "Enable for charging mode"
            isChecked = enabled
            textSize = 14f
            setTextColor(deps.textPrimary)
            buttonTintList = ColorStateList.valueOf(deps.accent)
            setOnCheckedChangeListener { _, checked -> enabled = checked }
        }

        fun chip(
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
                setTextColor(deps.textPrimary)
                isSelected = selected

                backgroundTintList = ColorStateList.valueOf(
                    if (selected) {
                        deps.panelPressed
                    } else {
                        Color.TRANSPARENT
                    }
                )
                strokeWidth = deps.dp(1)
                strokeColor = ColorStateList.valueOf(
                    if (selected) {
                        deps.accent
                    } else {
                        deps.borderColor
                    }
                )
                rippleColor =
                    ColorStateList.valueOf(com.elitedarkkaiser.redmagic.ui.AppTheme.rippleColor)
                cornerRadius = deps.dp(16)

                insetTop = 0
                insetBottom = 0
                minHeight = deps.dp(48)
                minWidth = 0
                minimumWidth = 0
                setPadding(
                    deps.dp(10),
                    deps.dp(8),
                    deps.dp(10),
                    deps.dp(8)
                )
                setOnClickListener { onClick() }
            }
        }

        lateinit var steadyBtn: Button
        lateinit var breatheBtn: Button
        lateinit var flashingBtn: Button
        lateinit var rapidBtn: Button
        lateinit var colorRow: LinearLayout
        lateinit var colorRow2: LinearLayout
        var colorRowsReady = false

        fun updateEffectButton(
            button: Button,
            selected: Boolean
        ) {
            button.isSelected = selected

            if (button is MaterialButton) {
                button.backgroundTintList = ColorStateList.valueOf(
                    if (selected) {
                        deps.panelPressed
                    } else {
                        Color.TRANSPARENT
                    }
                )
                button.strokeColor = ColorStateList.valueOf(
                    if (selected) {
                        deps.accent
                    } else {
                        deps.borderColor
                    }
                )
            } else {
                button.background = deps.roundedFill(
                    if (selected) {
                        deps.panelPressed
                    } else {
                        deps.panelColor
                    },
                    999
                )
            }
        }

        val paletteViews = mutableListOf<View>()
        fun refreshButtons() {
            paletteViews.forEach { it.invalidate() }
            updateEffectButton(
                steadyBtn,
                LedBrightness.effect(effect) == "steady"
            )
            updateEffectButton(
                breatheBtn,
                LedBrightness.effect(effect) == "breathe"
            )
            updateEffectButton(
                flashingBtn,
                LedBrightness.effect(effect) == "flashing"
            )
            updateEffectButton(
                rapidBtn,
                LedBrightness.effect(effect) == "rapid"
            )

            if (colorRowsReady) {
                colorRow.getChildAt(0).background = deps.colorDotDrawable("#FF0000", color == 1)
                colorRow.getChildAt(2).background = deps.colorDotDrawable("#FF8C00", color == 3)
                colorRow.getChildAt(4).background = deps.colorDotDrawable("#FFD600", color == 4)
                colorRow.getChildAt(6).background = deps.colorDotDrawable("#00E676", color == 5)

                colorRow2.getChildAt(0).background = deps.colorDotDrawable("#00E5FF", color == 6)
                colorRow2.getChildAt(2).background = deps.colorDotDrawable("#1565FF", color == 7)
                colorRow2.getChildAt(4).background = deps.colorDotDrawable("#A020F0", color == 8)
                colorRow2.getChildAt(6).background = deps.colorDotDrawable("#FF69B4", color == 9)
            }
        }

        val effectRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        steadyBtn = chip("Steady", LedBrightness.effect(effect) == "steady") {
            effect = LedBrightness.withEffect(effect, "steady")
            refreshButtons()
        }
        breatheBtn = chip("Breathe", LedBrightness.effect(effect) == "breathe") {
            effect = LedBrightness.withEffect(effect, "breathe")
            refreshButtons()
        }
        flashingBtn = chip("Flash", LedBrightness.effect(effect) == "flashing") {
            effect = LedBrightness.withEffect(effect, "flashing")
            refreshButtons()
        }
        rapidBtn = chip("Rapid", LedBrightness.effect(effect) == "rapid") {
            effect = LedBrightness.withEffect(effect, "rapid")
            refreshButtons()
        }

        effectRow.addView(steadyBtn, LinearLayout.LayoutParams(0, deps.dp(48), 1f))
        effectRow.addView(deps.space(deps.dp(6)))
        effectRow.addView(breatheBtn, LinearLayout.LayoutParams(0, deps.dp(48), 1f))
        effectRow.addView(deps.space(deps.dp(6)))
        effectRow.addView(flashingBtn, LinearLayout.LayoutParams(0, deps.dp(48), 1f))
        effectRow.addView(deps.space(deps.dp(6)))
        effectRow.addView(rapidBtn, LinearLayout.LayoutParams(0, deps.dp(48), 1f))

        colorRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, deps.dp(10), 0, 0)
        }

        colorRow2 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, deps.dp(10), 0, 0)
        }

        val chargingColors = listOf(
            1 to "#FF0000",
            3 to "#FF8C00",
            4 to "#FFD600",
            5 to "#00E676",
            6 to "#00E5FF",
            7 to "#1565FF",
            8 to "#A020F0",
            9 to "#FF69B4"
        )

        fun addColor(row: LinearLayout, colorId: Int, hex: String) {
            row.addView(deps.colorDotGeneric(hex, color == colorId) {
                color = colorId
                refreshButtons()
            })
            row.addView(deps.space(deps.dp(10)))
        }

        chargingColors.take(4).forEach { (colorId, hex) ->
            addColor(colorRow, colorId, hex)
        }

        chargingColors.drop(4).forEach { (colorId, hex) ->
            addColor(colorRow2, colorId, hex)
        }

        colorRowsReady = true

        fun applyFanPalette(value: String) {
            val palette = FanLedPalette.fromStockPreset(value) ?: return
            effect = FanLedPalette.normalizeEffect(effect)
            color = palette
            refreshButtons()
        }

        val presetBubbleRow1 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, deps.dp(12), 0, 0)
            addView(deps.fanPresetBubble("#FF69B4", "#FF0000", "#FF8C00", "#FF8C00", "0x3002101", { color == 0x101 }) { applyFanPalette("0x3002101") }.apply { paletteViews.add(this) })
            addView(deps.space(deps.dp(10)))
            addView(deps.fanPresetBubble("#1565FF", "#00E676", "#22D3EE", "#FF69B4", "0x3002102", { color == 0x102 }) { applyFanPalette("0x3002102") }.apply { paletteViews.add(this) })
            addView(deps.space(deps.dp(10)))
            addView(deps.fanPresetBubble("#22D3EE", "#FF0000", "#FFD600", "#FF69B4", "0x3002103", { color == 0x103 }) { applyFanPalette("0x3002103") }.apply { paletteViews.add(this) })
            addView(deps.space(deps.dp(10)))
            addView(deps.fanPresetBubble("#00E676", "#FF69B4", "#FF8C00", "#22D3EE", "0x3002104", { color == 0x104 }) { applyFanPalette("0x3002104") }.apply { paletteViews.add(this) })
        }

        val presetBubbleRow2 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, deps.dp(10), 0, 0)
            addView(deps.fanPresetBubble("#00E676", "#A020F0", "#FF8C00", "#FF69B4", "0x3002105", { color == 0x105 }) { applyFanPalette("0x3002105") }.apply { paletteViews.add(this) })
            addView(deps.space(deps.dp(10)))
            addView(deps.fanPresetBubble("#FF0000", "#FF0000", "#FF0000", "#FF0000", "0x3002106", { color == 0x106 }) { applyFanPalette("0x3002106") }.apply { paletteViews.add(this) })
            addView(deps.space(deps.dp(10)))
            addView(deps.fanPresetBubble("#22D3EE", "#FF8C00", "#22D3EE", "#A020F0", "0x3002107", { color == 0x107 }) { applyFanPalette("0x3002107") }.apply { paletteViews.add(this) })
            addView(deps.space(deps.dp(10)))
            addView(deps.fanPresetBubble("#22D3EE", "#FF0000", "#FF8C00", "#00E676", "0x3002108", { color == 0x108 }) { applyFanPalette("0x3002108") }.apply { paletteViews.add(this) })
        }

        val buttonRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, deps.dp(18), 0, 0)
        }

        val cancelBtn = MaterialButton(
            activity,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "Cancel"
            textSize = 13f
            isAllCaps = false
            setTextColor(deps.textPrimary)

            backgroundTintList =
                ColorStateList.valueOf(Color.TRANSPARENT)
            strokeWidth = deps.dp(1)
            strokeColor =
                ColorStateList.valueOf(deps.borderColor)
            rippleColor =
                ColorStateList.valueOf(com.elitedarkkaiser.redmagic.ui.AppTheme.rippleColor)
            cornerRadius = deps.dp(14)

            insetTop = 0
            insetBottom = 0
            minHeight = deps.dp(48)
            setPadding(
                deps.dp(18),
                deps.dp(10),
                deps.dp(18),
                deps.dp(10)
            )
        }

        val saveBtn = MaterialButton(activity).apply {
            text = "Save"
            textSize = 13f
            isAllCaps = false
            setTextColor(deps.textPrimary)

            backgroundTintList =
                ColorStateList.valueOf(deps.panelPressed)
            rippleColor =
                ColorStateList.valueOf(com.elitedarkkaiser.redmagic.ui.AppTheme.rippleColor)
            cornerRadius = deps.dp(14)

            insetTop = 0
            insetBottom = 0
            minHeight = deps.dp(48)
            setPadding(
                deps.dp(20),
                deps.dp(10),
                deps.dp(20),
                deps.dp(10)
            )
        }

        buttonRow.addView(cancelBtn)
        buttonRow.addView(deps.space(deps.dp(10)))
        buttonRow.addView(saveBtn)

        container.addView(titleView)
        container.addView(subtitleView)
        container.addView(enableCheck)
        container.addView(TextView(activity).apply {
            text = "Effect"
            textSize = 12f
            setTextColor(deps.textSecondary)
            setPadding(0, deps.dp(14), 0, deps.dp(6))
        })
        container.addView(effectRow)
        container.addView(TextView(activity).apply {
            text = "Color"
            textSize = 12f
            setTextColor(deps.textSecondary)
            setPadding(0, deps.dp(14), 0, 0)
        })
        container.addView(colorRow)
        container.addView(colorRow2)
        if (showFanPresets) {
            container.addView(TextView(activity).apply {
                text = "Fan presets"
                textSize = 12f
                setTextColor(deps.textSecondary)
                setPadding(0, deps.dp(14), 0, 0)
            })
            container.addView(presetBubbleRow1)
            container.addView(presetBubbleRow2)
        }
        LedBrightnessUi(activity, container, if (showFanPresets) "fan" else "logo", { effect }, { color },
            { effect = it }, {}, deps.textSecondary, deps.accent, deps.dp)
        container.addView(buttonRow)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(android.widget.ScrollView(activity).apply { addView(container) })
            .setCancelable(true)
            .create()

        cancelBtn.setOnClickListener {
            dialog.dismiss()
        }

        saveBtn.setOnClickListener {
            onSave(enabled, effect, color)
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.65f)
        }

        refreshButtons()
    }
}
