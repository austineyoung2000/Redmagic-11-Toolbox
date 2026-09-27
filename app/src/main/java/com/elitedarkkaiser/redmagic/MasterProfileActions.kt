package com.elitedarkkaiser.redmagic

import android.content.Context
import com.elitedarkkaiser.redmagic.state.LedState

object MasterProfileActions {
    fun captureAndSave(context: Context, name: String): MasterProfile {
        return captureCurrent(context, name).also {
            MasterProfileStorage.upsertProfile(context, it)
        }
    }

    fun captureCurrent(context: Context, name: String): MasterProfile {
        val fan = savedFanLedStateStorage(context)
        val logo = savedLogoLedStateStorage(context)
        val shoulder = savedShoulderLedStateStorage(context)
        val pump = savedPumpStateStorage(context)
        val triggers = readTriggerPrefsSnapshot(context)
        val hardware = HardwareSettingsSnapshot(
            fanEnabled = HardwareController.isFanEnabled(),
            fanLevel = HardwareController.readFanLevel() ?: 0,
            autoFanEnabled = isAutoFanEnabledStorage(context),
            fanCurveMode = selectedCurveStorage(context),
            pumpEnabled = pump.enabled,
            pumpProfile = pump.profile,
            autoPumpEnabled = pump.autoEnabled,
            fanLedEnabled = fan.enabled,
            fanLedEffect = fan.effect,
            fanLedColor = fan.color,
            logoLedEnabled = logo.enabled,
            logoLedEffect = logo.effect,
            logoLedColor = logo.color,
            shoulderLedEnabled = shoulder.enabled,
            shoulderLedEffect = shoulder.effect,
            shoulderLedColor = shoulder.color
        )

        return MasterProfile(
            name = name,
            hardware = hardware,
            triggers = triggers,
            pumpExperimentalAccepted = pump.experimentalAccepted,
            gameMode = getSavedGameModeProfileStorage(context),
            gamePackages = getSavedGamePackagesStorage(context),
            perGameProfiles = getSavedPerGameProfilesStorage(context),
            chargingEnabled = ChargingLedState.isEnabled(context),
            chargingFanLed = chargingLed(context,
                ChargingLedState.FAN_ENABLED_KEY, ChargingLedState.FAN_EFFECT_KEY,
                ChargingLedState.FAN_COLOR_KEY, true, "steady", 5),
            chargingLogoLed = chargingLed(context,
                ChargingLedState.LOGO_ENABLED_KEY, ChargingLedState.LOGO_EFFECT_KEY,
                ChargingLedState.LOGO_COLOR_KEY, true, "steady", 1),
            chargingShoulderLed = chargingLed(context,
                ChargingLedState.SHOULDER_ENABLED_KEY, ChargingLedState.SHOULDER_EFFECT_KEY,
                ChargingLedState.SHOULDER_COLOR_KEY, true, "breathe", 8),
            callLightingEnabled = CallLightingState.isEnabled(context),
            pauseFanDuringCalls = CallLightingState.shouldPauseFanDuringCalls(context),
            incomingCallFanLed = callLed(context,
                CallLightingState.INCOMING_FAN_ENABLED_KEY, CallLightingState.INCOMING_FAN_EFFECT_KEY,
                CallLightingState.INCOMING_FAN_COLOR_KEY, true, "flashing", 5),
            incomingCallLogoLed = callLed(context,
                CallLightingState.INCOMING_LOGO_ENABLED_KEY, CallLightingState.INCOMING_LOGO_EFFECT_KEY,
                CallLightingState.INCOMING_LOGO_COLOR_KEY, true, "flashing", 1),
            incomingCallShoulderLed = callLed(context,
                CallLightingState.INCOMING_SHOULDER_ENABLED_KEY, CallLightingState.INCOMING_SHOULDER_EFFECT_KEY,
                CallLightingState.INCOMING_SHOULDER_COLOR_KEY, true, "flashing", 8),
            connectedCallFanLed = callLed(context,
                CallLightingState.CONNECTED_FAN_ENABLED_KEY, CallLightingState.CONNECTED_FAN_EFFECT_KEY,
                CallLightingState.CONNECTED_FAN_COLOR_KEY, true, "steady", 5),
            connectedCallLogoLed = callLed(context,
                CallLightingState.CONNECTED_LOGO_ENABLED_KEY, CallLightingState.CONNECTED_LOGO_EFFECT_KEY,
                CallLightingState.CONNECTED_LOGO_COLOR_KEY, true, "steady", 1),
            connectedCallShoulderLed = callLed(context,
                CallLightingState.CONNECTED_SHOULDER_ENABLED_KEY, CallLightingState.CONNECTED_SHOULDER_EFFECT_KEY,
                CallLightingState.CONNECTED_SHOULDER_COLOR_KEY, true, "steady", 8),
            realtimePreviewEnabled = isRealTimePreviewEnabledStorage(context),
            rgbStudio = RgbStudioStorage.read(context),
            useFahrenheit = isUseFahrenheitStorage(context),
            magicKeyMode = MagicKeyActions.readModeValue(),
            magicKeyAppPackage = savedMagicKeyAppPackageStorage(context),
            magicKeyShortcut = savedMagicKeyShortcutStorage(context),
            sliderDualApp = SliderDualAppStorage.read(context),
            hapticFeedback = HapticFeedback.read(context),
            nativeTgkProfilesJson =
                NativeTgkStorage.createExportJson(
                    context = context,
                    allowEmpty = true
                ),
            chargeSeparationEnabled =
                ChargeSeparationController
                    .read(context)
                    .takeIf { it.success }
                    ?.enabled,
            refreshRateProfilesJson =
                RefreshRateStorage.createExportJson(
                    context = context,
                    allowEmpty = true
                ),
            touchTuningProfilesJson =
                TouchTuningStorage.createExportJson(
                    context = context,
                    allowEmpty = true
                ),
            performanceModeProfilesJson =
                PerformanceModeStorage.createExportJson(
                    context = context,
                    allowEmpty = true
                )
        )
    }

