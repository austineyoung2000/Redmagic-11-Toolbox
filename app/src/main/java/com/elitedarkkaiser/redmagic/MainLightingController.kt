package com.elitedarkkaiser.redmagic

import android.graphics.Typeface
import android.widget.LinearLayout
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.elitedarkkaiser.redmagic.ui.LightingTabDeps
import com.elitedarkkaiser.redmagic.ui.LightingTabUi
import com.elitedarkkaiser.redmagic.ui.components.LedControlViewFactory
import com.elitedarkkaiser.redmagic.ui.components.MainActivityUiKit

/**
 * Owns normal LED state and Lighting-tab composition. Zone dialogs are
 * delegated to MainLightingZoneDialogs; service orchestration remains in
 * MainLightingTabActions.
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

    private val zoneState = LightingZoneState()
    private val zoneDialogs by lazy {
        MainLightingZoneDialogs(
            activity = activity,
            uiKit = uiKit,
            ledViews = ledViews,
            runBackground = runBackground,
            state = zoneState,
            previewEnabled = { realTimePreviewEnabled }
        )
    }

    fun loadSavedState() {
        savedFanLedStateStorage(activity).let {
            zoneState.fanEnabled = it.enabled
            zoneState.fanEffect = it.effect
            zoneState.fanColor = it.color
        }
        savedLogoLedStateStorage(activity).let {
            zoneState.logoEnabled = it.enabled
            zoneState.logoEffect = it.effect
            zoneState.logoColor = it.color
        }
        savedShoulderLedStateStorage(activity).let {
            zoneState.shoulderEnabled = it.enabled
            zoneState.shoulderEffect = it.effect
            zoneState.shoulderColor = it.color
        }
        realTimePreviewEnabled =
            isRealTimePreviewEnabledStorage(activity)
    }

    fun applyMasterProfile(profile: MasterProfile) {
        val hardware = profile.hardware
        zoneState.fanEnabled = hardware.fanLedEnabled
        zoneState.fanEffect = FanLedPalette.normalizeEffect(
            hardware.fanLedEffect
        )
        zoneState.fanColor = FanLedPalette.normalizeColor(
            hardware.fanLedEffect,
            hardware.fanLedColor
        )
        zoneState.logoEnabled = hardware.logoLedEnabled
        zoneState.logoEffect = hardware.logoLedEffect
        zoneState.logoColor = hardware.logoLedColor
        zoneState.shoulderEnabled = hardware.shoulderLedEnabled
        zoneState.shoulderEffect = hardware.shoulderLedEffect
        zoneState.shoulderColor = hardware.shoulderLedColor
        realTimePreviewEnabled = profile.realtimePreviewEnabled

        zoneDialogs.refreshOpenDialogs()
    }

    fun applyAllLedState(effect: String, color: Int) {
        zoneState.fanEnabled = true
        zoneState.fanEffect = effect
        zoneState.fanColor = color
        zoneState.logoEnabled = true
        zoneState.logoEffect = effect
        zoneState.logoColor = color
        zoneState.shoulderEnabled = true
        zoneState.shoulderEffect = effect
        zoneState.shoulderColor = color

        zoneDialogs.refreshOpenDialogs()
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
            showFanLedDialog = { zoneDialogs.showFan() },
            showLogoLedDialog = { zoneDialogs.showLogo() },
            showShoulderLedDialog = { zoneDialogs.showShoulder() },
            rgbStudioSummary = { actions.rgbStudioSummary() },
            showRgbStudioDialog = { onUpdated ->
                actions.showRgbStudioDialog(
                    onUpdated = onUpdated,
                    onAllLedStateApplied = ::applyAllLedState
                )
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
                zoneDialogs.colorDot(colorId, hex, onClick)
            },
            colorDotDrawable = { hex, selected ->
                ledViews.colorDotDrawable(hex, selected)
            },
            fanPresetBubble = {
                    c1, c2, c3, c4, value, selected, onClick ->
                zoneDialogs.selectedFanPresetBubble(
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
                zoneDialogs.selectedFanPresetBubble(
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
                zoneDialogs.selectedFanPresetBubble(
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

}
