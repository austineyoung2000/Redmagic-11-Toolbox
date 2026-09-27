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
                runBackground {
                    HardwareController.setShoulderLedEffect(effect, color)
                }
            },
            disableLed = {
                disableRgbStudioForManualControl()
                runBackground {
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

    fun showLogo() {
        LogoLedDialogUi.showLogoLedDialog(
            activity = activity,
            originalEnabled = state.logoEnabled,
            originalEffect = state.logoEffect,
            originalColor = state.logoColor,
            currentEnabled = { state.logoEnabled },
            currentEffect = { state.logoEffect },
            currentColor = { state.logoColor },
            setEnabled = { state.logoEnabled = it },
            setEffect = { state.logoEffect = it },
            setColor = { state.logoColor = it },
            applyPreviewIfEnabled = {
                disableRgbStudioForManualControl()
                applyLogoPreviewIfEnabled()
            },
            applyEffect = { effect, color ->
                disableRgbStudioForManualControl()
                runBackground {
                    HardwareController.setLogoLedEffect(effect, color)
                }
            },
            disableLed = {
                disableRgbStudioForManualControl()
                runBackground {
                    HardwareController.setLogoLedEnabled(false)
                }
            },
            saveState = {
                saveLogoLedStateStorage(
                    activity,
                    LedState(
                        state.logoEnabled,
                        state.logoEffect,
                        state.logoColor
                    )
                )
            },
            startFanLedService = ::startFanLedService,
            stopFanLedService = ::stopFanLedService,
            anyLedEnabled = ::anyLedEnabled,
            setDialogRefresh = { refreshLogo = it },
            deps = LogoLedDialogUi.Deps(
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

    fun showFan() {
        FanLedDialogUi.showFanLedDialog(
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
                runBackground {
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
                        c1, c2, c3, c4, value, onClick ->
                    fanPresetBubble(
                        c1,
                        c2,
                        c3,
                        c4,
                        presetValue = value,
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
        selected: Boolean,
        onClick: () -> Unit
    ): View {
        return fanPresetBubble(
            c1,
            c2,
            c3,
            c4,
            presetValue = presetValue,
            selectedOverride = { selected },
            onClick = onClick
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
            runBackground { HardwareController.setFanLedEnabled(false) }
        }
    }

    private fun applyLogoPreviewIfEnabled() {
        if (!previewEnabled()) return

        runBackground {
            if (state.logoEnabled) {
                HardwareController.setLogoLedEffect(
                    state.logoEffect,
                    state.logoColor
                )
            } else {
                HardwareController.setLogoLedEnabled(false)
            }
        }
    }

    private fun applyShoulderPreviewIfEnabled() {
        if (!previewEnabled()) return

        runBackground {
            if (state.shoulderEnabled) {
                HardwareController.setShoulderLedEffect(
                    state.shoulderEffect,
                    state.shoulderColor
                )
            } else {
                HardwareController.setShoulderLedEnabled(false)
            }
        }
    }

    private fun applyFanSelection(effect: String, color: Int) {
        runBackground {
            if (effect.startsWith("preset:")) {
                HardwareController.setFanLedStockPreset(
                    effect.removePrefix("preset:")
                )
            } else {
                HardwareController.setFanLedEffect(effect, color)
            }
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
        state.fanEnabled = true
        state.fanEffect = "preset:$effectValue"
        state.fanColor = -1
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
                state.fanEffect == "preset:$presetValue"
            },
            onClick = onClick
        )
    }
}
