package com.elitedarkkaiser.redmagic

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder

class AutoFanService : Service() {

    companion object {
        private const val HYSTERESIS_F = 5f
    }

    private lateinit var workerThread: HandlerThread
    private lateinit var handler: Handler
    private var lastAppliedLevel = -1
    private val stateReceipt = CoolingStateReceipt(clock = { android.os.SystemClock.elapsedRealtime() })

    private fun needsApply(expected: Int): Boolean {
        val stamp = HardwareController.coolingStamp(true)
        if (lastAppliedLevel != expected) return true
        if (stateReceipt.isCurrent(stamp)) return false
        val live = HardwareTelemetry.readForControl()
        if (live != null && CoolingStateMatch.fan(live.fanEnabled, live.fanLevel, expected) &&
            stamp == HardwareController.coolingStamp(true)) {
            stateReceipt.record(stamp)
            return false
        }
        HardwareScreenPolicy.invalidateCoolingShutdownReceipt()
        return true
    }

    private fun applyLevel(level: Int) {
        val before = HardwareController.coolingStamp(true)
        val applied = HardwareController.setFanLevel(level, force = true)
        stateReceipt.recordWrite(before, HardwareController.coolingStamp(true), applied)
        if (applied) lastAppliedLevel = level
    }
    @Volatile
    private var latestTemperatureF: Float? = null
    private var temperatureSubscription:
        DeviceTemperatureMonitor.Subscription? = null

    private val loop = object : Runnable {
        override fun run() {
            if (HardwareScreenPolicy.isScreenInteractive(this@AutoFanService) &&
                (GameModeService.hasSelectedForegroundGame() ||
                    (CallLightingState.isActive(this@AutoFanService) &&
                        CallLightingState.wasFanPausedForCall(this@AutoFanService)))) {
                stateReceipt.invalidate()
                return
            }
            val tempF = latestTemperatureF
                ?: HardwareController.readTemperatureF()
            val nextLevel = chooseStableFanLevel(tempF, lastAppliedLevel)

            if (!HardwareScreenPolicy.isScreenInteractive(this@AutoFanService)) {
                if (HardwareScreenPolicy.coolingShouldStopWhileScreenOff(tempF)) {
                    if (needsApply(0)) applyLevel(0)
                    updateNotification(tempF, lastAppliedLevel)
                    return
                }
            }

            if (nextLevel != null && needsApply(nextLevel)) {
                if (HardwareScreenPolicy.blockCoolingWhileScreenOffUnlessHot(this@AutoFanService, "auto-fan-screen-off")) {
                    if (HardwareScreenPolicy.hasCoolingShutdownReceipt()) lastAppliedLevel = 0
                    updateNotification(tempF, lastAppliedLevel)
                    return
                }
                applyLevel(nextLevel)
            }

            updateNotification(
                tempF,
                lastAppliedLevel
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(
            CoolingControlNotification.NOTIFICATION_ID,
            CoolingControlNotification.startFan(this)
        )

        workerThread = HandlerThread(
            "RedMagicAutoFan",
            android.os.Process.THREAD_PRIORITY_BACKGROUND
        ).apply {
            start()
        }
        handler = Handler(workerThread.looper)

        handler.post {
            lastAppliedLevel = HardwareController.readFanLevel() ?: -1
            loop.run()
        }

        temperatureSubscription =
            DeviceTemperatureMonitor.subscribe(
                this,
                DeviceTemperatureMonitor.SamplingMode.BACKGROUND_CONTROL
            ) { temperatureC ->
                latestTemperatureF = temperatureC?.let {
                    (it * 9f / 5f) + 32f
                }
                if (::handler.isInitialized) {
                    handler.removeCallbacks(loop)
                    handler.post(loop)
                }
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        CoolingControlNotification.stopFan(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun chooseStableFanLevel(tempF: Float?, currentLevel: Int): Int? {
        if (tempF == null) return currentLevel.takeIf { it >= 0 }

        val baseLevel = HardwareController.chooseAutoFanLevelForTempF(tempF)

        if (currentLevel < 0) return baseLevel

        if (baseLevel > currentLevel) {
            return baseLevel
        }

        if (baseLevel < currentLevel) {
            val holdThreshold = when (currentLevel) {
                5 -> 131f - HYSTERESIS_F
                4 -> 122f - HYSTERESIS_F
                3 -> 113f - HYSTERESIS_F
                2 -> 104f - HYSTERESIS_F
                1 -> 95f - HYSTERESIS_F
                else -> Float.MIN_VALUE
            }

            return if (tempF < holdThreshold) baseLevel else currentLevel
        }

        return currentLevel
    }

    private fun updateNotification(
        tempF: Float?,
        level: Int?
    ) {
        CoolingControlNotification.updateFan(
            this,
            tempF,
            level
        )
    }
}
