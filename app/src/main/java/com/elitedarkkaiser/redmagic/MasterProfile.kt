package com.elitedarkkaiser.redmagic

import com.elitedarkkaiser.redmagic.state.LedState
data class MasterProfile(
    val name: String,
    val schemaVersion: Int = MasterProfileStorage.CURRENT_SCHEMA_VERSION,
    val hardware: HardwareSettingsSnapshot,
    val triggers: TriggerPrefsSnapshot,
    val pumpExperimentalAccepted: Boolean,
    val gameMode: GameModeProfile,
    val gamePackages: Set<String>,
    val perGameProfiles: Map<String, String>,
    val chargingEnabled: Boolean,
    val chargingFanLed: LedState,
    val chargingLogoLed: LedState,
    val chargingShoulderLed: LedState,

    val callLightingEnabled: Boolean,
    val pauseFanDuringCalls: Boolean,
    val incomingCallFanLed: LedState,
    val incomingCallLogoLed: LedState,
    val incomingCallShoulderLed: LedState,
    val connectedCallFanLed: LedState,
    val connectedCallLogoLed: LedState,
    val connectedCallShoulderLed: LedState,

    val realtimePreviewEnabled: Boolean,
    val rgbStudio: RgbStudioState,
    val useFahrenheit: Boolean,
    val magicKeyMode: Int,
    val magicKeyAppPackage: String?,
    val magicKeyShortcut: MagicKeyShortcutTarget? = null,
    val sliderDualApp: SliderDualAppConfig =
        SliderDualAppConfig(),
    val hapticFeedback: HapticFeedbackConfig =
        HapticFeedbackConfig(),
    val nativeTgkProfilesJson: String? = null,
    val chargeSeparationEnabled: Boolean? = null,
    val refreshRateProfilesJson: String? = null,
    val touchTuningProfilesJson: String? = null
)
