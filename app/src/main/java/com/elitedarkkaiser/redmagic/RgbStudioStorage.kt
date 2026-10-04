package com.elitedarkkaiser.redmagic

import android.content.Context
import com.elitedarkkaiser.redmagic.storage.AppPrefs

object RgbStudioStorage {
    private const val ENABLED = "rgb_studio_enabled"
    private const val SYNC_ZONES = "rgb_studio_sync_zones"
    private const val EFFECT = "rgb_studio_effect"
    private const val COLORS = "rgb_studio_colors"
    private const val SPLIT_TRIGGERS = "rgb_studio_split_triggers"
    private const val TOP_COLORS = "rgb_studio_top_trigger_colors"
    private const val BOTTOM_COLORS = "rgb_studio_bottom_trigger_colors"
    private const val LOGO_SPEED_MS = "rgb_studio_logo_speed_ms"
    private const val SHOULDER_SPEED_MS = "rgb_studio_shoulder_speed_ms"
    private const val FAN_SPEED_MS = "rgb_studio_fan_speed_ms"
    private const val SCREEN_OFF_TIMEOUT_MINUTES =
        "rgb_studio_screen_off_timeout_minutes"

    private val allowedColors = RgbStudioState.DEFAULT_COLORS.toSet()
    private val allowedEffects = setOf(
        "steady",
        "breathe",
        "flashing",
        "rapid"
    )
    private val allowedTimeouts = setOf(0, 1, 5, 15, 30)

    @Synchronized
    fun read(context: Context): RgbStudioState {
        val prefs = context.getSharedPreferences(
            AppPrefs.PREFS_NAME,
            Context.MODE_PRIVATE
        )

        val colors = prefs.getString(
            COLORS,
            RgbStudioState.DEFAULT_COLORS.joinToString(",")
        )
            .orEmpty()
            .split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in allowedColors }
            .distinct()
            .ifEmpty { RgbStudioState.DEFAULT_COLORS }

