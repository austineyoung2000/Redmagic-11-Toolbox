package com.elitedarkkaiser.redmagic

import android.content.Context
import com.elitedarkkaiser.redmagic.state.LedState
import com.elitedarkkaiser.redmagic.storage.AppPrefs

fun savedFanLedStateStorage(context: Context): LedState {
    val prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
    val savedEffect = prefs.getString(AppPrefs.FAN_LED_EFFECT, "steady") ?: "steady"
    val savedColor = prefs.getInt(AppPrefs.FAN_LED_COLOR, 5)
    return LedState(
        enabled = prefs.getBoolean(AppPrefs.FAN_LED_ENABLED, false),
        effect = FanLedPalette.normalizeEffect(savedEffect),
        color = FanLedPalette.normalizeColor(savedEffect, savedColor)
    )
}

fun saveFanLedStateStorage(context: Context, state: LedState) {
    context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(AppPrefs.FAN_LED_ENABLED, state.enabled)
        .putString(AppPrefs.FAN_LED_EFFECT, state.effect)
        .putInt(AppPrefs.FAN_LED_COLOR, state.color)
        .apply()
}

fun savedLogoLedStateStorage(context: Context): LedState {
    val prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
    return LedState(
        enabled = prefs.getBoolean(AppPrefs.LOGO_LED_ENABLED, true),
        effect = prefs.getString(AppPrefs.LOGO_LED_EFFECT, "steady") ?: "steady",
        color = prefs.getInt(AppPrefs.LOGO_LED_COLOR, 1)
    )
}

fun saveLogoLedStateStorage(context: Context, state: LedState) {
    context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(AppPrefs.LOGO_LED_ENABLED, state.enabled)
        .putString(AppPrefs.LOGO_LED_EFFECT, state.effect)
        .putInt(AppPrefs.LOGO_LED_COLOR, state.color)
        .apply()
}

fun savedShoulderLedStateStorage(context: Context): LedState {
    val prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
    return LedState(
        enabled = prefs.getBoolean(AppPrefs.SHOULDER_LED_ENABLED, true),
        effect = prefs.getString(AppPrefs.SHOULDER_LED_EFFECT, "breathe") ?: "breathe",
        color = prefs.getInt(AppPrefs.SHOULDER_LED_COLOR, 8)
    )
}

fun saveShoulderLedStateStorage(context: Context, state: LedState) {
    context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(AppPrefs.SHOULDER_LED_ENABLED, state.enabled)
        .putString(AppPrefs.SHOULDER_LED_EFFECT, state.effect)
        .putInt(AppPrefs.SHOULDER_LED_COLOR, state.color)
        .apply()
}
