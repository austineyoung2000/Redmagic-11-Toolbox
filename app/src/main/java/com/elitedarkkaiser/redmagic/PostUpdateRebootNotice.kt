package com.elitedarkkaiser.redmagic

import android.content.Context
import android.os.SystemClock
import com.google.android.material.dialog.MaterialAlertDialogBuilder

internal object PostUpdateRebootPolicy {
    fun shouldPrompt(
        firstInstallTimeMs: Long,
        lastUpdateTimeMs: Long,
        previouslyObservedUpdateTimeMs: Long,
        bootStartedAtMs: Long
    ): Boolean {
        if (lastUpdateTimeMs <= 0L) return false

        val isExistingInstall =
            firstInstallTimeMs > 0L && firstInstallTimeMs < lastUpdateTimeMs
        val updateChanged = when {
            previouslyObservedUpdateTimeMs <= 0L -> isExistingInstall
            else -> previouslyObservedUpdateTimeMs != lastUpdateTimeMs
        }

        return updateChanged && lastUpdateTimeMs >= bootStartedAtMs
    }
}

internal object PostUpdateRebootNotice {
    private const val PREFS = "post_update_runtime"
    private const val OBSERVED_UPDATE_TIME = "observed_update_time"

    fun showIfNeeded(activity: MainActivity) {
        val packageInfo = runCatching {
            @Suppress("DEPRECATION")
            activity.packageManager.getPackageInfo(
                activity.packageName,
                0
            )
        }.getOrNull() ?: return

        val prefs = activity.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
        val lastUpdateTime = packageInfo.lastUpdateTime
        val shouldPrompt = PostUpdateRebootPolicy.shouldPrompt(
            firstInstallTimeMs = packageInfo.firstInstallTime,
            lastUpdateTimeMs = lastUpdateTime,
            previouslyObservedUpdateTimeMs = prefs.getLong(
                OBSERVED_UPDATE_TIME,
                0L
            ),
            bootStartedAtMs =
                System.currentTimeMillis() - SystemClock.elapsedRealtime()
        )

        prefs.edit()
            .putLong(OBSERVED_UPDATE_TIME, lastUpdateTime)
            .apply()

        if (!shouldPrompt || activity.isFinishing || activity.isDestroyed) {
            return
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle("Reboot required after update")
            .setMessage(
                "Android and REDMAGIC can retain the previous accessibility, " +
                    "overlay, watchdog, and trigger runtime after an APK update. " +
                    "Reboot the phone once before testing gameplay controls, " +
                    "then confirm the Toolbox accessibility service is enabled."
            )
            .setPositiveButton("Got it", null)
            .show()
    }
}