        return sanitize(
            RgbStudioState(
                enabled = prefs.getBoolean(ENABLED, false),
                logoBrightness = prefs.getInt("rgb_studio_logo_brightness", 255),
                logoEnabled = prefs.getBoolean("rgb_studio_logo_enabled", true),
                barEnabled = prefs.getBoolean("rgb_studio_bar_enabled", true),
                barColors = prefs.getString("rgb_studio_bar_colors", null)?.split(',')?.mapNotNull { it.toIntOrNull() } ?: colors,
                barBrightness = prefs.getInt("rgb_studio_bar_brightness", prefs.getInt("rgb_studio_logo_brightness", 255)),
                barSpeedMs = prefs.getLong("rgb_studio_bar_speed_ms", prefs.getLong(LOGO_SPEED_MS, RgbStudioState.DEFAULT_SPEED_MS)),
                shoulderBrightness = prefs.getInt("rgb_studio_shoulder_brightness", 255),
                fanBrightness = prefs.getInt("rgb_studio_fan_brightness", 255),

                syncZones = prefs.getBoolean(SYNC_ZONES, true),
                effect = prefs.getString(EFFECT, "steady") ?: "steady",
                colors = colors,
                splitTriggers = prefs.getBoolean(SPLIT_TRIGGERS, false),
                topTriggerColors = prefs.getString(TOP_COLORS, null)?.split(',')?.mapNotNull { it.toIntOrNull() } ?: colors,
                bottomTriggerColors = prefs.getString(BOTTOM_COLORS, null)?.split(',')?.mapNotNull { it.toIntOrNull() } ?: colors,
                logoSpeedMs = prefs.getLong(
                    LOGO_SPEED_MS,
                    RgbStudioState.DEFAULT_SPEED_MS
                ),
                shoulderSpeedMs = prefs.getLong(
                    SHOULDER_SPEED_MS,
                    RgbStudioState.DEFAULT_SPEED_MS
                ),
                fanSpeedMs = prefs.getLong(
                    FAN_SPEED_MS,
                    RgbStudioState.DEFAULT_SPEED_MS
                ),
                screenOffTimeoutMinutes = prefs.getInt(
                    SCREEN_OFF_TIMEOUT_MINUTES,
                    0
                )
            )
        )
    }

    @Synchronized
    fun save(context: Context, state: RgbStudioState) {
        val safe = sanitize(state)
        context.getSharedPreferences(
            AppPrefs.PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putBoolean(ENABLED, safe.enabled)
            .putInt("rgb_studio_logo_brightness", safe.logoBrightness)
            .putBoolean("rgb_studio_logo_enabled", safe.logoEnabled)
            .putBoolean("rgb_studio_bar_enabled", safe.barEnabled)
            .putString("rgb_studio_bar_colors", safe.barColors.joinToString(","))
            .putInt("rgb_studio_bar_brightness", safe.barBrightness)
            .putLong("rgb_studio_bar_speed_ms", safe.barSpeedMs)
            .putInt("rgb_studio_shoulder_brightness", safe.shoulderBrightness)
            .putInt("rgb_studio_fan_brightness", safe.fanBrightness)

            .putBoolean(SYNC_ZONES, safe.syncZones)
            .putString(EFFECT, safe.effect)
            .putString(COLORS, safe.colors.joinToString(","))
            .putBoolean(SPLIT_TRIGGERS, safe.splitTriggers)
            .putString(TOP_COLORS, safe.topTriggerColors.joinToString(","))
            .putString(BOTTOM_COLORS, safe.bottomTriggerColors.joinToString(","))
            .putLong(LOGO_SPEED_MS, safe.logoSpeedMs)
            .putLong(SHOULDER_SPEED_MS, safe.shoulderSpeedMs)
            .putLong(FAN_SPEED_MS, safe.fanSpeedMs)
            .putInt(
                SCREEN_OFF_TIMEOUT_MINUTES,
                safe.screenOffTimeoutMinutes
            )
            .apply()
    }

    @Synchronized
    fun setEnabled(context: Context, enabled: Boolean) {
        val current = read(context)
        save(context, current.copy(enabled = enabled))
    }

    @Synchronized
    fun isEnabled(context: Context): Boolean {
        return read(context).enabled
    }

    @Synchronized
    fun summary(context: Context): String {
        val state = read(context)
        if (!state.enabled) return "Off"

        val mode = if (state.syncZones) "Synchronized" else "Per-zone"
        return "$mode${if (state.splitTriggers) " • Split triggers" else ""} • ${state.colors.size} colors • ${effectLabel(state.effect)}"
    }

    private fun sanitize(state: RgbStudioState): RgbStudioState {
        val colors = state.colors
            .filter { it in allowedColors }
            .distinct()
            .ifEmpty { RgbStudioState.DEFAULT_COLORS }

        return state.copy(
            logoBrightness = state.logoBrightness.coerceIn(32, 255),
            barBrightness = state.barBrightness.coerceIn(32, 255),
            barColors = state.barColors.filter { it in allowedColors }.distinct().ifEmpty { colors },
            barSpeedMs = state.barSpeedMs.coerceIn(500L, 6_000L),
            shoulderBrightness = state.shoulderBrightness.coerceIn(32, 255),
            fanBrightness = state.fanBrightness.coerceIn(32, 255),

            topTriggerColors = state.topTriggerColors.filter { it in allowedColors }.distinct().ifEmpty { colors },
            bottomTriggerColors = state.bottomTriggerColors.filter { it in allowedColors }.distinct().ifEmpty { colors },
            effect = state.effect.takeIf { it in allowedEffects } ?: "steady",
            colors = colors,
            logoSpeedMs = state.logoSpeedMs.coerceIn(500L, 6_000L),
            shoulderSpeedMs = state.shoulderSpeedMs.coerceIn(500L, 6_000L),
            fanSpeedMs = state.fanSpeedMs.coerceIn(500L, 6_000L),
            screenOffTimeoutMinutes = state.screenOffTimeoutMinutes
                .takeIf { it in allowedTimeouts }
                ?: 0
        )
    }

    private fun effectLabel(effect: String): String {
        return when (effect) {
            "breathe" -> "Breathe"
            "flashing" -> "Flashing"
            "rapid" -> "Rapid Blink"
            else -> "Steady"
        }
    }
}
