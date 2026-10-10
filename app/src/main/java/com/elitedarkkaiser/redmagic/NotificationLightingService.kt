package com.elitedarkkaiser.redmagic

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
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
    private var windowToken: String? = null
    private var windowTiming: NotificationWindowTiming? = null
    private var appliedZones: List<NotificationLedBatch.Zone>? = null
    @Volatile private var connectedAtMillis = 0L
    private val notificationSession = NotificationSessionDeduplicator()
    private val windowNotificationKeys = LinkedHashSet<String>()
    private val finish = Runnable {
        if (currentKey != null) {
            if (NotificationLightingState.isActive()) {
                if (!scheduleExpiry()) endWindow("durable-expiry-unavailable")
            } else endWindow("handler-expiry")
        }
    }
    private fun scheduleExpiry(): Boolean {
        worker.removeCallbacks(finish)
        val token = windowToken ?: return false
        val deadline = windowTiming?.deadline ?: return false
        if (!NotificationWindowDeadline.arm(this, token, deadline)) return false
        val remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        worker.postDelayed(finish, remaining)
        android.util.Log.i("NotificationLighting", "Durable expiry armed remainingMs=$remaining wakeLockHeld=${wakeLock?.isHeld == true}")
        return true
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON || intent?.action == Intent.ACTION_POWER_CONNECTED ||
                intent?.action == ACTION_SETTINGS_CHANGED) NotificationLightingState.expiresAt = 0L
            worker.post {
                if (intent?.action == NotificationWindowDeadline.ACTION_RELEASE) {
                    if (intent.getStringExtra("token") == windowToken) releaseWindowFields()
                    return@post
                }
                if (intent?.action == Intent.ACTION_SCREEN_OFF)
                    ModeTransitionCoordinator.restoreEffectiveOwner(this@NotificationLightingService, "notification-listener-screen-off")
                if (intent?.action == Intent.ACTION_SCREEN_ON) notificationSession.reset()
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
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(ACTION_SETTINGS_CHANGED)
            addAction(NotificationWindowDeadline.ACTION_RELEASE)
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
            // Ineligible callbacks must not poison deduplication for a later locked alert.
            if (!DeviceCompatibility.isSupportedDevice() || !NotificationLightingState.enabled(this) ||
                LedScreenPolicy.isScreenInteractive(this) || ChargingLedState.isChargingNow(this) ||
                (CallLightingState.isEnabled(this) && CallLightingState.isRingingNow(this))) return@post
            val p = NotificationLightingState.read(this,sbn.packageName) ?: return@post
            if (!(p.logo || p.triggers || p.fan)) return@post
            if (!NotificationWindowDeadline.prepare(this)) {
                android.util.Log.e("NotificationLighting", "Cannot start notification LEDs without durable exact expiry access")
                return@post
            }
            if (!notificationSession.accept(sbn.key, eligible = true)) {
                android.util.Log.i("NotificationLighting", "Skipped duplicate notification update in current screen-off session")
                return@post
            }
            val now = SystemClock.elapsedRealtime()
            // An expired-but-not-cleaned window must finish before another alert
            // can claim ownership. Each distinct notification gets its selected duration.
            if (currentKey != null && !NotificationLightingState.isActive()) endWindow("expired-before-post")
            run {
                wakeLock?.let { if (it.isHeld) it.release() }
                windowTiming = NotificationWindowTiming(now, p.seconds)
                windowToken = java.util.UUID.randomUUID().toString()
                wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,"Redmagic:NotificationLighting").apply {
                    setReferenceCounted(false); acquire(NotificationWindowTiming.SETUP_LIMIT_MS + 5000L)
                }
            }
            val timing = checkNotNull(windowTiming)
            windowNotificationKeys.add(sbn.key)
            currentKey = sbn.key
            android.util.Log.i("NotificationLighting", "Apply window seconds=${p.seconds} logo=${p.logo} triggers=${p.triggers} fan=${p.fan} deadline=${NotificationLightingState.expiresAt}")
            if (!scheduleExpiry()) {
                endWindow("durable-expiry-unavailable")
                return@post
            }
            try {
                val applied = ModeTransitionCoordinator.applyLedProfile(this,LedOwner.NOTIFICATION,sbn.key,force=true) {
                    val effect = p.effect
                    val zones = buildList {
                        if (p.logo) add(NotificationLedBatch.Zone("logo", effect, p.color))
                        if (p.triggers) add(NotificationLedBatch.Zone("triggers", effect, p.color))
                        if (p.fan) add(NotificationLedBatch.Zone("fan", effect, p.color))
                    }
                    if (NotificationProfileReplayPolicy.shouldWrite(appliedZones, zones)) {
                        val stopCooling = !HardwareScreenPolicy.isScreenInteractive(this) &&
                            !HardwareScreenPolicy.coolingAllowedWhileScreenOff(HardwareScreenPolicy.currentTempF())
                        val startedAt = SystemClock.elapsedRealtime()
                        val success = HardwareController.applyNotificationLeds(zones, stopCooling)
                        android.util.Log.i("NotificationLighting", "Batch zones=${zones.map { it.name }} writeSucceeded=$success elapsedMs=${SystemClock.elapsedRealtime()-startedAt} coolingStopped=$stopCooling")
                        if (!success) throw IllegalStateException("Coordinated notification LED application failed")
                        appliedZones = zones
                    } else {
                        android.util.Log.i("NotificationLighting", "Matching active notification output; restart timer without reprogramming LEDs")
                    }

                }
                if (!applied || LedScreenPolicy.isScreenInteractive(this) ||
                    ChargingLedState.isChargingNow(this) || (CallLightingState.isEnabled(this) && CallLightingState.isRingingNow(this))) {
                    endWindow("ownership-changed-during-apply")
                    return@post
                }
                val visibleStarted = ModeTransitionCoordinator.withLightingLock {
                    val token = windowToken ?: return@withLightingLock false
                    if (!NotificationWindowDeadline.owns(this, token) ||
                        !timing.markApplied(SystemClock.elapsedRealtime())) return@withLightingLock false
                    // Both the persisted identity/deadline and the system alarm
                    // move together, fenced against an expired setup callback.
                    if (!scheduleExpiry()) return@withLightingLock false
                    wakeLock?.acquire((timing.deadline-SystemClock.elapsedRealtime()).coerceAtLeast(1L)+5000L)
                    true
                }
                if (!visibleStarted) {
                    endWindow("application-deadline-exceeded")
                    return@post
                }
                android.util.Log.i("NotificationLighting", "Visible window started seconds=${p.seconds} deadline=${timing.deadline}")
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
            notificationSession.remove(sbn.key)
            if (windowNotificationKeys.remove(sbn.key) && windowNotificationKeys.isEmpty())
                endWindow("all-notifications-removed")
        }
    }
    private fun releaseWindowFields() {
        worker.removeCallbacks(finish)
        currentKey = null
        windowToken = null
        windowNotificationKeys.clear()
        windowTiming = null
        appliedZones = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
    private fun endWindow(reason: String = "cancelled") {
        val token = windowToken
        android.util.Log.i("NotificationLighting", "End reason=$reason hadWindow=${token != null}")
        // Keep CPU awake through the bounded physical handoff, not merely
        // through the visible timer. Releasing before root cleanup could let
        // screen-off suspend postpone the actual all-off writes.
        val cleanupWake = if (token != null) {
            (wakeLock ?: (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "Redmagic:NotificationHandoff")).apply {
                setReferenceCounted(false)
                acquire(25_000L)
            }
        } else null
        if (cleanupWake != null) wakeLock = null
        releaseWindowFields()
        try {
            if (token != null && !NotificationWindowDeadline.finish(this, token, reason))
                NotificationWindowDeadline.reconcileReleased(this, "$reason-stale-local-window")
        } catch (e: Exception) {
            android.util.Log.e("NotificationLighting", "Restoration failed", e)
        } finally {
            cleanupWake?.let { if (it.isHeld) it.release() }
        }
    }
    override fun onListenerConnected() {
        super.onListenerConnected()
        android.util.Log.i("NotificationLighting", "Listener connected")
        // Replayed notifications belong to the old session, not a new alert.
        connectedAtMillis = System.currentTimeMillis()
        val existingKeys = runCatching { activeNotifications.orEmpty().map { it.key } }.getOrDefault(emptyList())
        worker.post {
            notificationSession.seed(existingKeys)
            // Rebinding after process death must clear any orphaned LED window.
            if (currentKey == null) NotificationWindowDeadline.finish(this, null, "notification-listener-connected")
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
