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
    @Volatile private var connectedAtMillis = 0L
    private val seen = LinkedHashSet<String>()
    private val finish = Runnable { endWindow() }
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
        thread = HandlerThread("NotificationLighting").apply { start() }
        worker = Handler(thread.looper)
        ContextCompat.registerReceiver(this, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(ACTION_SETTINGS_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.postTime <= connectedAtMillis || sbn.packageName == packageName || sbn.isOngoing ||
            sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        worker.post {
            // Updates to the same notification do not extend its lighting window.
            if (!seen.add(sbn.key)) return@post
            if (seen.size > 256) seen.remove(seen.first())
            if (!DeviceCompatibility.isSupportedDevice() || !NotificationLightingState.enabled(this) ||
                LedScreenPolicy.isScreenInteractive(this) || ChargingLedState.isChargingNow(this) ||
                CallLightingState.isActive(this)) return@post
            val p = NotificationLightingState.read(this,sbn.packageName) ?: return@post
            if (!(p.logo || p.triggers || p.fan)) return@post
            val now = SystemClock.elapsedRealtime()
            if (!NotificationLightingState.isActive()) {
                if (currentKey != null) endWindow()
                NotificationLightingState.expiresAt = now + p.seconds * 1000L
                wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,"Redmagic:NotificationLighting").apply {
                    setReferenceCounted(false); acquire(p.seconds * 1000L + 5000L)
                }
            }
            currentKey = sbn.key
            try {
                ModeTransitionCoordinator.applyLedProfile(this,LedOwner.NOTIFICATION,sbn.key,force=true) {
                    HardwareController.turnOffAllLeds()
                    val effect = LedBrightness.encode(p.brightness,p.effect)
                    if (p.logo) HardwareController.setLogoLedEffect(p.logoState?.effect ?: effect,p.logoState?.color ?: p.color)
                    if (p.triggers) HardwareController.setShoulderLedEffect(p.triggerState?.effect ?: effect,p.triggerState?.color ?: p.color)
                    if (p.fan) HardwareController.setFanLedEffect(effect,p.color)
                    if (HardwareScreenPolicy.blockCoolingWhileScreenOffUnlessHot(this,"notification-lighting")) {
                        // LED commands may have re-enabled fan power since the
                        // shared policy last shut it down. Enforce it again.
                        HardwareController.enableFan(false)
                        HardwareController.enablePump(false)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("NotificationLighting", "Notification profile failed", e)
            } finally {
                worker.removeCallbacks(finish)
                worker.postDelayed(finish,(NotificationLightingState.expiresAt-SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            }
        }
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        worker.post { seen.remove(sbn.key); if (currentKey == sbn.key) endWindow() }
    }
    private fun endWindow() {
        val hadWindow = currentKey != null
        worker.removeCallbacks(finish)
        currentKey = null
        NotificationLightingState.expiresAt = 0L
        try { if (hadWindow) ModeTransitionCoordinator.restoreEffectiveOwner(this,"notification-window-ended") }
        catch (e: Exception) { android.util.Log.e("NotificationLighting", "Restoration failed", e) }
        finally { wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null }
    }
    override fun onListenerConnected() {
        super.onListenerConnected()
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
