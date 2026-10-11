package com.elitedarkkaiser.redmagic

import android.widget.Button
import com.elitedarkkaiser.redmagic.state.LedState

internal class MainLightingTabActions(
    private val activity: MainActivity,
    private val runBackground: (() -> Unit) -> Boolean,
    private val dp: (Int) -> Int,
    private val filterChip:
        (String, Boolean, () -> Unit) -> Button,
    private val updateSelectableButton:
        (Button, Boolean) -> Unit
) {
    fun rgbStudioSummary(): String {
        return RgbStudioStorage.summary(activity)
    }

    fun showRgbStudioDialog(
        onUpdated: () -> Unit,
        onAllLedStateApplied: (RgbStudioState, Int) -> Unit
    ) {
        RgbStudioDialog.show(
            activity = activity,
            initial = RgbStudioStorage.read(activity),
            deps = RgbStudioDialog.Deps(
                dp = dp,
                filterChip = filterChip,
                updateSelectableButton =
                    updateSelectableButton,
                onSaveAndApply = { state ->
                    RgbStudioStorage.save(activity, state)
                    if (state.enabled) {
                        HardwareServiceActions.startRgbCycle(
                            activity
                        )
                    } else {
                        HardwareServiceActions.stopRgbCycle(
                            activity
                        )
                    }
                    onUpdated()
                },
                onApplyToAll = { state ->
                    applyEffectToAllZones(
                        state,
                        state.colors.first(),
                        onAllLedStateApplied,
                        onUpdated
                    )
                },
                onStopService = {
                    RgbStudioStorage.setEnabled(
                        activity,
                        false
                    )
                    HardwareServiceActions.stopRgbCycle(
                        activity
                    )
                    onUpdated()
                }
            )
        )
    }

    fun isChargingLedEnabled(): Boolean {
        return ChargingLedState.isEnabled(activity)
    }

    fun setChargingLedEnabled(enabled: Boolean) {
        ChargingLedState.setEnabled(activity, enabled)
        HardwareServiceActions.startChargingMode(activity)
    }

    fun isCallLightingEnabled(): Boolean {
        return CallLightingState.isEnabled(activity)
    }

    fun setCallLightingEnabled(enabled: Boolean) {
        CallLightingState.setEnabled(activity, enabled)
        if (enabled) {
            HardwareServiceActions.startCallLighting(activity)
        } else {
            CallLightingState.setActive(activity, false)
            HardwareServiceActions.stopCallLighting(activity)
        }
    }

    fun shouldPauseFanDuringCalls(): Boolean {
        return CallLightingState
            .shouldPauseFanDuringCalls(activity)
    }

    fun setPauseFanDuringCalls(enabled: Boolean) {
        CallLightingState.setPauseFanDuringCalls(
            activity,
            enabled
        )
    }

    fun showIncomingCallProfileDialog(
        deps: CallLightingProfileUi.Deps
    ) {
        showCallProfileDialog(
            title = "Incoming Call Lighting",
            subtitle =
                "These LED settings apply automatically while " +
                    "an incoming call is ringing.",
            fanKeys = CallLightingProfileUi.ZoneKeys(
                CallLightingState.INCOMING_FAN_ENABLED_KEY,
                CallLightingState.INCOMING_FAN_EFFECT_KEY,
                CallLightingState.INCOMING_FAN_COLOR_KEY,
                "Fan LED",
                "flashing",
                5
            ),
            logoKeys = CallLightingProfileUi.ZoneKeys(
                CallLightingState.INCOMING_LOGO_ENABLED_KEY,
                CallLightingState.INCOMING_LOGO_EFFECT_KEY,
                CallLightingState.INCOMING_LOGO_COLOR_KEY,
                "Logo LED",
                "flashing",
                1
            ),
            shoulderKeys = CallLightingProfileUi.ZoneKeys(
                CallLightingState.INCOMING_SHOULDER_ENABLED_KEY,
                CallLightingState.INCOMING_SHOULDER_EFFECT_KEY,
                CallLightingState.INCOMING_SHOULDER_COLOR_KEY,
                "Shoulder LEDs",
                "flashing",
                8
            ),
            deps = deps
        )
    }

    fun showConnectedCallProfileDialog(
        deps: CallLightingProfileUi.Deps
    ) {
        showCallProfileDialog(
            title = "Connected Call Lighting",
            subtitle =
                "These LED settings apply automatically while " +
                    "a call is connected.",
            fanKeys = CallLightingProfileUi.ZoneKeys(
                CallLightingState.CONNECTED_FAN_ENABLED_KEY,
                CallLightingState.CONNECTED_FAN_EFFECT_KEY,
                CallLightingState.CONNECTED_FAN_COLOR_KEY,
                "Fan LED",
                "steady",
                5
            ),
            logoKeys = CallLightingProfileUi.ZoneKeys(
                CallLightingState.CONNECTED_LOGO_ENABLED_KEY,
                CallLightingState.CONNECTED_LOGO_EFFECT_KEY,
                CallLightingState.CONNECTED_LOGO_COLOR_KEY,
                "Logo LED",
                "steady",
                1
            ),
            shoulderKeys = CallLightingProfileUi.ZoneKeys(
                CallLightingState.CONNECTED_SHOULDER_ENABLED_KEY,
                CallLightingState.CONNECTED_SHOULDER_EFFECT_KEY,
                CallLightingState.CONNECTED_SHOULDER_COLOR_KEY,
                "Shoulder LEDs",
                "steady",
                8
            ),
            deps = deps
        )
    }

    private fun applyEffectToAllZones(
        selection: RgbStudioState,
        color: Int,
        onAllLedStateApplied: (RgbStudioState, Int) -> Unit,
        onUpdated: () -> Unit
    ) {
        RgbStudioStorage.setEnabled(activity, false)
        HardwareServiceActions.stopRgbCycle(
            activity,
            restoreNormalLeds = false
        )

        onAllLedStateApplied(selection, color)

        saveFanLedStateStorage(activity, LedState(true, LedBrightness.encode(selection.fanBrightness, selection.effect), color))
        saveLogoLedStateStorage(activity, LogoBarSelection(selection.effect, selection.logoEnabled, selection.logoBrightness,
            selection.barEnabled, selection.barColors.firstOrNull() ?: color, selection.barBrightness).state(color))
        saveShoulderLedStateStorage(activity, LedState(true, LedBrightness.encode(selection.shoulderBrightness, selection.effect), color))

        runBackground {
            ModeTransitionCoordinator.applyLedProfile(activity, LedOwner.NORMAL,
                "normal-all-zones-preview", force = true) {
                HardwareController.setRgbCycleFrame(
                    effectName = selection.effect,
                    logoColor = color,
                    shoulderColor = color,
                    fanColor = color,
                    logoBrightness = selection.logoBrightness,
                    shoulderBrightness = selection.shoulderBrightness,
                    fanBrightness = selection.fanBrightness,
                    barColor = selection.barColors.firstOrNull() ?: color,
                    barBrightness = selection.barBrightness,
                    logoEnabled = selection.logoEnabled,
                    barEnabled = selection.barEnabled
                )
            }
            HardwareServiceActions.startFanLed(activity)
        }
        onUpdated()
    }

    private fun showCallProfileDialog(
        title: String,
        subtitle: String,
        fanKeys: CallLightingProfileUi.ZoneKeys,
        logoKeys: CallLightingProfileUi.ZoneKeys,
        shoulderKeys: CallLightingProfileUi.ZoneKeys,
        deps: CallLightingProfileUi.Deps
    ) {
        CallLightingProfileUi.show(
            activity = activity,
            title = title,
            subtitle = subtitle,
            fanKeys = fanKeys,
            logoKeys = logoKeys,
            shoulderKeys = shoulderKeys,
            deps = deps
        )
    }
}
