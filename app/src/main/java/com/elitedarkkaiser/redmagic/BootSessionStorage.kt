package com.elitedarkkaiser.redmagic

import android.content.Context
import android.os.SystemClock
import java.io.File

internal object BootSessionStorage {
    private const val PREFS = "boot_runtime"
    private const val CORE_STARTED_BOOT_ID = "core_started_boot_id"

    @Synchronized
    fun claimCoreServiceStartup(context: Context): Boolean {
        val bootId = currentBootId()
        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        if (prefs.getString(CORE_STARTED_BOOT_ID, null) == bootId) {
            return false
        }

        prefs.edit()
            .putString(CORE_STARTED_BOOT_ID, bootId)
            .apply()
        return true
    }

    private fun currentBootId(): String {
        val kernelBootId = runCatching {
            File("/proc/sys/kernel/random/boot_id")
                .readText()
                .trim()
        }.getOrNull()

        if (!kernelBootId.isNullOrBlank()) {
            return kernelBootId
        }

        val bootEpochMinutes =
            (System.currentTimeMillis() -
                SystemClock.elapsedRealtime()) / 60_000L
        return "epoch-minute:$bootEpochMinutes"
    }
}
