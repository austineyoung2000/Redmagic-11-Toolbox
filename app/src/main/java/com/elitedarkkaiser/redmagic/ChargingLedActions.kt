package com.elitedarkkaiser.redmagic

import android.content.Context
import android.content.Intent

object ChargingLedActions {
    fun saveProfileAndApplyIfCharging(
        context: Context,
        enabledKey: String,
        effectKey: String,
        colorKey: String,
        enabled: Boolean,
        effect: String,
        color: Int
    ) {
        ChargingLedState.saveProfile(
            context,
            enabledKey,
            effectKey,
            colorKey,
            enabled,
            effect,
            color
        )

        HardwareServiceActions.startChargingMode(context)
        if (ChargingLedState.isEnabled(context) && ChargingLedState.isChargingNow(context)) {
            ChargingLedState.setActive(context, true)
            ChargingLedState.applyChargingProfile(context)
        }
    }
    internal fun showLogoDialog(
        activity: MainActivity,
        runBackground: (() -> Unit) -> Boolean,
        deps: ChargingLedProfileDialog.Deps,
        bar: Boolean = false
    ) {
        val profile = ChargingLedState.readProfile(
            activity,
            ChargingLedState.LOGO_ENABLED_KEY,
            ChargingLedState.LOGO_EFFECT_KEY,
            ChargingLedState.LOGO_COLOR_KEY,
            defaultEnabled = true,
            defaultEffect = "steady",
            defaultColor = 1
        )

        LogoBarProfileUi.show(activity,
            if (bar) "Charging GAME MODE bar" else "Charging Logo LED",
            com.elitedarkkaiser.redmagic.state.LedState(profile.enabled, profile.effect, profile.color),
            onlyBar = bar,
            onSave = { selection ->
                runBackground {
                    saveProfileAndApplyIfCharging(activity, ChargingLedState.LOGO_ENABLED_KEY,
                        ChargingLedState.LOGO_EFFECT_KEY, ChargingLedState.LOGO_COLOR_KEY,
                        selection.enabled, selection.effect, selection.color)
                }
            })
    }

    internal fun showShoulderDialog(
        activity: MainActivity,
        runBackground: (() -> Unit) -> Boolean,
        deps: ChargingLedProfileDialog.Deps
    ) {
        val profile = ChargingLedState.readProfile(
            activity,
            ChargingLedState.SHOULDER_ENABLED_KEY,
            ChargingLedState.SHOULDER_EFFECT_KEY,
            ChargingLedState.SHOULDER_COLOR_KEY,
            defaultEnabled = true,
            defaultEffect = "breathe",
            defaultColor = 8
        )

        TriggerLedProfileUi.show(
            activity = activity,
            title = "Charging Trigger LEDs",
            subtitle = "Shoulder LED profile used only while plugged in and charging.",
            initial = com.elitedarkkaiser.redmagic.state.LedState(profile.enabled, profile.effect, profile.color),
            onSave = { state ->
                runBackground {
                    saveProfileAndApplyIfCharging(
                        activity,
                        ChargingLedState.SHOULDER_ENABLED_KEY,
                        ChargingLedState.SHOULDER_EFFECT_KEY,
                        ChargingLedState.SHOULDER_COLOR_KEY,
                        state.enabled,
                        state.effect,
                        state.color
                    )
                }
            },
            deps = TriggerLedProfileUi.Deps(deps.textPrimary, deps.textSecondary, deps.accent,
                deps.panelPressed, deps.borderColor, deps.dp, deps.colorDotGeneric, deps.colorDotDrawable)
        )
    }

