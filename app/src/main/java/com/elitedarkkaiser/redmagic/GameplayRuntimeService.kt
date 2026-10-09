package com.elitedarkkaiser.redmagic

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.IBinder
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class GameplayRuntimeService : Service() {
    private val runtimeBinder = Binder()

    private val foregroundHandler = Handler(Looper.getMainLooper())

    private val rootExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "RedMagicTriggerActions").apply {
                priority = Thread.NORM_PRIORITY - 1
            }
        }

    private val nativeTgkExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "RedMagicNativeTgk").apply {
                priority = Thread.NORM_PRIORITY - 1
            }
        }

    private val refreshRateExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "RedMagicRefreshRate").apply {
                priority = Thread.NORM_PRIORITY - 1
            }
        }

    /*
     * Foreground transitions are serialized on nativeTgkExecutor.
     * A generation invalidates work that has not started without
     * interrupting an operation already talking to system_server.
     * Interrupting the vendor setup delay can leave its enable state
     * only partially applied, so completed operations are followed by
     * the newest queued transition instead.
     */
    private val nativeTgkGeneration = AtomicLong(0L)

    @Volatile
    private var nativeHealthCheckInFlight = false

    @Volatile
    private var lastNativeHealthCheckAt = 0L

    private var screenReceiverRegistered = false

    @Volatile
    private var runtimeStarted = false

    @Volatile
    private var lastAccessibilityCheckAt = 0L

    @Volatile
    private var foregroundRootProcess: Process? = null

    @Volatile
    private var foregroundRootReader: Thread? = null

    @Volatile
    private var foregroundRootPollSeconds: Int? = null

    @Volatile
    private var lastForegroundRootSampleAt = 0L

    @Volatile
    private var forceForegroundReconcile = false

    @Volatile
    private var nativeTgkStartupCleanupComplete = false

    private val foregroundPackagePattern = Regex(
        """[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+"""
    )

    private val foregroundMonitor = object : Runnable {
        override fun run() {
            if (
                isAccessibilityCheckDue() &&
                !isAccessibilityEnabled()
            ) {
                stopSelf()
                return
            }

            runCatching {
                if (
                    nativeTgkStartupCleanupComplete &&
                    isScreenInteractive()
                ) {
                    ensureForegroundRootMonitor()

                    if (!hasFreshRootForegroundSample()) {
                        latestResumedPackage()?.let { packageName ->
                            reconcileDetectedPackage(packageName)
                        }
                    }
                } else {
                    stopForegroundRootMonitor()
                }
            }.onFailure {
                logRuntimeFailure("foreground monitor", it)
            }

            if (runtimeStarted) {
                foregroundHandler.postDelayed(
                    this,
                    FOREGROUND_MONITOR_INTERVAL_MS
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        if (
            !DeviceCompatibility.isSupportedDevice() ||
            !isAccessibilityEnabled()
        ) {
            stopSelf()
            return
        }

        serviceRunning = true
        android.util.Log.i(
            TAG,
            "Gameplay runtime created pid=${android.os.Process.myPid()}"
        )
        startRuntimeIfNeeded()
    }

    @Volatile
    private var lastForegroundPackage: String? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent
        ) {
            runCatching {
                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                    lastForegroundPackage = null
                    forceForegroundReconcile = false
                    stopForegroundRootMonitor()
                    deactivateNativeTgk("screen off")
                    RefreshRateOverlay.hide()
                    RefreshRateCoordinator.clearRuntimeState()
                    dispatchPerformanceReset("screen off")
                } else if (intent.action == Intent.ACTION_SCREEN_ON) {
                    forceForegroundReconcile = true
                    if (nativeTgkStartupCleanupComplete) {
                        ensureForegroundRootMonitor()
                    }
                }
            }.onFailure {
                logRuntimeFailure("screen state change", it)
            }
        }
    }

    private fun handleAccessibilityHint(reportedPackage: String?) {
        runCatching {
            if (reportedPackage.isNullOrBlank()) {
                return@runCatching
            }

            if (!nativeTgkStartupCleanupComplete) {
                forceForegroundReconcile = true
                return@runCatching
            }

            ensureForegroundRootMonitor()

            /*
             * When available, topResumedActivity is the authoritative
             * source on REDMAGIC firmware. Do not race it with delayed or
             * missing Usage Events.
             */
            if (hasFreshRootForegroundSample()) {
                return@runCatching
            }

            /*
             * Our non-touchable gameplay overlay and SystemUI can emit
             * window events even though the mapped game remains the
             * resumed activity. Resolve those ambiguous events through
             * UsageEvents; when another app is genuinely resumed, the
             * resolved package immediately drives TGK cleanup.
             */
            val pkg = latestResumedPackage()
                ?: if (
                    reportedPackage == packageName ||
                    reportedPackage == SYSTEM_UI_PACKAGE
                ) {
                    return@runCatching
                } else {
                    reportedPackage
                }

            reconcileDetectedPackage(pkg)
        }.onFailure {
            logRuntimeFailure("accessibility event", it)
        }
    }

    private fun reconcileDetectedPackage(
        packageName: String
    ) {
        val orientation =
            NativeTgkCoordinator.currentOrientation(this)
        val mappingNeedsApply =
            !NativeTgkEditorRuntime.isEditing() &&
                NativeTgkCoordinator.hasReadyMapping(
                    context = this,
                    packageName = packageName,
                    orientation = orientation
                ) &&
                (
                    !NativeTgkRuntimeState.matches(
                        packageName,
                        orientation
                    ) ||
                        overlayNeedsRecovery(
                            packageName,
                            orientation
                        )
                    )
        val nativeHealthCheckDue =
            !NativeTgkEditorRuntime.isEditing() &&
                NativeTgkRuntimeState.matches(
                    packageName,
                    orientation
                ) &&
                isNativeHealthCheckDue()
        val editorNeedsStop =
            NativeTgkEditorRuntime
                .shouldStopForForeground(packageName)
        val performanceNeedsApply =
            PerformanceModeCoordinator.needsReconcile(
                this,
                packageName
            )
        val force = forceForegroundReconcile

        if (
            force ||
            packageName != lastForegroundPackage ||
            mappingNeedsApply ||
            nativeHealthCheckDue ||
            editorNeedsStop ||
            performanceNeedsApply
        ) {
            forceForegroundReconcile = false
            handleForegroundPackage(packageName)
        }
    }

    private fun handleForegroundPackage(pkg: String) {
        val foregroundChanged = lastForegroundPackage != pkg
        lastForegroundPackage = pkg
        if (foregroundChanged) {
            NativeTgkDiagnostics.recordForeground(this, pkg)
        }
        dispatchNativeTgkForForeground(pkg)
        dispatchRefreshRateForForeground(pkg)

        if (
            pkg == packageName ||
            pkg == SYSTEM_UI_PACKAGE
        ) {
            return
        }

        val isTrackedGame =
            getSavedGamePackagesStorage(this).contains(pkg)
        val gameModeActive =
            isGameModeLedOverrideActiveStorage(this)

        /*
         * Game Mode and native TGK mappings are independent.
         * Forward the event to Game Mode only when its existing
         * profile lifecycle requires it.
         */
        if (isTrackedGame || gameModeActive) {
            runCatching {
                startService(
                    Intent(
                        this,
                        GameModeService::class.java
                    ).apply {
                        putExtra("foreground_pkg", pkg)
                    }
                )
            }.onFailure {
                logRuntimeFailure("Game Mode dispatch", it)
            }
        }
    }

    override fun onConfigurationChanged(
        newConfig: Configuration
    ) {
        super.onConfigurationChanged(newConfig)

        runCatching {
            /* Reconcile against the next authoritative root sample. */
            forceForegroundReconcile = true
            ensureForegroundRootMonitor()
        }.onFailure {
            logRuntimeFailure("configuration change", it)
        }
    }

    override fun onBind(intent: Intent?): IBinder = runtimeBinder

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        android.util.Log.i(
            TAG,
            "Gameplay runtime start action=${intent?.action} " +
                "flags=$flags startId=$startId"
        )

        if (
            !DeviceCompatibility.isSupportedDevice() ||
            !isAccessibilityEnabled()
        ) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startRuntimeIfNeeded()

        when (intent?.action) {
            ACTION_FOREGROUND_HINT ->
                handleAccessibilityHint(
                    intent.getStringExtra(EXTRA_FOREGROUND_PACKAGE)
                )
            ACTION_CHECK_ACCESSIBILITY -> {
                foregroundHandler.postDelayed(
                    {
                        if (!isAccessibilityEnabled()) {
                            stopSelf()
                        }
                    },
                    ACCESSIBILITY_STATE_SETTLE_MS
                )
            }
        }

        return START_STICKY
    }

    private fun startRuntimeIfNeeded() {
        if (runtimeStarted) {
            return
        }
        runtimeStarted = true

        registerScreenReceiver()
        foregroundHandler.removeCallbacks(foregroundMonitor)
        nativeTgkStartupCleanupComplete = false

        /*
         * Repair any native TGK state left behind by an earlier
         * process termination before accepting new foreground
         * application events.
         */
        NativeTgkRuntimeState.clear()
        RefreshRateOverlay.hide()
        RefreshRateCoordinator.clearRuntimeState()
        runCatching {
            refreshRateExecutor.execute {
                runCatching {
                    PerformanceModeCoordinator
                        .recoverAndResetIfManaged(
                            applicationContext,
                            "gameplay runtime started"
                        )
                }.onFailure {
                    logRuntimeFailure(
                        "startup performance reset",
                        it
                    )
                }
            }
        }.onFailure {
            logRuntimeFailure("queue startup reset", it)
        }
        val startupGeneration =
            nativeTgkGeneration.incrementAndGet()

        runCatching {
            nativeTgkExecutor.submit {
                runCatching {
                    NativeTgkCoordinator.disable(
                        applicationContext,
                        "gameplay runtime started"
                    )
                }.onFailure {
                    logRuntimeFailure("startup TGK cleanup", it)
                }

                foregroundHandler.post {
                    if (
                        nativeTgkExecutor.isShutdown ||
                        nativeTgkGeneration.get() !=
                        startupGeneration
                    ) {
                        return@post
                    }

                    nativeTgkStartupCleanupComplete = true
                    forceForegroundReconcile = true
                    ensureForegroundRootMonitor()
                    foregroundHandler.removeCallbacks(
                        foregroundMonitor
                    )
                    foregroundHandler.post(foregroundMonitor)
                }
            }
        }.onFailure {
            logRuntimeFailure("runtime initialization", it)
        }

        submitRootAction {
            HardwareServiceActions
                .startTriggersIfAutoStartEnabled(this)
        }
    }

    override fun onDestroy() {
        android.util.Log.i(TAG, "Gameplay runtime stopping")
        serviceRunning = false
        runtimeStarted = false
        lastForegroundPackage = null
        forceForegroundReconcile = false
        nativeTgkStartupCleanupComplete = false
        stopForegroundRootMonitor()
        foregroundHandler.removeCallbacksAndMessages(null)
        NativeTgkRuntimeState.clear()
        RefreshRateOverlay.hide()
        RefreshRateCoordinator.clearRuntimeState()
        val resetPerformanceMode =
            PerformanceModeCoordinator.isActive()

        val cleanupGeneration =
            nativeTgkGeneration.incrementAndGet()

        if (screenReceiverRegistered) {
            runCatching {
                unregisterReceiver(screenReceiver)
            }
            screenReceiverRegistered = false
        }

        runCatching {
            nativeTgkExecutor.submit {
                if (
                    nativeTgkGeneration.get() ==
                    cleanupGeneration
                ) {
                    NativeTgkCoordinator.disable(
                        applicationContext,
                        "gameplay runtime stopped"
                    )
                }
            }
        }
        nativeTgkExecutor.shutdown()
        refreshRateExecutor.shutdownNow()

        if (resetPerformanceMode) {
            Thread(
                {
                    PerformanceModeCoordinator.resetIfOwned(
                        applicationContext,
                        "gameplay runtime stopped"
                    )
                },
                "RedMagicPerformanceCleanup"
            ).start()
        }

        rootExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun dispatchNativeTgkForForeground(
        packageName: String
    ) {
        if (NativeTgkEditorRuntime.isEditing()) {
            if (
                NativeTgkEditorRuntime
                    .shouldStopForForeground(packageName)
            ) {
                stopService(
                    Intent(
                        this,
                        NativeTgkEditorService::class.java
                    )
                )
            }
            invalidateNativeTgkWorkForEditor()
            return
        }

        val orientation =
            NativeTgkCoordinator.currentOrientation(this)

        val mappingReady =
            NativeTgkCoordinator.hasReadyMapping(
                context = this,
                packageName = packageName,
                orientation = orientation
            )

        if (!mappingReady) {
            /* Always remove any stale target window, even if the
             * in-memory TGK state was already cleared. */
            deactivateNativeTgk(
                "left mapped app or orientation"
            )
            return
        }

        if (
            NativeTgkRuntimeState.matches(
                packageName,
                orientation
            )
        ) {
            if (
                overlayNeedsRecovery(
                    packageName,
                    orientation
                )
            ) {
                scheduleNativeTgkApply(
                    packageName,
                    orientation,
                    "gameplay overlay missing"
                )
            } else {
                scheduleNativeHealthCheck(
                    packageName,
                    orientation
                )
            }
            return
        }

        /*
         * Mark the native path active before the two-second vendor
         * setup delay so the legacy F7/F8 readers cannot perform
         * quick actions during the transition.
         */
        scheduleNativeTgkApply(
            packageName,
            orientation,
            "foreground mapping changed"
        )
    }

    private fun scheduleNativeTgkApply(
        packageName: String,
        orientation: NativeTgkOrientation,
        reason: String
    ) {
        NativeTgkRuntimeState.markActive(
            packageName,
            orientation
        )

        val generation =
            nativeTgkGeneration.incrementAndGet()
        lastNativeHealthCheckAt = SystemClock.elapsedRealtime()

        android.util.Log.i(
            "RedmagicForeground",
            "Queueing native mapping generation=$generation " +
                "package=$packageName orientation=$orientation " +
                "reason=$reason"
        )

        runCatching {
            nativeTgkExecutor.submit nativeApply@{
                if (
                    nativeTgkGeneration.get() != generation ||
                    !NativeTgkRuntimeState.matches(
                        packageName,
                        orientation
                    )
                ) {
                    return@nativeApply
                }

                val result = runCatching {
                    NativeTgkCoordinator.applyForegroundMapping(
                        context = applicationContext,
                        packageName = packageName,
                        orientation = orientation
                    )
                }.getOrElse {
                    logRuntimeFailure("native TGK apply", it)
                    NativeTgkRuntimeState.clearIfMatches(
                        packageName,
                        orientation
                    )
                    NativeTgkGameplayOverlay.hide()
        TriggerBridgeFeedback.hide()
                    return@nativeApply
                }

                if (
                    !result.success &&
                    nativeTgkGeneration.get() == generation
                ) {
                    NativeTgkRuntimeState.clearIfMatches(
                        packageName,
                        orientation
                    )
                } else if (
                    result.success &&
                    nativeTgkGeneration.get() == generation
                ) {
                    NativeTgkRuntimeState.markBackendIfMatches(
                        packageName,
                        orientation,
                        result.backend
                    )
                    if (result.backend == TriggerMappingBackend.MODULE_BACKEND &&
                        NativeTgkStorage.getProfile(applicationContext, packageName)?.bridgeVisualFeedback == true) {
                        TriggerBridgeFeedback.show(applicationContext)
                    } else {
                        TriggerBridgeFeedback.hide()
                    }
                }
            }
        }.onFailure {
            NativeTgkRuntimeState.clearIfMatches(
                packageName,
                orientation
            )
            NativeTgkGameplayOverlay.hide()
        TriggerBridgeFeedback.hide()
        }
    }

    private fun deactivateNativeTgk(reason: String) {
        NativeTgkRuntimeState.clear()
        NativeTgkGameplayOverlay.hide()
        TriggerBridgeFeedback.hide()

        val generation =
            nativeTgkGeneration.incrementAndGet()
        lastNativeHealthCheckAt = 0L

        runCatching {
            nativeTgkExecutor.submit {
                runCatching {
                    if (
                        nativeTgkGeneration.get() == generation
                    ) {
                        NativeTgkCoordinator.disable(
                            applicationContext,
                            reason
                        )
                    }
                }.onFailure {
                    logRuntimeFailure("disable native TGK", it)
                }
            }
        }.onFailure {
            logRuntimeFailure("queue native TGK disable", it)
        }
    }

    private fun invalidateNativeTgkWorkForEditor() {
        nativeTgkGeneration.incrementAndGet()
        lastNativeHealthCheckAt = 0L
        NativeTgkRuntimeState.clear()
        NativeTgkGameplayOverlay.hide()
        TriggerBridgeFeedback.hide()
    }

    private fun isNativeHealthCheckDue(): Boolean {
        return !nativeHealthCheckInFlight &&
            SystemClock.elapsedRealtime() -
            lastNativeHealthCheckAt >=
            NATIVE_HEALTH_CHECK_INTERVAL_MS
    }

    private fun scheduleNativeHealthCheck(
        packageName: String,
        orientation: NativeTgkOrientation
    ) {
        if (!isNativeHealthCheckDue()) {
            return
        }

        val generation = nativeTgkGeneration.get()
        lastNativeHealthCheckAt = SystemClock.elapsedRealtime()
        nativeHealthCheckInFlight = true

        runCatching {
            nativeTgkExecutor.submit nativeHealth@{
                try {
                    if (
                        nativeTgkGeneration.get() != generation ||
                        !NativeTgkRuntimeState.matches(
                            packageName,
                            orientation
                        )
                    ) {
                        return@nativeHealth
                    }

                    val liveState = runCatching {
                        NativeTgkCoordinator.readActiveState(
                            applicationContext
                        )
                    }.getOrElse {
                        logRuntimeFailure("trigger backend health check", it)
                        return@nativeHealth
                    }
                    val mappingEnabled =
                        liveState.success &&
                            liveState.state?.mappingEnabled() == true

                    if (!mappingEnabled && liveState.success) {
                        foregroundHandler.post {
                            if (
                                nativeTgkGeneration.get() ==
                                generation &&
                                NativeTgkRuntimeState.matches(
                                    packageName,
                                    orientation
                                )
                            ) {
                                scheduleNativeTgkApply(
                                    packageName,
                                    orientation,
                                    "live mapping state disabled"
                                )
                            }
                        }
                    }
                } finally {
                    nativeHealthCheckInFlight = false
                }
            }
        }.onFailure {
            nativeHealthCheckInFlight = false
        }
    }

    private fun overlayNeedsRecovery(
        packageName: String,
        orientation: NativeTgkOrientation
    ): Boolean {
        /*
         * Runtime ownership alone is insufficient: Android can remove
         * either overlay window while the system mapping remains active.
         * Treat detached windows as a broken foreground session so the
         * normal apply path restores the views and verifies the mapping.
         */
        return NativeTgkCoordinator.expectsGameplayOverlay(
            this,
            packageName,
            orientation
        ) && !NativeTgkGameplayOverlay.isVisibleFor(
            packageName,
            orientation
        )
    }

    private fun dispatchRefreshRateForForeground(
        packageName: String
    ) {
        runCatching {
            refreshRateExecutor.execute {
                runCatching {
                    RefreshRateCoordinator.onForegroundPackage(
                        applicationContext,
                        packageName
                    )
                    PerformanceModeCoordinator.onForegroundPackage(
                        applicationContext,
                        packageName
                    )
                    RefreshRateOverlay.refresh()
                }.onFailure {
                    logRuntimeFailure("performance profile dispatch", it)
                }
            }
        }
    }

    private fun dispatchPerformanceReset(reason: String) {
        runCatching {
            refreshRateExecutor.execute {
                runCatching {
                    PerformanceModeCoordinator.resetIfOwned(
                        applicationContext,
                        reason
                    )
                }.onFailure {
                    logRuntimeFailure("performance reset", it)
                }
            }
        }
    }

    private fun latestResumedPackage(): String? {
        return runCatching {
            val manager = getSystemService(
                Context.USAGE_STATS_SERVICE
            ) as UsageStatsManager
            val end = System.currentTimeMillis()
            val events = manager.queryEvents(
                end - FOREGROUND_EVENT_LOOKBACK_MS,
                end
            )
            val event = UsageEvents.Event()
            var latestPackage: String? = null
            var latestTimestamp = Long.MIN_VALUE

            while (events.hasNextEvent()) {
                events.getNextEvent(event)

                if (
                    event.eventType !=
                    UsageEvents.Event.ACTIVITY_RESUMED &&
                    event.eventType !=
                    UsageEvents.Event.MOVE_TO_FOREGROUND
                ) {
                    continue
                }

                val candidate = event.packageName
                if (
                    !candidate.isNullOrBlank() &&
                    event.timeStamp >= latestTimestamp
                ) {
                    latestPackage = candidate
                    latestTimestamp = event.timeStamp
                }
            }

            latestPackage
        }.getOrNull()
    }

    private fun isScreenInteractive(): Boolean {
        return getSystemService(PowerManager::class.java)
            ?.isInteractive == true
    }

    private fun isAccessibilityCheckDue(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (
            now - lastAccessibilityCheckAt <
            ACCESSIBILITY_CHECK_INTERVAL_MS
        ) {
            return false
        }

        lastAccessibilityCheckAt = now
        return true
    }

    private fun isAccessibilityEnabled(): Boolean {
        return isAccessibilityConfigured(this)
    }

    @Synchronized
    private fun ensureForegroundRootMonitor() {
        if (!needsForegroundMonitoring()) {
            stopForegroundRootMonitor()
            return
        }

        val desiredPollSeconds = desiredForegroundPollSeconds()
        if (
            foregroundRootProcess?.isAlive == true &&
            foregroundRootPollSeconds == desiredPollSeconds
        ) {
            return
        }

        if (foregroundRootProcess != null) {
            stopForegroundRootMonitor()
        }

        val process = runCatching {
            ProcessBuilder(
                "su",
                "-c",
                rootForegroundMonitorCommand(
                    desiredPollSeconds
                )
            ).redirectErrorStream(true).start()
        }.getOrElse {
            android.util.Log.e(
                "RedmagicForeground",
                "Unable to start authoritative foreground monitor",
                it
            )
            return
        }

        foregroundRootProcess = process
        foregroundRootPollSeconds = desiredPollSeconds
        lastForegroundRootSampleAt = 0L

        val readerThread = Thread(
            {
                runCatching {
                    process.inputStream.bufferedReader().useLines {
                        lines ->
                        lines.forEach { rawLine ->
                            val detected = rawLine.trim()
                            if (
                                foregroundPackagePattern.matches(
                                    detected
                                )
                            ) {
                                foregroundHandler.post {
                                    if (
                                        foregroundRootProcess ===
                                        process &&
                                        isScreenInteractive()
                                    ) {
                                        lastForegroundRootSampleAt =
                                            SystemClock.elapsedRealtime()
                                        runCatching {
                                            reconcileDetectedPackage(
                                                detected
                                            )
                                        }.onFailure {
                                            logRuntimeFailure(
                                                "root foreground sample",
                                                it
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                synchronized(this) {
                    if (foregroundRootProcess === process) {
                        foregroundRootProcess = null
                        foregroundRootReader = null
                        foregroundRootPollSeconds = null
                        lastForegroundRootSampleAt = 0L
                    }
                }
            },
            "RedMagicForegroundReader"
        ).apply {
            priority = Thread.NORM_PRIORITY - 1
        }

        foregroundRootReader = readerThread
        readerThread.start()
    }

    @Synchronized
    private fun stopForegroundRootMonitor() {
        val process = foregroundRootProcess
        val reader = foregroundRootReader

        foregroundRootProcess = null
        foregroundRootReader = null
        foregroundRootPollSeconds = null
        lastForegroundRootSampleAt = 0L

        reader?.interrupt()
        if (process != null) {
            runCatching { process.destroy() }
            if (process.isAlive) {
                runCatching { process.destroyForcibly() }
            }
        }
    }

    private fun needsForegroundMonitoring(): Boolean {
        return NativeTgkEditorRuntime.isEditing() ||
            NativeTgkStorage.enabledPackages(this).isNotEmpty() ||
            RefreshRateStorage.readProfiles(this).any {
                it.enabled || it.pendingReset
            } ||
            PerformanceModeStorage.readProfiles(this).any {
                it.enabled
            }
    }

    private fun hasFreshRootForegroundSample(): Boolean {
        val pollSeconds = foregroundRootPollSeconds
            ?: desiredForegroundPollSeconds()
        val maximumAgeMs =
            pollSeconds.coerceAtLeast(1) * 1_000L +
                ROOT_FOREGROUND_SAMPLE_GRACE_MS

        return ForegroundAuthorityPolicy.hasFreshRootSample(
            processAlive = foregroundRootProcess?.isAlive == true,
            lastSampleAtMs = lastForegroundRootSampleAt,
            nowMs = SystemClock.elapsedRealtime(),
            maximumAgeMs = maximumAgeMs
        )
    }

    private fun desiredForegroundPollSeconds(): Int {
        val activeProfile =
            NativeTgkEditorRuntime.isEditing() ||
                NativeTgkRuntimeState.activePackage() != null ||
                RefreshRateCoordinator.isActive() ||
                PerformanceModeCoordinator.isActive()

        return if (activeProfile) {
            ACTIVE_FOREGROUND_POLL_SECONDS
        } else {
            IDLE_FOREGROUND_POLL_SECONDS
        }
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) {
            return
        }

        val filter = IntentFilter(
            Intent.ACTION_SCREEN_OFF
        ).apply {
            addAction(Intent.ACTION_SCREEN_ON)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                screenReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(
                screenReceiver,
                filter
            )
        }

        screenReceiverRegistered = true
    }

    companion object {
        @Volatile
        private var serviceRunning = false

        private const val TAG = "RedmagicGameplayRuntime"
        private const val ACTION_FOREGROUND_HINT =
            "com.elitedarkkaiser.redmagic.GAMEPLAY_FOREGROUND_HINT"
        private const val ACTION_CHECK_ACCESSIBILITY =
            "com.elitedarkkaiser.redmagic.CHECK_GAMEPLAY_ACCESSIBILITY"
        private const val EXTRA_FOREGROUND_PACKAGE =
            "foreground_package"
        private const val SYSTEM_UI_PACKAGE =
            "com.android.systemui"
        private const val FOREGROUND_EVENT_LOOKBACK_MS =
            15_000L
        private const val FOREGROUND_MONITOR_INTERVAL_MS =
            750L
        private const val ACCESSIBILITY_CHECK_INTERVAL_MS =
            5_000L
        private const val ACCESSIBILITY_STATE_SETTLE_MS =
            1_500L
        private const val NATIVE_HEALTH_CHECK_INTERVAL_MS =
            10_000L
        private const val ACTIVE_FOREGROUND_POLL_SECONDS = 1
        private const val IDLE_FOREGROUND_POLL_SECONDS = 3
        private const val ROOT_FOREGROUND_SAMPLE_GRACE_MS = 1_500L

        private fun rootForegroundMonitorCommand(
            pollSeconds: Int
        ) = """
            while true; do
                line="${'$'}(
                    dumpsys activity activities 2>/dev/null |
                    grep -m 1 'topResumedActivity='
                )"
                detected="${'$'}(
                    printf '%s\n' "${'$'}line" |
                    sed -n 's/.* u[0-9][0-9]* \([^/ ]*\)\/.*/\1/p'
                )"
                if [ -n "${'$'}detected" ]; then
                    printf '%s\n' "${'$'}detected"
                fi
                sleep $pollSeconds
            done
        """.trimIndent()

        internal fun ensureRunning(context: Context) {
            if (!isAccessibilityConfigured(context)) {
                return
            }

                        /*
             * Start the runtime from the live app/accessibility process before
             * asking the independent watchdog to bind it. REDMAGIC may reject
             * the watchdog's foreground-service launch immediately after an
             * APK replacement, even though an in-process service start is
             * allowed. The watchdog remains the long-term recovery owner.
             */
            if (!serviceRunning) {
                startRuntimeIntent(
                    context,
                    Intent(
                        context,
                        GameplayRuntimeService::class.java
                    )
                )
            }
GameplayRuntimeWatchdogService.ensureRunning(context)
        }

        internal fun forwardForegroundHint(
            context: Context,
            packageName: String
        ) {
            val intent = Intent(
                context,
                GameplayRuntimeService::class.java
            ).setAction(ACTION_FOREGROUND_HINT).apply {
                putExtra(EXTRA_FOREGROUND_PACKAGE, packageName)
            }
                                    /*
             * Never discard the event that woke recovery. Besides restoring
             * the runtime directly, this lets it reconcile the selected game
             * before the root foreground monitor produces its first sample.
             */
startRuntimeIntent(context, intent)
            if (!serviceRunning) {
                
            
                GameplayRuntimeWatchdogService.ensureRunning(context)
            }
        }

        internal fun checkAccessibilityState(context: Context) {
            if (serviceRunning) {
                startRuntimeIntent(
                    context,
                    Intent(
                        context,
                        GameplayRuntimeService::class.java
                    ).setAction(ACTION_CHECK_ACCESSIBILITY)
                )
            }
            GameplayRuntimeWatchdogService
                .checkAccessibilityState(context)
        }

        private fun startRuntimeIntent(
            context: Context,
            intent: Intent
        ) {
            runCatching {
                context.applicationContext.startService(intent)
            }.onFailure {
                android.util.Log.e(
                    TAG,
                    "Unable to dispatch gameplay runtime service",
                    it
                )
            }
        }

        internal fun isAccessibilityConfigured(
            context: Context
        ): Boolean {
            val appContext = context.applicationContext
            val expectedComponent = ComponentName(
                appContext,
                TriggerAccessibilityService::class.java
            )

            val managerEnabled = runCatching {
                appContext
                    .getSystemService(AccessibilityManager::class.java)
                    ?.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                    )
                    ?.any { service ->
                        val info = service.resolveInfo.serviceInfo
                        ComponentName(
                            info.packageName,
                            info.name
                        ) == expectedComponent
                    } == true
            }.getOrDefault(false)

            if (managerEnabled) {
                return true
            }

            return runCatching {
                Settings.Secure.getString(
                    appContext.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                    ?.split(':')
                    ?.mapNotNull {
                        ComponentName.unflattenFromString(it)
                    }
                    ?.any { it == expectedComponent } == true
            }.getOrDefault(false)
        }
    }

    private fun submitRootAction(action: () -> Unit) {
        runCatching {
            rootExecutor.execute {
                runCatching { action() }.onFailure {
                    logRuntimeFailure("root action", it)
                }
            }
        }.onFailure {
            logRuntimeFailure("queue root action", it)
        }
    }

    private fun logRuntimeFailure(
        operation: String,
        error: Throwable
    ) {
        android.util.Log.e(
            TAG,
            "Contained failure during $operation",
            error
        )
    }

}
