package com.elitedarkkaiser.redmagic

import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.elitedarkkaiser.redmagic.state.LedState

internal object CallLightingProfileUi {

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
        val filterChip: (String, Boolean, () -> Unit) -> Button,
        val space: (Int) -> View,
        val colorDotDrawable: (String, Boolean) -> Drawable,
        val colorDotGeneric: (String, Boolean, () -> Unit) -> View,
        val fanPresetBubble: (String, String, String, String, String, Boolean, () -> Unit) -> View
    )

    data class ZoneKeys(
        val enabledKey: String,
        val effectKey: String,
        val colorKey: String,
        val label: String,
        val defaultEffect: String,
        val defaultColor: Int
    )

    fun show(
        activity: MainActivity,
        title: String,
        subtitle: String,
        fanKeys: ZoneKeys,
        logoKeys: ZoneKeys,
        shoulderKeys: ZoneKeys,
        deps: Deps
    ) {
        var fan = CallLightingState.readLed(activity, fanKeys.enabledKey, fanKeys.effectKey, fanKeys.colorKey, true, fanKeys.defaultEffect, fanKeys.defaultColor)
        var logo = CallLightingState.readLed(activity, logoKeys.enabledKey, logoKeys.effectKey, logoKeys.colorKey, true, logoKeys.defaultEffect, logoKeys.defaultColor)
        var shoulder = CallLightingState.readLed(activity, shoulderKeys.enabledKey, shoulderKeys.effectKey, shoulderKeys.colorKey, true, shoulderKeys.defaultEffect, shoulderKeys.defaultColor)

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(deps.dp(22), deps.dp(18), deps.dp(22), deps.dp(12))
            background = deps.roundedBg(deps.panelColor, deps.borderColor, 22)
        }

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            addView(container, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        container.addView(TextView(activity).apply {
            text = title
            textSize = 20f
            setTextColor(deps.textPrimary)
            setTypeface(deps.typeface, Typeface.BOLD)
        })

        container.addView(TextView(activity).apply {
            text = subtitle
            textSize = 13f
            setTextColor(deps.textSecondary)
            setPadding(0, deps.dp(8), 0, deps.dp(12))
        })

        val modeLabel = if (title.contains("Incoming", ignoreCase = true)) "incoming calls" else "connected calls"

        container.addView(zoneEditor(activity, fanKeys.label, fan, deps, modeLabel, showFanPresets = true) { fan = it })
        container.addView(zoneEditor(activity, logoKeys.label, logo, deps, modeLabel, showFanPresets = false) { logo = it })
        container.addView(zoneEditor(activity, shoulderKeys.label, shoulder, deps, modeLabel, showFanPresets = false) { shoulder = it })

        val buttonRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, deps.dp(16), 0, 0)
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
        container.addView(buttonRow)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(scroll)
            .create()

        cancelBtn.setOnClickListener {
            dialog.dismiss()
        }

        saveBtn.setOnClickListener {
            CallLightingState.saveLed(activity, fanKeys.enabledKey, fanKeys.effectKey, fanKeys.colorKey, fan)
            CallLightingState.saveLed(activity, logoKeys.enabledKey, logoKeys.effectKey, logoKeys.colorKey, logo)
            CallLightingState.saveLed(activity, shoulderKeys.enabledKey, shoulderKeys.effectKey, shoulderKeys.colorKey, shoulder)

            if (CallLightingState.isEnabled(activity)) {
                HardwareServiceActions.startCallLighting(activity)
            }

            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun zoneEditor(
        activity: MainActivity,
        label: String,
        initial: LedState,
        deps: Deps,
        modeLabel: String,
        showFanPresets: Boolean,
        onChanged: (LedState) -> Unit
    ): LinearLayout {
        var enabled = initial.enabled
        var effect = if (showFanPresets) {
            FanLedPalette.normalizeEffect(initial.effect)
        } else {
            initial.effect
        }
        var color = if (showFanPresets) {
            FanLedPalette.normalizeColor(initial.effect, initial.color)
        } else {
            initial.color
        }

        lateinit var steadyBtn: Button
        lateinit var breatheBtn: Button
        lateinit var flashingBtn: Button
        lateinit var rapidBtn: Button
        lateinit var row1: LinearLayout
        lateinit var row2: LinearLayout
        lateinit var presetRow1: LinearLayout
        lateinit var presetRow2: LinearLayout

        fun publish() {
            onChanged(LedState(enabled, effect, color))
        }

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
                    18
                )
            }
        }

        fun refreshEffects() {
            updateEffectButton(
                steadyBtn,
                effect == "steady"
            )
            updateEffectButton(
                breatheBtn,
                effect == "breathe"
            )
            updateEffectButton(
                flashingBtn,
                effect == "flashing"
            )
            updateEffectButton(
                rapidBtn,
                effect == "rapid"
            )
        }

        fun refreshColors() {
            row1.getChildAt(0).background = deps.colorDotDrawable("#FF0000", color == 1)
            row1.getChildAt(2).background = deps.colorDotDrawable("#FF8C00", color == 3)
            row1.getChildAt(4).background = deps.colorDotDrawable("#FFD600", color == 4)
            row1.getChildAt(6).background = deps.colorDotDrawable("#00E676", color == 5)
            row2.getChildAt(0).background = deps.colorDotDrawable("#00E5FF", color == 6)
            row2.getChildAt(2).background = deps.colorDotDrawable("#1565FF", color == 7)
            row2.getChildAt(4).background = deps.colorDotDrawable("#A020F0", color == 8)
            row2.getChildAt(6).background = deps.colorDotDrawable("#FF69B4", color == 9)
        }

        fun applyFanPreset(value: String) {
            val palette = FanLedPalette.fromStockPreset(value) ?: return
            enabled = true
            effect = FanLedPalette.normalizeEffect(effect)
            color = palette
            refreshEffects()
            refreshColors()
            publish()
        }

        fun effectButton(text: String, value: String): Button {
            return deps.filterChip(text, effect == value) {
                effect = value
                refreshEffects()
                publish()
            }
        }

        fun colorDot(id: Int, hex: String): View {
            return deps.colorDotGeneric(hex, color == id) {
                color = id
                if (effect.startsWith("preset:")) effect = "steady"
                refreshEffects()
                refreshColors()
                publish()
            }
        }

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, deps.dp(10), 0, deps.dp(14))
        }

        card.addView(TextView(activity).apply {
            text = label
            textSize = 13f
            setTextColor(deps.textSecondary)
            setPadding(0, deps.dp(4), 0, deps.dp(6))
        })

        card.addView(MaterialCheckBox(activity).apply {
            text = "Enable $label for $modeLabel"
            isChecked = enabled
            textSize = 14f
            setTextColor(deps.textPrimary)
            buttonTintList = ColorStateList.valueOf(deps.accent)
            setOnCheckedChangeListener { _, checked ->
                enabled = checked
                publish()
            }
        })

        val effectRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        steadyBtn = effectButton("Steady", "steady")
        breatheBtn = effectButton("Breathe", "breathe")
        flashingBtn = effectButton("Flashing", "flashing")
        rapidBtn = effectButton("Rapid", "rapid")
        effectRow.addView(steadyBtn, LinearLayout.LayoutParams(0, deps.dp(44), 1f))
        effectRow.addView(deps.space(deps.dp(8)))
        effectRow.addView(breatheBtn, LinearLayout.LayoutParams(0, deps.dp(44), 1f))
        effectRow.addView(deps.space(deps.dp(8)))
        effectRow.addView(flashingBtn, LinearLayout.LayoutParams(0, deps.dp(44), 1f))
        effectRow.addView(deps.space(deps.dp(8)))
        effectRow.addView(rapidBtn, LinearLayout.LayoutParams(0, deps.dp(44), 1f))

        row1 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, deps.dp(10), 0, 0)
            addView(colorDot(1, "#FF0000"))
            addView(deps.space(deps.dp(10)))
            addView(colorDot(3, "#FF8C00"))
            addView(deps.space(deps.dp(10)))
            addView(colorDot(4, "#FFD600"))
            addView(deps.space(deps.dp(10)))
            addView(colorDot(5, "#00E676"))
        }

        row2 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, deps.dp(10), 0, 0)
            addView(colorDot(6, "#00E5FF"))
            addView(deps.space(deps.dp(10)))
            addView(colorDot(7, "#1565FF"))
            addView(deps.space(deps.dp(10)))
            addView(colorDot(8, "#A020F0"))
            addView(deps.space(deps.dp(10)))
            addView(colorDot(9, "#FF69B4"))
        }

        card.addView(effectRow)
        card.addView(row1)
        card.addView(row2)

        if (showFanPresets) {
            card.addView(TextView(activity).apply {
                text = "Fan LED presets"
                textSize = 13f
                setTextColor(deps.textSecondary)
                setPadding(0, deps.dp(12), 0, deps.dp(6))
            })

            presetRow1 = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(deps.fanPresetBubble("#FF69B4", "#FF0000", "#FF8C00", "#FF8C00", "0x3002101", color == 0x101) { applyFanPreset("0x3002101") })
                addView(deps.space(deps.dp(10)))
                addView(deps.fanPresetBubble("#1565FF", "#00E676", "#22D3EE", "#FF69B4", "0x3002102", color == 0x102) { applyFanPreset("0x3002102") })
                addView(deps.space(deps.dp(10)))
                addView(deps.fanPresetBubble("#22D3EE", "#FF0000", "#FFD600", "#FF69B4", "0x3002103", color == 0x103) { applyFanPreset("0x3002103") })
                addView(deps.space(deps.dp(10)))
                addView(deps.fanPresetBubble("#00E676", "#FF69B4", "#FF8C00", "#22D3EE", "0x3002104", color == 0x104) { applyFanPreset("0x3002104") })
            }

            presetRow2 = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, deps.dp(10), 0, 0)
                addView(deps.fanPresetBubble("#00E676", "#A020F0", "#FF8C00", "#FF69B4", "0x3002105", color == 0x105) { applyFanPreset("0x3002105") })
                addView(deps.space(deps.dp(10)))
                addView(deps.fanPresetBubble("#FF0000", "#FF0000", "#FF0000", "#FF0000", "0x3002106", color == 0x106) { applyFanPreset("0x3002106") })
                addView(deps.space(deps.dp(10)))
                addView(deps.fanPresetBubble("#22D3EE", "#FF8C00", "#22D3EE", "#A020F0", "0x3002107", color == 0x107) { applyFanPreset("0x3002107") })
                addView(deps.space(deps.dp(10)))
                addView(deps.fanPresetBubble("#22D3EE", "#FF0000", "#FF8C00", "#00E676", "0x3002108", color == 0x108) { applyFanPreset("0x3002108") })
            }

            card.addView(presetRow1)
            card.addView(presetRow2)
        }

        refreshEffects()
        refreshColors()
        return card
    }
}