    fun applyProfile(context: Context, profile: MasterProfile) {
        val hardware = profile.hardware
        saveFanLedStateStorage(context, LedState(hardware.fanLedEnabled, hardware.fanLedEffect, hardware.fanLedColor))
        saveLogoLedStateStorage(context, LedState(hardware.logoLedEnabled, hardware.logoLedEffect, hardware.logoLedColor))
        saveShoulderLedStateStorage(context, LedState(hardware.shoulderLedEnabled, hardware.shoulderLedEffect, hardware.shoulderLedColor))
        savePumpStateStorage(context, hardware.pumpEnabled, hardware.pumpProfile)
        saveAutoPumpStateStorage(context, hardware.autoPumpEnabled)
        setPumpExperimentalAcceptedStorage(context, profile.pumpExperimentalAccepted)
        saveSelectedCurveStorage(context, hardware.fanCurveMode)
        saveAutoFanEnabledStorage(context, hardware.autoFanEnabled)
        saveRealTimePreviewEnabledStorage(context, profile.realtimePreviewEnabled)
        saveTriggerPrefsStorage(context, profile.triggers)
        saveGameModeProfileStorage(context, profile.gameMode)
        setSavedGamePackagesStorage(context, profile.gamePackages)

        if (profile.schemaVersion >= 2) {
            setSavedPerGameProfilesStorage(context, profile.perGameProfiles)
            RgbStudioStorage.save(context, profile.rgbStudio)
            saveUseFahrenheitStorage(context, profile.useFahrenheit)
            if (
                profile.schemaVersion >= 3 &&
                profile.sliderDualApp.enabled
            ) {
                SliderDualAppStorage.save(
                    context,
                    profile.sliderDualApp
                )
                HardwareController
                    .disableSliderSystemHandling()
            } else {
                SliderDualAppStorage.disable(context)
                applyMagicKey(
                    context,
                    profile.magicKeyMode,
                    profile.magicKeyAppPackage,
                    profile.magicKeyShortcut
                )
            }
        }

        if (profile.schemaVersion >= 4) {
            HapticFeedback.save(
                context,
                profile.hapticFeedback
            )
        }

        if (profile.schemaVersion >= 7) {
            profile.nativeTgkProfilesJson?.let {
                NativeTgkRuntimeState.clear()
                NativeTgkCoordinator.disable(
                    context,
                    "master profile applied"
                )
                NativeTgkStorage.importProfilesJson(
                    context = context,
                    raw = it,
                    replaceExisting = true,
                    replaceAll = true
                )
            }
        }

        if (profile.schemaVersion >= 8) {
            profile.chargeSeparationEnabled?.let {
                ChargeSeparationController.setEnabled(
                    context,
                    it
                )
            }
        }

        if (profile.schemaVersion >= 9) {
            profile.refreshRateProfilesJson?.let {
                RefreshRateCoordinator.clearRuntimeState()
                RefreshRateStorage.importProfilesJson(
                    context = context,
                    raw = it,
                    replaceAll = true
                )
            }
        }

        if (profile.schemaVersion >= 10) {
            profile.touchTuningProfilesJson?.let {
                val previousPackages =
                    TouchTuningStorage.readProfiles(context)
                        .map { existing -> existing.packageName }
                        .toSet()
                TouchTuningStorage.importProfilesJson(
                    context = context,
                    raw = it,
                    replaceAll = true
                )
                TouchTuningController.reconcileStored(
                    context,
                    previousPackages
                )
            }
        }

        if (profile.schemaVersion >= 11) {
            profile.performanceModeProfilesJson?.let {
                PerformanceModeCoordinator.resetIfOwned(
                    context,
                    "master profile applied"
                )
                PerformanceModeStorage.importProfilesJson(
                    context = context,
                    raw = it,
                    replaceAll = true
                )
                PerformanceModeCoordinator.clearRuntimeState()
            }
        }

        ChargingLedState.setEnabled(context, profile.chargingEnabled)
        saveChargingLed(context, ChargingLedState.FAN_ENABLED_KEY,
            ChargingLedState.FAN_EFFECT_KEY, ChargingLedState.FAN_COLOR_KEY, profile.chargingFanLed)
        saveChargingLed(context, ChargingLedState.LOGO_ENABLED_KEY,
            ChargingLedState.LOGO_EFFECT_KEY, ChargingLedState.LOGO_COLOR_KEY, profile.chargingLogoLed)
        saveChargingLed(context, ChargingLedState.SHOULDER_ENABLED_KEY,
            ChargingLedState.SHOULDER_EFFECT_KEY, ChargingLedState.SHOULDER_COLOR_KEY, profile.chargingShoulderLed)

        CallLightingState.setEnabled(context, profile.callLightingEnabled)
        CallLightingState.setPauseFanDuringCalls(context, profile.pauseFanDuringCalls)
        saveCallLed(context, CallLightingState.INCOMING_FAN_ENABLED_KEY,
            CallLightingState.INCOMING_FAN_EFFECT_KEY, CallLightingState.INCOMING_FAN_COLOR_KEY, profile.incomingCallFanLed)
        saveCallLed(context, CallLightingState.INCOMING_LOGO_ENABLED_KEY,
            CallLightingState.INCOMING_LOGO_EFFECT_KEY, CallLightingState.INCOMING_LOGO_COLOR_KEY, profile.incomingCallLogoLed)
        saveCallLed(context, CallLightingState.INCOMING_SHOULDER_ENABLED_KEY,
            CallLightingState.INCOMING_SHOULDER_EFFECT_KEY, CallLightingState.INCOMING_SHOULDER_COLOR_KEY, profile.incomingCallShoulderLed)
        saveCallLed(context, CallLightingState.CONNECTED_FAN_ENABLED_KEY,
            CallLightingState.CONNECTED_FAN_EFFECT_KEY, CallLightingState.CONNECTED_FAN_COLOR_KEY, profile.connectedCallFanLed)
        saveCallLed(context, CallLightingState.CONNECTED_LOGO_ENABLED_KEY,
            CallLightingState.CONNECTED_LOGO_EFFECT_KEY, CallLightingState.CONNECTED_LOGO_COLOR_KEY, profile.connectedCallLogoLed)
        saveCallLed(context, CallLightingState.CONNECTED_SHOULDER_ENABLED_KEY,
            CallLightingState.CONNECTED_SHOULDER_EFFECT_KEY, CallLightingState.CONNECTED_SHOULDER_COLOR_KEY, profile.connectedCallShoulderLed)

        applyHardwareAndServices(context, profile)
    }

