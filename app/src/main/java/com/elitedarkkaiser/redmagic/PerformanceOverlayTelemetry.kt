package com.elitedarkkaiser.redmagic

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import java.io.File

data class PerformanceOverlaySnapshot(
    val temperatureC: Float?,
    val fanRpm: Int?
)

/**
 * Collects overlay telemetry away from the main thread. Temperature
 * uses the shared non-root monitor. The NX809J fan counter is read at
 * most once every 30 seconds, first directly and then through one
 * read-only root fallback when kernel permissions require it.
 */
object PerformanceOverlayTelemetry {
    private const val FAN_REFRESH_MS = 30_000L

    private val lock = Any()

    private var workerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var temperatureSubscription:
        DeviceTemperatureMonitor.Subscription? = null

    @Volatile
    private var temperatureC: Float? = null

    @Volatile
    private var fanRpm: Int? = null

    private val fanRunnable = object : Runnable {
        override fun run() {
            fanRpm = readFanRpm()
            RefreshRateOverlay.refresh()

            synchronized(lock) {
                handler?.postDelayed(this, FAN_REFRESH_MS)
            }
        }
    }

    fun start(context: Context) {
        synchronized(lock) {
            if (handler != null) return

            val appContext = context.applicationContext
            val thread = HandlerThread(
                "RedMagicPerformanceOverlay",
                android.os.Process.THREAD_PRIORITY_BACKGROUND
            ).apply { start() }

            workerThread = thread
            handler = Handler(thread.looper).also {
                it.post(fanRunnable)
            }
            temperatureSubscription =
                DeviceTemperatureMonitor.subscribe(
                    appContext,
                    DeviceTemperatureMonitor.SamplingMode.FOREGROUND
                ) { value ->
                    temperatureC = value
                    RefreshRateOverlay.refresh()
                }
        }
    }

    fun stop() {
        synchronized(lock) {
            temperatureSubscription?.close()
            temperatureSubscription = null
            handler?.removeCallbacksAndMessages(null)
            handler = null
            workerThread?.quitSafely()
            workerThread = null
            temperatureC = null
            fanRpm = null
        }
    }

    fun snapshot(): PerformanceOverlaySnapshot {
        return PerformanceOverlaySnapshot(
            temperatureC = temperatureC,
            fanRpm = fanRpm
        )
    }

    private fun readFanRpm(): Int? {
        val path = DeviceCompatibility.Paths.FAN_RPM
        val node = File(path)
        if (!node.exists()) return null

        runCatching {
            node.readText().trim().toIntOrNull()
        }.getOrNull()?.let { return it }

        return RootShell.execForOutput(
            "cat '$path' 2>/dev/null"
        )?.trim()?.toIntOrNull()
    }
}
