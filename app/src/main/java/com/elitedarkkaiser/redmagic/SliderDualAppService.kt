package com.elitedarkkaiser.redmagic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.Settings

class SliderDualAppService : Service() {
    companion object {
        private const val TAG = "RedmagicDualSlider"
        private const val CHANNEL_ID = "slider_dual_app_channel"
        private const val NOTIFICATION_ID = 1110
        private const val SETTING =
            "zte_keypad_slide_on_or_off"
        private const val DEBOUNCE_MS = 250L
        private val PACKAGE_PATTERN = Regex(
            "[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"
        )
    }

    private lateinit var workerThread: HandlerThread
    private lateinit var handler: Handler
    private var lastState: Int? = null

    private val dispatchRunnable = Runnable {
        dispatchCurrentState()
    }

    private val observer by lazy {
        object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                scheduleDispatch()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                "Dual-app slider monitoring active"
            )
        )

        workerThread = HandlerThread(
            "RedMagicDualSlider",
            android.os.Process.THREAD_PRIORITY_BACKGROUND
        ).apply { start() }
        handler = Handler(workerThread.looper)

        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(SETTING),
            false,
            observer
        )

        handler.post {
            lastState = readSliderState()
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        val config = SliderDualAppStorage.read(this)
        if (!config.enabled || !config.isComplete()) {
            stopSelf()
            return START_NOT_STICKY
        }

        handler.post {
            HardwareController.disableSliderSystemHandling()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching {
            contentResolver.unregisterContentObserver(observer)
        }
        if (::handler.isInitialized) {
            handler.removeCallbacksAndMessages(null)
        }
        if (::workerThread.isInitialized) {
            workerThread.quitSafely()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleDispatch() {
        if (!::handler.isInitialized) return
        handler.removeCallbacks(dispatchRunnable)
        handler.postDelayed(dispatchRunnable, DEBOUNCE_MS)
    }

    private fun dispatchCurrentState() {
        val state = readSliderState() ?: return
        val previous = lastState
        lastState = state

        // Starting or recreating the service must never launch an app.
        if (previous == null || previous == state) return

        val config = SliderDualAppStorage.read(this)
        if (!config.enabled || !config.isComplete()) return

        val pkg = config.packageForState(sliderUp = state == 1)
        if (pkg.isNullOrBlank() || !PACKAGE_PATTERN.matches(pkg)) {
            android.util.Log.w(TAG, "Ignored invalid app package")
            return
        }

        val launched = RootShell.exec(
            "monkey -p $pkg " +
                "-c android.intent.category.LAUNCHER 1 " +
                ">/dev/null 2>&1"
        )

        if (launched) {
            HapticFeedback.pulse(
                this,
                HapticFeedback.Event.MAGIC_KEY
            )
        }

        android.util.Log.i(
            TAG,
            "Slider ${if (state == 1) "up" else "down"} " +
                "launch result=$launched package=$pkg"
        )
    }

    private fun readSliderState(): Int? {
        val direct = runCatching {
            Settings.Global.getString(
                contentResolver,
                SETTING
            )
        }.getOrNull()?.trim()?.toIntOrNull()

        return direct?.takeIf { it == 0 || it == 1 }
            ?: HardwareController.readSliderState()
                ?.trim()
                ?.toIntOrNull()
                ?.takeIf { it == 0 || it == 1 }
    }

    private fun buildNotification(text: String): Notification {
        val builder = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ) {
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Dual-App Slider",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    "Launches configured apps when the Magic Key moves"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
