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
    private val stateReceipt = CoolingStateReceipt(clock = { android.os.SystemClock.elapsedRealtime() })

    private fun needsApply(expected: String): Boolean {
        val stamp = HardwareController.coolingStamp(false)
        if (lastProfile != expected) return true
        if (stateReceipt.isCurrent(stamp)) return false
        val live = HardwareTelemetry.readForControl()
        if (live != null && CoolingStateMatch.pump(live.pumpEnabled, live.pumpFreq, live.pumpSpeed, expected) &&
            stamp == HardwareController.coolingStamp(false)) {
            stateReceipt.record(stamp)
            return false
        }
        HardwareScreenPolicy.invalidateCoolingShutdownReceipt()
        return true
    }

    private fun applyProfile(profile: String) {
        val before = HardwareController.coolingStamp(false)
        val applied = HardwareController.setPumpProfile(profile, force = true)
        stateReceipt.recordWrite(before, HardwareController.coolingStamp(false), applied)
        if (applied) lastProfile = profile
    }
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
        if (HardwareScreenPolicy.isScreenInteractive(this@AutoPumpService) && GameModeService.hasSelectedForegroundGame()) {
            stateReceipt.invalidate()
            return tempF
        }
        if (!HardwareScreenPolicy.isScreenInteractive(this@AutoPumpService)) {
            if (HardwareScreenPolicy.coolingShouldStopWhileScreenOff(tempF)) {
                if (needsApply("off")) applyProfile("off")
                return tempF
            }
        }

        tempF ?: return null
        val profile = when {
            tempF >= 105f -> "quick"
            tempF >= 95f -> "medium"
            else -> "slow"
        }

        if (needsApply(profile)) {
            if (HardwareScreenPolicy.blockCoolingWhileScreenOffUnlessHot(this@AutoPumpService, "auto-pump-screen-off")) {
                if (HardwareScreenPolicy.hasCoolingShutdownReceipt()) lastProfile = "off"
                return tempF
            }
            applyProfile(profile)

        }

        return tempF
    }
}
