package com.elitedarkkaiser.redmagic

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock

class RgbCycleService : Service() {
    companion object {
        private const val OWNER_RECHECK_MS = 750L
        private const val MIN_FRAME_INTERVAL_MS = 500L
    }

    private lateinit var workerThread: HandlerThread
    private lateinit var handler: Handler
    private var state = RgbStudioState()
    private var colorIndexLogo = 0
    private var colorIndexShoulder = 0
    private var colorIndexFan = 0
    private var nextLogoAt = 0L
    private var nextShoulderAt = 0L
    private var nextFanAt = 0L
    private var screenOffAt: Long? = null
    private var ledsOffForTimeout = false
    private var lastFrameAt = 0L

    private val cycleRunnable = object : Runnable {
        override fun run() {
            runCycleTick()
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOffAt = SystemClock.elapsedRealtime()
                    scheduleNext(0L)
                }
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    screenOffAt = null
                    ledsOffForTimeout = false
                    resetDeadlines()
                    scheduleNext(0L)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(
            CoolingControlNotification.NOTIFICATION_ID,
            CoolingControlNotification.startRgb(this)
        )

        workerThread = HandlerThread(
            "RedMagicRgbCycle",
            android.os.Process.THREAD_PRIORITY_BACKGROUND
        ).apply { start() }
        handler = Handler(workerThread.looper)

        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
        )

        if (!LedScreenPolicy.isScreenInteractive(this)) {
            screenOffAt = SystemClock.elapsedRealtime()
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        state = RgbStudioStorage.read(this)
        if (!state.enabled) {
            stopSelf()
            return START_NOT_STICKY
        }

        normalizeIndexes()
        resetDeadlines()
        updateNotification()
        scheduleNext(0L)
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        if (::handler.isInitialized) {
            handler.removeCallbacksAndMessages(null)
        }
        if (::workerThread.isInitialized) {
            workerThread.quitSafely()
        }
        CoolingControlNotification.stopRgb(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun runCycleTick() {
        state = RgbStudioStorage.read(this)
        if (!state.enabled) {
            stopSelf()
            return
        }

        if (LedOwnership.current(this) != LedOwner.RGB_CYCLE) {
            scheduleNext(OWNER_RECHECK_MS)
            return
        }

        if (shouldPauseForScreenTimeout()) {
            if (!ledsOffForTimeout) {
                ModeTransitionCoordinator
                    .applyLedProfile(
                        context = this,
                        owner = LedOwner.NONE,
                        signature =
                            "rgb-screen-timeout",
                        force = true
                    ) {
                        HardwareController
                            .turnOffAllLeds()
                    }
                ledsOffForTimeout = true
            }
            scheduleNext(OWNER_RECHECK_MS)
            return
        }

        if (ledsOffForTimeout) {
            ledsOffForTimeout = false
            resetDeadlines()
        }

        val now = SystemClock.elapsedRealtime()
        val frameDelay =
            MIN_FRAME_INTERVAL_MS - (now - lastFrameAt)
        if (lastFrameAt != 0L && frameDelay > 0L) {
            scheduleNext(frameDelay)
            return
        }

        var frameApplied = false
        if (state.syncZones) {
            if (now >= nextLogoAt) {
                val color = nextColor(colorIndexLogo)
                applyFrame(
                    effectName = state.effect,
                    logoColor = color,
                    shoulderColor = color,
                    fanColor = color,
                )
                frameApplied = true
                colorIndexLogo = advanceIndex(colorIndexLogo)
                colorIndexShoulder = colorIndexLogo
                colorIndexFan = colorIndexLogo
                nextLogoAt = now + state.logoSpeedMs
                nextShoulderAt = nextLogoAt
                nextFanAt = nextLogoAt
            }
        } else {
            var logoColor: Int? = null
            var shoulderColor: Int? = null
            var fanColor: Int? = null

            if (now >= nextLogoAt) {
                logoColor = nextColor(colorIndexLogo)
                colorIndexLogo = advanceIndex(colorIndexLogo)
                nextLogoAt = now + state.logoSpeedMs
            }
            if (now >= nextShoulderAt) {
                shoulderColor = nextColor(colorIndexShoulder)
                colorIndexShoulder = advanceIndex(colorIndexShoulder)
                nextShoulderAt = now + state.shoulderSpeedMs
            }
            if (now >= nextFanAt) {
                fanColor = nextColor(colorIndexFan)
                colorIndexFan = advanceIndex(colorIndexFan)
                nextFanAt = now + state.fanSpeedMs
            }

            if (
                logoColor != null ||
                shoulderColor != null ||
                fanColor != null
            ) {
                applyFrame(
                    effectName = state.effect,
                    logoColor = logoColor,
                    shoulderColor = shoulderColor,
                    fanColor = fanColor,
                )
                frameApplied = true
            }
        }

        if (frameApplied) {
            lastFrameAt = SystemClock.elapsedRealtime()
        }

        val nextAt = minOf(nextLogoAt, nextShoulderAt, nextFanAt)
        scheduleNext((nextAt - SystemClock.elapsedRealtime()).coerceAtLeast(100L))
    }

    private fun applyFrame(
        effectName: String,
        logoColor: Int?,
        shoulderColor: Int?,
        fanColor: Int?
    ) {
        val signature = listOf(
            effectName,
            logoColor,
            shoulderColor,
            fanColor
        ).joinToString("|")

        ModeTransitionCoordinator.applyLedProfile(
            context = this,
            owner = LedOwner.RGB_CYCLE,
            signature = signature
        ) {
            HardwareController.setRgbCycleFrame(
                effectName = effectName,
                logoColor = logoColor,
                shoulderColor = shoulderColor,
                fanColor = fanColor
            )
        }
    }

    private fun shouldPauseForScreenTimeout(): Boolean {
        if (LedScreenPolicy.isScreenInteractive(this)) {
            screenOffAt = null
            return false
        }

        val offAt = screenOffAt ?: SystemClock.elapsedRealtime().also {
            screenOffAt = it
        }
        val timeoutMs = state.screenOffTimeoutMinutes * 60_000L
        return SystemClock.elapsedRealtime() - offAt >= timeoutMs
    }

    private fun resetDeadlines() {
        val now = SystemClock.elapsedRealtime()
        nextLogoAt = now
        nextShoulderAt = now
        nextFanAt = now
    }

    private fun normalizeIndexes() {
        val size = state.colors.size.coerceAtLeast(1)
        colorIndexLogo %= size
        colorIndexShoulder %= size
        colorIndexFan %= size
    }

    private fun nextColor(index: Int): Int {
        return state.colors[index.coerceIn(0, state.colors.lastIndex)]
    }

    private fun advanceIndex(index: Int): Int {
        return (index + 1) % state.colors.size
    }

    private fun scheduleNext(delayMs: Long) {
        if (!::handler.isInitialized) return
        handler.removeCallbacks(cycleRunnable)
        handler.postDelayed(cycleRunnable, delayMs)
    }

    private fun updateNotification() {
        val mode =
            if (state.syncZones) {
                "Synchronized"
            } else {
                "Per-zone"
            }

        CoolingControlNotification.updateRgb(
            this,
            mode
        )
    }
}
