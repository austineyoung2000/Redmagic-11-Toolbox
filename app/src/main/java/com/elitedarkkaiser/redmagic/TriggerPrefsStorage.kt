package com.elitedarkkaiser.redmagic

import android.content.Context

private const val TRIGGER_PREFS_NAME = "triggers"
private const val LEFT_TRIGGER_KEY = "left_trigger"
private const val RIGHT_TRIGGER_KEY = "right_trigger"
private const val INTENT_UNLOCK_RIGHT_TRIGGER_KEY = "intent_unlock_right_trigger"
private const val TRIGGERS_AUTO_START_KEY = "triggers_auto_start"
private const val SAFETY_MODE_KEY = "trigger_safety_mode"
private const val HOLD_DURATION_KEY = "trigger_hold_duration_ms"
private const val UNLOCK_TIMEOUT_KEY = "trigger_unlock_timeout_ms"
private const val BLOCK_ON_LOCK_SCREEN_KEY = "trigger_block_on_lock_screen"
private const val GAME_MODE_ONLY_KEY = "trigger_game_mode_only"
private const val LEFT_UNLOCKS_RIGHT_KEY = "trigger_left_unlocks_right"
private const val LEFT_UNLOCK_TAPS_KEY = "left_intent_unlock_tap_count"
private const val RIGHT_UNLOCK_TAPS_KEY = "intent_unlock_tap_count"
private const val TRIGGERS_DISABLED_UNTIL_RESTART_KEY =
    "triggers_disabled_until_restart"

data class TriggerPrefsSnapshot(
    val triggerEnabled: Boolean,
    val leftTriggerAction: String,
    val rightTriggerAction: String,
    val intentUnlockRightTrigger: Boolean,
    val triggersAutoStart: Boolean,
    val safetyMode: String = TriggerSafetyConfig.DEFAULT_MODE,
    val holdDurationMs: Int = TriggerSafetyConfig.DEFAULT_HOLD_MS,
    val unlockTimeoutMs: Long = TriggerSafetyConfig.DEFAULT_UNLOCK_TIMEOUT_MS,
    val blockOnLockScreen: Boolean = true,
    val gameModeOnly: Boolean = false,
    val leftUnlocksRight: Boolean = false,
    val leftUnlockTapCount: Int = 1,
    val rightUnlockTapCount: Int = 2
)

data class TriggerSafetyConfig(
    val mode: String = DEFAULT_MODE,
    val holdDurationMs: Int = DEFAULT_HOLD_MS,
    val unlockTimeoutMs: Long = DEFAULT_UNLOCK_TIMEOUT_MS,
    val blockOnLockScreen: Boolean = true,
    val gameModeOnly: Boolean = false,
    val leftUnlocksRight: Boolean = false,
    val leftUnlockTapCount: Int = 1,
    val rightUnlockTapCount: Int = 2
) {
    fun usesIntentUnlock(): Boolean {
        return mode == MODE_INTENT || mode == MODE_INTENT_HOLD
    }

    fun usesHold(): Boolean {
        return mode == MODE_HOLD || mode == MODE_INTENT_HOLD
    }

    companion object {
        const val MODE_OFF = "off"
        const val MODE_INTENT = "intent"
        const val MODE_HOLD = "hold"
        const val MODE_INTENT_HOLD = "intent_hold"
        const val DEFAULT_MODE = MODE_INTENT
        const val DEFAULT_HOLD_MS = 120
        const val DEFAULT_UNLOCK_TIMEOUT_MS = 2_500L

        val ALLOWED_MODES = setOf(
            MODE_OFF,
            MODE_INTENT,
            MODE_HOLD,
            MODE_INTENT_HOLD
        )
        val ALLOWED_HOLD_DURATIONS = setOf(80, 120, 180, 250)
        val ALLOWED_UNLOCK_TIMEOUTS = setOf(
            1_500L,
            2_500L,
            5_000L,
            10_000L
        )
    }
}

