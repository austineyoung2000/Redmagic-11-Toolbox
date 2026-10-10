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
    @Volatile private var connectedAtMillis = 0L
    private val seen = LinkedHashSet<String>()
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
            // can claim ownership. A burst never extends the first deadline.
            if (currentKey != null && !NotificationLightingState.isActive()) endWindow("expired-before-post")
            if (currentKey == null) {
                NotificationLightingState.expiresAt = now + p.seconds * 1000L
                wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,"Redmagic:NotificationLighting").apply {
                    setReferenceCounted(false); acquire(p.seconds * 1000L + 5000L)
                }
            }
            currentKey = sbn.key
            android.util.Log.i("NotificationLighting", "Apply window seconds=${p.seconds} logo=${p.logo} triggers=${p.triggers} fan=${p.fan} deadline=${NotificationLightingState.expiresAt}")
            scheduleExpiry()
            try {
                ModeTransitionCoordinator.applyLedProfile(this,LedOwner.NOTIFICATION,sbn.key,force=true) {
                    val effect = LedBrightness.encode(p.brightness,p.effect)
                    val zones = buildList {
                        if (p.logo) add(NotificationLedBatch.Zone("logo", p.logoState?.effect ?: effect, p.logoState?.color ?: p.color))
                        if (p.triggers) add(NotificationLedBatch.Zone("triggers", p.triggerState?.effect ?: effect, p.triggerState?.color ?: p.color))
                        if (p.fan) add(NotificationLedBatch.Zone("fan", effect, p.color))
                    }
                    val stopCooling = !HardwareScreenPolicy.isScreenInteractive(this) &&
                        !HardwareScreenPolicy.coolingAllowedWhileScreenOff(HardwareScreenPolicy.currentTempF())
                    val startedAt = SystemClock.elapsedRealtime()
                    val success = HardwareController.applyNotificationLeds(zones, stopCooling)
                    android.util.Log.i("NotificationLighting", "Batch zones=${zones.map { it.name }} writeSucceeded=$success elapsedMs=${SystemClock.elapsedRealtime()-startedAt} coolingStopped=$stopCooling")
                    if (!success) throw IllegalStateException("Coordinated notification LED application failed")

                }
            } catch (e: Exception) {
                android.util.Log.e("NotificationLighting", "Notification profile failed", e)
            } finally {
                // Slow profile/root writes must not leave an already-expired
                // profile lit or start a fresh full-duration timer afterward.
                if (!NotificationLightingState.isActive()) endWindow("expired-during-apply")
            }
        }
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        worker.post { seen.remove(sbn.key); if (currentKey == sbn.key) endWindow() }
    }
    private fun endWindow(reason: String = "cancelled") {
        val hadWindow = currentKey != null
        android.util.Log.i("NotificationLighting", "End reason=$reason hadWindow=$hadWindow overdueMs=${(SystemClock.elapsedRealtime()-NotificationLightingState.expiresAt).coerceAtLeast(0L)}")
        worker.removeCallbacks(finish)
        expiryAlarm?.let { getSystemService(AlarmManager::class.java).cancel(it) }
        expiryAlarm = null
        currentKey = null
        NotificationLightingState.expiresAt = 0L
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
