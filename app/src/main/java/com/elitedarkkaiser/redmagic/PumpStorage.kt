package com.elitedarkkaiser.redmagic

import android.content.Context
import com.elitedarkkaiser.redmagic.state.PumpState
import com.elitedarkkaiser.redmagic.storage.AppPrefs

fun savedPumpStateStorage(context: Context): PumpState {
    val prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
    val profile = (prefs.getString(AppPrefs.PUMP_PROFILE, "quick") ?: "quick").lowercase()
    val normalizedProfile = if (
        profile == "slow" ||
        profile == "medium" ||
        profile == "quick" ||
        profile == "experimental"
    ) {
        profile
    } else {
        "quick"
    }

    return PumpState(
        enabled = prefs.getBoolean(AppPrefs.PUMP_ENABLED, false),
        profile = normalizedProfile,
        autoEnabled = prefs.getBoolean(AppPrefs.AUTO_PUMP_ENABLED, false),
        experimentalAccepted = prefs.getBoolean(AppPrefs.PUMP_EXPERIMENTAL_ACCEPTED, false)
    )
}

fun savePumpStateStorage(context: Context, enabled: Boolean, profile: String) {
    context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(AppPrefs.PUMP_ENABLED, enabled)
        .putString(AppPrefs.PUMP_PROFILE, profile)
        .apply()
}

fun setPumpExperimentalAcceptedStorage(context: Context, accepted: Boolean) {
    context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(AppPrefs.PUMP_EXPERIMENTAL_ACCEPTED, accepted)
        .apply()
}

fun saveAutoPumpStateStorage(context: Context, enabled: Boolean) {
    context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(AppPrefs.AUTO_PUMP_ENABLED, enabled)
        .apply()
}