fun readTriggerSafetyConfig(
    context: Context
): TriggerSafetyConfig {
    val prefs = context.getSharedPreferences(
        TRIGGER_PREFS_NAME,
        Context.MODE_PRIVATE
    )

    val legacyIntentEnabled = prefs.getBoolean(
        INTENT_UNLOCK_RIGHT_TRIGGER_KEY,
        true
    )
    val defaultMode = if (legacyIntentEnabled) {
        TriggerSafetyConfig.MODE_INTENT
    } else {
        TriggerSafetyConfig.MODE_OFF
    }

    val mode = prefs.getString(
        SAFETY_MODE_KEY,
        defaultMode
    ).orEmpty().takeIf {
        it in TriggerSafetyConfig.ALLOWED_MODES
    } ?: defaultMode

    val holdDuration = prefs.getInt(
        HOLD_DURATION_KEY,
        TriggerSafetyConfig.DEFAULT_HOLD_MS
    ).takeIf {
        it in TriggerSafetyConfig.ALLOWED_HOLD_DURATIONS
    } ?: TriggerSafetyConfig.DEFAULT_HOLD_MS

    val unlockTimeout = prefs.getLong(
        UNLOCK_TIMEOUT_KEY,
        TriggerSafetyConfig.DEFAULT_UNLOCK_TIMEOUT_MS
    ).takeIf {
        it in TriggerSafetyConfig.ALLOWED_UNLOCK_TIMEOUTS
    } ?: TriggerSafetyConfig.DEFAULT_UNLOCK_TIMEOUT_MS

    return TriggerSafetyConfig(
        mode = mode,
        holdDurationMs = holdDuration,
        unlockTimeoutMs = unlockTimeout,
        blockOnLockScreen = prefs.getBoolean(
            BLOCK_ON_LOCK_SCREEN_KEY,
            true
        ),
        gameModeOnly = prefs.getBoolean(
            GAME_MODE_ONLY_KEY,
            false
        ),
        leftUnlocksRight = prefs.getBoolean(
            LEFT_UNLOCKS_RIGHT_KEY,
            false
        ),
        leftUnlockTapCount = prefs.getInt(
            LEFT_UNLOCK_TAPS_KEY,
            1
        ).coerceIn(1, 4),
        rightUnlockTapCount = prefs.getInt(
            RIGHT_UNLOCK_TAPS_KEY,
            2
        ).coerceIn(2, 4)
    )
}

fun saveTriggerSafetyConfig(
    context: Context,
    config: TriggerSafetyConfig
) {
    val safeMode = config.mode.takeIf {
        it in TriggerSafetyConfig.ALLOWED_MODES
    } ?: TriggerSafetyConfig.DEFAULT_MODE
    val safeHold = config.holdDurationMs.takeIf {
        it in TriggerSafetyConfig.ALLOWED_HOLD_DURATIONS
    } ?: TriggerSafetyConfig.DEFAULT_HOLD_MS
    val safeTimeout = config.unlockTimeoutMs.takeIf {
        it in TriggerSafetyConfig.ALLOWED_UNLOCK_TIMEOUTS
    } ?: TriggerSafetyConfig.DEFAULT_UNLOCK_TIMEOUT_MS

    context.getSharedPreferences(
        TRIGGER_PREFS_NAME,
        Context.MODE_PRIVATE
    ).edit()
        .putString(SAFETY_MODE_KEY, safeMode)
        .putInt(HOLD_DURATION_KEY, safeHold)
        .putLong(UNLOCK_TIMEOUT_KEY, safeTimeout)
        .putBoolean(
            BLOCK_ON_LOCK_SCREEN_KEY,
            config.blockOnLockScreen
        )
        .putBoolean(GAME_MODE_ONLY_KEY, config.gameModeOnly)
        .putBoolean(
            LEFT_UNLOCKS_RIGHT_KEY,
            config.leftUnlocksRight
        )
        .putInt(
            LEFT_UNLOCK_TAPS_KEY,
            config.leftUnlockTapCount.coerceIn(1, 4)
        )
        .putInt(
            RIGHT_UNLOCK_TAPS_KEY,
            config.rightUnlockTapCount.coerceIn(2, 4)
        )
        .putBoolean(
            INTENT_UNLOCK_RIGHT_TRIGGER_KEY,
            safeMode == TriggerSafetyConfig.MODE_INTENT ||
                safeMode == TriggerSafetyConfig.MODE_INTENT_HOLD
        )
        .apply()
}

fun triggerSafetySummaryStorage(context: Context): String {
    val config = readTriggerSafetyConfig(context)
    val modeLabel = when (config.mode) {
        TriggerSafetyConfig.MODE_OFF -> "Off"
        TriggerSafetyConfig.MODE_INTENT -> "Intent Unlock"
        TriggerSafetyConfig.MODE_HOLD ->
            "Hold ${config.holdDurationMs}ms"
        TriggerSafetyConfig.MODE_INTENT_HOLD ->
            "Intent + Hold ${config.holdDurationMs}ms"
        else -> "Intent Unlock"
    }
    val restrictions = buildList {
        if (config.blockOnLockScreen) {
            add("lock screen blocked")
        }
        if (config.gameModeOnly) {
            add("Game Mode only")
        }
    }

    return if (restrictions.isEmpty()) {
        "Current mode: $modeLabel"
    } else {
        "Current mode: $modeLabel • " +
            restrictions.joinToString(" • ")
    }
}

