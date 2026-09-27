package com.elitedarkkaiser.redmagic

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import com.google.android.material.slider.Slider
import android.widget.TextView
import android.view.animation.LinearInterpolator
import android.animation.ValueAnimator
import android.animation.ArgbEvaluator
import android.widget.Toast
import com.elitedarkkaiser.redmagic.storage.AppPrefs
import com.elitedarkkaiser.redmagic.state.LedState
import com.google.android.material.button.MaterialButton
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.elitedarkkaiser.redmagic.ui.AppTheme

class MainActivity : Activity() {
    companion object {
        private const val MASTER_BACKUP_EXPORT_REQUEST = 8201
        private const val MASTER_BACKUP_IMPORT_REQUEST = 8202
    }

    private var useFahrenheit = true


    private lateinit var tempText: TextView
    private lateinit var curveStatusText: TextView
    private lateinit var fanSeek: Slider
    private lateinit var autoCurveCheck: CheckBox

    private lateinit var quietCurveButton: Button
    private lateinit var balancedCurveButton: Button
    private lateinit var turboCurveButton: Button

    private lateinit var deviceRomValue: TextView
    private lateinit var deviceCpuValue: TextView
    private lateinit var deviceRamValue: TextView
    private lateinit var dashboardText: TextView
    private lateinit var activeModeText: TextView
    private lateinit var thermalHistoryView:
        com.elitedarkkaiser.redmagic.ui.ThermalHistoryView
    private var lastDisplayedRpm: Int = -1
    private var lastDisplayedTempF: Float? = null

    private var coolingTabBuilt = false
    private var controlsTabBuilt = false
    private var hardwareTabBuilt = false
    private var lightingTabBuilt = false

    private lateinit var homeTab: LinearLayout
    private lateinit var coolingTab: LinearLayout
    private lateinit var controlsTab: LinearLayout
    private lateinit var hardwareTab: LinearLayout
    private lateinit var lightingTab: LinearLayout
    private var magicKeyStatusLabelRef: TextView? = null
    private var dialogRefreshPump: (() -> Unit)? = null
    private var dialogRefreshShoulderLed: (() -> Unit)? = null
    private var dialogRefreshLogoLed: (() -> Unit)? = null
    private var dialogRefreshFanLed: (() -> Unit)? = null
    private var gameModeAppsTextRef: TextView? = null

    private var smartPumpStatusView: TextView? = null
    private var smartPumpSpeedView: TextView? = null

    private lateinit var homeNav: LinearLayout
    private lateinit var coolingNav: LinearLayout
    private lateinit var controlsNav: LinearLayout
    private lateinit var lightingNav: LinearLayout

    private var selectedCurve = "balanced"
    private var autoFanCurveEnabled = false
    private var realTimePreviewEnabled = true

    private var fanLedEnabled = true
    private var fanLedEffect = "steady"
    private var fanLedColor = 1

    private var logoLedEnabled = true
    private var logoLedEffect = "steady"
    private var logoLedColor = 1

    private var shoulderLedEnabled = true
    private var shoulderLedEffect = "breathe"
    private var shoulderLedColor = 8

    private var pumpEnabled = false
    private var pumpProfile = "quick"
    private var autoPumpEnabled = false




    private val bgColor get() = AppTheme.bgColor
    private val panelColor get() = AppTheme.panelColor
    private val panelPressed get() = AppTheme.panelPressed
    private val borderColor get() = AppTheme.borderColor

    private val accent get() = AppTheme.accentColor
    private val chipOn get() = AppTheme.chipOnColor
    private val chipActive get() = AppTheme.chipActiveColor
    private val danger get() = AppTheme.dangerColor
    private val textPrimary get() = AppTheme.textPrimary
    private val textSecondary get() = AppTheme.textSecondary
    private val typeface: Typeface? = Typeface.SANS_SERIF
    private val highlightBorder get() = AppTheme.highlightBorder
    private val statusRefreshHandler =
        Handler(Looper.getMainLooper())

    private val statusRefreshExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { task ->
            Thread({
                android.os.Process.setThreadPriority(
                    android.os.Process.THREAD_PRIORITY_BACKGROUND
                )
                task.run()
            }, "RedMagicStatusRefresh")
        }

    private val statusRefreshRunning = AtomicBoolean(false)
    private val statusRefreshPending = AtomicBoolean(false)

    private val statusRefreshRunnable = object : Runnable {
        override fun run() {
            refreshStatus()
            statusRefreshHandler.postDelayed(this, 30_000L)
        }
    }

    private var mainUiReady = false
    private var deviceCapabilities =
        DeviceCapabilities.unknown()
    private var temperatureSubscription:
        DeviceTemperatureMonitor.Subscription? = null

    private val initialStatusRefreshRunnable = Runnable {
        if (
            !mainUiReady ||
            isFinishing ||
            isDestroyed
        ) {
            return@Runnable
        }

        refreshStatus()
        startStatusRefreshLoop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTheme.configure(this)

        if (!DeviceCompatibility.isSupportedDevice()) {
            showUnsupportedDeviceDialog()
            return
        }
        
        initDefaultTriggerMappingsStorage(this)
        deviceCapabilities =
            deviceCapabilitiesStorage(this)

        val needsFirstInstallSetup =
            !isFirstInstallPermissionsPromptedStorage(this) ||
                !PermissionActions.hasUsageStatsPermission(this)

        if (needsFirstInstallSetup) {
            FirstInstallPermissionsDialog.show(this) {
                setCachedRootAccessStorage(this, true)
                launchMainUi()
            }
            return
        }

        verifyRootAndLaunch()
    }

