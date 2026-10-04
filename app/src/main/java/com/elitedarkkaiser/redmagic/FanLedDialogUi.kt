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
import android.widget.ScrollView

internal object FanLedDialogUi {
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
        val colorDot: (Int, String, () -> Unit) -> View,
        val colorDotDrawable: (String, Boolean) -> Drawable,
        val fanPresetBubble: (
            String,
            String,
            String,
            String,
            String,
            () -> Boolean,
            () -> Unit
        ) -> View
    )

    fun showFanLedDialog(
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
        applySelection: (String, Int) -> Unit,
        disableLed: () -> Unit,
        saveState: () -> Unit,
        startFanLedService: () -> Unit,
        stopFanLedService: () -> Unit,
        anyLedEnabled: () -> Boolean,
        applyFanPreset: (String) -> Unit,
        setDialogRefresh: (((() -> Unit)?) -> Unit),
        deps: Deps,
        title: String = "Fan LED",
        subtitle: String = "Confirmed working options for fan LED",
        enableLabel: String = "Enable fan light",
        brightnessControls: Boolean = false
    ) {
        var dialogRefresh: (() -> Unit)? = null
        fun visibleEffect() = LedBrightness.effect(currentEffect())
        fun changeEffect(value: String) {
            val selected = LedBrightness.withEffect(currentEffect(), value)
            setEffect(if (brightnessControls && !LedBrightness.supported("fan", selected, currentColor()))
                LedBrightness.encode(255, value) else selected)
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
            setPadding(0, deps.dp(8), 0, 0)
        }

        val enableCheck = MaterialCheckBox(activity).apply {
            text = enableLabel
            isChecked = currentEnabled()
            textSize = 14f
            setTextColor(deps.textPrimary)
            buttonTintList = ColorStateList.valueOf(deps.accent)
            setPadding(0, deps.dp(14), 0, 0)
            setOnCheckedChangeListener { _, checked ->
                setEnabled(checked)
                if (checked) {
                    applySelection(currentEffect(), currentColor())
                } else {
                    disableLed()
                }
            }
        }

        val effectLabel = TextView(activity).apply {
            text = "Effect"
            textSize = 12f
            setTextColor(deps.textSecondary)
            setPadding(0, deps.dp(16), 0, deps.dp(8))
        }

        val effectsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val steadyBtn = deps.filterChip("Steady", visibleEffect() == "steady") {
            changeEffect("steady")
            applyPreviewIfEnabled()
            dialogRefresh?.invoke()
        }

        val breatheBtn = deps.filterChip("Breathe", visibleEffect() == "breathe") {
            changeEffect("breathe")
            applyPreviewIfEnabled()
            dialogRefresh?.invoke()
        }

        val flashingBtn = deps.filterChip("Flashing", visibleEffect() == "flashing") {
            changeEffect("flashing")
            applyPreviewIfEnabled()
            dialogRefresh?.invoke()
        }

        val rapidBtn = deps.filterChip("Rapid", visibleEffect() == "rapid") {
            changeEffect("rapid")
            applyPreviewIfEnabled()
            dialogRefresh?.invoke()
        }

        effectsRow.addView(steadyBtn, LinearLayout.LayoutParams(0, deps.dp(44), 1f))
        effectsRow.addView(deps.space(deps.dp(8)))
        effectsRow.addView(breatheBtn, LinearLayout.LayoutParams(0, deps.dp(44), 1f))
        effectsRow.addView(deps.space(deps.dp(8)))
        effectsRow.addView(flashingBtn, LinearLayout.LayoutParams(0, deps.dp(44), 1f))
        effectsRow.addView(deps.space(deps.dp(8)))
        effectsRow.addView(rapidBtn, LinearLayout.LayoutParams(0, deps.dp(44), 1f))

        val blinkBtn = deps.filterChip("Blink", visibleEffect() == "blink") {
            changeEffect("blink")
            applyPreviewIfEnabled()
            dialogRefresh?.invoke()
        }
        val blinkRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(blinkBtn, LinearLayout.LayoutParams(0, deps.dp(44), 1f))
        }

        val colorLabel = TextView(activity).apply {
            text = "Color"
            textSize = 12f
            setTextColor(deps.textSecondary)
            setPadding(0, deps.dp(16), 0, deps.dp(10))
        }

        val colorRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val colorRow2 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, deps.dp(10), 0, 0)
        }

        fun addColor(row: LinearLayout, colorId: Int, hex: String) {
            row.addView(deps.colorDot(colorId, hex) {
                setColor(colorId)
                if (visibleEffect().startsWith("preset:")) changeEffect("steady")
                applyPreviewIfEnabled()
                dialogRefresh?.invoke()
            })
        }

        addColor(colorRow, 1, "#FF0000")
        colorRow.addView(deps.space(deps.dp(10)))
        addColor(colorRow, 3, "#FF8C00")
        colorRow.addView(deps.space(deps.dp(10)))
        addColor(colorRow, 4, "#FFD600")
        colorRow.addView(deps.space(deps.dp(10)))
        addColor(colorRow, 5, "#00E676")

        addColor(colorRow2, 6, "#00E5FF")
        colorRow2.addView(deps.space(deps.dp(10)))
        addColor(colorRow2, 7, "#1565FF")
        colorRow2.addView(deps.space(deps.dp(10)))
        addColor(colorRow2, 8, "#A020F0")
        colorRow2.addView(deps.space(deps.dp(10)))
        addColor(colorRow2, 9, "#FF69B4")

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
        container.addView(effectLabel)
        container.addView(effectsRow)
        if (brightnessControls) container.addView(blinkRow)

        fun presetBubble(
            c1: String,
            c2: String,
            c3: String,
            c4: String,
            value: String
        ): View {
            return deps.fanPresetBubble(
                c1,
                c2,
                c3,
                c4,
                value,
                {
                    FanLedPalette.isSelected(
                        currentEffect(),
                        currentColor(),
                        value
                    )
                },
                { applyFanPreset(value) }
            )
        }

        val presetBubbleRow1 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, deps.dp(10), 0, 0)
            addView(presetBubble("#FF69B4", "#FF0000", "#FF8C00", "#FF8C00", "0x3002101"))
            addView(deps.space(deps.dp(10)))
            addView(presetBubble("#1565FF", "#00E676", "#22D3EE", "#FF69B4", "0x3002102"))
            addView(deps.space(deps.dp(10)))
            addView(presetBubble("#22D3EE", "#FF0000", "#FFD600", "#FF69B4", "0x3002103"))
            addView(deps.space(deps.dp(10)))
            addView(presetBubble("#00E676", "#FF69B4", "#FF8C00", "#22D3EE", "0x3002104"))
        }

        val presetBubbleRow2 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, deps.dp(10), 0, 0)
            addView(presetBubble("#00E676", "#A020F0", "#FF8C00", "#FF69B4", "0x3002105"))
            addView(deps.space(deps.dp(10)))
            addView(presetBubble("#FF0000", "#FF0000", "#FF0000", "#FF0000", "0x3002106"))
            addView(deps.space(deps.dp(10)))
            addView(presetBubble("#22D3EE", "#FF8C00", "#22D3EE", "#A020F0", "0x3002107"))
            addView(deps.space(deps.dp(10)))
            addView(presetBubble("#22D3EE", "#FF0000", "#FF8C00", "#00E676", "0x3002108"))
        }

        container.addView(colorLabel)
        container.addView(colorRow)
        container.addView(colorRow2)
        container.addView(presetBubbleRow1)
        container.addView(presetBubbleRow2)
        val brightness = if (brightnessControls) LedBrightnessUi(activity, container, "fan",
            currentEffect, currentColor, setEffect, applyPreviewIfEnabled,
            deps.textSecondary, deps.accent, deps.dp) else null
        container.addView(buttonRow)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(ScrollView(activity).apply { addView(container) })
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        var saved = false
        fun restoreOriginal() {
            setEnabled(originalEnabled)
            setEffect(originalEffect)
            setColor(originalColor)

            if (currentEnabled()) {
                applySelection(currentEffect(), currentColor())
            } else {
                disableLed()
            }
        }

        cancelBtn.setOnClickListener {
            dialog.dismiss()
        }

        saveBtn.setOnClickListener {
            saved = true
            saveState()
            if (currentEnabled()) {
                applySelection(currentEffect(), currentColor())
            } else {
                disableLed()
            }
            if (anyLedEnabled()) {
                startFanLedService()
            } else {
                stopFanLedService()
            }
            dialog.dismiss()
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
                    999
                )
            }
        }

        fun repaint() {
            updateEffectButton(blinkBtn, visibleEffect() == "blink")
            updateEffectButton(
                steadyBtn,
                visibleEffect() == "steady"
            )
            updateEffectButton(
                breatheBtn,
                visibleEffect() == "breathe"
            )
            updateEffectButton(
                flashingBtn,
                visibleEffect() == "flashing"
            )
            updateEffectButton(
                rapidBtn,
                visibleEffect() == "rapid"
            )
        }

        fun updateColorDots() {
            val presetActive = FanLedPalette.isPalette(
                FanLedPalette.normalizeColor(
                    currentEffect(),
                    currentColor()
                )
            )

            (colorRow.getChildAt(0) as View).background = deps.colorDotDrawable("#FF0000", !presetActive && currentColor() == 1)
            (colorRow.getChildAt(2) as View).background = deps.colorDotDrawable("#FF8C00", !presetActive && currentColor() == 3)
            (colorRow.getChildAt(4) as View).background = deps.colorDotDrawable("#FFD600", !presetActive && currentColor() == 4)
            (colorRow.getChildAt(6) as View).background = deps.colorDotDrawable("#00E676", !presetActive && currentColor() == 5)

            (colorRow2.getChildAt(0) as View).background = deps.colorDotDrawable("#00E5FF", !presetActive && currentColor() == 6)
            (colorRow2.getChildAt(2) as View).background = deps.colorDotDrawable("#1565FF", !presetActive && currentColor() == 7)
            (colorRow2.getChildAt(4) as View).background = deps.colorDotDrawable("#A020F0", !presetActive && currentColor() == 8)
            (colorRow2.getChildAt(6) as View).background = deps.colorDotDrawable("#FF69B4", !presetActive && currentColor() == 9)
        }

        fun refreshUi() {
            brightness?.refresh()
            repaint()
            updateColorDots()
            presetBubbleRow1.invalidate()
            presetBubbleRow2.invalidate()
            for (i in 0 until presetBubbleRow1.childCount) {
                presetBubbleRow1.getChildAt(i).invalidate()
            }
            for (i in 0 until presetBubbleRow2.childCount) {
                presetBubbleRow2.getChildAt(i).invalidate()
            }
        }

        dialogRefresh = { refreshUi() }
        setDialogRefresh(dialogRefresh)
        dialog.setOnDismissListener {
            if (!saved) restoreOriginal()
            setDialogRefresh(null)
        }

        refreshUi()
        dialog.show()

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.65f)
        }
    }
}
