package com.elitedarkkaiser.redmagic

import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import com.elitedarkkaiser.redmagic.state.LedState
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.elitedarkkaiser.redmagic.ui.LightingTabDeps
import com.elitedarkkaiser.redmagic.ui.LightingTabUi
import com.elitedarkkaiser.redmagic.ui.components.LedControlViewFactory
import com.elitedarkkaiser.redmagic.ui.components.MainActivityUiKit

/**
 * Owns the normal LED profile, Lighting-tab composition, previews, and zone
 * dialogs. Mode/service orchestration remains in MainLightingTabActions.
 */
internal class MainLightingController(
    private val activity: MainActivity,
    private val uiKit: MainActivityUiKit,
    private val ledViews: LedControlViewFactory,
    private val actions: MainLightingTabActions,
    private val runBackground: (() -> Unit) -> Boolean,
    private val capabilities: () -> DeviceCapabilities,
    private val showGameModeAppPicker: () -> Unit,
    private val showGameModeProfileDialog: () -> Unit
) {
    private var realTimePreviewEnabled = true

    private var fanEnabled = true
    private var fanEffect = "steady"
    private var fanColor = 1

    private var logoEnabled = true
    private var logoEffect = "steady"
    private var logoColor = 1

    private var shoulderEnabled = true
    private var shoulderEffect = "breathe"
    private var shoulderColor = 8

    private var refreshFanDialog: (() -> Unit)? = null
    private var refreshLogoDialog: (() -> Unit)? = null
    private var refreshShoulderDialog: (() -> Unit)? = null

    fun loadSavedState() {
        savedFanLedStateStorage(activity).let {
            fanEnabled = it.enabled
            fanEffect = it.effect
            fanColor = it.color
        }
        savedLogoLedStateStorage(activity).let {
            logoEnabled = it.enabled
            logoEffect = it.effect
            logoColor = it.color
        }
        savedShoulderLedStateStorage(activity).let {
            shoulderEnabled = it.enabled
            shoulderEffect = it.effect
            shoulderColor = it.color
        }
        realTimePreviewEnabled =
            isRealTimePreviewEnabledStorage(activity)
    }

    fun applyMasterProfile(profile: MasterProfile) {
        val hardware = profile.hardware
        fanEnabled = hardware.fanLedEnabled
        fanEffect = hardware.fanLedEffect
        fanColor = hardware.fanLedColor
        logoEnabled = hardware.logoLedEnabled
        logoEffect = hardware.logoLedEffect
        logoColor = hardware.logoLedColor
        shoulderEnabled = hardware.shoulderLedEnabled
        shoulderEffect = hardware.shoulderLedEffect
        shoulderColor = hardware.shoulderLedColor
        realTimePreviewEnabled = profile.realtimePreviewEnabled

        refreshFanDialog?.invoke()
        refreshLogoDialog?.invoke()
        refreshShoulderDialog?.invoke()
    }

    fun applyAllLedState(effect: String, color: Int) {
        fanEnabled = true
        fanEffect = effect
        fanColor = color
        logoEnabled = true
        logoEffect = effect
        logoColor = color
        shoulderEnabled = true
        shoulderEffect = effect
        shoulderColor = color

        refreshFanDialog?.invoke()
        refreshLogoDialog?.invoke()
        refreshShoulderDialog?.invoke()
    }

    fun startRgbStudioIfEnabled() {
        if (RgbStudioStorage.isEnabled(activity)) {
            HardwareServiceActions.startRgbCycle(activity)
        }
    }

    fun createView(): LinearLayout {
        return LightingTabUi.create(
            activity,
            createTabDependencies()
        )
    }

    private fun createTabDependencies(): LightingTabDeps {
        return LightingTabDeps(
            scrollTabContainer = { uiKit.scrollTabContainer() },
            sectionPanel = { uiKit.sectionPanel() },
            sectionHeader = { icon, text ->
                uiKit.sectionHeader(icon, text)
            },
            bodyText = { text -> uiKit.bodyText(text) },
            subtleLabel = { text -> uiKit.subtleLabel(text) },
            infoRow = { label, value ->
                uiKit.infoRow(label, value)
            },
            actionButton = { text, danger, onClick ->
                uiKit.actionButton(text, danger, onClick)
            },
            filterChip = { label, selected, onClick ->
                ledViews.filterChip(label, selected, onClick)
            },
            updateSelectableButton = { button, selected ->
                uiKit.updateSelectableButton(button, selected)
            },
            singleRow = { button -> uiKit.singleRow(button) },
            row = { left, right -> uiKit.row(left, right) },
            dp = { value -> uiKit.dp(value) },
            capabilities = capabilities(),
            getRealTimePreviewEnabled = {
                realTimePreviewEnabled
            },
            setRealTimePreviewEnabled = {
                realTimePreviewEnabled = it
            },
            saveRealTimePreviewEnabled = {
                saveRealTimePreviewEnabledStorage(activity, it)
            },
            showFanLedDialog = { showFanLedDialog() },
            showLogoLedDialog = { showLogoLedDialog() },
            showShoulderLedDialog = { showShoulderLedDialog() },
            rgbStudioSummary = { actions.rgbStudioSummary() },
            showRgbStudioDialog = { onUpdated ->
                actions.showRgbStudioDialog(onUpdated)
            },
            showGameModeAppPicker = showGameModeAppPicker,
            showGameModeProfileDialog = showGameModeProfileDialog,
            gameModeAppsSummary = {
                gameModeAppsSummaryStorage(activity)
            },
            getChargingLedEnabled = {
                actions.isChargingLedEnabled()
            },
            setChargingLedEnabled = {
                actions.setChargingLedEnabled(it)
            },
            showChargingFanLedDialog = {
                showChargingFanLedDialog()
            },
            showChargingLogoLedDialog = {
                ChargingLedActions.showLogoDialog(
                    activity = activity,
                    runBackground = runBackground,
                    deps = chargingLedDialogDeps()
                )
            },
            showChargingShoulderLedDialog = {
                ChargingLedActions.showShoulderDialog(
                    activity = activity,
                    runBackground = runBackground,
                    deps = chargingLedDialogDeps()
                )
            },
            getCallLightingEnabled = {
                actions.isCallLightingEnabled()
            },
            setCallLightingEnabled = {
                actions.setCallLightingEnabled(it)
            },
            getPauseFanDuringCalls = {
                actions.shouldPauseFanDuringCalls()
            },
            setPauseFanDuringCalls = {
                actions.setPauseFanDuringCalls(it)
            },
            showIncomingCallProfileDialog = {
                actions.showIncomingCallProfileDialog(
                    callLightingProfileDeps()
                )
            },
            showConnectedCallProfileDialog = {
                actions.showConnectedCallProfileDialog(
                    callLightingProfileDeps()
                )
            }
        )
    }

    private fun showChargingFanLedDialog() {
        ChargingLedActions.showFanDialog(
            activity = activity,
            runBackground = runBackground,
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
                selectedFanPresetBubble(
                    c1,
                    c2,
                    c3,
                    c4,
                    value,
                    selected,
                    onClick
                )
            }
        )
    }

    private fun callLightingProfileDeps(): CallLightingProfileUi.Deps {
        return CallLightingProfileUi.Deps(
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
            filterChip = { label, selected, onClick ->
                ledViews.filterChip(label, selected, onClick)
            },
            space = { value -> uiKit.space(value) },
            colorDotDrawable = { hex, selected ->
                ledViews.colorDotDrawable(hex, selected)
            },
            colorDotGeneric = { hex, selected, onClick ->
                ledViews.colorDot(hex, selected, onClick)
            },
            fanPresetBubble = {
                    c1, c2, c3, c4, value, selected, onClick ->
                selectedFanPresetBubble(
                    c1,
                    c2,
                    c3,
                    c4,
                    value,
                    selected,
                    onClick
                )
            }
        )
    }

    private fun chargingLedDialogDeps():
        ChargingLedProfileDialog.Deps {
        return ChargingLedProfileDialog.Deps(
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
            colorDotGeneric = { hex, selected, onClick ->
                ledViews.colorDot(hex, selected, onClick)
            },
            colorDotDrawable = { hex, selected ->
                ledViews.colorDotDrawable(hex, selected)
            },
            fanPresetBubble = {
                    c1, c2, c3, c4, value, selected, onClick ->
                selectedFanPresetBubble(
                    c1,
                    c2,
                    c3,
                    c4,
                    value,
                    selected,
                    onClick
                )
            }
        )
    }

    private fun showShoulderLedDialog() {
        ShoulderLedDialogUi.showShoulderLedDialog(
            activity = activity,
            originalEnabled = shoulderEnabled,
            originalEffect = shoulderEffect,
            originalColor = shoulderColor,
            currentEnabled = { shoulderEnabled },
            currentEffect = { shoulderEffect },
            currentColor = { shoulderColor },
            setEnabled = { shoulderEnabled = it },
            setEffect = { shoulderEffect = it },
            setColor = { shoulderColor = it },
            applyPreviewIfEnabled = {
                disableRgbStudioForManualControl()
                applyShoulderPreviewIfEnabled()
            },
            applyEffect = { effect, color ->
                disableRgbStudioForManualControl()
                runBackground {
                    HardwareController.setShoulderLedEffect(
                        effect,
                        color
                    )
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
                        shoulderEnabled,
                        shoulderEffect,
                        shoulderColor
                    )
                )
            },
            startFanLedService = { startFanLedService() },
            stopFanLedService = { stopFanLedService() },
            anyLedEnabled = { anyLedEnabled() },
            setDialogRefresh = { refreshShoulderDialog = it },
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

    private fun showLogoLedDialog() {
        LogoLedDialogUi.showLogoLedDialog(
            activity = activity,
            originalEnabled = logoEnabled,
            originalEffect = logoEffect,
            originalColor = logoColor,
            currentEnabled = { logoEnabled },
            currentEffect = { logoEffect },
            currentColor = { logoColor },
            setEnabled = { logoEnabled = it },
            setEffect = { logoEffect = it },
            setColor = { logoColor = it },
            applyPreviewIfEnabled = {
                disableRgbStudioForManualControl()
                applyLogoPreviewIfEnabled()
            },
            applyEffect = { effect, color ->
                disableRgbStudioForManualControl()
                runBackground {
                    HardwareController.setLogoLedEffect(
                        effect,
                        color
                    )
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
                    LedState(logoEnabled, logoEffect, logoColor)
                )
            },
            startFanLedService = { startFanLedService() },
            stopFanLedService = { stopFanLedService() },
            anyLedEnabled = { anyLedEnabled() },
            setDialogRefresh = { refreshLogoDialog = it },
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

    private fun showFanLedDialog() {
        FanLedDialogUi.showFanLedDialog(
            activity = activity,
            originalEnabled = fanEnabled,
            originalEffect = fanEffect,
            originalColor = fanColor,
            currentEnabled = { fanEnabled },
            currentEffect = { fanEffect },
            currentColor = { fanColor },
            setEnabled = { fanEnabled = it },
            setEffect = { fanEffect = it },
            setColor = { fanColor = it },
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
                    LedState(fanEnabled, fanEffect, fanColor)
                )
            },
            startFanLedService = { startFanLedService() },
            stopFanLedService = { stopFanLedService() },
            anyLedEnabled = { anyLedEnabled() },
            applyFanPreset = { applyFanPreset(it) },
            setDialogRefresh = { refreshFanDialog = it },
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

    private fun applyFanPreviewIfEnabled() {
        if (!realTimePreviewEnabled) return

        if (fanEnabled) {
            applyFanSelection(fanEffect, fanColor)
        } else {
            runBackground {
                HardwareController.setFanLedEnabled(false)
            }
        }
    }

    private fun applyLogoPreviewIfEnabled() {
        if (!realTimePreviewEnabled) return

        runBackground {
            if (logoEnabled) {
                HardwareController.setLogoLedEffect(
                    logoEffect,
                    logoColor
                )
            } else {
                HardwareController.setLogoLedEnabled(false)
            }
        }
    }

    private fun applyShoulderPreviewIfEnabled() {
        if (!realTimePreviewEnabled) return

        runBackground {
            if (shoulderEnabled) {
                HardwareController.setShoulderLedEffect(
                    shoulderEffect,
                    shoulderColor
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
        fanEnabled = true
        fanEffect = "preset:$effectValue"
        fanColor = -1

        applyFanSelection(fanEffect, fanColor)
        refreshFanDialog?.invoke()
    }

    private fun startFanLedService() {
        runBackground {
            HardwareServiceActions.startFanLed(activity)
        }
    }

    private fun stopFanLedService() {
        runBackground {
            HardwareServiceActions.stopFanLed(activity)
        }
    }

    private fun anyLedEnabled(): Boolean {
        return fanEnabled || logoEnabled || shoulderEnabled
    }

    private fun selectedFanPresetBubble(
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

    private fun fanPresetBubble(
        vararg colors: String,
        presetValue: String,
        selectedOverride: (() -> Boolean)? = null,
        onClick: () -> Unit
    ): View {
        return ledViews.fanPresetBubble(
            colors = colors.toList(),
            selected = selectedOverride ?: {
                fanEffect == "preset:$presetValue"
            },
            onClick = onClick
        )
    }

    private fun colorDot(
        colorId: Int,
        hex: String,
        onClick: () -> Unit
    ): View {
        return ledViews.colorDot(
            hex = hex,
            selected = fanColor == colorId,
            onClick = onClick
        )
    }
}
