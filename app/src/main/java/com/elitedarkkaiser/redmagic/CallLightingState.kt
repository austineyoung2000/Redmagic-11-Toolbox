package com.elitedarkkaiser.redmagic

import android.content.Context
import com.elitedarkkaiser.redmagic.state.LedState

internal object CallLightingState {
    const val PREFS = "redmagic_hw_controls_prefs"

    const val ENABLED_KEY = "call_lighting_enabled"
    const val ACTIVE_KEY = "call_lighting_active"
    const val PAUSE_FAN_DURING_CALLS_KEY = "call_lighting_pause_fan_during_calls"
    const val PRE_CALL_FAN_ENABLED_KEY = "call_lighting_pre_call_fan_enabled"
    const val PRE_CALL_FAN_LEVEL_KEY = "call_lighting_pre_call_fan_level"
    const val FAN_PAUSED_ACTIVE_KEY =
        "call_lighting_fan_pause_active"

    const val INCOMING_FAN_ENABLED_KEY = "call_incoming_fan_led_enabled"
    const val INCOMING_FAN_EFFECT_KEY = "call_incoming_fan_led_effect"
    const val INCOMING_FAN_COLOR_KEY = "call_incoming_fan_led_color"

    const val INCOMING_LOGO_ENABLED_KEY = "call_incoming_logo_led_enabled"
    const val INCOMING_LOGO_EFFECT_KEY = "call_incoming_logo_led_effect"
    const val INCOMING_LOGO_COLOR_KEY = "call_incoming_logo_led_color"

    const val INCOMING_SHOULDER_ENABLED_KEY = "call_incoming_shoulder_led_enabled"
    const val INCOMING_SHOULDER_EFFECT_KEY = "call_incoming_shoulder_led_effect"
    const val INCOMING_SHOULDER_COLOR_KEY = "call_incoming_shoulder_led_color"

    const val CONNECTED_FAN_ENABLED_KEY = "call_connected_fan_led_enabled"
    const val CONNECTED_FAN_EFFECT_KEY = "call_connected_fan_led_effect"
    const val CONNECTED_FAN_COLOR_KEY = "call_connected_fan_led_color"

    const val CONNECTED_LOGO_ENABLED_KEY = "call_connected_logo_led_enabled"
    const val CONNECTED_LOGO_EFFECT_KEY = "call_connected_logo_led_effect"
    const val CONNECTED_LOGO_COLOR_KEY = "call_connected_logo_led_color"

    const val CONNECTED_SHOULDER_ENABLED_KEY = "call_connected_shoulder_led_enabled"
    const val CONNECTED_SHOULDER_EFFECT_KEY = "call_connected_shoulder_led_effect"
    const val CONNECTED_SHOULDER_COLOR_KEY = "call_connected_shoulder_led_color"

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ENABLED_KEY, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ENABLED_KEY, enabled)
            .apply()
    }

    fun isActive(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ACTIVE_KEY, false)
    }

    fun setActive(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ACTIVE_KEY, active)
            .apply()
    }


    fun shouldPauseFanDuringCalls(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(PAUSE_FAN_DURING_CALLS_KEY, false)
    }

    fun setPauseFanDuringCalls(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PAUSE_FAN_DURING_CALLS_KEY, enabled)
            .apply()
    }

    fun savePreCallFanState(context: Context, enabled: Boolean, level: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PRE_CALL_FAN_ENABLED_KEY, enabled)
            .putInt(PRE_CALL_FAN_LEVEL_KEY, level)
            .apply()
    }

    fun setFanPausedForCall(
        context: Context,
        active: Boolean
    ) {
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).edit()
            .putBoolean(
                FAN_PAUSED_ACTIVE_KEY,
                active
            )
            .apply()
    }

    fun wasFanPausedForCall(
        context: Context
    ): Boolean {
        return context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).getBoolean(
            FAN_PAUSED_ACTIVE_KEY,
            false
        )
    }

    fun restorePreCallFanState(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wasEnabled = prefs.getBoolean(PRE_CALL_FAN_ENABLED_KEY, false)
        val level = prefs.getInt(PRE_CALL_FAN_LEVEL_KEY, 0)

        if (wasEnabled) {
            HardwareController.setFanLevel(level)
        } else {
            HardwareController.enableFan(false)
        }
    }

    fun readLed(context: Context, enabledKey: String, effectKey: String, colorKey: String, defaultEnabled: Boolean, defaultEffect: String, defaultColor: Int): LedState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedEffect = prefs.getString(effectKey, defaultEffect) ?: defaultEffect
        val savedColor = prefs.getInt(colorKey, defaultColor)
        val isFanProfile = effectKey == INCOMING_FAN_EFFECT_KEY ||
            effectKey == CONNECTED_FAN_EFFECT_KEY
        return LedState(
            enabled = prefs.getBoolean(enabledKey, defaultEnabled),
            effect = if (isFanProfile) {
                FanLedPalette.normalizeEffect(savedEffect)
            } else {
                savedEffect
            },
            color = if (isFanProfile) {
                FanLedPalette.normalizeColor(savedEffect, savedColor)
            } else {
                savedColor
            }
        )
    }

    fun saveLed(context: Context, enabledKey: String, effectKey: String, colorKey: String, state: LedState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(enabledKey, state.enabled)
            .putString(effectKey, state.effect)
            .putInt(colorKey, state.color)
            .apply()
    }
}
