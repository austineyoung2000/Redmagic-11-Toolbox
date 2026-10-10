package com.elitedarkkaiser.redmagic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import java.util.concurrent.Executors

/** An explicit system PendingIntent can start the app again after process death. */
class NotificationExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!DeviceCompatibility.isSupportedDevice()) return
        val token = intent.getStringExtra("token") ?: return
        val value = NotificationDeadline(token, intent.getLongExtra("deadline", 0L), intent.getIntExtra("boot", -1))
        val pending = goAsync()
        val app = context.applicationContext
        val lock = app.getSystemService(PowerManager::class.java).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "Redmagic:DurableNotificationExpiry").apply {
            setReferenceCounted(false)
            acquire(25_000L)
        }
        executor.execute {
            try { NotificationWindowDeadline.expire(app, value) }
            catch (error: Exception) { android.util.Log.e("NotificationDeadline", "Expiry recovery failed", error) }
            finally {
                if (lock.isHeld) lock.release()
                pending.finish()
            }
        }
    }
    companion object {
        private val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "RedmagicNotificationRecovery").apply { isDaemon = true }
        }
    }
}
