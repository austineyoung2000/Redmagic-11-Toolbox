package com.elitedarkkaiser.redmagic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder

class FanLedService : Service() {

    companion object {
        private const val CHANNEL_ID = "fan_led_service_channel"
        private const val NOTIF_ID = 1102
    }

    private lateinit var workerThread: HandlerThread
    private lateinit var handler: Handler

    private val reapplyRunnable = Runnable {
        reapplySavedLedState()
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    handler.removeCallbacks(reapplyRunnable)
                    LedScreenPolicy.postScreenEvent(this@FanLedService, handler, intent.action, "normal-screen-off") {
                        if (
                            ChargingLedState.isEnabled(this@FanLedService) &&
                            ChargingLedState.isChargingNow(this@FanLedService)
                        ) {
                            ChargingLedState.setActive(
                                this@FanLedService,
                                true
                            )
                            ChargingLedState.applyChargingProfile(
                                this@FanLedService
                            )
                        } else {
                            ModeTransitionCoordinator
                                .restoreEffectiveOwner(
                                    this@FanLedService,
                                    "screen-off"
                                )
                        }
                    }
                }
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    scheduleLedReapply(delayMs = 1_500L)
                }
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED -> {
                    LedScreenPolicy.postScreenEvent(this@FanLedService, handler,
                        if (LedScreenPolicy.isScreenInteractive(this@FanLedService)) intent.action
                        else Intent.ACTION_SCREEN_OFF, "led-power-handoff") {
                        ModeTransitionCoordinator.restoreEffectiveOwner(this@FanLedService, "led-power-handoff")
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(
            NOTIF_ID,
            buildNotification("LED screen and power policy active")
        )

        workerThread = HandlerThread(
            "RedMagicFanLed",
            android.os.Process.THREAD_PRIORITY_BACKGROUND
        ).apply {
            start()
        }
        handler = Handler(workerThread.looper)

        registerFanLedReceiver()
        BootDiagnostics.record(this, "LED screen/power guard receiver registered")
        handler.post {
            ChargingLedRecovery.repairStaleChargingOwnership(
                this@FanLedService
            )
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (!LedScreenPolicy.isScreenInteractive(this)) {
            LedScreenPolicy.postScreenEvent(this, handler, Intent.ACTION_SCREEN_OFF,
                "led-guard-started-screen-off") { reapplySavedLedState() }
        } else {
            scheduleLedReapply()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching {
            unregisterReceiver(screenReceiver)
        }

        if (::handler.isInitialized) {
            handler.removeCallbacksAndMessages(null)
        }
        if (::workerThread.isInitialized) {
            workerThread.quitSafely()
        }

        super.onDestroy()
        BootDiagnostics.record(this, "LED screen/power guard destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerFanLedReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun scheduleLedReapply(delayMs: Long = 0L) {
        if (!::handler.isInitialized) return

        handler.removeCallbacks(reapplyRunnable)

        if (delayMs > 0L) {
            handler.postDelayed(reapplyRunnable, delayMs)
        } else {
            handler.post(reapplyRunnable)
        }
    }

    private fun reapplySavedLedState() {
        val prefs = getSharedPreferences(
            "redmagic_hw_controls_prefs",
            Context.MODE_PRIVATE
        )

        if (!LedScreenPolicy.isScreenInteractive(this)) {
            // A sticky restart can happen after the screen-off broadcast.
            ModeTransitionCoordinator.restoreEffectiveOwner(this, "led-guard-started-screen-off")
            return
        }
        if (!LedOwnership.canNormalApply(this)) {
            android.util.Log.i(
                "RedmagicLedOwnership",
                "FanLedService skipped normal LED " +
                    "apply because owner=" +
                    LedOwnership.current(this)
            )
            return
        }

        val fanEnabled =
            prefs.getBoolean(
                "fan_led_enabled",
                false
            )
        val fanEffect =
            prefs.getString(
                "fan_led_effect",
                "steady"
            ) ?: "steady"
        val fanColor =
            prefs.getInt("fan_led_color", 5)

        val logoEnabled =
            prefs.getBoolean(
                "logo_led_enabled",
                true
            )
        val logoEffect =
            prefs.getString(
                "logo_led_effect",
                "steady"
            ) ?: "steady"
        val logoColor =
            prefs.getInt("logo_led_color", 1)

        val shoulderEnabled =
            prefs.getBoolean(
                "shoulder_led_enabled",
                true
            )
        val shoulderEffect =
            prefs.getString(
                "shoulder_led_effect",
                "breathe"
            ) ?: "breathe"
        val shoulderColor =
            prefs.getInt(
                "shoulder_led_color",
                8
            )

        val signature = listOf(
            fanEnabled,
            fanEffect,
            fanColor,
            logoEnabled,
            logoEffect,
            logoColor,
            shoulderEnabled,
            shoulderEffect,
            shoulderColor
        ).joinToString("|")

        ModeTransitionCoordinator.applyLedProfile(
            context = this,
            owner = LedOwner.NORMAL,
            signature = signature
        ) {
            if (fanEnabled) {
                if (
                    fanEffect.startsWith(
                        "preset:"
                    )
                ) {
                    HardwareController
                        .setFanLedStockPreset(
                            fanEffect.removePrefix(
                                "preset:"
                            )
                        )
                } else {
                    HardwareController
                        .setFanLedEffect(
                            fanEffect,
                            fanColor
                        )
                }
            } else {
                HardwareController
                    .setFanLedEnabled(false)
            }

            if (logoEnabled) {
                HardwareController
                    .setLogoLedEffect(
                        logoEffect,
                        logoColor
                    )
            } else {
                HardwareController
                    .setLogoLedEnabled(false)
            }

            if (shoulderEnabled) {
                HardwareController
                    .setShoulderLedEffect(
                        shoulderEffect,
                        shoulderColor
                    )
            } else {
                HardwareController
                    .setShoulderLedEnabled(false)
            }
        }

        if (
            fanEnabled ||
            logoEnabled ||
            shoulderEnabled
        ) {
            updateNotification(
                "LED persistence active • Fan: " +
                    (
                        if (fanEnabled) {
                            "on"
                        } else {
                            "off"
                        }
                    ) +
                    " • Logo: " +
                    (
                        if (logoEnabled) {
                            "on"
                        } else {
                            "off"
                        }
                    ) +
                    " • Shoulder: " +
                    (
                        if (shoulderEnabled) {
                            "on"
                        } else {
                            "off"
                        }
                    )
            )
        } else {
            updateNotification("LED screen and power policy active • Saved zones off")
        }
    }

    private fun turnOffAllManagedLeds() {
        HardwareController.setFanLedEnabled(false)
        HardwareController.setLogoLedEnabled(false)
        HardwareController.setShoulderLedEnabled(false)
    }

    private fun buildNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Redmagic 11 Toolbox")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LED Persistence Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps custom LED settings synced with screen state"
            }

            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
