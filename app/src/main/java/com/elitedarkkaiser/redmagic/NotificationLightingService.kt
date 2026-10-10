package com.elitedarkkaiser.redmagic

import android.app.AlarmManager
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat

class NotificationLightingService : NotificationListenerService() {
    private lateinit var thread: HandlerThread
    private lateinit var worker: Handler
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentKey: String? = null
    private var windowTiming: NotificationWindowTiming? = null
    @Volatile private var connectedAtMillis = 0L
    private val seen = LinkedHashSet<String>()
    private val windowNotificationKeys = LinkedHashSet<String>()
    private val finish = Runnable { endWindow("handler-expiry") }
    private var expiryAlarm: AlarmManager.OnAlarmListener? = null
    private fun scheduleExpiry() {
        worker.removeCallbacks(finish)
        expiryAlarm?.let { getSystemService(AlarmManager::class.java).cancel(it) }
        val deadline = NotificationLightingState.expiresAt
        val remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        worker.postDelayed(finish, remaining)
        // A wakeup alarm uses elapsed realtime, unlike Handler's uptime clock.
        // Keep the bounded partial wake lock too: Doze may defer ordinary alarms.
        val alarm = AlarmManager.OnAlarmListener {
            android.util.Log.i("NotificationLighting", "Elapsed alarm delivered deadline=$deadline")
            val cleanupLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "Redmagic:NotificationCleanup").apply {
                setReferenceCounted(false)
                acquire(5000L)
            }
            worker.post {
                try {
                    if (NotificationLightingState.expiresAt == deadline && currentKey != null)
                        endWindow("elapsed-alarm-expiry")
                } finally { if (cleanupLock.isHeld) cleanupLock.release() }
            }
        }
        expiryAlarm = alarm
        runCatching {
            getSystemService(AlarmManager::class.java).setExact(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline,
                "Redmagic:NotificationExpiry", alarm, Handler(Looper.getMainLooper()))
        }.onFailure { android.util.Log.w("NotificationLighting", "Expiry alarm unavailable; bounded wake lock and handler remain", it) }
        android.util.Log.i("NotificationLighting", "Expiry armed remainingMs=$remaining wakeLockHeld=${wakeLock?.isHeld == true}")
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            worker.post {
                if (intent?.action == Intent.ACTION_SCREEN_ON || intent?.action == Intent.ACTION_POWER_CONNECTED ||
                    intent?.action == ACTION_SETTINGS_CHANGED ||
                    !NotificationLightingState.enabled(this@NotificationLightingService)) endWindow()
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        android.util.Log.i("NotificationLighting", "Listener service created")
        thread = HandlerThread("NotificationLighting").apply { start() }
        worker = Handler(thread.looper)
        ContextCompat.registerReceiver(this, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(ACTION_SETTINGS_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val ignored = when {
            sbn.postTime <= connectedAtMillis -> "old-or-replayed"
            sbn.packageName == packageName -> "own-app"
            sbn.isOngoing -> "ongoing"
            sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 -> "group-summary"
            else -> null
        }
        android.util.Log.i("NotificationLighting", "Notification callback filter=${ignored ?: "eligible"}")
        if (ignored != null) return
        worker.post {
            // Updates to the same notification do not extend its lighting window.
            if (!seen.add(sbn.key)) {
                android.util.Log.i("NotificationLighting", "Skipped duplicate notification update")
                return@post
            }
            if (seen.size > 256) seen.remove(seen.first())
            if (!DeviceCompatibility.isSupportedDevice() || !NotificationLightingState.enabled(this) ||
                LedScreenPolicy.isScreenInteractive(this) || ChargingLedState.isChargingNow(this) ||
                CallLightingState.isActive(this)) return@post
            val p = NotificationLightingState.read(this,sbn.packageName) ?: return@post
            if (!(p.logo || p.triggers || p.fan)) return@post
            val now = SystemClock.elapsedRealtime()
            // An expired-but-not-cleaned window must finish before another alert
            // can claim ownership. Each distinct notification gets its selected duration.
            if (currentKey != null && !NotificationLightingState.isActive()) endWindow("expired-before-post")
            run {
                wakeLock?.let { if (it.isHeld) it.release() }
                windowTiming = NotificationWindowTiming(now, p.seconds)
                NotificationLightingState.expiresAt = windowTiming!!.deadline
                wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,"Redmagic:NotificationLighting").apply {
                    setReferenceCounted(false); acquire(NotificationWindowTiming.SETUP_LIMIT_MS + 5000L)
                }
            }
            val timing = checkNotNull(windowTiming)
            windowNotificationKeys.add(sbn.key)
            currentKey = sbn.key
            android.util.Log.i("NotificationLighting", "Apply window seconds=${p.seconds} logo=${p.logo} triggers=${p.triggers} fan=${p.fan} deadline=${NotificationLightingState.expiresAt}")
            scheduleExpiry()
            try {
                val applied = ModeTransitionCoordinator.applyLedProfile(this,LedOwner.NOTIFICATION,sbn.key,force=true) {
                    val effect = p.effect
                    val zones = buildList {
                        if (p.logo) add(NotificationLedBatch.Zone("logo", effect, p.color))
                        if (p.triggers) add(NotificationLedBatch.Zone("triggers", effect, p.color))
                        if (p.fan) add(NotificationLedBatch.Zone("fan", effect, p.color))
                    }
                    val stopCooling = !HardwareScreenPolicy.isScreenInteractive(this) &&
                        !HardwareScreenPolicy.coolingAllowedWhileScreenOff(HardwareScreenPolicy.currentTempF())
                    val startedAt = SystemClock.elapsedRealtime()
                    val success = HardwareController.applyNotificationLeds(zones, stopCooling)
                    android.util.Log.i("NotificationLighting", "Batch zones=${zones.map { it.name }} writeSucceeded=$success elapsedMs=${SystemClock.elapsedRealtime()-startedAt} coolingStopped=$stopCooling")
                    if (!success) throw IllegalStateException("Coordinated notification LED application failed")

                }
                if (!applied || LedScreenPolicy.isScreenInteractive(this) ||
                    ChargingLedState.isChargingNow(this) || CallLightingState.isActive(this)) {
                    endWindow("ownership-changed-during-apply")
                    return@post
                }
                val firstApplication = !timing.applied
                if (!timing.markApplied(SystemClock.elapsedRealtime())) {
                    endWindow("application-deadline-exceeded")
                    return@post
                }
                if (firstApplication) {
                    NotificationLightingState.expiresAt = timing.deadline
                    // Renew after each distinct alert, matching stock timeout restart.
                    // Total lock budget is bounded by setup + visible duration + cleanup.
                    wakeLock?.acquire((timing.deadline-SystemClock.elapsedRealtime()).coerceAtLeast(1L)+5000L)
                    scheduleExpiry()
                    android.util.Log.i("NotificationLighting", "Visible window started seconds=${p.seconds} deadline=${timing.deadline}")
                }
            } catch (e: Exception) {
                android.util.Log.e("NotificationLighting", "Notification profile failed", e)
                endWindow("application-failed")
            } finally {
                // Slow profile/root writes must not leave an already-expired
                // profile lit or start a fresh full-duration timer afterward.
                if (currentKey != null && !NotificationLightingState.isActive()) endWindow("expired-during-apply")
            }
        }
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        worker.post {
            seen.remove(sbn.key)
            if (windowNotificationKeys.remove(sbn.key) && windowNotificationKeys.isEmpty())
                endWindow("all-notifications-removed")
        }
    }
    private fun endWindow(reason: String = "cancelled") {
        val hadWindow = currentKey != null
        android.util.Log.i("NotificationLighting", "End reason=$reason hadWindow=$hadWindow overdueMs=${(SystemClock.elapsedRealtime()-NotificationLightingState.expiresAt).coerceAtLeast(0L)}")
        worker.removeCallbacks(finish)
        expiryAlarm?.let { getSystemService(AlarmManager::class.java).cancel(it) }
        expiryAlarm = null
        currentKey = null
        windowNotificationKeys.clear()
        windowTiming = null
        NotificationLightingState.expiresAt = 0L
        // The same transition used by screen-off receivers selects call/charging
        // or invokes turnOffAllLeds under the profile lock. No separate HAL off.
        try { if (hadWindow) ModeTransitionCoordinator.restoreEffectiveOwner(this,"notification-window-ended") }
        catch (e: Exception) { android.util.Log.e("NotificationLighting", "Restoration failed", e) }
        finally { wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null }
    }
    override fun onListenerConnected() {
        super.onListenerConnected()
        android.util.Log.i("NotificationLighting", "Listener connected")
        // Replayed notifications belong to the old session, not a new alert.
        connectedAtMillis = System.currentTimeMillis()
        val existingKeys = runCatching { activeNotifications.orEmpty().map { it.key } }.getOrDefault(emptyList())
        worker.post {
            seen.clear()
            seen.addAll(existingKeys.takeLast(256))
            // Rebinding after process death must clear any orphaned LED window.
            if (currentKey == null) ModeTransitionCoordinator.restoreEffectiveOwner(this,"notification-listener-connected")
        }
    }
    override fun onListenerDisconnected() { worker.post { endWindow() } }
    override fun onDestroy() {
        unregisterReceiver(receiver)
        worker.post { endWindow(); thread.quitSafely() }
        super.onDestroy()
    }
    companion object { const val ACTION_SETTINGS_CHANGED = "com.elitedarkkaiser.redmagic.NOTIFICATION_LIGHTING_CHANGED" }
}
