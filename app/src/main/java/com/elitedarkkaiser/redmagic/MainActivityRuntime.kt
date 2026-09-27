package com.elitedarkkaiser.redmagic

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal data class MainStatusSnapshot(
    val telemetry: HardwareTelemetrySnapshot,
    val deviceInfo: DeviceInfoCache,
    val dashboardSummary: String,
    val activeModeSummary: String,
    val temperatureHistory: List<TemperatureHistory.Sample>
)

/**
 * Owns MainActivity's background queue and foreground-only status refresh
 * lifecycle. Keeping the scheduling state together prevents callbacks and
 * temperature subscriptions from surviving a stopped or destroyed activity.
 */
internal class MainActivityRuntime(
    private val activity: MainActivity,
    private val onStatusSnapshot: (MainStatusSnapshot) -> Unit
) {
    private companion object {
        const val INITIAL_REFRESH_DELAY_MS = 2_500L
        const val PERIODIC_REFRESH_INTERVAL_MS = 30_000L
    }

    private val appContext: Context =
        activity.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { task ->
            Thread({
                Process.setThreadPriority(
                    Process.THREAD_PRIORITY_BACKGROUND
                )
                task.run()
            }, "RedMagicStatusRefresh")
        }

    private val refreshRunning = AtomicBoolean(false)
    private val refreshPending = AtomicBoolean(false)

    @Volatile
    private var started = false

    @Volatile
    private var uiReady = false

    @Volatile
    private var destroyed = false

    private var temperatureSubscription:
        DeviceTemperatureMonitor.Subscription? = null

    private val periodicRefresh = object : Runnable {
        override fun run() {
            if (!canRefresh()) return

            requestStatusRefresh()
            mainHandler.postDelayed(
                this,
                PERIODIC_REFRESH_INTERVAL_MS
            )
        }
    }

    private val initialRefresh = Runnable {
        if (!canRefresh()) return@Runnable

        requestStatusRefresh()
        schedulePeriodicRefresh()
    }

    fun submit(task: () -> Unit): Boolean {
        if (destroyed) return false

        return runCatching {
            executor.execute(task)
            true
        }.getOrDefault(false)
    }

    fun verifyRoot(onResult: (Boolean) -> Unit): Boolean {
        return submit {
            val rooted = RootShell.hasRoot()

            mainHandler.post {
                if (!hostAlive()) return@post
                onResult(rooted)
            }
        }
    }

    fun onUiReady() {
        if (destroyed) return

        uiReady = true
        if (!started) return

        startLiveTemperatureUpdates()
        mainHandler.removeCallbacks(initialRefresh)
        mainHandler.postDelayed(
            initialRefresh,
            INITIAL_REFRESH_DELAY_MS
        )
    }

    fun onStart() {
        if (destroyed) return

        started = true
        if (!uiReady) return

        startLiveTemperatureUpdates()
        mainHandler.removeCallbacks(initialRefresh)
        mainHandler.post(initialRefresh)
    }

    fun onStop() {
        started = false
        stopLiveTemperatureUpdates()
        mainHandler.removeCallbacks(initialRefresh)
        mainHandler.removeCallbacks(periodicRefresh)
        refreshPending.set(false)
    }

    fun destroy() {
        if (destroyed) return

        destroyed = true
        onStop()
        executor.shutdownNow()
    }

    fun requestStatusRefresh() {
        if (!canRefresh()) return

        if (!refreshRunning.compareAndSet(false, true)) {
            refreshPending.set(true)
            return
        }

        if (!submit(::runRefreshLoop)) {
            refreshRunning.set(false)
        }
    }

    private fun runRefreshLoop() {
        try {
            do {
                refreshPending.set(false)

                if (canRefresh()) {
                    publish(collectStatusSnapshot())
                }
            } while (
                canRefresh() &&
                refreshPending.getAndSet(false)
            )
        } finally {
            refreshRunning.set(false)

            /*
             * Cover a request arriving after the loop's final pending check
             * but before refreshRunning was cleared.
             */
            if (
                canRefresh() &&
                refreshPending.getAndSet(false)
            ) {
                requestStatusRefresh()
            }
        }
    }

    private fun collectStatusSnapshot(): MainStatusSnapshot {
        val rooted =
            hasCachedRootAccessStorage(appContext) ||
                RootShell.hasRoot()
        val telemetry = HardwareTelemetry.read()
        val deviceInfo =
            loadDeviceInfoCacheStorage(appContext)
                ?: DeviceInfoCache(
                    rom = HardwareController
                        .readShortRomFingerprint(),
                    cpu = HardwareController.readCpuModel(),
                    ram = HardwareController.readRamInfo()
                ).also {
                    saveDeviceInfoCacheStorage(appContext, it)
                }

        return MainStatusSnapshot(
            telemetry = telemetry,
            deviceInfo = deviceInfo,
            dashboardSummary = DashboardSnapshot.buildSummary(
                context = appContext,
                hardware = telemetry,
                rooted = rooted
            ),
            activeModeSummary =
                ActiveModeInspector.summary(appContext),
            temperatureHistory = TemperatureHistory.snapshot()
        )
    }

    private fun publish(snapshot: MainStatusSnapshot) {
        mainHandler.post {
            if (!canRefresh() || !hostAlive()) return@post
            onStatusSnapshot(snapshot)
        }
    }

    private fun schedulePeriodicRefresh() {
        mainHandler.removeCallbacks(periodicRefresh)
        if (!canRefresh()) return

        mainHandler.postDelayed(
            periodicRefresh,
            PERIODIC_REFRESH_INTERVAL_MS
        )
    }

    private fun startLiveTemperatureUpdates() {
        if (temperatureSubscription != null) return

        temperatureSubscription =
            DeviceTemperatureMonitor.subscribe(
                appContext,
                DeviceTemperatureMonitor.SamplingMode.FOREGROUND
            ) {
                requestStatusRefresh()
            }
    }

    private fun stopLiveTemperatureUpdates() {
        temperatureSubscription?.close()
        temperatureSubscription = null
    }

    private fun canRefresh(): Boolean {
        return !destroyed && started && uiReady
    }

    private fun hostAlive(): Boolean {
        return !activity.isFinishing && !activity.isDestroyed
    }
}