    @Deprecated("Legacy result API retained for Android 9 compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (
            NativeTgkDocumentTransfer.handleActivityResult(
                activity = this,
                requestCode = requestCode,
                resultCode = resultCode,
                data = data
            )
        ) {
            return
        }

        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        when (requestCode) {
            MASTER_BACKUP_EXPORT_REQUEST -> {
                submitBackgroundTask {
                    val error = runCatching {
                        val json = MasterProfileStorage
                            .createBackupJson(this)
                        contentResolver.openOutputStream(
                            uri,
                            "wt"
                        )?.bufferedWriter()?.use {
                            it.write(json)
                        } ?: error("Unable to open backup destination")
                    }.exceptionOrNull()

                    runOnUiThread {
                        Toast.makeText(
                            this,
                            error?.message
                                ?: "Master backup exported",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            MASTER_BACKUP_IMPORT_REQUEST -> {
                submitBackgroundTask {
                    val result = runCatching {
                        val raw = contentResolver
                            .openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            ?: error("Unable to read backup")
                        MasterProfileStorage.importBackup(
                            this,
                            raw
                        )
                    }

                    runOnUiThread {
                        result.onSuccess { imported ->
                            Toast.makeText(
                                this,
                                "Imported ${imported.savedProfileCount} " +
                                    "saved profiles and restored settings",
                                Toast.LENGTH_LONG
                            ).show()
                            recreate()
                        }.onFailure { error ->
                            Toast.makeText(
                                this,
                                error.message
                                    ?: "Backup import failed",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun verifyRootAndLaunch() {
        if (hasCachedRootAccessStorage(this)) {
            launchMainUi()
            return
        }

        runCatching {
            statusRefreshExecutor.execute {
                val rooted = RootShell.hasRoot()

                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }

                    if (rooted) {
                        setCachedRootAccessStorage(this, true)
                        launchMainUi()
                    } else {
                        showRootRequiredDialog()
                    }
                }
            }
        }.onFailure {
            if (!isFinishing && !isDestroyed) {
                showRootRequiredDialog()
            }
        }
    }

    private fun startStatusRefreshLoop() {
        statusRefreshHandler.removeCallbacks(statusRefreshRunnable)
        statusRefreshHandler.postDelayed(
            statusRefreshRunnable,
            30_000L
        )
    }

    private fun startLiveTemperatureUpdates() {
        if (temperatureSubscription != null) return

        temperatureSubscription =
            DeviceTemperatureMonitor.subscribe(
                this,
                DeviceTemperatureMonitor.SamplingMode.FOREGROUND
            ) {
                refreshStatus()
            }
    }

    private fun stopLiveTemperatureUpdates() {
        temperatureSubscription?.close()
        temperatureSubscription = null
    }


    override fun onStart() {
        super.onStart()

        if (mainUiReady) {
            startLiveTemperatureUpdates()
            statusRefreshHandler.removeCallbacks(
                initialStatusRefreshRunnable
            )
            statusRefreshHandler.post(
                initialStatusRefreshRunnable
            )
        }
    }

    override fun onResume() {
        super.onResume()

        val savedUnit = isUseFahrenheitStorage(this)
        if (savedUnit != useFahrenheit) {
            useFahrenheit = savedUnit
            if (mainUiReady) {
                refreshStatus()
            }
        }
    }

    override fun onStop() {
        stopLiveTemperatureUpdates()
        statusRefreshHandler.removeCallbacks(
            initialStatusRefreshRunnable
        )
        statusRefreshHandler.removeCallbacks(
            statusRefreshRunnable
        )
        statusRefreshPending.set(false)
        super.onStop()
    }

    override fun onDestroy() {
        stopLiveTemperatureUpdates()
        statusRefreshHandler.removeCallbacks(
            statusRefreshRunnable
        )
        statusRefreshHandler.removeCallbacks(
            initialStatusRefreshRunnable
        )
        statusRefreshPending.set(false)
        statusRefreshExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun showMagicKeyAppPicker(
        targetButton: Button,
        shortcutButton: Button?
    ) {
        MagicKeyAppPickerDialog.show(
            activity = this,
            targetButton = targetButton,
            statusLabel = magicKeyStatusLabelRef,
            applyLaunchAppMagicKeyMode = { pkg, label, statusLabel, sliderButton ->
                MagicKeyActions.applyLaunchAppMode(
                    activity = this,
                    pkg = pkg,
                    label = label,
                    statusLabel = statusLabel,
                    sliderButton = sliderButton,
                    shortcutButton = shortcutButton,
                    runBackground = { task ->
                            submitBackgroundTask(task)
                        },
                        refreshStatus = { refreshStatus() }
                )
            },
            deps = magicKeyPickerDeps()
        )
    }

    private fun magicKeyPickerDeps() =
        MagicKeyAppPickerDialog.Deps(
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            panelColor = panelColor,
            borderColor = borderColor,
            typeface = typeface,
            dp = { value -> dp(value) },
            roundedBg = { fill, stroke, radius ->
                roundedBg(fill, stroke, radius)
            },
            roundedFill = { color, radius ->
                roundedFill(color, radius)
            },
            space = { value -> space(value) }
        )

    private fun showSliderDualAppDialog(targetButton: Button) {
        val status = magicKeyStatusLabelRef ?: return

        SliderDualAppDialog.show(
            activity = this,
            statusLabel = status,
            pickerDeps = magicKeyPickerDeps(),
            runBackground = { task ->
                submitBackgroundTask(task)
            },
            onSaved = {
                targetButton.text =
                    SliderDualAppStorage.summary(this)
                refreshStatus()
            }
        )
    }

    private fun showMagicKeyShortcutPicker(
        targetButton: Button,
        appButton: Button?
    ) {
        MagicKeyShortcutDialog.show(
            activity = this,
            targetButton = targetButton,
            appButton = appButton,
            statusLabel = magicKeyStatusLabelRef,
            pickerDeps = magicKeyPickerDeps(),
            runBackground = { task ->
                submitBackgroundTask(task)
            },
            refreshStatus = { refreshStatus() }
        )
    }

    private fun applyFanLedSelection(
        effect: String,
        color: Int
    ) {
        submitBackgroundTask {
            applyFanLedSelectionNow(effect, color)
        }
    }

    private fun applyFanLedSelectionNow(
        effect: String,
        color: Int
    ) {
        if (effect.startsWith("preset:")) {
            HardwareController.setFanLedStockPreset(
                effect.removePrefix("preset:")
            )
        } else {
            HardwareController.setFanLedEffect(effect, color)
        }
    }

    private fun buildAutoPumpStatusText(): Pair<String, String> {
        val tempF = lastDisplayedTempF
        if (tempF == null) {
            return "Pump Mode: AUTO • Unknown temp" to "Speed: ? • Freq: ?"
        }

        val profile = when {
            tempF >= 105f -> "Quick"
            tempF >= 90f -> "Medium"
            else -> "Slow"
        }

        val speed = when (profile) {
            "Quick" -> 80
            "Medium" -> 60
            else -> 40
        }

        return "Pump Mode: AUTO • $profile (${TempFormat.formatDisplayTempFromF(tempF, useFahrenheit)})" to
            "Speed: $speed • Freq: 4"
    }


    private fun applyMasterProfileToUiState(
        profile: MasterProfile
    ) {
        val hardware = profile.hardware
        autoFanCurveEnabled = hardware.autoFanEnabled
        selectedCurve = hardware.fanCurveMode
        pumpEnabled = hardware.pumpEnabled
        pumpProfile = hardware.pumpProfile
        autoPumpEnabled = hardware.autoPumpEnabled
        fanLedEnabled = hardware.fanLedEnabled
        fanLedEffect = hardware.fanLedEffect
        fanLedColor = hardware.fanLedColor
        logoLedEnabled = hardware.logoLedEnabled
        logoLedEffect = hardware.logoLedEffect
        logoLedColor = hardware.logoLedColor
        shoulderLedEnabled = hardware.shoulderLedEnabled
        shoulderLedEffect = hardware.shoulderLedEffect
        shoulderLedColor = hardware.shoulderLedColor
        realTimePreviewEnabled = profile.realtimePreviewEnabled
        useFahrenheit = profile.useFahrenheit
        fanSeek.value = hardware.fanLevel.toFloat()
        restoreFanCurveUiState()
        refreshSmartPumpStatusViews()
        refreshStatus()
    }

    private fun refreshSmartPumpStatusViews() {
        val statusView = smartPumpStatusView ?: return
        val speedView = smartPumpSpeedView ?: return

        if (autoPumpEnabled) {
            val status = buildAutoPumpStatusText()
            statusView.text = status.first
            speedView.text = status.second
        } else {
            val manualLabel = pumpProfile.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
            val manualSpeed = when (pumpProfile.lowercase()) {
                "slow" -> 40
                "medium" -> 60
                "quick" -> 80
                "experimental" -> 90
                else -> 60
            }
            statusView.text = "Pump Mode: MANUAL • $manualLabel"
            speedView.text = "Speed: $manualSpeed • Freq: 4"
        }
    }

    private fun applyPumpProfile(profile: String) {
        pumpProfile = profile
        pumpEnabled = true
        autoPumpEnabled = false
        savePumpStateStorage(this, pumpEnabled, pumpProfile)
        saveAutoPumpStateStorage(this, autoPumpEnabled)
        refreshSmartPumpStatusViews()

        submitBackgroundTask {
            HardwareServiceActions.stopAutoPump(this)
            HardwareController.setPumpProfile(profile)
            refreshStatus()
        }
    }

    private fun confirmExperimentalPumpThenApply(
        onApplied: () -> Unit = {}
    ) {
        if (savedPumpStateStorage(this).experimentalAccepted) {
            applyPumpProfile("experimental")
            onApplied()
            return
        }

        ExperimentalPumpDialog.show(
            activity = this,
            onCancel = { },
            onConfirm = {
                setPumpExperimentalAcceptedStorage(this, true)
                applyPumpProfile("experimental")
                onApplied()
            },
            deps = ExperimentalPumpDialog.Deps(
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                panelColor = panelColor,
                borderColor = borderColor,
                panelPressed = panelPressed,
                typeface = typeface,
                dp = { value -> dp(value) },
                roundedBg = { fill, stroke, radius -> roundedBg(fill, stroke, radius) },
                roundedFill = { color, radius -> roundedFill(color, radius) },
                space = { value -> space(value) }
            )
        )
    }

    private fun showRootRequiredDialog() {
        DeviceGateDialogs.showRootRequiredDialog(
            activity = this,
            onClose = { finish() },
            deps = DeviceGateDialogs.Deps(
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                panelColor = panelColor,
                borderColor = borderColor,
                panelPressed = panelPressed,
                accent = accent,
                typeface = typeface,
                dp = { value -> dp(value) },
                roundedBg = { fill, stroke, radius -> roundedBg(fill, stroke, radius) },
                roundedFill = { color, radius -> roundedFill(color, radius) }
            )
        )
    }

    private fun showUnsupportedDeviceDialog() {
        DeviceGateDialogs.showUnsupportedDeviceDialog(
            activity = this,
            model = DeviceCompatibility
                .identity()
                .detectedModel,
            onClose = { finish() },
            deps = DeviceGateDialogs.Deps(
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                panelColor = panelColor,
                borderColor = borderColor,
                panelPressed = panelPressed,
                accent = accent,
                typeface = typeface,
                dp = { value -> dp(value) },
                roundedBg = { fill, stroke, radius ->
                    roundedBg(fill, stroke, radius)
                },
                roundedFill = { color, radius ->
                    roundedFill(color, radius)
                }
            )
        )
    }

    private fun startCapabilityScan() {
        DeviceScanActions.runBackgroundScan(this) {
            capabilities ->
            deviceCapabilities = capabilities

            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    refreshCapabilityAwareTabs()
                }
            }
        }
    }

    private fun launchMainUi() {
        startCapabilityScan()

        MainUiStartup.applySavedHardwareState(
            applySavedFanLedStateOnLaunch = {
                val state = savedFanLedStateStorage(this)
                fanLedEnabled = state.enabled
                fanLedEffect = state.effect
                fanLedColor = state.color
            },
            applySavedLogoLedStateOnLaunch = {
                val state = savedLogoLedStateStorage(this)
                logoLedEnabled = state.enabled
                logoLedEffect = state.effect
                logoLedColor = state.color
            },
            applySavedShoulderLedStateOnLaunch = {
                val state = savedShoulderLedStateStorage(this)
                shoulderLedEnabled = state.enabled
                shoulderLedEffect = state.effect
                shoulderLedColor = state.color
            },
            applySavedPumpStateOnLaunch = {
                val state = savedPumpStateStorage(this)
                pumpEnabled = state.enabled
                pumpProfile = state.profile
                autoPumpEnabled = state.autoEnabled
            },
            setRealTimePreviewEnabled = { value -> realTimePreviewEnabled = value },
            isRealTimePreviewEnabledSaved = { isRealTimePreviewEnabledStorage(this) },
            setUseFahrenheit = { value -> useFahrenheit = value },
            isUseFahrenheitSaved = { isUseFahrenheitStorage(this) },
            setAutoPumpEnabled = { value -> autoPumpEnabled = value },
            isAutoPumpEnabledSaved = { savedPumpStateStorage(this).autoEnabled }
        )

        val result = MainUiLauncher.launch(
            activity = this,
            topInset = getStatusBarHeight(),
            bgColor = bgColor,
            dp = { value -> dp(value) },
            createHomeTab = { createHomeTab() },
            createCoolingTab = { createCoolingTab() },
            createControlsTab = { createControlsTab() },
            createHardwareTab = { createHardwareTab() },
            createLightingTab = { createLightingTab() },
            bottomNavBar = { bottomNavBar() }
        )

        homeTab = result.homeTab
        coolingTab = result.coolingTab
        controlsTab = result.controlsTab
        hardwareTab = result.hardwareTab
        lightingTab = result.lightingTab

        coolingTabBuilt = false
        controlsTabBuilt = false
        hardwareTabBuilt = false
        lightingTabBuilt = false

        MainUiStartup.applyLaunchHardware(
            fanLedEnabled = fanLedEnabled,
            fanLedEffect = fanLedEffect,
            fanLedColor = fanLedColor,
            logoLedEnabled = logoLedEnabled,
            logoLedEffect = logoLedEffect,
            logoLedColor = logoLedColor,
            shoulderLedEnabled = shoulderLedEnabled,
            shoulderLedEffect = shoulderLedEffect,
            shoulderLedColor = shoulderLedColor,
            pumpEnabled = pumpEnabled,
            pumpProfile = pumpProfile,
            applyFanLedSelection = { effect, color -> applyFanLedSelection(effect, color) },
            startFanLedService = { HardwareServiceActions.startFanLed(this) },
            stopFanLedService = { HardwareServiceActions.stopFanLed(this) }
        )

        if (autoPumpEnabled) {
            HardwareServiceActions.startAutoPump(this)
        }

        if (RgbStudioStorage.isEnabled(this)) {
            HardwareServiceActions.startRgbCycle(this)
        }

        if (readTriggerPrefsSnapshot(this).triggersAutoStart) {
            submitBackgroundTask {
                HardwareServiceActions
                    .startTriggersIfAutoStartEnabled(this)
                refreshStatus()
            }
        }

        switchTab("home")
        mainUiReady = true
        startLiveTemperatureUpdates()
        statusRefreshHandler.postDelayed(
            initialStatusRefreshRunnable,
            2_500L
        )
        // Do not start background services just because the UI opened.
        // Game Mode starts from selected-app foreground events.
        // Charging Mode starts from boot, plug state, or explicit toggle.
    }

    private fun restoreFanCurveUiState() {
        selectedCurve = selectedCurveStorage(this)
        autoFanCurveEnabled = isAutoFanEnabledStorage(this)
        val fanAvailable =
            !deviceCapabilities.scanComplete ||
                deviceCapabilities.fanAvailable

        if (fanAvailable) {
            autoCurveCheck.isChecked =
                autoFanCurveEnabled
        }

        if (!fanAvailable) {
            curveStatusText.text =
                "Fan controls unavailable on this ROM"
        } else if (autoFanCurveEnabled) {
            curveStatusText.text = "Auto fan curve active • Running in background service"
        } else {
            curveStatusText.text = "Selected curve: $selectedCurve • Manual control"
        }

        when (selectedCurve) {
            "quiet" -> setActiveMode(quietCurveButton)
            "turbo" -> setActiveMode(turboCurveButton)
            else -> setActiveMode(balancedCurveButton)
        }
        updateManualCurveUiState()
    }

    private fun submitBackgroundTask(
        task: () -> Unit
    ): Boolean {
        return runCatching {
            statusRefreshExecutor.execute(task)
            true
        }.getOrDefault(false)
    }

    private fun createHomeTab(): LinearLayout {
        val result = com.elitedarkkaiser.redmagic.ui.HomeTabUi.create(
            com.elitedarkkaiser.redmagic.ui.HomeTabDeps(
                scrollTabContainer = { scrollTabContainer() },
                sectionPanel = { sectionPanel() },
                sectionHeader = { icon, text -> sectionHeader(icon, text) },
                subtitleText = { text -> subtitleText(text) },
                bodyText = { text -> bodyText(text) },
                ledTitleText = { text -> ledTitleText(text) },
                infoValue = { infoValue() },
                infoRow = { label, valueView -> infoRow(label, valueView) },
                statusChip = { text -> statusChip(text) },
                actionButton = { text, isDanger, onClick -> actionButton(text, isDanger, onClick) },
                singleRow = { button -> singleRow(button) },
                segmentedChip = { label, selected, onClick -> segmentedChip(label, selected, onClick) },
                space = { width -> space(width) },
                dp = { value -> dp(value) },
                runBackground = { task ->
                    submitBackgroundTask(task)
                },
                hasUsageStatsPermission = { PermissionActions.hasUsageStatsPermission(this) },
                openUsageStatsAccessSettings = { PermissionActions.openUsageStatsAccessSettings(this) },
                showGamePickerDialog = { showGamePickerDialog() },
                updateGameModeStatusUI = { textView -> updateGameModeStatusUI(textView) },
                openSettings = {
                    startActivity(
                        Intent(this, SettingsActivity::class.java)
                    )
                },
                openUrl = { url -> openUrl(url) },
                deviceScanSummary = {
                    deviceScanSummaryStorage(this)
                },
                activeModeSummary = {
                    ActiveModeInspector.summary(this)
                }
            )
        )

        deviceRomValue = result.refs.deviceRomValue
        deviceCpuValue = result.refs.deviceCpuValue
        deviceRamValue = result.refs.deviceRamValue
        dashboardText = result.refs.dashboardText
        activeModeText = result.refs.activeModeText
        thermalHistoryView =
            result.refs.thermalHistoryView

        return result.view
    }

    private fun createCoolingTab(): LinearLayout {
        val result = com.elitedarkkaiser.redmagic.ui.CoolingTabUi.create(coolingTabDeps())

        assignCoolingRefs(result.refs)

        return result.view
    }

    private fun coolingTabDeps(): com.elitedarkkaiser.redmagic.ui.CoolingTabDeps {
        return com.elitedarkkaiser.redmagic.ui.CoolingTabDeps(
                scrollTabContainer = { scrollTabContainer() },
                sectionPanel = { sectionPanel() },
                sectionHeader = { icon, text -> sectionHeader(icon, text) },
                subtleLabel = { text -> subtleLabel(text) },
                bodyText = { text -> bodyText(text) },
                segmentedChip = { label, selected, onClick -> segmentedChip(label, selected, onClick) },
                updateSelectableButton = { button, selected ->
                    updateSelectableButton(button, selected)
                },
                actionButton = { text, isDanger, onClick -> actionButton(text, isDanger, onClick) },
                row = { left, right -> row(left, right) },
                singleRow = { button -> singleRow(button) },
                space = { width -> space(width) },
                spacer = { height -> spacer(height) },
                dp = { value -> dp(value) },
                roundedBg = { fill, stroke, radiusDp -> roundedBg(fill, stroke, radiusDp) },
                runBackground = { task ->
                    submitBackgroundTask(task)
                },
                capabilities = deviceCapabilities,

                getSelectedCurve = { selectedCurve },
                setSelectedCurve = { value -> selectedCurve = value },
                setSelectedCurveSaved = { value -> saveSelectedCurveStorage(this, value) },

                getAutoFanCurveEnabled = { autoFanCurveEnabled },
                setAutoFanCurveEnabled = { value -> autoFanCurveEnabled = value },
                setAutoFanEnabledSaved = { value -> saveAutoFanEnabledStorage(this, value) },

                getPumpEnabled = { pumpEnabled },
                setPumpEnabled = { value -> pumpEnabled = value },
                getPumpProfile = { pumpProfile },
                setPumpProfileValue = { value -> pumpProfile = value },
                getAutoPumpEnabled = { autoPumpEnabled },
                setAutoPumpEnabled = { value -> autoPumpEnabled = value },

                setSelectedFanProgress = { value -> fanSeek.value = value.toFloat() },
                startAutoFanService = { HardwareServiceActions.startAutoFan(this) },
                stopAutoFanService = { HardwareServiceActions.stopAutoFan(this) },
                startAutoPumpService = { HardwareServiceActions.startAutoPump(this) },
                stopAutoPumpService = { HardwareServiceActions.stopAutoPump(this) },
                savePumpState = { savePumpStateStorage(this, pumpEnabled, pumpProfile) },
                saveAutoPumpState = { saveAutoPumpStateStorage(this, autoPumpEnabled) },
                refreshStatus = { refreshStatus() },
                refreshSmartPumpStatusViews = { refreshSmartPumpStatusViews() },
                buildAutoPumpStatusText = { buildAutoPumpStatusText() },
                applyPumpProfile = { profile -> applyPumpProfile(profile) },
                confirmExperimentalPumpThenApply = { onApplied ->
                    confirmExperimentalPumpThenApply(onApplied)
                },
                updateManualCurveUiState = { updateManualCurveUiState() }
            )
    }

    private fun assignCoolingRefs(refs: com.elitedarkkaiser.redmagic.ui.CoolingTabUi.Refs) {
        tempText = refs.tempText
        curveStatusText = refs.curveStatusText
        fanSeek = refs.fanSeek
        autoCurveCheck = refs.autoCurveCheck
        quietCurveButton = refs.quietCurveButton
        balancedCurveButton = refs.balancedCurveButton
        turboCurveButton = refs.turboCurveButton
        smartPumpStatusView = refs.smartPumpStatusView
        smartPumpSpeedView = refs.smartPumpSpeedView
    }

    private fun applyFanLedPreviewIfEnabled() {
        if (!realTimePreviewEnabled) return

        val enabled = fanLedEnabled
        val effect = fanLedEffect
        val color = fanLedColor

        if (enabled) {
            applyFanLedSelection(effect, color)
        } else {
            submitBackgroundTask {
                HardwareController.setFanLedEnabled(false)
            }
        }
    }

    private fun applyLogoLedPreviewIfEnabled() {
        if (!realTimePreviewEnabled) return

        val enabled = logoLedEnabled
        val effect = logoLedEffect
        val color = logoLedColor

        submitBackgroundTask {
            if (enabled) {
                HardwareController.setLogoLedEffect(
                    effect,
                    color
                )
            } else {
                HardwareController.setLogoLedEnabled(false)
            }
        }
    }

    private fun applyShoulderLedPreviewIfEnabled() {
        if (!realTimePreviewEnabled) return

        val enabled = shoulderLedEnabled
        val effect = shoulderLedEffect
        val color = shoulderLedColor

        submitBackgroundTask {
            if (enabled) {
                HardwareController.setShoulderLedEffect(
                    effect,
                    color
                )
            } else {
                HardwareController.setShoulderLedEnabled(false)
            }
        }
    }

    private fun createControlsTab(): LinearLayout {
        val result = com.elitedarkkaiser.redmagic.ui.ControlsTabUi.create(
            this,
            com.elitedarkkaiser.redmagic.ui.ControlsTabDeps(
                scrollTabContainer = { scrollTabContainer() },
                sectionPanel = { sectionPanel() },
                sectionHeader = { icon, text -> sectionHeader(icon, text) },
                bodyText = { text -> bodyText(text) },
                subtleLabel = { text -> subtleLabel(text) },
                actionButton = { text, isDanger, onClick -> actionButton(text, isDanger, onClick) },
                smallActionButton = { text, isDanger, onClick -> smallActionButton(text, isDanger, onClick) },
                singleRow = { button -> singleRow(button) },
                row = { left, right -> row(left, right) },
                flowRow = { views -> flowRow(*views) },
                space = { width -> space(width) },
                spacer = { height -> spacer(height) },
                dp = { value -> dp(value) },
                runBackground = { task ->
                    submitBackgroundTask(task)
                },
                capabilities = deviceCapabilities,

                refreshStatus = { refreshStatus() },
                readMagicKeyModeLabel = {
                    if (SliderDualAppStorage.read(this).enabled) {
                        "Dual App Slider"
                    } else {
                        MagicKeyActions.readModeLabel()
                    }
                },
                applyStockMagicKeyMode = { label, action, statusLabel, sliderButton, shortcutButton ->
                    MagicKeyActions.applyStockMode(
                        activity = this,
                        label = label,
                        applyMode = action,
                        statusLabel = statusLabel,
                        sliderButton = sliderButton,
                        shortcutButton = shortcutButton,
                        runBackground = { task ->
                            submitBackgroundTask(task)
                        },
                        refreshStatus = { refreshStatus() }
                    )
                },
                disableMagicKeyMode = { statusLabel, sliderButton, shortcutButton ->
                    MagicKeyActions.disableMode(
                        activity = this,
                        statusLabel = statusLabel,
                        sliderButton = sliderButton,
                        shortcutButton = shortcutButton,
                        runBackground = { task ->
                            submitBackgroundTask(task)
                        },
                        refreshStatus = { refreshStatus() }
                    )
                },
                resolveMagicKeyAppLabel = { pkg -> MagicKeyActions.resolveAppLabel(this, pkg) },
                savedMagicKeyAppPackage = { savedMagicKeyAppPackageStorage(this) },
                showMagicKeyAppPicker = { button, shortcutButton ->
                    showMagicKeyAppPicker(button, shortcutButton)
                },
                savedMagicKeyShortcut = {
                    savedMagicKeyShortcutStorage(this)?.label
                },
                showMagicKeyShortcutPicker = { button, appButton ->
                    showMagicKeyShortcutPicker(button, appButton)
                },
                sliderDualAppSummary = {
                    SliderDualAppStorage.summary(this)
                },
                showSliderDualAppDialog = { button ->
                    showSliderDualAppDialog(button)
                }
            )
        )

        magicKeyStatusLabelRef = result.refs.magicKeyStatusLabel
        return result.view
    }

    private fun createHardwareTab(): LinearLayout {
        return com.elitedarkkaiser.redmagic.ui.HardwareTabUi.create(
            this,
            com.elitedarkkaiser.redmagic.ui.HardwareTabDeps(
                scrollTabContainer = { scrollTabContainer() },
                sectionPanel = { sectionPanel() },
                sectionHeader = { icon, text -> sectionHeader(icon, text) },
                bodyText = { text -> bodyText(text) },
                subtleLabel = { text -> subtleLabel(text) },
                actionButton = { text, isDanger, onClick -> actionButton(text, isDanger, onClick) },
                singleRow = { button -> singleRow(button) },
                row = { left, right -> row(left, right) },
                space = { width -> space(width) },
                dp = { value -> dp(value) },
                capabilities = deviceCapabilities,

                showTriggerSetupDialog = { showTriggerSetupDialog() },
                showNativeTgkProfileDialog = {
                    showNativeTgkProfileDialog()
                },
                showNativeTgkDiagnosticsDialog = {
                    NativeTgkDiagnosticsDialog.show(this)
                },
                triggerSafetySummary = {
                    triggerSafetySummaryStorage(this)
                },
                showTriggerSafetyDialog = { onSaved ->
                    showTriggerSafetyDialog(onSaved)
                },
                enableTriggersAndService = { onComplete ->
                    val submitted = submitBackgroundTask {
                        val enabled =
                            HardwareServiceActions
                                .enableTriggersManually(
                                    this
                                )

                        if (enabled) {
                            refreshStatus()
                        }

                        runOnUiThread {
                            if (isFinishing || isDestroyed) {
                                return@runOnUiThread
                            }
                            onComplete(enabled)
                        }
                    }

                    if (!submitted) {
                        onComplete(false)
                    }
                },
                disableTriggersAndService = { onComplete ->
                    val submitted = submitBackgroundTask {
                        val disabled =
                            HardwareServiceActions
                                .disableTriggersUntilRestart(
                                    this
                                )

                        refreshStatus()

                        runOnUiThread {
                            if (isFinishing || isDestroyed) {
                                return@runOnUiThread
                            }
                            onComplete(disabled)
                        }
                    }

                    if (!submitted) {
                        onComplete(false)
                    }
                },

                readChargeSeparation = { onComplete ->
                    val submitted = submitBackgroundTask {
                        val result =
                            ChargeSeparationController.read(this)

                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                onComplete(result)
                            }
                        }
                    }

                    if (!submitted) {
                        onComplete(
                            ChargeSeparationResult(
                                success = false,
                                enabled = null,
                                backend = null,
                                message = "Unable to read charge separation"
                            )
                        )
                    }
                },
                setChargeSeparation = { enabled, onComplete ->
                    val submitted = submitBackgroundTask {
                        val result =
                            ChargeSeparationController.setEnabled(
                                this,
                                enabled
                            )

                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                onComplete(result)
                            }
                        }
                    }

                    if (!submitted) {
                        onComplete(
                            ChargeSeparationResult(
                                success = false,
                                enabled = null,
                                backend = null,
                                message = "Unable to apply charge separation"
                            )
                        )
                    }
                },
                showRefreshRateProfiles = {
                    RefreshRateProfileDialog.show(this)
                },
                showTouchTuningProfiles = {
                    TouchTuningProfileDialog.show(this)
                },
                showPerformanceModeProfiles = {
                    PerformanceModeProfileDialog.show(this)
                },

                loadMasterProfiles = { MasterProfileStorage.loadProfiles(this) },
                saveMasterProfile = { name, onComplete ->
                    val submitted = submitBackgroundTask {
                        val saved = runCatching {
                            MasterProfileActions.captureAndSave(
                                this,
                                name
                            )
                            true
                        }.getOrDefault(false)

                        runOnUiThread {
                            if (isFinishing || isDestroyed) {
                                return@runOnUiThread
                            }

                            Toast.makeText(
                                this,
                                if (saved) {
                                    "Saved $name"
                                } else {
                                    "Failed to save $name"
                                },
                                Toast.LENGTH_SHORT
                            ).show()

                            onComplete(saved)
                        }
                    }

                    if (!submitted) {
                        Toast.makeText(
                            this,
                            "Unable to start master-profile capture",
                            Toast.LENGTH_SHORT
                        ).show()
                        onComplete(false)
                    }
                },
                applyMasterProfile = { profile ->
                    val submitted = submitBackgroundTask {
                        val applied = runCatching {
                            MasterProfileActions.applyProfile(
                                this,
                                profile
                            )
                            MasterProfileStorage.markProfileApplied(
                                this,
                                profile.name
                            )
                            true
                        }.getOrDefault(false)

                        runOnUiThread {
                            if (isFinishing || isDestroyed) {
                                return@runOnUiThread
                            }

                            if (applied) {
                                applyMasterProfileToUiState(profile)
                            }

                            Toast.makeText(
                                this,
                                if (applied) {
                                    "Applied ${profile.name}"
                                } else {
                                    "Failed to apply ${profile.name}"
                                },
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    if (!submitted) {
                        Toast.makeText(
                            this,
                            "Unable to start master-profile application",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                deleteMasterProfile = { name ->
                    MasterProfileStorage.deleteProfile(this, name)
                    Toast.makeText(this, "Deleted $name", Toast.LENGTH_SHORT).show()
                },
                exportMasterBackup = {
                    startActivityForResult(
                        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/json"
                            putExtra(
                                Intent.EXTRA_TITLE,
                                "redmagic-control-center-backup.json"
                            )
                        },
                        MASTER_BACKUP_EXPORT_REQUEST
                    )
                },
                importMasterBackup = {
                    startActivityForResult(
                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/json"
                        },
                        MASTER_BACKUP_IMPORT_REQUEST
                    )
                },
                automationRulesSummary = {
                    AutomationRulesStorage.summary(this)
                },
                showAutomationRulesDialog = { onSaved ->
                    AutomationRulesDialog.show(
                        this,
                        onSaved
                    )
                }
            )
        )
    }

    private fun createLightingTab(): LinearLayout {
        return com.elitedarkkaiser.redmagic.ui.LightingTabUi.create(
            this,
            com.elitedarkkaiser.redmagic.ui.LightingTabDeps(
                scrollTabContainer = { scrollTabContainer() },
                sectionPanel = { sectionPanel() },
                sectionHeader = { icon, text -> sectionHeader(icon, text) },
                bodyText = { text -> bodyText(text) },
                subtleLabel = { text -> subtleLabel(text) },
                infoRow = { label, valueView -> infoRow(label, valueView) },
                actionButton = { text, isDanger, onClick -> actionButton(text, isDanger, onClick) },
                filterChip = { label, selected, onClick ->
                    filterChip(label, selected, onClick)
                },
                updateSelectableButton = { button, selected ->
                    updateSelectableButton(button, selected)
                },
                singleRow = { button -> singleRow(button) },
                row = { left, right -> row(left, right) },
                dp = { value -> dp(value) },
                capabilities = deviceCapabilities,

                getRealTimePreviewEnabled = { realTimePreviewEnabled },
                setRealTimePreviewEnabled = { value -> realTimePreviewEnabled = value },
                saveRealTimePreviewEnabled = { value -> saveRealTimePreviewEnabledStorage(this, value) },

                showFanLedDialog = { showFanLedDialog() },
                showLogoLedDialog = { showLogoLedDialog() },
                showShoulderLedDialog = { showShoulderLedDialog() },
                rgbStudioSummary = {
                    RgbStudioStorage.summary(this)
                },
                showRgbStudioDialog = { onUpdated ->
                    RgbStudioDialog.show(
                        activity = this,
                        initial = RgbStudioStorage.read(this),
                        deps = RgbStudioDialog.Deps(
                            dp = { value -> dp(value) },
                            filterChip = { label, selected, onClick ->
                                filterChip(label, selected, onClick)
                            },
                            updateSelectableButton = { button, selected ->
                                updateSelectableButton(button, selected)
                            },
                            onSaveAndApply = { state ->
                                RgbStudioStorage.save(this, state)
                                if (state.enabled) {
                                    HardwareServiceActions.startRgbCycle(this)
                                } else {
                                    HardwareServiceActions.stopRgbCycle(this)
                                }
                                onUpdated()
                            },
                            onApplyToAll = { effect, color ->
                                RgbStudioStorage.setEnabled(this, false)
                                HardwareServiceActions.stopRgbCycle(
                                    this,
                                    restoreNormalLeds = false
                                )

                                fanLedEnabled = true
                                fanLedEffect = effect
                                fanLedColor = color
                                logoLedEnabled = true
                                logoLedEffect = effect
                                logoLedColor = color
                                shoulderLedEnabled = true
                                shoulderLedEffect = effect
                                shoulderLedColor = color

                                saveFanLedStateStorage(
                                    this,
                                    LedState(true, effect, color)
                                )
                                saveLogoLedStateStorage(
                                    this,
                                    LedState(true, effect, color)
                                )
                                saveShoulderLedStateStorage(
                                    this,
                                    LedState(true, effect, color)
                                )

                                submitBackgroundTask {
                                    HardwareController.setRgbCycleFrame(
                                        effectName = effect,
                                        logoColor = color,
                                        shoulderColor = color,
                                        fanColor = color
                                    )
                                    HardwareServiceActions.startFanLed(this)
                                }
                                onUpdated()
                            },
                            onStopService = {
                                RgbStudioStorage.setEnabled(
                                    this,
                                    false
                                )
                                HardwareServiceActions.stopRgbCycle(
                                    this
                                )
                                onUpdated()
                            }
                        )
                    )
                },
                showGameModeAppPicker = { showGamePickerDialog() },
                showGameModeProfileDialog = { showGameModeProfileDialog() },
                gameModeAppsSummary = { gameModeAppsSummaryStorage(this) },

                getChargingLedEnabled = { ChargingLedState.isEnabled(this) },
                setChargingLedEnabled = { enabled ->
                    ChargingLedState.setEnabled(this, enabled)
                    HardwareServiceActions.startChargingMode(this)
                },
                showChargingFanLedDialog = {
                    ChargingLedActions.showFanDialog(
                        activity = this,
                        runBackground = { task ->
                            submitBackgroundTask(task)
                        },
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        panelColor = panelColor,
                        borderColor = borderColor,
                        panelPressed = panelPressed,
                        accent = accent,
                        typeface = typeface,
                        dp = { value -> dp(value) },
                        roundedBg = { fill, stroke, radius -> roundedBg(fill, stroke, radius) },
                        roundedFill = { color, radius -> roundedFill(color, radius) },
                        space = { value -> space(value) },
                        filterChip = { label, selected, onClick -> filterChip(label, selected, onClick) },
                        colorDot = { colorId, hex, onClick -> colorDot(colorId, hex, onClick) },
                        colorDotDrawable = { hex, selected -> colorDotDrawable(hex, selected) },
                        fanPresetBubble = { c1, c2, c3, c4, presetValue, selected, onClick ->
                            selectedFanPresetBubble(c1, c2, c3, c4, presetValue, selected, onClick)
                        }
                    )
                },
                showChargingLogoLedDialog = {
                    ChargingLedActions.showLogoDialog(
                        activity = this,
                        runBackground = { task ->
                            submitBackgroundTask(task)
                        },
                        deps = chargingLedDialogDeps()
                    )
                },
                showChargingShoulderLedDialog = {
                    ChargingLedActions.showShoulderDialog(
                        activity = this,
                        runBackground = { task ->
                            submitBackgroundTask(task)
                        },
                        deps = chargingLedDialogDeps()
                    )
                },
                getCallLightingEnabled = { CallLightingState.isEnabled(this) },
                setCallLightingEnabled = { enabled ->
                    CallLightingState.setEnabled(this, enabled)
                    if (enabled) {
                        HardwareServiceActions.startCallLighting(this)
                    } else {
                        CallLightingState.setActive(this, false)
                        HardwareServiceActions.stopCallLighting(this)
                    }
                },
                getPauseFanDuringCalls = { CallLightingState.shouldPauseFanDuringCalls(this) },
                setPauseFanDuringCalls = { enabled ->
                    CallLightingState.setPauseFanDuringCalls(this, enabled)
                },
                showIncomingCallProfileDialog = {
                    CallLightingProfileUi.show(
                        activity = this,
                        title = "Incoming Call Lighting",
                        subtitle = "These LED settings apply automatically while an incoming call is ringing.",
                        fanKeys = CallLightingProfileUi.ZoneKeys(
                            CallLightingState.INCOMING_FAN_ENABLED_KEY,
                            CallLightingState.INCOMING_FAN_EFFECT_KEY,
                            CallLightingState.INCOMING_FAN_COLOR_KEY,
                            "Fan LED",
                            "flashing",
                            5
                        ),
                        logoKeys = CallLightingProfileUi.ZoneKeys(
                            CallLightingState.INCOMING_LOGO_ENABLED_KEY,
                            CallLightingState.INCOMING_LOGO_EFFECT_KEY,
                            CallLightingState.INCOMING_LOGO_COLOR_KEY,
                            "Logo LED",
                            "flashing",
                            1
                        ),
                        shoulderKeys = CallLightingProfileUi.ZoneKeys(
                            CallLightingState.INCOMING_SHOULDER_ENABLED_KEY,
                            CallLightingState.INCOMING_SHOULDER_EFFECT_KEY,
                            CallLightingState.INCOMING_SHOULDER_COLOR_KEY,
                            "Shoulder LEDs",
                            "flashing",
                            8
                        ),
                        deps = callLightingProfileDeps()
                    )
                },
                showConnectedCallProfileDialog = {
                    CallLightingProfileUi.show(
                        activity = this,
                        title = "Connected Call Lighting",
                        subtitle = "These LED settings apply automatically while a call is connected.",
                        fanKeys = CallLightingProfileUi.ZoneKeys(
                            CallLightingState.CONNECTED_FAN_ENABLED_KEY,
                            CallLightingState.CONNECTED_FAN_EFFECT_KEY,
                            CallLightingState.CONNECTED_FAN_COLOR_KEY,
                            "Fan LED",
                            "steady",
                            5
                        ),
                        logoKeys = CallLightingProfileUi.ZoneKeys(
                            CallLightingState.CONNECTED_LOGO_ENABLED_KEY,
                            CallLightingState.CONNECTED_LOGO_EFFECT_KEY,
                            CallLightingState.CONNECTED_LOGO_COLOR_KEY,
                            "Logo LED",
                            "steady",
                            1
                        ),
                        shoulderKeys = CallLightingProfileUi.ZoneKeys(
                            CallLightingState.CONNECTED_SHOULDER_ENABLED_KEY,
                            CallLightingState.CONNECTED_SHOULDER_EFFECT_KEY,
                            CallLightingState.CONNECTED_SHOULDER_COLOR_KEY,
                            "Shoulder LEDs",
                            "steady",
                            8
                        ),
                        deps = callLightingProfileDeps()
                    )
                }
            )
        )
    }

    private fun callLightingProfileDeps(): CallLightingProfileUi.Deps {
        return CallLightingProfileUi.Deps(
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            panelColor = panelColor,
            borderColor = borderColor,
            panelPressed = panelPressed,
            accent = accent,
            typeface = typeface,
            dp = { value -> dp(value) },
            roundedBg = { fill, stroke, radius -> roundedBg(fill, stroke, radius) },
            roundedFill = { color, radius -> roundedFill(color, radius) },
            filterChip = { label, selected, onClick -> filterChip(label, selected, onClick) },
            space = { value -> space(value) },
            colorDotDrawable = { hex, selected -> colorDotDrawable(hex, selected) },
            colorDotGeneric = { hex, selected, onClick -> colorDotGeneric(hex, selected, onClick) },
            fanPresetBubble = { c1, c2, c3, c4, presetValue, selected, onClick ->
                selectedFanPresetBubble(c1, c2, c3, c4, presetValue, selected, onClick)
            }
        )
    }

    private fun chargingLedDialogDeps(): ChargingLedProfileDialog.Deps {
        return ChargingLedProfileDialog.Deps(
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            panelColor = panelColor,
            borderColor = borderColor,
            panelPressed = panelPressed,
            accent = accent,
            typeface = typeface,
            dp = { value -> dp(value) },
            roundedBg = { fill, stroke, radius -> roundedBg(fill, stroke, radius) },
            roundedFill = { color, radius -> roundedFill(color, radius) },
            space = { value -> space(value) },
            colorDotGeneric = { hex, selected, onClick -> colorDotGeneric(hex, selected, onClick) },
            colorDotDrawable = { hex, selected -> colorDotDrawable(hex, selected) },
            fanPresetBubble = { h1, h2, h3, h4, value, selected, onClick ->
                selectedFanPresetBubble(h1, h2, h3, h4, value, selected, onClick)
            }
        )
    }

    private fun showNativeTgkProfileDialog() {
        NativeTgkProfileDialog.show(this)
    }

    private fun showTriggerSetupDialog() {
        TriggerSetupDialog.show(
            activity = this,
            deps = TriggerSetupDialog.Deps(
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                panelColor = panelColor,
                borderColor = borderColor,
                panelPressed = panelPressed,
                accent = accent,
                typeface = typeface,
                dp = { value -> dp(value) },
                roundedBg = { fill, stroke, radius -> roundedBg(fill, stroke, radius) },
                roundedFill = { color, radius -> roundedFill(color, radius) },
                space = { value -> space(value) }
            )
        )
    }

    private fun showTriggerSafetyDialog(
        onSaved: () -> Unit
    ) {
        TriggerSafetyDialog.show(
            activity = this,
            deps = TriggerSafetyDialog.Deps(
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                panelColor = panelColor,
                borderColor = borderColor,
                panelPressed = panelPressed,
                accent = accent,
                typeface = typeface,
                dp = { value -> dp(value) },
                roundedBg = { fill, stroke, radius ->
                    roundedBg(fill, stroke, radius)
                },
                space = { value -> space(value) }
            ),
            onSaved = onSaved
        )
    }

    private fun showGameModeProfileDialog() {
        GameModeUi.showGameModeProfileDialog(
            activity = this,
            current = getSavedGameModeProfileStorage(this),
            deps = GameModeUi.Deps(
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                panelColor = panelColor,
                borderColor = borderColor,
                panelPressed = panelPressed,
                accent = accent,
                typeface = typeface,
                dp = { value -> dp(value) },
                roundedBg = { fill, stroke, radius -> roundedBg(fill, stroke, radius) },
                roundedFill = { color, radius -> roundedFill(color, radius) },
                filterChip = { label, selected, onClick -> filterChip(label, selected, onClick) },
                space = { value -> space(value) },
                colorDotDrawable = { hex, selected -> colorDotDrawable(hex, selected) },
                colorDotGeneric = { hex, selected, onClick -> colorDotGeneric(hex, selected, onClick) },
            ),
            onSaveProfile = { profile ->
                saveGameModeProfileStorage(this, profile)
                GameModeActions.applySavedProfileThroughService(this)
            }
        )
    }

    private fun showPumpProfileDialog() {
        PumpDialogUi.showPumpProfileDialog(
            activity = this,
            originalEnabled = pumpEnabled,
            originalProfile = pumpProfile,
            currentProfile = { pumpProfile },
            setPumpEnabled = { value -> pumpEnabled = value },
            setPumpProfile = { value -> pumpProfile = value },
            applyHardwareProfile = { value ->
                submitBackgroundTask {
                    HardwareController.setPumpProfile(value)
                }
            },
            disablePump = {
                submitBackgroundTask {
                    HardwareController.enablePump(false)
                }
            },
            savePumpState = { savePumpStateStorage(this, pumpEnabled, pumpProfile) },
            confirmExperimentalPumpThenApply = { confirmExperimentalPumpThenApply() },
            setDialogRefreshPump = { callback -> dialogRefreshPump = callback },
            deps = PumpDialogUi.Deps(
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                panelColor = panelColor,
                borderColor = borderColor,
                panelPressed = panelPressed,
                typeface = typeface,
                dp = { value -> dp(value) },
                roundedBg = { fill, stroke, radius -> roundedBg(fill, stroke, radius) },
                roundedFill = { color, radius -> roundedFill(color, radius) },
                filterChip = { label, selected, onClick -> filterChip(label, selected, onClick) },
                space = { value -> space(value) }
            )
        )
    }

    private fun showShoulderLedDialog() {
        ShoulderLedDialogUi.showShoulderLedDialog(
            activity = this,
            originalEnabled = shoulderLedEnabled,
            originalEffect = shoulderLedEffect,
            originalColor = shoulderLedColor,
            currentEnabled = { shoulderLedEnabled },
            currentEffect = { shoulderLedEffect },
            currentColor = { shoulderLedColor },
            setEnabled = { value -> shoulderLedEnabled = value },
            setEffect = { value -> shoulderLedEffect = value },
            setColor = { value -> shoulderLedColor = value },
            applyPreviewIfEnabled = {
                disableRgbStudioForManualLedControl()
                applyShoulderLedPreviewIfEnabled()
            },
            applyEffect = { effect, color ->
                disableRgbStudioForManualLedControl()
                submitBackgroundTask {
                    HardwareController.setShoulderLedEffect(
                        effect,
                        color
                    )
                }
            },
            disableLed = {
                disableRgbStudioForManualLedControl()
                submitBackgroundTask {
                    HardwareController.setShoulderLedEnabled(false)
                }
            },
            saveState = { saveShoulderLedStateStorage(this, LedState(shoulderLedEnabled, shoulderLedEffect, shoulderLedColor)) },
            startFanLedService = {
                submitBackgroundTask {
                    HardwareServiceActions.startFanLed(this)
                }
            },
            stopFanLedService = {
                submitBackgroundTask {
                    HardwareServiceActions.stopFanLed(this)
                }
            },
            anyLedEnabled = { fanLedEnabled || logoLedEnabled || shoulderLedEnabled },
            setDialogRefresh = { callback -> dialogRefreshShoulderLed = callback },
            deps = ShoulderLedDialogUi.Deps(
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                panelColor = panelColor,
                borderColor = borderColor,
                panelPressed = panelPressed,
                accent = accent,
                typeface = typeface,
                dp = { value -> dp(value) },
                roundedBg = { fill, stroke, radius -> roundedBg(fill, stroke, radius) },
                roundedFill = { color, radius -> roundedFill(color, radius) },
                space = { value -> space(value) },
                filterChip = { label, selected, onClick -> filterChip(label, selected, onClick) },
                colorDotGeneric = { hex, selected, onClick -> colorDotGeneric(hex, selected, onClick) },
                colorDotDrawable = { hex, selected -> colorDotDrawable(hex, selected) }
            )
        )
    }

    private fun showLogoLedDialog() {
        LogoLedDialogUi.showLogoLedDialog(
            activity = this,
            originalEnabled = logoLedEnabled,
            originalEffect = logoLedEffect,
            originalColor = logoLedColor,
            currentEnabled = { logoLedEnabled },
            currentEffect = { logoLedEffect },
            currentColor = { logoLedColor },
            setEnabled = { value -> logoLedEnabled = value },
            setEffect = { value -> logoLedEffect = value },
            setColor = { value -> logoLedColor = value },
            applyPreviewIfEnabled = {
                disableRgbStudioForManualLedControl()
                applyLogoLedPreviewIfEnabled()
            },
            applyEffect = { effect, color ->
                disableRgbStudioForManualLedControl()
                submitBackgroundTask {
                    HardwareController.setLogoLedEffect(
                        effect,
                        color
                    )
                }
            },
            disableLed = {
                disableRgbStudioForManualLedControl()
                submitBackgroundTask {
                    HardwareController.setLogoLedEnabled(false)
                }
            },
            saveState = { saveLogoLedStateStorage(this, LedState(logoLedEnabled, logoLedEffect, logoLedColor)) },
            startFanLedService = {
                submitBackgroundTask {
                    HardwareServiceActions.startFanLed(this)
                }
            },
            stopFanLedService = {
                submitBackgroundTask {
                    HardwareServiceActions.stopFanLed(this)
                }
            },
            anyLedEnabled = { fanLedEnabled || logoLedEnabled || shoulderLedEnabled },
            setDialogRefresh = { callback -> dialogRefreshLogoLed = callback },
            deps = LogoLedDialogUi.Deps(
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                panelColor = panelColor,
                borderColor = borderColor,
                panelPressed = panelPressed,
                accent = accent,
                typeface = typeface,
                dp = { value -> dp(value) },
                roundedBg = { fill, stroke, radius -> roundedBg(fill, stroke, radius) },
                roundedFill = { color, radius -> roundedFill(color, radius) },
                space = { value -> space(value) },
                filterChip = { label, selected, onClick -> filterChip(label, selected, onClick) },
                colorDotGeneric = { hex, selected, onClick -> colorDotGeneric(hex, selected, onClick) },
                colorDotDrawable = { hex, selected -> colorDotDrawable(hex, selected) }
            )
        )
    }

    private fun showFanLedDialog() {
        FanLedDialogUi.showFanLedDialog(
            activity = this,
            originalEnabled = fanLedEnabled,
            originalEffect = fanLedEffect,
            originalColor = fanLedColor,
            currentEnabled = { fanLedEnabled },
            currentEffect = { fanLedEffect },
            currentColor = { fanLedColor },
            setEnabled = { value -> fanLedEnabled = value },
            setEffect = { value -> fanLedEffect = value },
            setColor = { value -> fanLedColor = value },
            applyPreviewIfEnabled = {
                disableRgbStudioForManualLedControl()
                applyFanLedPreviewIfEnabled()
            },
            applySelection = { effect, color ->
                disableRgbStudioForManualLedControl()
                applyFanLedSelection(effect, color)
            },
            disableLed = {
                disableRgbStudioForManualLedControl()
                submitBackgroundTask {
                    HardwareController.setFanLedEnabled(false)
                }
            },
            saveState = { saveFanLedStateStorage(this, LedState(fanLedEnabled, fanLedEffect, fanLedColor)) },
            startFanLedService = {
                submitBackgroundTask {
                    HardwareServiceActions.startFanLed(this)
                }
            },
            stopFanLedService = {
                submitBackgroundTask {
                    HardwareServiceActions.stopFanLed(this)
                }
            },
            anyLedEnabled = { fanLedEnabled || logoLedEnabled || shoulderLedEnabled },
            applyFanPreset = { preset -> applyFanPreset(preset) },
            setDialogRefresh = { callback -> dialogRefreshFanLed = callback },
            deps = FanLedDialogUi.Deps(
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                panelColor = panelColor,
                borderColor = borderColor,
                panelPressed = panelPressed,
                accent = accent,
                typeface = typeface,
                dp = { value -> dp(value) },
                roundedBg = { fill, stroke, radius -> roundedBg(fill, stroke, radius) },
                roundedFill = { color, radius -> roundedFill(color, radius) },
                space = { value -> space(value) },
                filterChip = { label, selected, onClick -> filterChip(label, selected, onClick) },
                colorDot = { colorId, hex, onClick -> colorDot(colorId, hex, onClick) },
                colorDotDrawable = { hex, selected -> colorDotDrawable(hex, selected) },
                fanPresetBubble = { c1, c2, c3, c4, presetValue, onClick ->
                    fanPresetBubble(c1, c2, c3, c4, presetValue = presetValue, onClick = onClick)
                }
            )
        )
    }

    private fun disableRgbStudioForManualLedControl() {
        if (!RgbStudioStorage.isEnabled(this)) return

        RgbStudioStorage.setEnabled(this, false)
        HardwareServiceActions.stopRgbCycle(
            this,
            restoreNormalLeds = false
        )
    }

    private fun filterChip(
        label: String,
        selected: Boolean,
        onClick: () -> Unit
    ): Button {
        return MaterialButton(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = label
            textSize = 11f
            isAllCaps = false
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            isSingleLine = true
            minWidth = 0
            minimumWidth = 0
            setTextColor(textPrimary)

            backgroundTintList = ColorStateList.valueOf(
                if (selected) panelPressed else Color.TRANSPARENT
            )
            strokeWidth = dp(1)
            strokeColor = ColorStateList.valueOf(
                if (selected) highlightBorder else borderColor
            )
            rippleColor =
                ColorStateList.valueOf(AppTheme.rippleColor)
            cornerRadius = dp(16)

            insetTop = 0
            insetBottom = 0
            minHeight = dp(40)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { onClick() }
        }
    }

    private fun applyFanPreset(effectValue: String) {
        fanLedEnabled = true
        fanLedEffect = "preset:$effectValue"
        fanLedColor = -1

        applyFanLedSelection(
            effect = fanLedEffect,
            color = fanLedColor
        )
        dialogRefreshFanLed?.invoke()
    }

    private fun selectedFanPresetBubble(
        c1: String,
        c2: String,
        c3: String,
        c4: String,
        presetValue: String,
        selected: Boolean,
        onClick: () -> Unit
    ): View {
        return fanPresetBubble(
            c1,
            c2,
            c3,
            c4,
            presetValue = presetValue,
            selectedOverride = { selected },
            onClick = onClick
        )
    }

    private fun fanPresetBubble(
        vararg hexes: String,
        presetValue: String,
        selectedOverride: (() -> Boolean)? = null,
        onClick: () -> Unit
    ): View {
        require(hexes.size == 4) { "fanPresetBubble requires exactly 4 colors" }

        return object : View(this) {
            private val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            private val ringPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = dp(3).toFloat()
            }

            init {
                val size = dp(42)
                layoutParams = LinearLayout.LayoutParams(size, size)
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }

            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)

                val pad = dp(3).toFloat()
                val rect = android.graphics.RectF(
                    pad,
                    pad,
                    width.toFloat() - pad,
                    height.toFloat() - pad
                )

                val saveCount = canvas.save()
                val clipPath = android.graphics.Path().apply {
                    addOval(rect, android.graphics.Path.Direction.CW)
                }
                canvas.clipPath(clipPath)

                val midX = rect.centerX()
                val midY = rect.centerY()

                fillPaint.color = Color.parseColor(hexes[0])
                canvas.drawRect(rect.left, rect.top, midX, midY, fillPaint)

                fillPaint.color = Color.parseColor(hexes[1])
                canvas.drawRect(midX, rect.top, rect.right, midY, fillPaint)

                fillPaint.color = Color.parseColor(hexes[2])
                canvas.drawRect(rect.left, midY, midX, rect.bottom, fillPaint)

                fillPaint.color = Color.parseColor(hexes[3])
                canvas.drawRect(midX, midY, rect.right, rect.bottom, fillPaint)

                canvas.restoreToCount(saveCount)

                val selected = selectedOverride?.invoke() ?: (fanLedEffect == "preset:$presetValue")
                ringPaint.color = if (selected) {
                    Color.WHITE
                } else {
                    Color.TRANSPARENT
                }
                canvas.drawOval(rect, ringPaint)
            }
        }
    }

    private fun colorDot(colorId: Int, hex: String, onClick: () -> Unit): View {
        return View(this).apply {
            val size = dp(42)
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = colorDotDrawable(hex, fanLedColor == colorId)
            setOnClickListener { onClick() }
        }
    }

    private fun colorDotDrawable(hex: String, selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(hex))
            setStroke(dp(3), if (selected) Color.WHITE else Color.TRANSPARENT)
        }
    }

    private fun colorDotGeneric(hex: String, selected: Boolean, onClick: () -> Unit): View {
        return View(this).apply {
            val size = dp(42)
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = colorDotDrawable(hex, selected)
            setOnClickListener { onClick() }
        }
    }


    private fun refreshCapabilityAwareTabs() {
        if (
            !mainUiReady ||
            !::homeTab.isInitialized
        ) {
            return
        }

        val parent = homeTab.parent as? ViewGroup
            ?: return

        fun replaceBuiltTab(
            oldTab: LinearLayout,
            newTab: LinearLayout
        ): LinearLayout {
            val index = parent.indexOfChild(oldTab)

            if (index < 0) {
                return oldTab
            }

            newTab.visibility = oldTab.visibility
            parent.removeViewAt(index)
            parent.addView(newTab, index)
            return newTab
        }

        if (coolingTabBuilt) {
            coolingTab = replaceBuiltTab(
                coolingTab,
                createCoolingTab()
            )
            restoreFanCurveUiState()
        }

        if (controlsTabBuilt) {
            controlsTab = replaceBuiltTab(
                controlsTab,
                createControlsTab()
            )
        }

        if (hardwareTabBuilt) {
            hardwareTab = replaceBuiltTab(
                hardwareTab,
                createHardwareTab()
            )
        }

        if (lightingTabBuilt) {
            lightingTab = replaceBuiltTab(
                lightingTab,
                createLightingTab()
            )
        }
    }

    private fun switchTab(tab: String) {
        val parent = homeTab.parent as ViewGroup

        fun replaceTab(index: Int, newTab: LinearLayout): LinearLayout {
            parent.removeViewAt(index)
            parent.addView(newTab, index)
            return newTab
        }

        when (tab) {
            "cooling" -> {
                if (!coolingTabBuilt) {
                    coolingTab = replaceTab(1, createCoolingTab())
                    coolingTabBuilt = true
                    restoreFanCurveUiState()
                }
            }

            "controls" -> {
                if (!controlsTabBuilt) {
                    controlsTab = replaceTab(2, createControlsTab())
                    controlsTabBuilt = true
                }
            }

            "hardware" -> {
                if (!hardwareTabBuilt) {
                    hardwareTab = replaceTab(3, createHardwareTab())
                    hardwareTabBuilt = true
                }
            }

            "lighting" -> {
                if (!lightingTabBuilt) {
                    lightingTab = replaceTab(4, createLightingTab())
                    lightingTabBuilt = true
                }
            }
        }

        homeTab.visibility = if (tab == "home") View.VISIBLE else View.GONE
        coolingTab.visibility = if (tab == "cooling") View.VISIBLE else View.GONE
        controlsTab.visibility = if (tab == "controls") View.VISIBLE else View.GONE
        hardwareTab.visibility = if (tab == "hardware") View.VISIBLE else View.GONE
        lightingTab.visibility = if (tab == "lighting") View.VISIBLE else View.GONE

        setNavSelected(homeNav, tab == "home")
        setNavSelected(coolingNav, tab == "cooling")
        setNavSelected(controlsNav, tab == "controls")
        val hardwareNav = (controlsNav.parent as LinearLayout).getChildAt(3) as LinearLayout
        setNavSelected(hardwareNav, tab == "hardware")
        setNavSelected(lightingNav, tab == "lighting")
    }

    private fun setNavSelected(nav: LinearLayout, selected: Boolean) {
        nav.background = roundedFill(
            if (selected) panelPressed else Color.TRANSPARENT,
            18
        )
        nav.alpha = if (selected) 1f else 0.72f
    }

    private fun bottomNavBar(): LinearLayout {
        homeNav = navItem("⌂", "Home") { switchTab("home") }
        coolingNav = navItem("❄", "Cooling") { switchTab("cooling") }
        controlsNav = navItem("⌘", "Controls") { switchTab("controls") }
        val hardwareNav = navItem("⌥", "Hardware") { switchTab("hardware") }
        lightingNav = navItem("✦", "Lighting") { switchTab("lighting") }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundedTopBar()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            addView(homeNav, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(coolingNav, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(controlsNav, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(hardwareNav, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(lightingNav, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun navItem(icon: String, label: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            addView(TextView(this@MainActivity).apply {
                text = icon
                textSize = 20f
                setTextColor(textPrimary)
                gravity = Gravity.CENTER
            })

            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 12f
                setTextColor(textPrimary)
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun roundedTopBar(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(panelColor)
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), borderColor)
        }
    }

    private fun updateManualCurveUiState() {
        val fanAvailable =
            !deviceCapabilities.scanComplete ||
                deviceCapabilities.fanAvailable
        val manualEnabled =
            fanAvailable && !autoFanCurveEnabled
        val alpha = if (manualEnabled) 1f else 0.40f

        quietCurveButton.alpha = alpha
        balancedCurveButton.alpha = alpha
        turboCurveButton.alpha = alpha

        quietCurveButton.isEnabled = manualEnabled
        balancedCurveButton.isEnabled = manualEnabled
        turboCurveButton.isEnabled = manualEnabled

        quietCurveButton.isClickable = manualEnabled
        balancedCurveButton.isClickable = manualEnabled
        turboCurveButton.isClickable = manualEnabled
    }

    private fun refreshStatus() {
        if (!statusRefreshRunning.compareAndSet(false, true)) {
            statusRefreshPending.set(true)
            return
        }

        runCatching {
            statusRefreshExecutor.execute {
                try {
                    do {
                        statusRefreshPending.set(false)
                        refreshStatusOnce()
                    } while (statusRefreshPending.getAndSet(false))
                } finally {
                    statusRefreshRunning.set(false)

                    /*
                     * Cover a request arriving after the loop's final pending
                     * check but before the running flag was cleared.
                     */
                    if (statusRefreshPending.getAndSet(false)) {
                        refreshStatus()
                    }
                }
            }
        }.onFailure {
            statusRefreshRunning.set(false)
        }
    }

    private fun refreshStatusOnce() {
        val rooted =
            hasCachedRootAccessStorage(this) || RootShell.hasRoot()

        val telemetry = HardwareTelemetry.read()
        val fanEnabled = telemetry.fanEnabled == true
        val rpmRaw = telemetry.fanRpm
        val tempF = telemetry.temperatureF

        val cachedDeviceInfo = loadDeviceInfoCacheStorage(this)
        val deviceInfo = cachedDeviceInfo ?: DeviceInfoCache(
            rom = HardwareController.readShortRomFingerprint(),
            cpu = HardwareController.readCpuModel(),
            ram = HardwareController.readRamInfo()
        ).also {
            saveDeviceInfoCacheStorage(this, it)
        }

        val romText = deviceInfo.rom
        val cpuText = deviceInfo.cpu
        val ramText = deviceInfo.ram
        val modelText = Build.MODEL ?: "Unknown"

        val dashboardSummary =
            DashboardSnapshot.buildSummary(
                context = this,
                hardware = telemetry,
                rooted = rooted
            )

        runOnUiThread {
            if (isFinishing || isDestroyed) {
                return@runOnUiThread
            }

            val rpm = when {
                rpmRaw == null ->
                    lastDisplayedRpm.takeIf { it >= 0 }
                lastDisplayedRpm < 0 -> rpmRaw
                else ->
                    ((lastDisplayedRpm * 0.7) + (rpmRaw * 0.3))
                        .toInt()
            }

            if (rpm != null) {
                lastDisplayedRpm = rpm
            }

            val previousTempF = lastDisplayedTempF
            val tempTrend = when {
                tempF == null || previousTempF == null -> ""
                tempF > previousTempF + 1f -> " ↑"
                tempF < previousTempF - 1f -> " ↓"
                else -> " →"
            }

            if (tempF != null) {
                lastDisplayedTempF = tempF
            }

            refreshSmartPumpStatusViews()

            deviceRomValue.text = romText
            deviceCpuValue.text = cpuText
            deviceRamValue.text = ramText

            if (::dashboardText.isInitialized) {
                dashboardText.text = dashboardSummary
            }

            if (::activeModeText.isInitialized) {
                activeModeText.text =
                    ActiveModeInspector.summary(this)
            }

            if (::thermalHistoryView.isInitialized) {
                thermalHistoryView.setHistory(
                    TemperatureHistory.snapshot(),
                    useFahrenheit
                )
            }

            if (::tempText.isInitialized) {
                tempText.text = if (tempF != null) {
                    "Current temp: ${
                        TempFormat.formatDisplayTempFromF(
                            tempF,
                            useFahrenheit
                        )
                    }$tempTrend"
                } else {
                    "Current temp: --"
                }
            }

        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Throwable) {
        }
    }

    private fun scrollTabContainer(): LinearLayout {
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            addView(ScrollView(this@MainActivity).apply {
                setBackgroundColor(bgColor)
                addView(inner)
            })

            tag = inner
        }.also { outer ->
            val scroll = outer.getChildAt(0) as ScrollView
            val child = scroll.getChildAt(0) as LinearLayout
            outer.removeAllViews()
            outer.addView(scroll.apply { removeAllViews(); addView(child) })
        }
    }

    private fun subtitleText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(textSecondary)
            setPadding(0, dp(4), 0, 0)
        }
    }

    private fun ledTitleText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 20f
            setTextColor(textPrimary)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(14), 0, 0, 0)
            setShadowLayer(dp(6).toFloat(), 0f, 0f, accent)

            val animator = ValueAnimator.ofObject(
                ArgbEvaluator(),
                accent,
                textPrimary,
                accent
            ).apply {
                duration = 1800L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator()
                addUpdateListener { anim ->
                    val c = anim.animatedValue as Int
                    setTextColor(c)
                    setShadowLayer(dp(8).toFloat(), 0f, 0f, c)
                }
            }

            post { animator.start() }
        }
    }

    private fun bodyText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(textSecondary)
            setPadding(0, dp(4), 0, 0)
        }
    }

    private fun infoRow(label: String, valueView: TextView): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, 0, 0, dp(6))

            val labelView = TextView(this@MainActivity).apply {
                text = label
                textSize = 13f
                setTextColor(textSecondary)
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            valueView.layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )

            addView(labelView)
            addView(valueView)
        }
    }

    private fun infoValue(): TextView {
        return TextView(this).apply {
            text = "--"
            textSize = 13f
            setTextColor(textPrimary)
            setLineSpacing(0f, 1.1f)
            isSingleLine = false
            maxLines = 4
        }
    }

    private fun sectionHeader(icon: String, text: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))

            val iconView = TextView(this@MainActivity).apply {
                this.text = icon
                textSize = 11f
                setTextColor(textSecondary)
                gravity = Gravity.CENTER
                background = roundedFill(panelPressed, 10)
                setPadding(dp(7), dp(5), dp(7), dp(5))
            }

            val labelView = TextView(this@MainActivity).apply {
                this.text = text
                textSize = 11f
                setTextColor(accent)
                setTypeface(typeface, Typeface.BOLD)
                letterSpacing = 0.06f
                setPadding(dp(8), 0, 0, 0)
            }

            addView(iconView)
            addView(labelView)
        }
    }

    private fun sectionPanel(): LinearLayout {
        val shapeModel = ShapeAppearanceModel.builder()
            .setAllCornerSizes(dp(22).toFloat())
            .build()

        val panelBackground = MaterialShapeDrawable(shapeModel).apply {
            initializeElevationOverlay(this@MainActivity)
            fillColor = ColorStateList.valueOf(panelColor)
            strokeWidth = dp(1).toFloat()
            strokeColor = ColorStateList.valueOf(borderColor)
            elevation = dp(2).toFloat()
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = panelBackground
            elevation = dp(2).toFloat()
            clipToOutline = true

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14)
            }
        }
    }

    private fun statusChip(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(textPrimary)
            textSize = 10f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            isSingleLine = true
            minHeight = dp(40)
            minimumWidth = dp(80)
            includeFontPadding = false
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = roundedFill(chipOn, 999)
        }
    }

    private fun setChipState(view: TextView, active: Boolean) {
        view.background = roundedFill(if (active) chipActive else chipOn, 14)
        view.setTextColor(textPrimary)
    }

    private fun setActiveMode(active: Button) {
        updateSelectableButton(
            quietCurveButton,
            active === quietCurveButton
        )
        updateSelectableButton(
            balancedCurveButton,
            active === balancedCurveButton
        )
        updateSelectableButton(
            turboCurveButton,
            active === turboCurveButton
        )
    }

    private fun updateSelectableButton(
        button: Button,
        selected: Boolean
    ) {
        button.isSelected = selected

        if (button is MaterialButton) {
            button.backgroundTintList = ColorStateList.valueOf(
                if (selected) panelPressed else Color.TRANSPARENT
            )
            button.strokeColor = ColorStateList.valueOf(
                if (selected) highlightBorder else borderColor
            )
        }
    }

    private fun subtleLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(textSecondary)
            setPadding(0, dp(4), 0, 0)
        }
    }

    private fun segmentedChip(
        label: String,
        selected: Boolean,
        onClick: () -> Unit
    ): Button {
        return MaterialButton(
            this,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setTextColor(textPrimary)
            strokeWidth = dp(1)
            rippleColor = ColorStateList.valueOf(AppTheme.rippleColor)
            cornerRadius = dp(20)

            insetTop = 0
            insetBottom = 0
            minHeight = dp(48)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            updateSelectableButton(this, selected)
            setOnClickListener { onClick() }
        }
    }

    private fun actionButton(text: String, isDanger: Boolean = false, onClick: () -> Unit): Button {
        return MaterialButton(this).apply {
            this.text = text
            textSize = 13f
            isAllCaps = false
            setTextColor(textPrimary)

            backgroundTintList = ColorStateList.valueOf(
                if (isDanger) danger else panelPressed
            )
            rippleColor = ColorStateList.valueOf(
                if (isDanger) highlightBorder else AppTheme.rippleColor
            )
            cornerRadius = dp(16)

            insetTop = 0
            insetBottom = 0
            minHeight = dp(48)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { onClick() }
        }
    }

    private fun row(left: Button, right: Button): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }

            left.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
            right.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }

            addView(left)
            addView(right)
        }
    }

    private fun singleRow(button: Button): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }

            button.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(button)
        }
    }

    private fun applyPressEffect(view: View, pressedColor: Int = panelPressed) {
        val normalBg = view.background
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.alpha = 0.96f
                    v.animate().scaleX(0.985f).scaleY(0.985f).setDuration(80).start()
                    v.background = when (v) {
                        is Button -> roundedFill(pressedColor, 16)
                        else -> roundedBg(pressedColor, highlightBorder, 18)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1f
                    v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                    v.background = normalBg
                }
            }
            false
        }
    }

    private fun roundedBg(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            setStroke(dp(1), stroke)
        }
    }

    private fun roundedFill(fill: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
        }
    }

    private fun space(width: Int): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(width, 1)
        }
    }

    private fun spacer(height: Int): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else dp(24)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun smallActionButton(label: String, isDanger: Boolean = false, onClick: () -> Unit): Button {
        return MaterialButton(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setTextColor(textPrimary)

            backgroundTintList = ColorStateList.valueOf(
                if (isDanger) danger else panelPressed
            )
            rippleColor = ColorStateList.valueOf(
                if (isDanger) highlightBorder else AppTheme.rippleColor
            )
            cornerRadius = dp(14)

            insetTop = 0
            insetBottom = 0
            minHeight = dp(44)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { onClick() }
        }
    }

    private fun flowRow(vararg views: View): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(6))
            views.forEachIndexed { index, v ->
                val params = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
                if (index > 0) params.marginStart = dp(6)
                addView(v, params)
            }
        }
    }


    private fun showGamePickerDialog() {
        showGamePickerDialogUI(this) {
            gameModeAppsTextRef?.text = gameModeAppsSummaryStorage(this)
        }
    }




    private fun updateGameModeStatusUI(textView: TextView) {
        textView.text = getGameModeStatusTextStorage(this)
    }


}