    internal fun showFanDialog(
        activity: MainActivity,
        runBackground: (() -> Unit) -> Boolean,
        textPrimary: Int,
        textSecondary: Int,
        panelColor: Int,
        borderColor: Int,
        panelPressed: Int,
        accent: Int,
        typeface: android.graphics.Typeface?,
        dp: (Int) -> Int,
        roundedBg: (Int, Int, Int) -> android.graphics.drawable.Drawable,
        roundedFill: (Int, Int) -> android.graphics.drawable.Drawable,
        space: (Int) -> android.view.View,
        filterChip: (String, Boolean, () -> Unit) -> android.widget.Button,
        colorDot: (Int, String, () -> Unit) -> android.view.View,
        colorDotDrawable: (String, Boolean) -> android.graphics.drawable.Drawable,
        fanPresetBubble: (
            String,
            String,
            String,
            String,
            String,
            () -> Boolean,
            () -> Unit
        ) -> android.view.View
    ) {
        val chargingFanProfile = ChargingLedState.readProfile(
            activity,
            ChargingLedState.FAN_ENABLED_KEY,
            ChargingLedState.FAN_EFFECT_KEY,
            ChargingLedState.FAN_COLOR_KEY,
            defaultEnabled = true,
            defaultEffect = "steady",
            defaultColor = 5
        )
        var chargingFanEnabled = chargingFanProfile.enabled
        var chargingFanEffect = chargingFanProfile.effect
        var chargingFanColor = chargingFanProfile.color
        var chargingFanDialogRefresh: (() -> Unit)? = null

        FanLedDialogUi.showFanLedDialog(
            activity = activity,
            originalEnabled = chargingFanEnabled,
            originalEffect = chargingFanEffect,
            originalColor = chargingFanColor,
            currentEnabled = { chargingFanEnabled },
            currentEffect = { chargingFanEffect },
            currentColor = { chargingFanColor },
            setEnabled = { value -> chargingFanEnabled = value },
            setEffect = { value -> chargingFanEffect = value },
            setColor = { value -> chargingFanColor = value },
            applyPreviewIfEnabled = {
                val enabled = chargingFanEnabled
                val effect = chargingFanEffect
                val color = chargingFanColor

                runBackground {
                    if (
                        ChargingLedState.isEnabled(activity) &&
                        ChargingLedState.isChargingNow(activity)
                    ) {
                        if (enabled) HardwareController.setFanLedEffect(effect, color)
                        else HardwareController.setFanLedEnabled(false)
                    }
                }
            },
            applySelection = { effect, color ->
                runBackground {
                    if (LedOwnership.current(activity) == LedOwner.CHARGING) {
                        HardwareController.setFanLedEffect(effect, color)
                    }
                }
            },
            disableLed = {
                runBackground {
                    if (LedOwnership.current(activity) == LedOwner.CHARGING) HardwareController.setFanLedEnabled(false)
                }
            },
            saveState = {
                val enabled = chargingFanEnabled
                val effect = chargingFanEffect
                val color = chargingFanColor

                runBackground {
                    saveProfileAndApplyIfCharging(
                        activity,
                        ChargingLedState.FAN_ENABLED_KEY,
                        ChargingLedState.FAN_EFFECT_KEY,
                        ChargingLedState.FAN_COLOR_KEY,
                        enabled,
                        effect,
                        color
                    )
                }
            },
            startFanLedService = {
                runBackground {
                    HardwareServiceActions.startChargingMode(activity)
                }
            },
            stopFanLedService = {
                runBackground {
                    HardwareServiceActions.startChargingMode(activity)
                }
            },
            anyLedEnabled = { ChargingLedState.isEnabled(activity) },
            applyFanPreset = { value ->
                val palette =
                    FanLedPalette.fromStockPreset(value)
                if (palette != null) {
                    chargingFanEnabled = true
                    chargingFanEffect = FanLedPalette.normalizeEffect(
                        chargingFanEffect
                    )
                    chargingFanColor = palette

                    runBackground {
                        if (LedOwnership.current(activity) == LedOwner.CHARGING) {
                            HardwareController.setFanLedEffect(chargingFanEffect, chargingFanColor)
                        }
                    }
                    chargingFanDialogRefresh?.invoke()
                }
            },
            setDialogRefresh = { callback -> chargingFanDialogRefresh = callback },
            deps = FanLedDialogUi.Deps(
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                panelColor = panelColor,
                borderColor = borderColor,
                panelPressed = panelPressed,
                accent = accent,
                typeface = typeface,
                dp = dp,
                roundedBg = roundedBg,
                roundedFill = roundedFill,
                space = space,
                filterChip = filterChip,
                colorDot = colorDot,
                colorDotDrawable = colorDotDrawable,
                fanPresetBubble = {
                        c1, c2, c3, c4, presetValue, selected, onClick ->
                    fanPresetBubble(
                        c1,
                        c2,
                        c3,
                        c4,
                        presetValue,
                        selected,
                        onClick
                    )
                }
            ),
            title = "Charging Fan LED",
            subtitle = "Fan LED profile used only while plugged in and charging.",
            enableLabel = "Enable for charging mode",
            brightnessControls = true
        )
    }

}
