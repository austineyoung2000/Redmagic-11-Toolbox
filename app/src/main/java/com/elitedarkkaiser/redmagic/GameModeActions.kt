package com.elitedarkkaiser.redmagic

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.widget.Button
import android.widget.Toast
import com.google.android.material.button.MaterialButton

internal object GameModeActions {

    fun startServiceSilentlyIfPermitted(context: Context) {
        if (PermissionActions.hasUsageStatsPermission(context)) {
            context.startService(Intent(context, GameModeService::class.java))
        }
    }

    fun startServiceIfPermitted(context: Context) {
        if (!PermissionActions.hasUsageStatsPermission(context)) {
            Toast.makeText(
                context,
                "Grant Usage Access to enable Game Mode",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        context.startService(Intent(context, GameModeService::class.java))
    }

    fun applySavedProfileThroughService(context: Context) {
        val canContinue =
            PermissionActions.hasUsageStatsPermission(context)

        context.startService(
            Intent(context, GameModeService::class.java).apply {
                putExtra(
                    GameModeService.EXTRA_APPLY_SAVED_PROFILE,
                    true
                )
                putExtra(
                    GameModeService.EXTRA_CONTINUE_AFTER_APPLY,
                    canContinue
                )
            }
        )

        if (!canContinue) {
            Toast.makeText(
                context,
                "Grant Usage Access to enable Game Mode",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun buildProfile(
        fanEnabled: Boolean,
        fanLevel: Int,
        pumpEnabled: Boolean,
        pumpProfile: String,
        fanLedEnabled: Boolean,
        fanLedEffect: String,
        fanLedColor: Int,
        logoLedEnabled: Boolean,
        logoLedEffect: String,
        logoLedColor: Int,
        shoulderLedEnabled: Boolean,
        shoulderLedEffect: String,
        shoulderLedColor: Int
    ): GameModeProfile {
        return GameModeProfile(
            fanEnabled = fanEnabled,
            fanLevel = fanLevel,
            pumpEnabled = pumpEnabled,
            pumpProfile = pumpProfile,
            fanLedEnabled = fanLedEnabled,
            fanLedEffect = fanLedEffect,
            fanLedColor = fanLedColor,
            logoLedEnabled = logoLedEnabled,
            logoLedEffect = logoLedEffect,
            logoLedColor = logoLedColor,
            shoulderLedEnabled = shoulderLedEnabled,
            shoulderLedEffect = shoulderLedEffect,
            shoulderLedColor = shoulderLedColor
        )
    }

    fun saveProfile(
        context: Context,
        profile: GameModeProfile,
        persistProfile: (GameModeProfile) -> Unit,
        onSaved: () -> Unit = {}
    ) {
        persistProfile(profile)
        Toast.makeText(context, "Game profile saved", Toast.LENGTH_SHORT).show()
        onSaved()
    }

    private fun updateSelectableButton(
        button: Button,
        selected: Boolean,
        roundedFill: (Int, Int) -> Drawable,
        selectedColor: Int,
        unselectedColor: Int
    ) {
        val stateColor =
            if (selected) selectedColor else unselectedColor

        button.isSelected = selected

        if (button is MaterialButton) {
            button.backgroundTintList =
                ColorStateList.valueOf(stateColor)
            button.strokeColor =
                ColorStateList.valueOf(stateColor)
        } else {
            button.background = roundedFill(stateColor, 999)
        }
    }

    fun refreshPumpButtons(
        selectedProfile: String,
        slowBtn: Button,
        mediumBtn: Button,
        quickBtn: Button,
        roundedFill: (Int, Int) -> Drawable,
        selectedColor: Int,
        unselectedColor: Int
    ) {
        updateSelectableButton(
            slowBtn,
            selectedProfile == "slow",
            roundedFill,
            selectedColor,
            unselectedColor
        )
        updateSelectableButton(
            mediumBtn,
            selectedProfile == "medium",
            roundedFill,
            selectedColor,
            unselectedColor
        )
        updateSelectableButton(
            quickBtn,
            selectedProfile == "quick",
            roundedFill,
            selectedColor,
            unselectedColor
        )
    }

    fun updatePumpProfile(
        value: String,
        onProfileChanged: (String) -> Unit,
        refreshButtons: () -> Unit
    ) {
        onProfileChanged(value)
        refreshButtons()
    }

    fun refreshLedEffectButtons(
        selectedEffect: String,
        steadyBtn: Button,
        breatheBtn: Button,
        flashingBtn: Button,
        rapidBtn: Button,
        roundedFill: (Int, Int) -> Drawable,
        selectedColor: Int,
        unselectedColor: Int
    ) {
        updateSelectableButton(
            steadyBtn,
            selectedEffect == "steady",
            roundedFill,
            selectedColor,
            unselectedColor
        )
        updateSelectableButton(
            breatheBtn,
            selectedEffect == "breathe",
            roundedFill,
            selectedColor,
            unselectedColor
        )
        updateSelectableButton(
            flashingBtn,
            selectedEffect == "flashing",
            roundedFill,
            selectedColor,
            unselectedColor
        )
        updateSelectableButton(
            rapidBtn,
            selectedEffect == "rapid",
            roundedFill,
            selectedColor,
            unselectedColor
        )
    }

    fun updateLedEffect(
        value: String,
        onEffectChanged: (String) -> Unit,
        refreshButtons: () -> Unit
    ) {
        onEffectChanged(value)
        refreshButtons()
    }

    fun updateLedColor(
        id: Int,
        currentEffect: String,
        onColorChanged: (Int) -> Unit,
        onEffectChanged: (String) -> Unit,
        refreshColorDots: () -> Unit,
        refreshEffectButtons: () -> Unit
    ) {
        onColorChanged(id)
        if (currentEffect.startsWith("preset:")) {
            onEffectChanged("steady")
        }
        refreshColorDots()
        refreshEffectButtons()
    }

    fun applyLedPreset(
        value: String,
        currentEffect: String,
        onEffectChanged: (String) -> Unit,
        onColorChanged: (Int) -> Unit,
        refreshEffectButtons: () -> Unit,
        refreshColorDots: () -> Unit,
        refreshPresetBubbles: () -> Unit
    ) {
        val palette = FanLedPalette.fromStockPreset(value) ?: return
        onEffectChanged(FanLedPalette.normalizeEffect(currentEffect))
        onColorChanged(palette)
        refreshEffectButtons()
        refreshColorDots()
        refreshPresetBubbles()
    }

    fun updateLogoLedEffect(
        value: String,
        onEffectChanged: (String) -> Unit
    ) {
        onEffectChanged(value)
    }

    fun updateLogoLedColor(
        id: Int,
        onColorChanged: (Int) -> Unit,
        refreshColorDots: () -> Unit
    ) {
        onColorChanged(id)
        refreshColorDots()
    }

    fun refreshShoulderEffectButtons(
        selectedEffect: String,
        steadyBtn: Button,
        breatheBtn: Button,
        flashingBtn: Button,
        rapidBtn: Button,
        roundedFill: (Int, Int) -> Drawable,
        selectedColor: Int,
        unselectedColor: Int
    ) {
        updateSelectableButton(
            steadyBtn,
            selectedEffect == "steady",
            roundedFill,
            selectedColor,
            unselectedColor
        )
        updateSelectableButton(
            breatheBtn,
            selectedEffect == "breathe",
            roundedFill,
            selectedColor,
            unselectedColor
        )
        updateSelectableButton(
            flashingBtn,
            selectedEffect == "flashing",
            roundedFill,
            selectedColor,
            unselectedColor
        )
        updateSelectableButton(
            rapidBtn,
            selectedEffect == "rapid",
            roundedFill,
            selectedColor,
            unselectedColor
        )
    }

    fun updateShoulderLedEffect(
        value: String,
        onEffectChanged: (String) -> Unit,
        refreshButtons: () -> Unit
    ) {
        onEffectChanged(value)
        refreshButtons()
    }

    fun updateShoulderLedColor(
        id: Int,
        onColorChanged: (Int) -> Unit,
        refreshColorDots: () -> Unit
    ) {
        onColorChanged(id)
        refreshColorDots()
    }
    fun applyProfileNow(
        profile: GameModeProfile,
        applyFanLed: (String, Int) -> Unit
    ) {
        if (profile.fanEnabled) {
            HardwareController.setFanLevel(profile.fanLevel)
        } else {
            HardwareController.enableFan(false)
        }

        if (profile.pumpEnabled) {
            HardwareController.setPumpProfile(profile.pumpProfile)
        } else {
            HardwareController.enablePump(false)
        }

        if (profile.fanLedEnabled) {
            applyFanLed(profile.fanLedEffect, profile.fanLedColor)
        } else {
            HardwareController.setFanLedEnabled(false)
        }

        if (profile.logoLedEnabled) {
            HardwareController.setLogoLedEffect(profile.logoLedEffect, profile.logoLedColor)
        } else {
            HardwareController.setLogoLedEnabled(false)
        }

        if (profile.shoulderLedEnabled) {
            HardwareController.setShoulderLedEffect(profile.shoulderLedEffect, profile.shoulderLedColor)
        } else {
            HardwareController.setShoulderLedEnabled(false)
        }
    }

}
