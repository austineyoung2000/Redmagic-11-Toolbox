package com.elitedarkkaiser.redmagic

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder

class AutoPumpService : Service() {

    private lateinit var workerThread: HandlerThread
    private lateinit var handler: Handler
    private var lastProfile: String? = null
    @Volatile
    private var latestTemperatureF: Float? = null
    private var temperatureSubscription:
        DeviceTemperatureMonitor.Subscription? = null

    private val pollRunnable = object : Runnable {
        override fun run() {
            val tempF = applyPumpRule(
                latestTemperatureF
                    ?: DashboardSnapshot.readCpuTempF()
                        .toFloatOrNull()
            )
            CoolingControlNotification.updatePump(
                this@AutoPumpService,
                tempF,
                lastProfile
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(
            CoolingControlNotification.NOTIFICATION_ID,
            CoolingControlNotification.startPump(this)
        )

        workerThread = HandlerThread(
            "RedMagicAutoPump",
            android.os.Process.THREAD_PRIORITY_BACKGROUND
        ).apply {
            start()
        }
        handler = Handler(workerThread.looper)

        temperatureSubscription =
            DeviceTemperatureMonitor.subscribe(
                this,
                DeviceTemperatureMonitor.SamplingMode.BACKGROUND_CONTROL
            ) { temperatureC ->
                latestTemperatureF = temperatureC?.let {
                    (it * 9f / 5f) + 32f
                }
                if (::handler.isInitialized) {
                    handler.removeCallbacks(pollRunnable)
                    handler.post(pollRunnable)
                }
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        temperatureSubscription?.close()
        temperatureSubscription = null

        if (::handler.isInitialized) {
            handler.removeCallbacksAndMessages(null)
        }
        if (::workerThread.isInitialized) {
            workerThread.quitSafely()
        }
        CoolingControlNotification.stopPump(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun applyPumpRule(tempF: Float?): Float? {
        tempF ?: return null

        if (!HardwareScreenPolicy.isScreenInteractive(this@AutoPumpService)) {
            if (HardwareScreenPolicy.coolingShouldStopWhileScreenOff(tempF)) {
                if (lastProfile != "off") {
                    HardwareController.enablePump(false)
                }
                lastProfile = "off"
                return tempF
            }
        }

        val profile = when {
            tempF >= 105f -> "quick"
            tempF >= 95f -> "medium"
            else -> "slow"
        }

        if (profile != lastProfile) {
            if (HardwareScreenPolicy.blockCoolingWhileScreenOffUnlessHot(this@AutoPumpService, "auto-pump-screen-off")) {
                lastProfile = "off"
                return tempF
            }
            HardwareController.setPumpProfile(profile)
            lastProfile = profile

        }

        return tempF
    }
}