fun readTriggerPrefsSnapshot(context: Context): TriggerPrefsSnapshot {
    val prefs = context.getSharedPreferences(TRIGGER_PREFS_NAME, Context.MODE_PRIVATE)
    val autoStart = prefs.getBoolean(TRIGGERS_AUTO_START_KEY, false)
    val safety = readTriggerSafetyConfig(context)
    return TriggerPrefsSnapshot(
        triggerEnabled = autoStart,
        leftTriggerAction = prefs.getString(LEFT_TRIGGER_KEY, "NONE") ?: "NONE",
        rightTriggerAction = prefs.getString(RIGHT_TRIGGER_KEY, "NONE") ?: "NONE",
        intentUnlockRightTrigger = prefs.getBoolean(INTENT_UNLOCK_RIGHT_TRIGGER_KEY, true),
        triggersAutoStart = autoStart,
        safetyMode = safety.mode,
        holdDurationMs = safety.holdDurationMs,
        unlockTimeoutMs = safety.unlockTimeoutMs,
        blockOnLockScreen = safety.blockOnLockScreen,
        gameModeOnly = safety.gameModeOnly,
        leftUnlocksRight = safety.leftUnlocksRight,
        leftUnlockTapCount = safety.leftUnlockTapCount,
        rightUnlockTapCount = safety.rightUnlockTapCount
    )
}

fun setTriggersAutoStartStorage(
    context: Context,
    enabled: Boolean
) {
    context.getSharedPreferences(
        TRIGGER_PREFS_NAME,
        Context.MODE_PRIVATE
    ).edit()
        .putBoolean(TRIGGERS_AUTO_START_KEY, enabled)
        .apply()
}

fun saveTriggerPrefsStorage(
    context: Context,
    profile: TriggerPrefsSnapshot
) {
    context.getSharedPreferences(TRIGGER_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(LEFT_TRIGGER_KEY, profile.leftTriggerAction)
        .putString(RIGHT_TRIGGER_KEY, profile.rightTriggerAction)
        .putBoolean(INTENT_UNLOCK_RIGHT_TRIGGER_KEY, profile.intentUnlockRightTrigger)
        .putBoolean(TRIGGERS_AUTO_START_KEY, profile.triggersAutoStart)
        .apply()

    saveTriggerSafetyConfig(
        context,
        TriggerSafetyConfig(
            mode = profile.safetyMode,
            holdDurationMs = profile.holdDurationMs,
            unlockTimeoutMs = profile.unlockTimeoutMs,
            blockOnLockScreen = profile.blockOnLockScreen,
            gameModeOnly = profile.gameModeOnly,
            leftUnlocksRight = profile.leftUnlocksRight,
            leftUnlockTapCount = profile.leftUnlockTapCount,
            rightUnlockTapCount = profile.rightUnlockTapCount
        )
    )
}

fun initDefaultTriggerMappingsStorage(context: Context) {
    val prefs = context.getSharedPreferences(TRIGGER_PREFS_NAME, Context.MODE_PRIVATE)
    if (!prefs.contains(LEFT_TRIGGER_KEY)) {
        prefs.edit()
            .putString(LEFT_TRIGGER_KEY, "VOL_DOWN")
            .putString(RIGHT_TRIGGER_KEY, "VOL_UP")
            .apply()
    }
}

fun triggersDisabledUntilRestartStorage(
    context: Context
): Boolean {
    return context.getSharedPreferences(
        TRIGGER_PREFS_NAME,
        Context.MODE_PRIVATE
    ).getBoolean(
        TRIGGERS_DISABLED_UNTIL_RESTART_KEY,
        false
    )
}

fun setTriggersDisabledUntilRestartStorage(
    context: Context,
    disabled: Boolean
) {
    context.getSharedPreferences(
        TRIGGER_PREFS_NAME,
        Context.MODE_PRIVATE
    ).edit()
        .putBoolean(
            TRIGGERS_DISABLED_UNTIL_RESTART_KEY,
            disabled
        )
        .apply()
}
