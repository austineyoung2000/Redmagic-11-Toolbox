package com.elitedarkkaiser.redmagic

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log

/** Persisted window identity plus a system-owned alarm, coordinated with every handoff. */
internal object NotificationWindowDeadline {
    const val ACTION_RELEASE = "com.elitedarkkaiser.redmagic.NOTIFICATION_WINDOW_RELEASED"
    private const val TAG = "NotificationDeadline"
    private fun prefs(c: Context) = c.getSharedPreferences("notification_deadline", Context.MODE_PRIVATE)
    fun boot(c: Context) = Settings.Global.getInt(c.contentResolver, Settings.Global.BOOT_COUNT, 0)
    private fun read(c: Context): NotificationDeadline? {
        val p = prefs(c)
        val token = p.getString("token", null) ?: return null
        return NotificationDeadline(token, p.getLong("deadline", 0L), p.getInt("boot", -1))
    }
    private fun intent(c: Context, value: NotificationDeadline) =
        Intent(c, NotificationExpiryReceiver::class.java)
            .setData(Uri.parse("redmagic://notification-expiry/${value.token}/${value.deadline}/${value.boot}"))
            .putExtra("token", value.token).putExtra("deadline", value.deadline).putExtra("boot", value.boot)
    private fun cancelAlarm(c: Context, value: NotificationDeadline) {
        PendingIntent.getBroadcast(c, 0, intent(c, value), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            c.getSystemService(AlarmManager::class.java).cancel(it)
            it.cancel()
        }
    }

    fun status(c: Context): String {
        val value = read(c)
        val exact = c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        return "exactAccess=$exact, recoveryRecord=${value != null}, logicalActive=${NotificationLightingState.isActive()}, remainingMs=${value?.let { (it.deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L) } ?: 0L}"
    }

    fun owns(c: Context, token: String) = read(c)?.token == token

    fun prepare(c: Context): Boolean {
        LightingRootExecutor.initialize(c)
        val manager = c.getSystemService(AlarmManager::class.java)
        if (!manager.canScheduleExactAlarms()) {
            // Same root-grant model as existing usage/overlay/phone bootstrap.
            val success = LightingRootExecutor.exec("appops set ${LightingRootCommand.quote(c.packageName)} SCHEDULE_EXACT_ALARM allow")
            Log.i(TAG, "Exact expiry root grant acknowledged=$success")
        }
        return manager.canScheduleExactAlarms()
    }

    fun arm(c: Context, token: String, deadline: Long): Boolean = ModeTransitionCoordinator.withLightingLock {
        if (!NotificationLightingState.enabled(c) || LedScreenPolicy.isScreenInteractive(c) ||
            ChargingLedState.isChargingNow(c) ||
            (CallLightingState.isEnabled(c) && CallLightingState.isRingingNow(c))) return@withLightingLock false
        val value = NotificationDeadline(token, deadline, boot(c))
        val previous = read(c)
        val pending = PendingIntent.getBroadcast(c, 0, intent(c, value), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val armed = runCatching {
            c.getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, pending)
            true
        }.onFailure { Log.e(TAG, "Durable expiry alarm could not be armed", it) }.getOrDefault(false)
        if (!armed) {
            pending.cancel()
            return@withLightingLock false
        }
        if (!prefs(c).edit().putString("token", token).putLong("deadline", deadline)
                .putInt("boot", value.boot).putInt("recovery_attempts", 0).commit()) {
            pending.cancel()
            return@withLightingLock false
        }
        NotificationLightingState.windowToken = token
        NotificationLightingState.expiresAt = deadline
        if (previous != null && previous != value) cancelAlarm(c, previous)
        armed
    }

    /** Release logical ownership, retaining recovery until hardware acknowledges a handoff. */
    fun supersede(c: Context) = ModeTransitionCoordinator.withLightingLock {
        val value = read(c) ?: return@withLightingLock
        NotificationLightingState.expiresAt = 0L
        NotificationLightingState.windowToken = null
        signalRelease(c, value.token)
    }

    fun acknowledgeHandoff(c: Context) = ModeTransitionCoordinator.withLightingLock {
        val value = read(c) ?: return@withLightingLock
        if (NotificationLightingState.isActive()) return@withLightingLock
        cancelAlarm(c, value)
        if (!prefs(c).edit().clear().commit()) Log.e(TAG, "Could not clear acknowledged recovery record")
    }

    private fun retryRecovery(c: Context, value: NotificationDeadline) {
        if (read(c) != value) return
        val attempts = prefs(c).getInt("recovery_attempts", 0)
        if (attempts >= 3) {
            Log.e(TAG, "Recovery exhausted; hardware handoff was not acknowledged")
            return
        }
        prefs(c).edit().putInt("recovery_attempts", attempts + 1).commit()
        val pending = PendingIntent.getBroadcast(c, 0, intent(c, value), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        runCatching {
            c.getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, maxOf(SystemClock.elapsedRealtime() + 2000L, value.deadline), pending)
        }.onFailure { Log.e(TAG, "Could not schedule bounded handoff recovery", it) }
    }

    fun finish(c: Context, token: String?, reason: String): Boolean = ModeTransitionCoordinator.withLightingLock {
        val value = read(c)
        if (token != null && value?.token != token && NotificationLightingState.windowToken != token)
            return@withLightingLock false
        supersede(c)
        NotificationLightingState.expiresAt = 0L
        NotificationLightingState.windowToken = null
        try {
            ModeTransitionCoordinator.restoreEffectiveOwner(c, reason)
        } finally {
            // Android may reject a service restart or a restoration path may
            // throw. Retain and rearm recovery even when no receipt returns.
            if (value != null) retryRecovery(c, value)
        }
        true
    }

    fun reconcileReleased(c: Context, reason: String) = ModeTransitionCoordinator.withLightingLock {
        if (read(c) != null && !NotificationLightingState.isEligible(c))
            ModeTransitionCoordinator.restoreEffectiveOwner(c, reason)
    }

    fun expire(c: Context, callback: NotificationDeadline): Boolean = ModeTransitionCoordinator.withLightingLock {
        if (!NotificationDeadlinePolicy.expired(read(c), callback, boot(c), SystemClock.elapsedRealtime())) {
            Log.i(TAG, "Ignored stale/early notification expiry callback")
            return@withLightingLock false
        }
        // The callback has no direct LED command. All expiry/restoration goes
        // through the same priority resolver as wake, charging and call exit.
        finish(c, callback.token, "durable-notification-expiry")
    }

    private fun signalRelease(c: Context, token: String) {
        c.sendBroadcast(Intent(ACTION_RELEASE).setPackage(c.packageName).putExtra("token", token))
    }
}
