package com.elitedarkkaiser.redmagic

import android.graphics.Typeface
import android.view.View
import com.elitedarkkaiser.redmagic.state.LedState
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.elitedarkkaiser.redmagic.ui.components.LedControlViewFactory
import com.elitedarkkaiser.redmagic.ui.components.MainActivityUiKit

internal data class LightingZoneState(
    var fanEnabled: Boolean = true,
    var fanEffect: String = "steady",
    var fanColor: Int = 1,
    var logoEnabled: Boolean = true,
    var logoEffect: String = "steady",
    var logoColor: Int = 1,
    var shoulderEnabled: Boolean = true,
    var shoulderEffect: String = "breathe",
    var shoulderColor: Int = 8
)

/** Owns normal fan, logo, and shoulder LED dialog behavior. */
internal class MainLightingZoneDialogs(
    private val activity: MainActivity,
    private val uiKit: MainActivityUiKit,
    private val ledViews: LedControlViewFactory,
    private val runBackground: (() -> Unit) -> Boolean,
    private val state: LightingZoneState,
    private val previewEnabled: () -> Boolean
) {
    private fun runLedPreview(block: () -> Unit): Boolean = runBackground {
        ModeTransitionCoordinator.applyLedProfile(activity, LedOwner.NORMAL,
            "normal-editor-preview", force = true, block = block)
    }

    private var refreshFan: (() -> Unit)? = null
    private var refreshLogo: (() -> Unit)? = null
    private var refreshShoulder: (() -> Unit)? = null

    fun refreshOpenDialogs() {
        refreshFan?.invoke()
        refreshLogo?.invoke()
        refreshShoulder?.invoke()
    }

    fun showShoulder() {
        ShoulderLedDialogUi.showShoulderLedDialog(
            activity = activity,
            originalEnabled = state.shoulderEnabled,
            originalEffect = state.shoulderEffect,
            originalColor = state.shoulderColor,
            currentEnabled = { state.shoulderEnabled },
            currentEffect = { state.shoulderEffect },
            currentColor = { state.shoulderColor },
            setEnabled = { state.shoulderEnabled = it },
            setEffect = { state.shoulderEffect = it },
            setColor = { state.shoulderColor = it },
            applyPreviewIfEnabled = {
                disableRgbStudioForManualControl()
                applyShoulderPreviewIfEnabled()
            },
            applyEffect = { effect, color ->
                disableRgbStudioForManualControl()
                applyShoulderSelection(effect, color)
            },
            disableLed = {
                disableRgbStudioForManualControl()
                runLedPreview {
                    HardwareController.setShoulderLedEnabled(false)
                }
            },
            saveState = {
                saveShoulderLedStateStorage(
                    activity,
                    LedState(
                        state.shoulderEnabled,
                        state.shoulderEffect,
                        state.shoulderColor
                    )
                )
            },
            startFanLedService = ::startFanLedService,
            stopFanLedService = ::stopFanLedService,
            anyLedEnabled = ::anyLedEnabled,
            setDialogRefresh = { refreshShoulder = it },
            deps = ShoulderLedDialogUi.Deps(
                textPrimary = AppTheme.textPrimary,
                textSecondary = AppTheme.textSecondary,
                panelColor = AppTheme.panelColor,
                borderColor = AppTheme.borderColor,
                panelPressed = AppTheme.panelPressed,
                accent = AppTheme.accentColor,
                typeface = Typeface.SANS_SERIF,
                dp = { value -> uiKit.dp(value) },
                roundedBg = { fill, stroke, radius ->
                    uiKit.roundedBg(fill, stroke, radius)
                },
                roundedFill = { color, radius ->
                    uiKit.roundedFill(color, radius)
                },
                space = { value -> uiKit.space(value) },
                filterChip = { label, selected, onClick ->
                    ledViews.filterChip(label, selected, onClick)
                },
                colorDotGeneric = { hex, selected, onClick ->
                    ledViews.colorDot(hex, selected, onClick)
                },
                colorDotDrawable = { hex, selected ->
                    ledViews.colorDotDrawable(hex, selected)
                }
            )
        )
    }

    fun showLogo(bar: Boolean = false) {
        val original = LedState(state.logoEnabled, state.logoEffect, state.logoColor)
        fun apply(selection: LedState) {
            if (selection.enabled) applyLogoSelection(selection.effect, selection.color)
            else runLedPreview { HardwareController.setLogoLedEnabled(false) }
        }
        fun update(selection: LedState) {
            state.logoEnabled = selection.enabled
            state.logoEffect = selection.effect
            state.logoColor = selection.color
        }
        LogoBarProfileUi.show(activity, if (bar) "GAME MODE bar" else "Logo LED",
            original, onlyBar = bar,
            onPreview = { selection ->
                update(selection)
                if (previewEnabled()) {
                    disableRgbStudioForManualControl()
                    apply(selection)
                }
            },
            onSave = { selection ->
                disableRgbStudioForManualControl()
                update(selection)
                saveLogoLedStateStorage(activity, selection)
                apply(selection)
                if (anyLedEnabled()) startFanLedService() else stopFanLedService()
            },
            onCancel = {
                update(original)
                if (previewEnabled()) apply(original)
            })
    }

    fun showFan() {
        FanLedDialogUi.showFanLedDialog(
            brightnessControls = true,
            activity = activity,
            originalEnabled = state.fanEnabled,
            originalEffect = state.fanEffect,
            originalColor = state.fanColor,
            currentEnabled = { state.fanEnabled },
            currentEffect = { state.fanEffect },
            currentColor = { state.fanColor },
            setEnabled = { state.fanEnabled = it },
            setEffect = { state.fanEffect = it },
            setColor = { state.fanColor = it },
            applyPreviewIfEnabled = {
                disableRgbStudioForManualControl()
                applyFanPreviewIfEnabled()
            },
            applySelection = { effect, color ->
                disableRgbStudioForManualControl()
                applyFanSelection(effect, color)
            },
            disableLed = {
                disableRgbStudioForManualControl()
                runLedPreview {
                    HardwareController.setFanLedEnabled(false)
                }
            },
            saveState = {
                saveFanLedStateStorage(
                    activity,
                    LedState(
                        state.fanEnabled,
                        state.fanEffect,
                        state.fanColor
                    )
                )
            },
            startFanLedService = ::startFanLedService,
            stopFanLedService = ::stopFanLedService,
            anyLedEnabled = ::anyLedEnabled,
            applyFanPreset = ::applyFanPreset,
            setDialogRefresh = { refreshFan = it },
            deps = FanLedDialogUi.Deps(
                textPrimary = AppTheme.textPrimary,
                textSecondary = AppTheme.textSecondary,
                panelColor = AppTheme.panelColor,
                borderColor = AppTheme.borderColor,
                panelPressed = AppTheme.panelPressed,
                accent = AppTheme.accentColor,
                typeface = Typeface.SANS_SERIF,
                dp = { value -> uiKit.dp(value) },
                roundedBg = { fill, stroke, radius ->
                    uiKit.roundedBg(fill, stroke, radius)
                },
                roundedFill = { color, radius ->
                    uiKit.roundedFill(color, radius)
                },
                space = { value -> uiKit.space(value) },
                filterChip = { label, selected, onClick ->
                    ledViews.filterChip(label, selected, onClick)
                },
                colorDot = { colorId, hex, onClick ->
                    colorDot(colorId, hex, onClick)
                },
                colorDotDrawable = { hex, selected ->
                    ledViews.colorDotDrawable(hex, selected)
                },
                fanPresetBubble = {
                        c1, c2, c3, c4, value, selected, onClick ->
                    fanPresetBubble(
                        c1,
                        c2,
                        c3,
                        c4,
                        presetValue = value,
                        selectedOverride = selected,
                        onClick = onClick
                    )
                }
            )
        )
    }

    fun selectedFanPresetBubble(
        c1: String,
        c2: String,
        c3: String,
        c4: String,
        presetValue: String,
        selected: () -> Boolean,
        onClick: () -> Unit
    ): View {
        return fanPresetBubble(
            c1,
            c2,
            c3,
            c4,
            presetValue = presetValue,
            selectedOverride = selected,
            onClick = onClick
        )
    }

    fun selectedFanPresetBubble(
        c1: String,
        c2: String,
        c3: String,
        c4: String,
        presetValue: String,
        selected: Boolean,
        onClick: () -> Unit
    ): View {
        return selectedFanPresetBubble(
            c1,
            c2,
            c3,
            c4,
            presetValue,
            { selected },
            onClick
        )
    }

    fun colorDot(
        colorId: Int,
        hex: String,
        onClick: () -> Unit
    ): View {
        return ledViews.colorDot(
            hex = hex,
            selected = state.fanColor == colorId,
            onClick = onClick
        )
    }

    private fun applyFanPreviewIfEnabled() {
        if (!previewEnabled()) return

        if (state.fanEnabled) {
            applyFanSelection(state.fanEffect, state.fanColor)
        } else {
            runLedPreview { HardwareController.setFanLedEnabled(false) }
        }
    }

    private fun applyLogoPreviewIfEnabled() {
        if (!previewEnabled()) return

        runLedPreview {
            if (state.logoEnabled) {
                if (!HardwareController.setLogoLedEffect(state.logoEffect, state.logoColor)) {
                    reportLightingFailure("Logo")
                }
            } else {
                HardwareController.setLogoLedEnabled(false)
            }
        }
    }

    private fun applyShoulderPreviewIfEnabled() {
        if (!previewEnabled()) return

        if (state.shoulderEnabled) {
            applyShoulderSelection(state.shoulderEffect, state.shoulderColor)
        } else {
            runLedPreview { HardwareController.setShoulderLedEnabled(false) }
        }
    }

    private fun applyShoulderSelection(effect: String, color: Int) {
        runLedPreview {
            if (!HardwareController.setShoulderLedEffect(effect, color)) {
                activity.runOnUiThread {
                    android.widget.Toast.makeText(
                        activity,
                        "Trigger lighting could not be applied. Check root access and ROM compatibility.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun reportLightingFailure(zone: String) {
        activity.runOnUiThread {
            android.widget.Toast.makeText(activity,
                "$zone lighting could not be applied. Check root access and ROM compatibility.",
                android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun applyLogoSelection(effect: String, color: Int) {
        runLedPreview {
            if (!HardwareController.setLogoLedEffect(effect, color)) reportLightingFailure("Logo")
        }
    }

    private fun applyFanSelection(effect: String, color: Int) {
        runLedPreview {
            val success = if (effect.startsWith("preset:")) {
                HardwareController.setFanLedStockPreset(effect.removePrefix("preset:"))
            } else HardwareController.setFanLedEffect(effect, color)
            if (!success) reportLightingFailure("Fan")
        }
    }

    private fun disableRgbStudioForManualControl() {
        if (!RgbStudioStorage.isEnabled(activity)) return

        RgbStudioStorage.setEnabled(activity, false)
        HardwareServiceActions.stopRgbCycle(
            activity,
            restoreNormalLeds = false
        )
    }

    private fun applyFanPreset(effectValue: String) {
        val palette = FanLedPalette.fromStockPreset(effectValue) ?: return
        state.fanEnabled = true
        state.fanEffect = FanLedPalette.normalizeEffect(state.fanEffect)
        if (!LedBrightness.supported("fan", state.fanEffect, palette) && LedBrightness.level(state.fanEffect) != 255) {
            state.fanEffect = LedBrightness.encode(255, LedBrightness.effect(state.fanEffect))
        }
        state.fanColor = palette
        applyFanSelection(state.fanEffect, state.fanColor)
        refreshFan?.invoke()
    }

    private fun startFanLedService() {
        runBackground { HardwareServiceActions.startFanLed(activity) }
    }

    private fun stopFanLedService() {
        runBackground { HardwareServiceActions.stopFanLed(activity) }
    }

    private fun anyLedEnabled(): Boolean {
        return state.fanEnabled ||
            state.logoEnabled ||
            state.shoulderEnabled
    }

    private fun fanPresetBubble(
        vararg colors: String,
        presetValue: String,
        selectedOverride: (() -> Boolean)? = null,
        onClick: () -> Unit
    ): View {
        return ledViews.fanPresetBubble(
            colors = colors.toList(),
            selected = selectedOverride ?: {
                FanLedPalette.isSelected(
                    state.fanEffect,
                    state.fanColor,
                    presetValue
                )
            },
            onClick = onClick
        )
    }
}
