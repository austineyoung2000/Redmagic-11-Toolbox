package com.elitedarkkaiser.redmagic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock

/**
 * A deliberately small process that owns foreground-service priority and an
 * important binding to the heavier gameplay runtime. REDMAGIC firmware can
 * terminate the overlay-hosting process with SIGKILL and discard its started
 * service record; the independent binding recreates that process without
 * depending on AccessibilityManager to rebind its crashed service.
 */
class GameplayRuntimeWatchdogService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var bindRequested = false
    private var bindRecoveryAllowedAt = 0L
    private var runtimeBinder: IBinder? = null

    private val runtimeConnection = object : ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName,
            service: IBinder
        ) {
            bindRequested = true
            bindRecoveryAllowedAt = 0L
            runtimeBinder = service
            android.util.Log.i(
                TAG,
                "Gameplay runtime process connected"
            )
        }

        override fun onServiceDisconnected(name: ComponentName) {
            runtimeBinder = null
            bindRecoveryAllowedAt =
                SystemClock.elapsedRealtime() +
                    DISCONNECT_GRACE_MS
            scheduleRuntimeRecovery("service disconnected")
        }

        override fun onBindingDied(name: ComponentName) {
            runtimeBinder = null
            safelyUnbindRuntime()
            scheduleRuntimeRecovery("binding died")
        }

        override fun onNullBinding(name: ComponentName) {
            runtimeBinder = null
            safelyUnbindRuntime()
            scheduleRuntimeRecovery("null binding")
        }
    }

    private val watchdog = object : Runnable {
        override fun run() {
            if (
                !GameplayRuntimeService.isAccessibilityConfigured(
                    this@GameplayRuntimeWatchdogService
                )
            ) {
                stopSelf()
                return
            }

            if (runtimeBinder?.isBinderAlive != true) {
                val canReplaceBinding =
                    !bindRequested ||
                        SystemClock.elapsedRealtime() >=
                        bindRecoveryAllowedAt
                if (canReplaceBinding) {
                    safelyUnbindRuntime()
                    ensureRuntimeBound()
                }
            }

            handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        android.util.Log.i(
            TAG,
            "Gameplay watchdog created pid=${android.os.Process.myPid()}"
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (
            !DeviceCompatibility.isSupportedDevice() ||
            !GameplayRuntimeService.isAccessibilityConfigured(this)
        ) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startWatchdogForeground()
        ensureRuntimeBound()
        handler.removeCallbacks(watchdog)
        handler.post(watchdog)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        safelyUnbindRuntime()
        stopForeground(STOP_FOREGROUND_REMOVE)
        android.util.Log.i(TAG, "Gameplay watchdog stopped")
        super.onDestroy()
    }

    private fun scheduleRuntimeRecovery(reason: String) {
        android.util.Log.w(
            TAG,
            "Scheduling gameplay runtime recovery: $reason"
        )
        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, REBIND_DELAY_MS)
    }

    private fun ensureRuntimeBound() {
        if (
            bindRequested &&
            runtimeBinder?.isBinderAlive == true
        ) {
            return
        }

        val intent = Intent(
            this,
            GameplayRuntimeService::class.java
        )
        bindRequested = runCatching {
            bindService(
                intent,
                runtimeConnection,
                Context.BIND_AUTO_CREATE or
                    Context.BIND_IMPORTANT or
                    Context.BIND_ABOVE_CLIENT
            )
        }.getOrElse {
            android.util.Log.e(
                TAG,
                "Unable to bind gameplay runtime",
                it
            )
            false
        }
        if (bindRequested) {
            bindRecoveryAllowedAt =
                SystemClock.elapsedRealtime() +
                    BIND_CONNECT_TIMEOUT_MS
        }
    }

    private fun safelyUnbindRuntime() {
        if (!bindRequested) {
            bindRecoveryAllowedAt = 0L
            runtimeBinder = null
            return
        }

        runCatching {
            unbindService(runtimeConnection)
        }
        bindRequested = false
        bindRecoveryAllowedAt = 0L
        runtimeBinder = null
    }

    private fun startWatchdogForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL,
                "Gameplay Runtime",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    "Keeps configured gaming overlays and controls active"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }

        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL)
        } else {
            Notification.Builder(this)
        }

        startForeground(
            NOTIFICATION_ID,
            builder
                .setContentTitle("Redmagic gameplay controls")
                .setContentText(
                    "Monitoring configured games for overlays and controls"
                )
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentIntent(pendingIntent)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        )
    }

    companion object {
        private const val TAG = "RedmagicGameplayWatchdog"
        private const val ACTION_KEEP_WATCHDOG =
            "com.elitedarkkaiser.redmagic.KEEP_GAMEPLAY_WATCHDOG"
        private const val ACTION_CHECK_ACCESSIBILITY =
            "com.elitedarkkaiser.redmagic.CHECK_WATCHDOG_ACCESSIBILITY"
        private const val NOTIFICATION_CHANNEL = "gameplay_runtime"
        private const val NOTIFICATION_ID = 1401
        private const val WATCHDOG_INTERVAL_MS = 2_000L
        private const val REBIND_DELAY_MS = 350L
        private const val DISCONNECT_GRACE_MS = 1_000L
        private const val BIND_CONNECT_TIMEOUT_MS = 3_000L

        internal fun ensureRunning(context: Context) {
            if (!GameplayRuntimeService.isAccessibilityConfigured(context)) {
                return
            }

            dispatch(
                context,
                Intent(
                    context,
                    GameplayRuntimeWatchdogService::class.java
                ).setAction(ACTION_KEEP_WATCHDOG),
                foreground = true
            )
        }

        internal fun checkAccessibilityState(context: Context) {
            dispatch(
                context,
                Intent(
                    context,
                    GameplayRuntimeWatchdogService::class.java
                ).setAction(ACTION_CHECK_ACCESSIBILITY),
                foreground = false
            )
        }

        private fun dispatch(
            context: Context,
            intent: Intent,
            foreground: Boolean
        ) {
            runCatching {
                val appContext = context.applicationContext
                if (
                    foreground &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
            }.onFailure {
                android.util.Log.e(
                    TAG,
                    "Unable to dispatch gameplay watchdog",
                    it
                )
            }
        }
    }
}
