package com.elitedarkkaiser.redmagic

import android.content.Context

/** User-selected manual state. A missing value is not inferred from telemetry. */
internal object ManualFanStorage {
    private const val PREFS = "manual_fan"
    private const val LEVEL = "level"

    fun read(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(LEVEL)) return null
        return prefs.getInt(LEVEL, 0).coerceIn(0, 5)
    }

    fun save(context: Context, level: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(LEVEL, level.coerceIn(0, 5)).apply()
        BootCoolingRestore.markUserChange(context, "fan")
    }
}
