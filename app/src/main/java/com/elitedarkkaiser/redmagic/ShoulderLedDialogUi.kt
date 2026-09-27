package com.elitedarkkaiser.redmagic

import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import android.widget.LinearLayout
import android.widget.TextView

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

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(deps.dp(22), deps.dp(18), deps.dp(22), deps.dp(12))
            background = deps.roundedBg(deps.panelColor, deps.borderColor, 22)
        }

        val titleView = TextView(activity).apply {
            text = "Shoulder LEDs"
            textSize = 20f
            setTextColor(deps.textPrimary)
            setTypeface(deps.typeface, Typeface.BOLD)
        }

        val subtitleView = TextView(activity).apply {
            text = "Customize shoulder LED strips with instant preview"
            textSize = 13f
            setTextColor(deps.textSecondary)
            setPadding(0, deps.dp(8), 0, 0)
        }

        val enableCheck = MaterialCheckBox(activity).apply {
            text = "Enable shoulder LEDs"
            isChecked = currentEnabled()
            textSize = 14f
            setTextColor(deps.textPrimary)
            buttonTintList = ColorStateList.valueOf(deps.accent)
            setPadding(0, deps.dp(14), 0, 0)
            setOnCheckedChangeListener { _, checked ->
                ShoulderLedActions.setPreviewEnabled(
                    enabled = checked,
                    onEnabledChanged = setEnabled,
                    applyEffect = applyEffect,
                    disableLed = disableLed,
                    currentEffect = currentEffect,
                    currentColor = currentColor
                )
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

        val steadyBtn = deps.filterChip("Steady", currentEffect() == "steady") {
            ShoulderLedActions.setPreviewEffect(
                effect = "steady",
                onEffectChanged = setEffect,
                applyPreviewIfEnabled = applyPreviewIfEnabled,
                refreshDialog = { dialogRefresh?.invoke() }
            )
        }

        val breatheBtn = deps.filterChip("Breathe", currentEffect() == "breathe") {
            ShoulderLedActions.setPreviewEffect(
                effect = "breathe",
                onEffectChanged = setEffect,
                applyPreviewIfEnabled = applyPreviewIfEnabled,
                refreshDialog = { dialogRefresh?.invoke() }
            )
        }

        val flashingBtn = deps.filterChip("Flashing", currentEffect() == "flashing") {
            ShoulderLedActions.setPreviewEffect(
                effect = "flashing",
                onEffectChanged = setEffect,
                applyPreviewIfEnabled = applyPreviewIfEnabled,
                refreshDialog = { dialogRefresh?.invoke() }
            )
        }

        val rapidBtn = deps.filterChip("Rapid", currentEffect() == "rapid") {
            ShoulderLedActions.setPreviewEffect(
                effect = "rapid",
                onEffectChanged = setEffect,
                applyPreviewIfEnabled = applyPreviewIfEnabled,
                refreshDialog = { dialogRefresh?.invoke() }
            )
        }

        effectsRow.addView(steadyBtn, LinearLayout.LayoutParams(0, deps.dp(44), 1f))
        effectsRow.addView(deps.space(deps.dp(8)))
        effectsRow.addView(breatheBtn, LinearLayout.LayoutParams(0, deps.dp(44), 1f))
        effectsRow.addView(deps.space(deps.dp(8)))
        effectsRow.addView(flashingBtn, LinearLayout.LayoutParams(0, deps.dp(44), 1f))
        effectsRow.addView(deps.space(deps.dp(8)))
        effectsRow.addView(rapidBtn, LinearLayout.LayoutParams(0, deps.dp(44), 1f))

        val colorLabel = TextView(activity).apply {
            text = "Color"
            textSize = 12f
            setTextColor(deps.textSecondary)
            setPadding(0, deps.dp(16), 0, deps.dp(10))
        }

        val colorRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(deps.colorDotGeneric("#FF0000", currentColor() == 1) {
                ShoulderLedActions.setPreviewColor(
                    color = 1,
                    onColorChanged = setColor,
                    applyPreviewIfEnabled = applyPreviewIfEnabled,
                    refreshDialog = { dialogRefresh?.invoke() }
                )
            })
            addView(deps.space(deps.dp(10)))
            addView(deps.colorDotGeneric("#FF8C00", currentColor() == 3) {
                ShoulderLedActions.setPreviewColor(
                    color = 3,
                    onColorChanged = setColor,
                    applyPreviewIfEnabled = applyPreviewIfEnabled,
                    refreshDialog = { dialogRefresh?.invoke() }
                )
            })
            addView(deps.space(deps.dp(10)))
            addView(deps.colorDotGeneric("#FFD600", currentColor() == 4) {
                ShoulderLedActions.setPreviewColor(
                    color = 4,
                    onColorChanged = setColor,
                    applyPreviewIfEnabled = applyPreviewIfEnabled,
                    refreshDialog = { dialogRefresh?.invoke() }
                )
            })
            addView(deps.space(deps.dp(10)))
            addView(deps.colorDotGeneric("#00E676", currentColor() == 5) {
                ShoulderLedActions.setPreviewColor(
                    color = 5,
                    onColorChanged = setColor,
                    applyPreviewIfEnabled = applyPreviewIfEnabled,
                    refreshDialog = { dialogRefresh?.invoke() }
                )
            })
        }

        val colorRow2 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, deps.dp(10), 0, 0)
            addView(deps.colorDotGeneric("#00E5FF", currentColor() == 6) {
                ShoulderLedActions.setPreviewColor(
                    color = 6,
                    onColorChanged = setColor,
                    applyPreviewIfEnabled = applyPreviewIfEnabled,
                    refreshDialog = { dialogRefresh?.invoke() }
                )
            })
            addView(deps.space(deps.dp(10)))
            addView(deps.colorDotGeneric("#1565FF", currentColor() == 7) {
                ShoulderLedActions.setPreviewColor(
                    color = 7,
                    onColorChanged = setColor,
                    applyPreviewIfEnabled = applyPreviewIfEnabled,
                    refreshDialog = { dialogRefresh?.invoke() }
                )
            })
            addView(deps.space(deps.dp(10)))
            addView(deps.colorDotGeneric("#A020F0", currentColor() == 8) {
                ShoulderLedActions.setPreviewColor(
                    color = 8,
                    onColorChanged = setColor,
                    applyPreviewIfEnabled = applyPreviewIfEnabled,
                    refreshDialog = { dialogRefresh?.invoke() }
                )
            })
            addView(deps.space(deps.dp(10)))
            addView(deps.colorDotGeneric("#FF69B4", currentColor() == 9) {
                ShoulderLedActions.setPreviewColor(
                    color = 9,
                    onColorChanged = setColor,
                    applyPreviewIfEnabled = applyPreviewIfEnabled,
                    refreshDialog = { dialogRefresh?.invoke() }
                )
            })
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
        container.addView(effectLabel)
        container.addView(effectsRow)
        container.addView(colorLabel)
        container.addView(colorRow)
        container.addView(colorRow2)
        container.addView(buttonRow)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(container)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        )

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
            updateEffectButton(
                steadyBtn,
                currentEffect() == "steady"
            )
            updateEffectButton(
                breatheBtn,
                currentEffect() == "breathe"
            )
            updateEffectButton(
                flashingBtn,
                currentEffect() == "flashing"
            )
            updateEffectButton(
                rapidBtn,
                currentEffect() == "rapid"
            )
        }

        fun updateColorDots() {
            colorRow.getChildAt(0).background = deps.colorDotDrawable("#FF0000", currentColor() == 1)
            colorRow.getChildAt(2).background = deps.colorDotDrawable("#FF8C00", currentColor() == 3)
            colorRow.getChildAt(4).background = deps.colorDotDrawable("#FFD600", currentColor() == 4)
            colorRow.getChildAt(6).background = deps.colorDotDrawable("#00E676", currentColor() == 5)

            colorRow2.getChildAt(0).background = deps.colorDotDrawable("#00E5FF", currentColor() == 6)
            colorRow2.getChildAt(2).background = deps.colorDotDrawable("#1565FF", currentColor() == 7)
            colorRow2.getChildAt(4).background = deps.colorDotDrawable("#A020F0", currentColor() == 8)
            colorRow2.getChildAt(6).background = deps.colorDotDrawable("#FF69B4", currentColor() == 9)
        }

        fun refreshUi() {
            repaint()
            updateColorDots()
        }

        dialogRefresh = { refreshUi() }
        setDialogRefresh(dialogRefresh)
        dialog.setOnDismissListener {
            setDialogRefresh(null)
        }

        cancelBtn.setOnClickListener {
            ShoulderLedActions.restoreOriginalState(
                originalEnabled = originalEnabled,
                originalEffect = originalEffect,
                originalColor = originalColor,
                setEnabled = setEnabled,
                setEffect = setEffect,
                setColor = setColor,
                applyEffect = applyEffect,
                disableLed = disableLed
            )
            dialog.dismiss()
        }

        saveBtn.setOnClickListener {
            ShoulderLedActions.commitState(
                enabled = currentEnabled(),
                effect = currentEffect(),
                color = currentColor(),
                saveState = saveState,
                applyEffect = applyEffect,
                disableLed = disableLed,
                startFanLedService = startFanLedService,
                stopFanLedService = stopFanLedService,
                anyLedEnabled = anyLedEnabled
            )
            dialog.dismiss()
        }

        dialog.setOnCancelListener {
            ShoulderLedActions.restoreOriginalState(
                originalEnabled = originalEnabled,
                originalEffect = originalEffect,
                originalColor = originalColor,
                setEnabled = setEnabled,
                setEffect = setEffect,
                setColor = setColor,
                applyEffect = applyEffect,
                disableLed = disableLed
            )
        }

        refreshUi()
        dialog.show()

        dialog.window?.apply {
            setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            )
            setDimAmount(0.65f)
        }
    }
}
