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
            LedScreenPolicy.postScreenEvent(this@NotificationLightingService, worker, intent?.action, "notification-screen") event@ {
                if (intent?.action == NotificationWindowDeadline.ACTION_RELEASE) {
                    if (intent.getStringExtra("token") == windowToken) releaseWindowFields()
                    return@event
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
        val tracked = NotificationLightingState.packages(this).contains(sbn.packageName)
        fun trace(stage: String) { if (tracked) recordAttempt("${sbn.packageName}: $stage") }
        val ignored = when {
            sbn.postTime <= connectedAtMillis -> "old-or-replayed"
            sbn.packageName == packageName -> "own-app"
            sbn.isOngoing -> "ongoing"
            sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 -> "group-summary"
            else -> null
        }
        android.util.Log.i("NotificationLighting", "Notification callback filter=${ignored ?: "eligible"}")
        trace("callback filter=${ignored ?: "eligible"}; flags=${sbn.notification.flags}; postAgeMs=${System.currentTimeMillis()-sbn.postTime}")
        if (ignored != null) return
        worker.post {
            // Ineligible callbacks must not poison deduplication for a later locked alert.
            val rejected = when {
                !DeviceCompatibility.isSupportedDevice() -> "unsupported device"
                !NotificationLightingState.enabled(this) -> "feature disabled"
                LedScreenPolicy.isScreenInteractive(this) -> "screen interactive"
                ChargingLedState.isChargingNow(this) -> "plugged in"
                CallLightingState.isEnabled(this) && CallLightingState.isRingingNow(this) -> "ringing call"
                else -> null
            }
            if (rejected != null) { trace("rejected: $rejected"); return@post }
            val p = NotificationLightingState.read(this,sbn.packageName)
            if (p == null) { trace("rejected: missing or invalid profile"); return@post }
            if (!(p.logo || p.triggers || p.fan)) { trace("rejected: no zones selected"); return@post }
            trace("worker eligible; preparing exact expiry")
            if (!NotificationWindowDeadline.prepare(this)) {
                trace("rejected: exact expiry unavailable")
                android.util.Log.e("NotificationLighting", "Cannot start notification LEDs without durable exact expiry access")
                return@post
            }
            if (!notificationSession.accept(sbn.key, sbn.postTime, eligible = true)) {
                trace("rejected: duplicate or older key/post-time delivery")
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
                trace("rejected: setup deadline could not be armed")
                endWindow("durable-expiry-unavailable")
                return@post
            }
            try {
                trace("setup deadline armed; requesting NOTIFICATION ownership")
                val applied = ModeTransitionCoordinator.applyLedProfile(this,LedOwner.NOTIFICATION,sbn.key,force=true) {
                    val effect = p.effect
                    val zones = buildList {
                        if (p.logo) add(NotificationLedBatch.Zone("logo", effect, p.color))
                        if (p.triggers) add(NotificationLedBatch.Zone("triggers", effect, p.color))
                        if (p.fan) add(NotificationLedBatch.Zone("fan", effect, p.color))
                    }
                    if (NotificationProfileReplayPolicy.shouldWrite(appliedZones, zones)) {
                        trace("ownership accepted; reading cooling temperature")
                        val stopCooling = !HardwareScreenPolicy.isScreenInteractive(this) &&
                            !HardwareScreenPolicy.coolingAllowedWhileScreenOff(HardwareScreenPolicy.currentTempF())
                        val startedAt = SystemClock.elapsedRealtime()
                        trace("issuing stock batch; zones=${zones.map { it.name }}; stopCooling=$stopCooling")
                        val success = HardwareController.applyNotificationLeds(zones, stopCooling)
                        trace("stock batch returned success=$success; elapsedMs=${SystemClock.elapsedRealtime()-startedAt}")
                        android.util.Log.i("NotificationLighting", "Batch zones=${zones.map { it.name }} writeSucceeded=$success elapsedMs=${SystemClock.elapsedRealtime()-startedAt} coolingStopped=$stopCooling")
                        if (!success) throw IllegalStateException("Coordinated notification LED application failed")
                        appliedZones = zones
                    } else {
                        android.util.Log.i("NotificationLighting", "Matching active notification output; restart timer without reprogramming LEDs")
                    }

                }
                if (!applied || LedScreenPolicy.isScreenInteractive(this) ||
                    ChargingLedState.isChargingNow(this) || (CallLightingState.isEnabled(this) && CallLightingState.isRingingNow(this))) {
                    trace("profile not retained; applied=$applied; owner=${LedOwnership.current(this)}")
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
                    trace("rejected: visible deadline could not start")
                    endWindow("application-deadline-exceeded")
                    return@post
                }
                android.util.Log.i("NotificationLighting", "Visible window started seconds=${p.seconds} deadline=${timing.deadline}")
                trace("visible timer started: ${p.seconds}s")
            } catch (e: Exception) {
                trace("application failed: ${e.javaClass.simpleName}")
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
        if (token != null) recordAttempt("handoff: $reason")
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
        listenerConnected = true
        rebindStatus = "connected at ${SystemClock.elapsedRealtime()/1000}s"
        BootDiagnostics.record(this, "Notification listener connected")
        android.util.Log.i("NotificationLighting", "Listener connected")
        // Replayed notifications belong to the old session, not a new alert.
        connectedAtMillis = System.currentTimeMillis()
        val existingPosts = runCatching { activeNotifications.orEmpty().map { it.key to it.postTime } }.getOrDefault(emptyList())
        worker.post {
            notificationSession.seed(existingPosts)
            // Rebinding after process death must clear any orphaned LED window.
            if (currentKey == null) NotificationWindowDeadline.finish(this, null, "notification-listener-connected")
        }
    }
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        listenerConnected = false
        BootDiagnostics.record(this, "Notification listener disconnected")
        worker.post {
            try { endWindow("listener-disconnected") }
            finally { ensureConnected(this) }
        }
    }
    override fun onDestroy() {
        listenerConnected = false
        unregisterReceiver(receiver)
        worker.post { endWindow(); thread.quitSafely() }
        super.onDestroy()
    }
    companion object {
        const val ACTION_SETTINGS_CHANGED = "com.elitedarkkaiser.redmagic.NOTIFICATION_LIGHTING_CHANGED"
        @Volatile private var listenerConnected = false
        private var lastRebindAt = -10_000L
        @Volatile private var rebindStatus = "not requested"
        private val attemptTrace = java.util.ArrayDeque<String>()
        @Synchronized private fun recordAttempt(stage: String) {
            if (attemptTrace.size == 32) attemptTrace.removeFirst()
            attemptTrace.addLast("${SystemClock.elapsedRealtime()/1000}s: $stage")
        }
        @Synchronized fun attemptReport(): String = if (attemptTrace.isEmpty())
            "No configured-app callbacks recorded in this process." else attemptTrace.joinToString("\n")
        fun connectionStatus() = "connected=$listenerConnected; rebind=$rebindStatus"

        @Synchronized fun ensureConnected(context: Context) {
            if (!DeviceCompatibility.isSupportedDevice() || !NotificationLightingState.enabled(context) || listenerConnected) return
            val component = android.content.ComponentName(context, NotificationLightingService::class.java)
            if (!context.getSystemService(android.app.NotificationManager::class.java).isNotificationListenerAccessGranted(component)) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastRebindAt < 10_000L) return
            lastRebindAt = now
            try {
                // Android owns this binding; startService cannot substitute for it.
                requestRebind(component)
                rebindStatus = "requested at ${now / 1000}s (awaiting connection callback)"
                BootDiagnostics.record(context, "Notification listener rebind requested")
            } catch (error: Exception) {
                rebindStatus = "failed: ${error.javaClass.simpleName}"
                BootDiagnostics.record(context, "Notification listener rebind failed: ${error.javaClass.simpleName}")
            }
        }
    }
}
