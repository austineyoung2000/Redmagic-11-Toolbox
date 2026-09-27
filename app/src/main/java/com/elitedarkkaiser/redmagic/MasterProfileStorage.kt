package com.elitedarkkaiser.redmagic

import android.content.Context
import com.elitedarkkaiser.redmagic.state.LedState
import org.json.JSONArray
import org.json.JSONObject

object MasterProfileStorage {
    const val CURRENT_SCHEMA_VERSION = 11
    private const val PREFS = "master_profiles"
    private const val KEY = "profiles"
    private const val LAST_APPLIED_KEY = "last_applied_profile"
    private const val BACKUP_FORMAT = "redmagic-control-center-backup"

    data class ImportResult(
        val savedProfileCount: Int,
        val appliedCurrentSettings: Boolean
    )

    fun loadProfiles(context: Context): MutableList<MasterProfile> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return buildList {
            for (index in 0 until array.length()) {
                runCatching {
                    array.getJSONObject(index).toMasterProfile()
                }.getOrNull()?.let(::add)
            }
        }.toMutableList()
    }

    fun saveProfiles(context: Context, profiles: List<MasterProfile>) {
        val array = JSONArray()
        profiles.forEach { array.put(it.toJson()) }
        check(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, array.toString()).commit()
        ) { "Unable to save master profiles" }
    }

    fun upsertProfile(context: Context, profile: MasterProfile) {
        val profiles = loadProfiles(context)
        val index = profiles.indexOfFirst { it.name == profile.name }
        if (index >= 0) profiles[index] = profile else profiles.add(profile)
        saveProfiles(context, profiles)
    }

    fun deleteProfile(context: Context, name: String) {
        saveProfiles(context, loadProfiles(context).filterNot { it.name == name })
        AutomationRulesStorage.clearProfileReferences(
            context,
            name
        )
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(LAST_APPLIED_KEY, null) == name) {
            prefs.edit().remove(LAST_APPLIED_KEY).apply()
        }
    }

    fun markProfileApplied(context: Context, name: String) {
        if (loadProfiles(context).none { it.name == name }) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(LAST_APPLIED_KEY, name)
            .apply()
    }

    fun lastAppliedProfile(context: Context): MasterProfile? {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(LAST_APPLIED_KEY, null)
            ?: return null
        return loadProfiles(context).firstOrNull { it.name == name }
    }

    fun createBackupJson(context: Context): String {
        val saved = JSONArray()
        loadProfiles(context).forEach { saved.put(it.toJson()) }
        return JSONObject().apply {
            put("format", BACKUP_FORMAT)
            put("schemaVersion", CURRENT_SCHEMA_VERSION)
            put("exportedAtEpochMs", System.currentTimeMillis())
            put("currentSettings", MasterProfileActions.captureCurrent(
                context,
                "Current device settings"
            ).toJson())
            put("savedProfiles", saved)
            put(
                "automationRules",
                AutomationRulesStorage.toJson(context)
            )
        }.toString(2)
    }

    fun importBackup(context: Context, raw: String): ImportResult {
        require(raw.length <= 5_000_000) { "Backup is larger than 5 MB" }
        val root = JSONObject(raw)
        require(root.optString("format") == BACKUP_FORMAT) {
            "This is not a RedMagic Control Center backup"
        }
        require(root.optInt("schemaVersion", 0) in 1..CURRENT_SCHEMA_VERSION) {
            "Unsupported backup version"
        }

        val imported = mutableListOf<MasterProfile>()
        val array = root.optJSONArray("savedProfiles") ?: JSONArray()
        for (index in 0 until array.length()) {
            imported += array.getJSONObject(index).toMasterProfile()
        }

        val merged = loadProfiles(context).associateBy { it.name }.toMutableMap()
        imported.forEach { merged[it.name] = it }
        saveProfiles(context, merged.values.sortedBy { it.name.lowercase() })

        root.optJSONObject("automationRules")?.let {
            AutomationRulesStorage.restoreFromJson(
                context,
                it
            )
        }

        val current = root.optJSONObject("currentSettings")?.toMasterProfile()
        current?.let { MasterProfileActions.applyProfile(context, it) }
        return ImportResult(imported.size, current != null)
    }

    private fun MasterProfile.toJson() = JSONObject().apply {
        put("schemaVersion", schemaVersion)
        put("name", name)
        put("hardware", hardware.toJson())
        put("triggers", triggers.toJson())
        put("pumpExperimentalAccepted", pumpExperimentalAccepted)
        put("gameMode", gameMode.toJson())
        put("gamePackages", JSONArray(gamePackages.toList()))
        put("perGameProfiles", JSONObject(perGameProfiles))
        put("chargingEnabled", chargingEnabled)
        put("chargingFanLed", chargingFanLed.toJson())
        put("chargingLogoLed", chargingLogoLed.toJson())
        put("chargingShoulderLed", chargingShoulderLed.toJson())
        put("callLightingEnabled", callLightingEnabled)
        put("pauseFanDuringCalls", pauseFanDuringCalls)
        put("incomingCallFanLed", incomingCallFanLed.toJson())
        put("incomingCallLogoLed", incomingCallLogoLed.toJson())
        put("incomingCallShoulderLed", incomingCallShoulderLed.toJson())
        put("connectedCallFanLed", connectedCallFanLed.toJson())
        put("connectedCallLogoLed", connectedCallLogoLed.toJson())
        put("connectedCallShoulderLed", connectedCallShoulderLed.toJson())
        put("realtimePreviewEnabled", realtimePreviewEnabled)
        put("rgbStudio", rgbStudio.toJson())
        put("useFahrenheit", useFahrenheit)
        put("magicKeyMode", magicKeyMode)
        put("magicKeyAppPackage", magicKeyAppPackage ?: JSONObject.NULL)
        put(
            "magicKeyShortcut",
            magicKeyShortcut?.toJson() ?: JSONObject.NULL
        )
        put("sliderDualApp", sliderDualApp.toJson())
        put("hapticFeedback", hapticFeedback.toJson())
        nativeTgkProfilesJson?.let {
            put("nativeTgkProfiles", JSONObject(it))
        }
        chargeSeparationEnabled?.let {
            put("chargeSeparationEnabled", it)
        }
        refreshRateProfilesJson?.let {
            put("refreshRateProfiles", JSONObject(it))
        }
        touchTuningProfilesJson?.let {
            put("touchTuningProfiles", JSONObject(it))
        }
        performanceModeProfilesJson?.let {
            put("performanceModeProfiles", JSONObject(it))
        }
    }

    private fun JSONObject.toMasterProfile(): MasterProfile {
        val version = optInt("schemaVersion", 1)
        val hardwareObject = getJSONObject("hardware")
        var hardware = hardwareObject.toHardwareProfile()

        // Version 1 stored canonical fan and pump values twice. Preserve the
        // values that its apply path actually used.
        if (version == 1) {
            optJSONObject("pump")?.let { pump ->
                hardware = hardware.copy(
                    pumpEnabled = pump.optBoolean("enabled", hardware.pumpEnabled),
                    pumpProfile = pump.optString("profile", hardware.pumpProfile),
                    autoPumpEnabled = pump.optBoolean("autoEnabled", hardware.autoPumpEnabled)
                )
            }
            hardware = hardware.copy(
                autoFanEnabled = optBoolean("autoFanEnabled", hardware.autoFanEnabled),
                fanCurveMode = optString("selectedFanCurve", hardware.fanCurveMode)
            )
        }

        val triggerObject = optJSONObject("triggers") ?: hardwareObject
        return MasterProfile(
            name = optString("name", "Imported profile").ifBlank { "Imported profile" },
            schemaVersion = version,
            hardware = hardware,
            triggers = triggerObject.toTriggerPrefsSnapshot(),
            pumpExperimentalAccepted = optBoolean(
                "pumpExperimentalAccepted",
                optJSONObject("pump")?.optBoolean("experimentalAccepted", false) ?: false
            ),
            gameMode = optJSONObject("gameMode")?.toGameModeProfile()
                ?: defaultGameModeProfile(),
            gamePackages = (optJSONArray("gamePackages") ?: JSONArray()).toStringSet(),
            perGameProfiles = optJSONObject("perGameProfiles").toStringMap(),
            chargingEnabled = optBoolean("chargingEnabled", false),
            chargingFanLed = optJSONObject("chargingFanLed").toLedState(true, "steady", 5),
            chargingLogoLed = optJSONObject("chargingLogoLed").toLedState(true, "steady", 1),
            chargingShoulderLed = optJSONObject("chargingShoulderLed").toLedState(true, "breathe", 8),
            callLightingEnabled = optBoolean("callLightingEnabled", false),
            pauseFanDuringCalls = optBoolean("pauseFanDuringCalls", false),
            incomingCallFanLed = optJSONObject("incomingCallFanLed").toLedState(true, "flashing", 5),
            incomingCallLogoLed = optJSONObject("incomingCallLogoLed").toLedState(true, "flashing", 1),
            incomingCallShoulderLed = optJSONObject("incomingCallShoulderLed").toLedState(true, "flashing", 8),
            connectedCallFanLed = optJSONObject("connectedCallFanLed").toLedState(true, "steady", 5),
            connectedCallLogoLed = optJSONObject("connectedCallLogoLed").toLedState(true, "steady", 1),
            connectedCallShoulderLed = optJSONObject("connectedCallShoulderLed").toLedState(true, "steady", 8),
            realtimePreviewEnabled = optBoolean("realtimePreviewEnabled", true),
            rgbStudio = optJSONObject("rgbStudio")?.toRgbStudioState() ?: RgbStudioState(),
            useFahrenheit = optBoolean("useFahrenheit", true),
            magicKeyMode = optInt("magicKeyMode", -1),
            magicKeyAppPackage = optString("magicKeyAppPackage")
                .takeIf { it.isNotBlank() && it != "null" },
            magicKeyShortcut = if (version >= 5) {
                optJSONObject("magicKeyShortcut")
                    .toMagicKeyShortcutTarget()
            } else {
                null
            },
            sliderDualApp = if (version >= 3) {
                optJSONObject("sliderDualApp")
                    .toSliderDualAppConfig()
            } else {
                SliderDualAppConfig()
            },
            hapticFeedback = if (version >= 4) {
                optJSONObject("hapticFeedback")
                    .toHapticFeedbackConfig()
            } else {
                HapticFeedbackConfig()
            },
            nativeTgkProfilesJson = if (version >= 7) {
                optJSONObject("nativeTgkProfiles")?.toString()
            } else {
                null
            },
            chargeSeparationEnabled = if (
                version >= 8 &&
                has("chargeSeparationEnabled")
            ) {
                optBoolean("chargeSeparationEnabled")
            } else {
                null
            },
            refreshRateProfilesJson = if (version >= 9) {
                optJSONObject("refreshRateProfiles")?.toString()
            } else {
                null
            },
            touchTuningProfilesJson = if (version >= 10) {
                optJSONObject("touchTuningProfiles")?.toString()
            } else {
                null
            },
            performanceModeProfilesJson = if (version >= 11) {
                optJSONObject("performanceModeProfiles")?.toString()
            } else {
                null
            }
        )
    }

    private fun MagicKeyShortcutTarget.toJson() =
        JSONObject().apply {
            put("packageName", packageName)
            put("shortcutId", shortcutId)
            put("label", label)
        }

    private fun JSONObject?.toMagicKeyShortcutTarget():
        MagicKeyShortcutTarget? {
        if (this == null) return null

        val packageName = optString("packageName")
        val shortcutId = optString("shortcutId")
        if (packageName.isBlank() || shortcutId.isBlank()) {
            return null
        }

        return MagicKeyShortcutTarget(
            packageName = packageName,
            shortcutId = shortcutId,
            label = optString("label", shortcutId)
                .ifBlank { shortcutId }
        )
    }

    private fun HapticFeedbackConfig.toJson() =
        JSONObject().apply {
            put("enabled", enabled)
            put("strength", HapticFeedback.Strength.fromKey(
                strength
            ).key)
        }

    private fun JSONObject?.toHapticFeedbackConfig():
        HapticFeedbackConfig {
        if (this == null) return HapticFeedbackConfig()

        return HapticFeedbackConfig(
            enabled = optBoolean("enabled", false),
            strength = HapticFeedback.Strength.fromKey(
                optString(
                    "strength",
                    HapticFeedback.Strength.MEDIUM.key
                )
            ).key
        )
    }

    private fun SliderDualAppConfig.toJson() =
        JSONObject().apply {
            put("enabled", enabled)
            put("defaultUpPackage", defaultUpPackage ?: JSONObject.NULL)
            put(
                "defaultDownPackage",
                defaultDownPackage ?: JSONObject.NULL
            )
            put("scheduleEnabled", scheduleEnabled)
            put("scheduleStartMinutes", scheduleStartMinutes)
            put("scheduleEndMinutes", scheduleEndMinutes)
            put(
                "scheduledUpPackage",
                scheduledUpPackage ?: JSONObject.NULL
            )
            put(
                "scheduledDownPackage",
                scheduledDownPackage ?: JSONObject.NULL
            )
        }

    private fun JSONObject?.toSliderDualAppConfig():
        SliderDualAppConfig {
        if (this == null) return SliderDualAppConfig()

        fun optionalPackage(key: String): String? {
            return optString(key)
                .takeIf { it.isNotBlank() && it != "null" }
        }

        val config = SliderDualAppConfig(
            enabled = optBoolean("enabled", false),
            defaultUpPackage = optionalPackage("defaultUpPackage"),
            defaultDownPackage = optionalPackage(
                "defaultDownPackage"
            ),
            scheduleEnabled = optBoolean(
                "scheduleEnabled",
                false
            ),
            scheduleStartMinutes = optInt(
                "scheduleStartMinutes",
                22 * 60
            ).coerceIn(0, 1439),
            scheduleEndMinutes = optInt(
                "scheduleEndMinutes",
                7 * 60
            ).coerceIn(0, 1439),
            scheduledUpPackage = optionalPackage(
                "scheduledUpPackage"
            ),
            scheduledDownPackage = optionalPackage(
                "scheduledDownPackage"
            )
        )

        return config.copy(
            enabled = config.enabled && config.isComplete()
        )
    }

    private fun HardwareSettingsSnapshot.toJson() = JSONObject().apply {
        put("fanEnabled", fanEnabled); put("fanLevel", fanLevel)
        put("autoFanEnabled", autoFanEnabled); put("fanCurveMode", fanCurveMode)
        put("pumpEnabled", pumpEnabled); put("pumpProfile", pumpProfile)
        put("autoPumpEnabled", autoPumpEnabled); put("fanLedEnabled", fanLedEnabled)
        put("fanLedEffect", fanLedEffect); put("fanLedColor", fanLedColor)
        put("logoLedEnabled", logoLedEnabled); put("logoLedEffect", logoLedEffect)
        put("logoLedColor", logoLedColor); put("shoulderLedEnabled", shoulderLedEnabled)
        put("shoulderLedEffect", shoulderLedEffect); put("shoulderLedColor", shoulderLedColor)
    }

    private fun JSONObject.toHardwareProfile() = HardwareSettingsSnapshot(
        fanEnabled = optBoolean("fanEnabled", false),
        fanLevel = optInt("fanLevel", 0).coerceIn(0, 5),
        autoFanEnabled = optBoolean("autoFanEnabled", false),
        fanCurveMode = optString("fanCurveMode", "balanced"),
        pumpEnabled = optBoolean("pumpEnabled", false),
        pumpProfile = optString("pumpProfile", "quick"),
        autoPumpEnabled = optBoolean("autoPumpEnabled", false),
        fanLedEnabled = optBoolean("fanLedEnabled", false),
        fanLedEffect = optString("fanLedEffect", "steady"),
        fanLedColor = optInt("fanLedColor", 5),
        logoLedEnabled = optBoolean("logoLedEnabled", true),
        logoLedEffect = optString("logoLedEffect", "steady"),
        logoLedColor = optInt("logoLedColor", 1),
        shoulderLedEnabled = optBoolean("shoulderLedEnabled", true),
        shoulderLedEffect = optString("shoulderLedEffect", "breathe"),
        shoulderLedColor = optInt("shoulderLedColor", 8)
    )

    private fun TriggerPrefsSnapshot.toJson() = JSONObject().apply {
        put("triggerEnabled", triggerEnabled); put("leftTriggerAction", leftTriggerAction)
        put("rightTriggerAction", rightTriggerAction)
        put("intentUnlockRightTrigger", intentUnlockRightTrigger)
        put("triggersAutoStart", triggersAutoStart)
        put("safetyMode", safetyMode)
        put("holdDurationMs", holdDurationMs)
        put("unlockTimeoutMs", unlockTimeoutMs)
        put("blockOnLockScreen", blockOnLockScreen)
        put("gameModeOnly", gameModeOnly)
        put("leftUnlocksRight", leftUnlocksRight)
        put("leftUnlockTapCount", leftUnlockTapCount)
        put("rightUnlockTapCount", rightUnlockTapCount)
    }

    private fun JSONObject.toTriggerPrefsSnapshot(): TriggerPrefsSnapshot {
        val legacyIntent =
            optBoolean("intentUnlockRightTrigger", true)
        val defaultMode = if (legacyIntent) {
            TriggerSafetyConfig.MODE_INTENT
        } else {
            TriggerSafetyConfig.MODE_OFF
        }
        val safetyMode = optString(
            "safetyMode",
            defaultMode
        ).takeIf {
            it in TriggerSafetyConfig.ALLOWED_MODES
        } ?: defaultMode
        val holdDuration = optInt(
            "holdDurationMs",
            TriggerSafetyConfig.DEFAULT_HOLD_MS
        ).takeIf {
            it in TriggerSafetyConfig.ALLOWED_HOLD_DURATIONS
        } ?: TriggerSafetyConfig.DEFAULT_HOLD_MS
        val unlockTimeout = optLong(
            "unlockTimeoutMs",
            TriggerSafetyConfig.DEFAULT_UNLOCK_TIMEOUT_MS
        ).takeIf {
            it in TriggerSafetyConfig.ALLOWED_UNLOCK_TIMEOUTS
        } ?: TriggerSafetyConfig.DEFAULT_UNLOCK_TIMEOUT_MS

        return TriggerPrefsSnapshot(
            triggerEnabled = optBoolean(
                "triggerEnabled",
                optBoolean("triggersAutoStart", false)
            ),
            leftTriggerAction =
                optString("leftTriggerAction", "NONE"),
            rightTriggerAction =
                optString("rightTriggerAction", "NONE"),
            intentUnlockRightTrigger = legacyIntent,
            triggersAutoStart =
                optBoolean("triggersAutoStart", false),
            safetyMode = safetyMode,
            holdDurationMs = holdDuration,
            unlockTimeoutMs = unlockTimeout,
            blockOnLockScreen =
                optBoolean("blockOnLockScreen", true),
            gameModeOnly = optBoolean("gameModeOnly", false),
            leftUnlocksRight =
                optBoolean("leftUnlocksRight", false),
            leftUnlockTapCount =
                optInt("leftUnlockTapCount", 1)
                    .coerceIn(1, 4),
            rightUnlockTapCount =
                optInt("rightUnlockTapCount", 2)
                    .coerceIn(2, 4)
        )
    }

    private fun GameModeProfile.toJson() = JSONObject().apply {
        put("fanEnabled", fanEnabled); put("fanLevel", fanLevel)
        put("pumpEnabled", pumpEnabled); put("pumpProfile", pumpProfile)
        put("fanLedEnabled", fanLedEnabled); put("fanLedEffect", fanLedEffect)
        put("fanLedColor", fanLedColor); put("logoLedEnabled", logoLedEnabled)
        put("logoLedEffect", logoLedEffect); put("logoLedColor", logoLedColor)
        put("shoulderLedEnabled", shoulderLedEnabled)
        put("shoulderLedEffect", shoulderLedEffect); put("shoulderLedColor", shoulderLedColor)
    }

    private fun JSONObject.toGameModeProfile() = GameModeProfile(
        fanEnabled = optBoolean("fanEnabled", true), fanLevel = optInt("fanLevel", 3),
        pumpEnabled = optBoolean("pumpEnabled", false), pumpProfile = optString("pumpProfile", "quick"),
        fanLedEnabled = optBoolean("fanLedEnabled", true), fanLedEffect = optString("fanLedEffect", "steady"),
        fanLedColor = optInt("fanLedColor", 5), logoLedEnabled = optBoolean("logoLedEnabled", true),
        logoLedEffect = optString("logoLedEffect", "steady"), logoLedColor = optInt("logoLedColor", 1),
        shoulderLedEnabled = optBoolean("shoulderLedEnabled", true),
        shoulderLedEffect = optString("shoulderLedEffect", "breathe"), shoulderLedColor = optInt("shoulderLedColor", 8)
    )

    private fun defaultGameModeProfile() = JSONObject().toGameModeProfile()

    private fun LedState.toJson() = JSONObject().apply {
        put("enabled", enabled); put("effect", effect); put("color", color)
    }

    private fun JSONObject?.toLedState(enabled: Boolean, effect: String, color: Int) = LedState(
        this?.optBoolean("enabled", enabled) ?: enabled,
        this?.optString("effect", effect) ?: effect,
        this?.optInt("color", color) ?: color
    )

    private fun RgbStudioState.toJson() = JSONObject().apply {
        put("enabled", enabled); put("syncZones", syncZones); put("effect", effect)
        put("colors", JSONArray(colors)); put("logoSpeedMs", logoSpeedMs)
        put("shoulderSpeedMs", shoulderSpeedMs); put("fanSpeedMs", fanSpeedMs)
        put("screenOffTimeoutMinutes", screenOffTimeoutMinutes)
    }

    private fun JSONObject.toRgbStudioState() = RgbStudioState(
        enabled = optBoolean("enabled", false), syncZones = optBoolean("syncZones", true),
        effect = optString("effect", "steady"),
        colors = (optJSONArray("colors") ?: JSONArray()).toIntList().ifEmpty { RgbStudioState.DEFAULT_COLORS },
        logoSpeedMs = optLong("logoSpeedMs", RgbStudioState.DEFAULT_SPEED_MS),
        shoulderSpeedMs = optLong("shoulderSpeedMs", RgbStudioState.DEFAULT_SPEED_MS),
        fanSpeedMs = optLong("fanSpeedMs", RgbStudioState.DEFAULT_SPEED_MS),
        screenOffTimeoutMinutes = optInt("screenOffTimeoutMinutes", 0)
    )

    private fun JSONArray.toStringSet() = buildSet {
        for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(::add)
    }

    private fun JSONArray.toIntList() = buildList {
        for (index in 0 until length()) add(optInt(index))
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        return keys().asSequence().mapNotNull { key ->
            optString(key).takeIf { value -> value.isNotBlank() }?.let { key to it }
        }.toMap()
    }
}