    private fun applyHardwareAndServices(context: Context, profile: MasterProfile) {
        val hardware = profile.hardware
        if (hardware.fanEnabled) HardwareController.setFanLevel(hardware.fanLevel)
        else HardwareController.enableFan(false)
        if (hardware.autoFanEnabled) HardwareServiceActions.startAutoFan(context)
        else HardwareServiceActions.stopAutoFan(context)

        if (hardware.pumpEnabled || hardware.autoPumpEnabled) {
            HardwareController.setPumpProfile(hardware.pumpProfile)
        } else HardwareController.enablePump(false)
        if (hardware.autoPumpEnabled) HardwareServiceActions.startAutoPump(context)
        else HardwareServiceActions.stopAutoPump(context)

        if (profile.triggers.triggersAutoStart && !triggersDisabledUntilRestartStorage(context)) {
            HardwareController.enableTriggers()
            HardwareServiceActions.startTriggers(context)
        } else HardwareServiceActions.stopTriggers(context)

        if (profile.rgbStudio.enabled) HardwareServiceActions.startRgbCycle(context)
        else HardwareServiceActions.stopRgbCycle(context)
        GameModeActions.startServiceSilentlyIfPermitted(context)
        HardwareServiceActions.startChargingMode(context)
        if (profile.callLightingEnabled) HardwareServiceActions.startCallLighting(context)
        else {
            CallLightingState.setActive(context, false)
            HardwareServiceActions.stopCallLighting(context)
        }
        if (profile.sliderDualApp.enabled) {
            HardwareServiceActions.startSliderDualApp(context)
        } else {
            HardwareServiceActions.stopSliderDualApp(context)
        }
        ModeTransitionCoordinator.invalidate()
        ModeTransitionCoordinator.restoreEffectiveOwner(context, "master-profile-applied")
        HapticFeedback.pulse(
            context,
            HapticFeedback.Event.MASTER_PROFILE
        )
    }

    private fun applyMagicKey(
        context: Context,
        mode: Int,
        pkg: String?,
        shortcut: MagicKeyShortcutTarget?
    ) {
        if (mode < 0) return
        val applied = when (mode) {
            1 -> HardwareController.setSliderOpenCamera()
            2 -> HardwareController.setSliderOpenGameSpace()
            3 -> HardwareController.setSliderSoundMode()
            4 -> HardwareController.setSliderFlashlight()
            5 -> HardwareController.setSliderVoiceRecorder()
            16 -> !pkg.isNullOrBlank() && HardwareController.setSliderLaunchApp(pkg)
            17 -> shortcut != null &&
                HardwareController.setSliderLaunchShortcut(
                    shortcut.packageName,
                    shortcut.shortcutId
                )
            0 -> HardwareController.disableSliderSystemHandling()
            else -> false
        }
        if (applied) {
            saveMagicKeyAppPackageStorage(
                context,
                pkg.takeIf { mode == 16 }
            )
            saveMagicKeyShortcutStorage(
                context,
                shortcut.takeIf { mode == 17 }
            )
        }
    }

    private fun chargingLed(context: Context, enabledKey: String, effectKey: String,
        colorKey: String, enabled: Boolean, effect: String, color: Int): LedState {
        val p = ChargingLedState.readProfile(context, enabledKey, effectKey, colorKey,
            enabled, effect, color)
        return LedState(p.enabled, p.effect, p.color)
    }

    private fun callLed(context: Context, enabledKey: String, effectKey: String,
        colorKey: String, enabled: Boolean, effect: String, color: Int) =
        CallLightingState.readLed(context, enabledKey, effectKey, colorKey, enabled, effect, color)

    private fun saveChargingLed(context: Context, enabledKey: String, effectKey: String,
        colorKey: String, state: LedState) = ChargingLedState.saveProfile(
        context, enabledKey, effectKey, colorKey, state.enabled, state.effect, state.color)

    private fun saveCallLed(context: Context, enabledKey: String, effectKey: String,
        colorKey: String, state: LedState) = CallLightingState.saveLed(
        context, enabledKey, effectKey, colorKey, state)
}
