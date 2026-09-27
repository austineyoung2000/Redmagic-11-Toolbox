package com.elitedarkkaiser.redmagic

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.state.LedState
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.elitedarkkaiser.redmagic.ui.components.LedControlViewFactory
import com.elitedarkkaiser.redmagic.ui.components.MainActivityUiKit
import com.elitedarkkaiser.redmagic.ui.components.MainBottomNavigation

class MainActivity : Activity() {
    private var useFahrenheit = true


    private lateinit var deviceRomValue: TextView
    private lateinit var deviceCpuValue: TextView
    private lateinit var deviceRamValue: TextView
    private lateinit var dashboardText: TextView
    private lateinit var activeModeText: TextView
    private lateinit var thermalHistoryView:
        com.elitedarkkaiser.redmagic.ui.ThermalHistoryView
    private var lastDisplayedRpm: Int = -1

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
    private var dialogRefreshShoulderLed: (() -> Unit)? = null
    private var dialogRefreshLogoLed: (() -> Unit)? = null
    private var dialogRefreshFanLed: (() -> Unit)? = null
    private var gameModeAppsTextRef: TextView? = null

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

    private val bgColor get() = AppTheme.bgColor
    private val panelColor get() = AppTheme.panelColor
    private val panelPressed get() = AppTheme.panelPressed
    private val borderColor get() = AppTheme.borderColor

    private val accent get() = AppTheme.accentColor
    private val textPrimary get() = AppTheme.textPrimary
    private val textSecondary get() = AppTheme.textSecondary
    private val typeface: Typeface? = Typeface.SANS_SERIF
    private val highlightBorder get() = AppTheme.highlightBorder
    private val mainUiKit by lazy(LazyThreadSafetyMode.NONE) {
        MainActivityUiKit(this)
    }
    private val ledControlViews by lazy(
        LazyThreadSafetyMode.NONE
    ) {
        LedControlViewFactory(this)
    }
    private val bottomNavigation by lazy(
        LazyThreadSafetyMode.NONE
    ) {
        MainBottomNavigation(this) { tab ->
            switchTab(tab)
        }
    }
    private val hardwareTabActions by lazy(
        LazyThreadSafetyMode.NONE
    ) {
        MainHardwareTabActions(
            activity = this,
            runBackground = { task ->
                submitBackgroundTask(task)
            },
            onMasterProfileApplied = { profile ->
                applyMasterProfileToUiState(profile)
            }
        )
    }
    private val lightingTabActions by lazy(
        LazyThreadSafetyMode.NONE
    ) {
        MainLightingTabActions(
            activity = this,
            runBackground = { task ->
                submitBackgroundTask(task)
            },
            dp = { value -> mainUiKit.dp(value) },
            filterChip = { label, selected, onClick ->
                ledControlViews.filterChip(label, selected, onClick)
            },
            updateSelectableButton = { button, selected ->
                mainUiKit.updateSelectableButton(button, selected)
            },
            onAllLedStateApplied = { effect, color ->
                fanLedEnabled = true
                fanLedEffect = effect
                fanLedColor = color
                logoLedEnabled = true
                logoLedEffect = effect
                logoLedColor = color
                shoulderLedEnabled = true
                shoulderLedEffect = effect
                shoulderLedColor = color
            }
        )
    }
    private val coolingTabActions by lazy(
        LazyThreadSafetyMode.NONE
    ) {
        MainCoolingTabActions(
            activity = this,
            uiKit = mainUiKit,
            runBackground = { task ->
                submitBackgroundTask(task)
            },
            refreshStatus = { refreshStatus() },
            capabilities = { deviceCapabilities },
            useFahrenheit = { useFahrenheit }
        )
    }
    private var mainUiReady = false
    private var deviceCapabilities =
        DeviceCapabilities.unknown()
    private lateinit var activityRuntime: MainActivityRuntime

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTheme.configure(this)

        if (!DeviceCompatibility.isSupportedDevice()) {
            showUnsupportedDeviceDialog()
            return
        }

        activityRuntime = MainActivityRuntime(
            activity = this,
            onStatusSnapshot = ::applyStatusSnapshot
        )
        
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

        if (
            MasterProfileDocumentTransfer.handleActivityResult(
                activity = this,
                requestCode = requestCode,
                resultCode = resultCode,
                data = data,
                runBackground = { task ->
                    submitBackgroundTask(task)
                }
            )
        ) {
            return
        }
    }

    private fun verifyRootAndLaunch() {
        if (hasCachedRootAccessStorage(this)) {
            launchMainUi()
            return
        }

        val submitted = activityRuntime.verifyRoot { rooted ->
            if (rooted) {
                setCachedRootAccessStorage(this, true)
                launchMainUi()
            } else {
                showRootRequiredDialog()
            }
        }

        if (!submitted && !isFinishing && !isDestroyed) {
            showRootRequiredDialog()
        }
    }


    override fun onStart() {
        super.onStart()

        if (::activityRuntime.isInitialized) {
            activityRuntime.onStart()
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
        if (::activityRuntime.isInitialized) {
            activityRuntime.onStop()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (::activityRuntime.isInitialized) {
            activityRuntime.destroy()
        }
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
            dp = { value -> mainUiKit.dp(value) },
            roundedBg = { fill, stroke, radius ->
                mainUiKit.roundedBg(fill, stroke, radius)
            },
            roundedFill = { color, radius ->
                mainUiKit.roundedFill(color, radius)
            },
            space = { value -> mainUiKit.space(value) }
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

    private fun applyMasterProfileToUiState(
        profile: MasterProfile
    ) {
        val hardware = profile.hardware
        coolingTabActions.applyMasterProfile(hardware)
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
        refreshStatus()
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
                dp = { value -> mainUiKit.dp(value) },
                roundedBg = { fill, stroke, radius -> mainUiKit.roundedBg(fill, stroke, radius) },
                roundedFill = { color, radius -> mainUiKit.roundedFill(color, radius) }
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
                dp = { value -> mainUiKit.dp(value) },
                roundedBg = { fill, stroke, radius ->
                    mainUiKit.roundedBg(fill, stroke, radius)
                },
                roundedFill = { color, radius ->
                    mainUiKit.roundedFill(color, radius)
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
                coolingTabActions.loadSavedPumpState()
            },
            setRealTimePreviewEnabled = { value -> realTimePreviewEnabled = value },
            isRealTimePreviewEnabledSaved = { isRealTimePreviewEnabledStorage(this) },
            setUseFahrenheit = { value -> useFahrenheit = value },
            isUseFahrenheitSaved = { isUseFahrenheitStorage(this) },
            setAutoPumpEnabled = { value ->
                coolingTabActions.setAutoPumpEnabled(value)
            },
            isAutoPumpEnabledSaved = { savedPumpStateStorage(this).autoEnabled }
        )

        val result = MainUiLauncher.launch(
            activity = this,
            topInset = mainUiKit.getStatusBarHeight(),
            bgColor = bgColor,
            dp = { value -> mainUiKit.dp(value) },
            createHomeTab = { createHomeTab() },
            createCoolingTab = { createCoolingTab() },
            createControlsTab = { createControlsTab() },
            createHardwareTab = { createHardwareTab() },
            createLightingTab = { createLightingTab() },
            bottomNavBar = {
                bottomNavigation.createView()
            }
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

        coolingTabActions.startAutoPumpIfEnabled()

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
        activityRuntime.onUiReady()
        // Do not start background services just because the UI opened.
        // Game Mode starts from selected-app foreground events.
        // Charging Mode starts from boot, plug state, or explicit toggle.
    }

    private fun submitBackgroundTask(
        task: () -> Unit
    ): Boolean {
        return activityRuntime.submit(task)
    }

    private fun createHomeTab(): LinearLayout {
        val result = com.elitedarkkaiser.redmagic.ui.HomeTabUi.create(
            com.elitedarkkaiser.redmagic.ui.HomeTabDeps(
                scrollTabContainer = { mainUiKit.scrollTabContainer() },
                sectionPanel = { mainUiKit.sectionPanel() },
                sectionHeader = { icon, text -> mainUiKit.sectionHeader(icon, text) },
                subtitleText = { text -> mainUiKit.subtitleText(text) },
                bodyText = { text -> mainUiKit.bodyText(text) },
                ledTitleText = { text -> mainUiKit.ledTitleText(text) },
                infoValue = { mainUiKit.infoValue() },
                infoRow = { label, valueView -> mainUiKit.infoRow(label, valueView) },
                statusChip = { text -> mainUiKit.statusChip(text) },
                actionButton = { text, isDanger, onClick -> mainUiKit.actionButton(text, isDanger, onClick) },
                singleRow = { button -> mainUiKit.singleRow(button) },
                segmentedChip = { label, selected, onClick -> mainUiKit.segmentedChip(label, selected, onClick) },
                space = { width -> mainUiKit.space(width) },
                dp = { value -> mainUiKit.dp(value) },
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
        return coolingTabActions.createView()
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
                scrollTabContainer = { mainUiKit.scrollTabContainer() },
                sectionPanel = { mainUiKit.sectionPanel() },
                sectionHeader = { icon, text -> mainUiKit.sectionHeader(icon, text) },
                bodyText = { text -> mainUiKit.bodyText(text) },
                subtleLabel = { text -> mainUiKit.subtleLabel(text) },
                actionButton = { text, isDanger, onClick -> mainUiKit.actionButton(text, isDanger, onClick) },
                smallActionButton = { text, isDanger, onClick -> mainUiKit.smallActionButton(text, isDanger, onClick) },
                singleRow = { button -> mainUiKit.singleRow(button) },
                row = { left, right -> mainUiKit.row(left, right) },
                flowRow = { views -> mainUiKit.flowRow(*views) },
                space = { width -> mainUiKit.space(width) },
                spacer = { height -> mainUiKit.spacer(height) },
                dp = { value -> mainUiKit.dp(value) },
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
                scrollTabContainer = { mainUiKit.scrollTabContainer() },
                sectionPanel = { mainUiKit.sectionPanel() },
                sectionHeader = { icon, text -> mainUiKit.sectionHeader(icon, text) },
                bodyText = { text -> mainUiKit.bodyText(text) },
                subtleLabel = { text -> mainUiKit.subtleLabel(text) },
                actionButton = { text, isDanger, onClick -> mainUiKit.actionButton(text, isDanger, onClick) },
                singleRow = { button -> mainUiKit.singleRow(button) },
                row = { left, right -> mainUiKit.row(left, right) },
                space = { width -> mainUiKit.space(width) },
                dp = { value -> mainUiKit.dp(value) },
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
                    hardwareTabActions.readChargeSeparation(
                        onComplete
                    )
                },
                setChargeSeparation = { enabled, onComplete ->
                    hardwareTabActions.setChargeSeparation(
                        enabled,
                        onComplete
                    )
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

                loadMasterProfiles = {
                    hardwareTabActions.loadMasterProfiles()
                },
                saveMasterProfile = { name, onComplete ->
                    hardwareTabActions.saveMasterProfile(
                        name,
                        onComplete
                    )
                },
                applyMasterProfile = { profile ->
                    hardwareTabActions.applyMasterProfile(
                        profile
                    )
                },
                deleteMasterProfile = { name ->
                    hardwareTabActions.deleteMasterProfile(name)
                },
                exportMasterBackup = {
                    hardwareTabActions.requestMasterBackupExport()
                },
                importMasterBackup = {
                    hardwareTabActions.requestMasterBackupImport()
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
                scrollTabContainer = { mainUiKit.scrollTabContainer() },
                sectionPanel = { mainUiKit.sectionPanel() },
                sectionHeader = { icon, text -> mainUiKit.sectionHeader(icon, text) },
                bodyText = { text -> mainUiKit.bodyText(text) },
                subtleLabel = { text -> mainUiKit.subtleLabel(text) },
                infoRow = { label, valueView -> mainUiKit.infoRow(label, valueView) },
                actionButton = { text, isDanger, onClick -> mainUiKit.actionButton(text, isDanger, onClick) },
                filterChip = { label, selected, onClick ->
                    ledControlViews.filterChip(label, selected, onClick)
                },
                updateSelectableButton = { button, selected ->
                    mainUiKit.updateSelectableButton(button, selected)
                },
                singleRow = { button -> mainUiKit.singleRow(button) },
                row = { left, right -> mainUiKit.row(left, right) },
                dp = { value -> mainUiKit.dp(value) },
                capabilities = deviceCapabilities,

                getRealTimePreviewEnabled = { realTimePreviewEnabled },
                setRealTimePreviewEnabled = { value -> realTimePreviewEnabled = value },
                saveRealTimePreviewEnabled = { value -> saveRealTimePreviewEnabledStorage(this, value) },

                showFanLedDialog = { showFanLedDialog() },
                showLogoLedDialog = { showLogoLedDialog() },
                showShoulderLedDialog = { showShoulderLedDialog() },
                rgbStudioSummary = {
                    lightingTabActions.rgbStudioSummary()
                },
                showRgbStudioDialog = { onUpdated ->
                    lightingTabActions.showRgbStudioDialog(
                        onUpdated
                    )
                },
                showGameModeAppPicker = { showGamePickerDialog() },
                showGameModeProfileDialog = { showGameModeProfileDialog() },
                gameModeAppsSummary = { gameModeAppsSummaryStorage(this) },

                getChargingLedEnabled = {
                    lightingTabActions.isChargingLedEnabled()
                },
                setChargingLedEnabled = { enabled ->
                    lightingTabActions.setChargingLedEnabled(
                        enabled
                    )
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
                        dp = { value -> mainUiKit.dp(value) },
                        roundedBg = { fill, stroke, radius -> mainUiKit.roundedBg(fill, stroke, radius) },
                        roundedFill = { color, radius -> mainUiKit.roundedFill(color, radius) },
                        space = { value -> mainUiKit.space(value) },
                        filterChip = { label, selected, onClick -> ledControlViews.filterChip(label, selected, onClick) },
                        colorDot = { colorId, hex, onClick -> colorDot(colorId, hex, onClick) },
                        colorDotDrawable = { hex, selected -> ledControlViews.colorDotDrawable(hex, selected) },
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
                getCallLightingEnabled = {
                    lightingTabActions.isCallLightingEnabled()
                },
                setCallLightingEnabled = { enabled ->
                    lightingTabActions.setCallLightingEnabled(
                        enabled
                    )
                },
                getPauseFanDuringCalls = {
                    lightingTabActions.shouldPauseFanDuringCalls()
                },
                setPauseFanDuringCalls = { enabled ->
                    lightingTabActions.setPauseFanDuringCalls(
                        enabled
                    )
                },
                showIncomingCallProfileDialog = {
                    lightingTabActions
                        .showIncomingCallProfileDialog(
                            callLightingProfileDeps()
                        )
                },
                showConnectedCallProfileDialog = {
                    lightingTabActions
                        .showConnectedCallProfileDialog(
                            callLightingProfileDeps()
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
            dp = { value -> mainUiKit.dp(value) },
            roundedBg = { fill, stroke, radius -> mainUiKit.roundedBg(fill, stroke, radius) },
            roundedFill = { color, radius -> mainUiKit.roundedFill(color, radius) },
            filterChip = { label, selected, onClick -> ledControlViews.filterChip(label, selected, onClick) },
            space = { value -> mainUiKit.space(value) },
            colorDotDrawable = { hex, selected -> ledControlViews.colorDotDrawable(hex, selected) },
            colorDotGeneric = { hex, selected, onClick -> ledControlViews.colorDot(hex, selected, onClick) },
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
            dp = { value -> mainUiKit.dp(value) },
            roundedBg = { fill, stroke, radius -> mainUiKit.roundedBg(fill, stroke, radius) },
            roundedFill = { color, radius -> mainUiKit.roundedFill(color, radius) },
            space = { value -> mainUiKit.space(value) },
            colorDotGeneric = { hex, selected, onClick -> ledControlViews.colorDot(hex, selected, onClick) },
            colorDotDrawable = { hex, selected -> ledControlViews.colorDotDrawable(hex, selected) },
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
                dp = { value -> mainUiKit.dp(value) },
                roundedBg = { fill, stroke, radius -> mainUiKit.roundedBg(fill, stroke, radius) },
                roundedFill = { color, radius -> mainUiKit.roundedFill(color, radius) },
                space = { value -> mainUiKit.space(value) }
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
                dp = { value -> mainUiKit.dp(value) },
                roundedBg = { fill, stroke, radius ->
                    mainUiKit.roundedBg(fill, stroke, radius)
                },
                space = { value -> mainUiKit.space(value) }
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
                dp = { value -> mainUiKit.dp(value) },
                roundedBg = { fill, stroke, radius -> mainUiKit.roundedBg(fill, stroke, radius) },
                roundedFill = { color, radius -> mainUiKit.roundedFill(color, radius) },
                filterChip = { label, selected, onClick -> ledControlViews.filterChip(label, selected, onClick) },
                space = { value -> mainUiKit.space(value) },
                colorDotDrawable = { hex, selected -> ledControlViews.colorDotDrawable(hex, selected) },
                colorDotGeneric = { hex, selected, onClick -> ledControlViews.colorDot(hex, selected, onClick) },
            ),
            onSaveProfile = { profile ->
                saveGameModeProfileStorage(this, profile)
                GameModeActions.applySavedProfileThroughService(this)
            }
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
                dp = { value -> mainUiKit.dp(value) },
                roundedBg = { fill, stroke, radius -> mainUiKit.roundedBg(fill, stroke, radius) },
                roundedFill = { color, radius -> mainUiKit.roundedFill(color, radius) },
                space = { value -> mainUiKit.space(value) },
                filterChip = { label, selected, onClick -> ledControlViews.filterChip(label, selected, onClick) },
                colorDotGeneric = { hex, selected, onClick -> ledControlViews.colorDot(hex, selected, onClick) },
                colorDotDrawable = { hex, selected -> ledControlViews.colorDotDrawable(hex, selected) }
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
                dp = { value -> mainUiKit.dp(value) },
                roundedBg = { fill, stroke, radius -> mainUiKit.roundedBg(fill, stroke, radius) },
                roundedFill = { color, radius -> mainUiKit.roundedFill(color, radius) },
                space = { value -> mainUiKit.space(value) },
                filterChip = { label, selected, onClick -> ledControlViews.filterChip(label, selected, onClick) },
                colorDotGeneric = { hex, selected, onClick -> ledControlViews.colorDot(hex, selected, onClick) },
                colorDotDrawable = { hex, selected -> ledControlViews.colorDotDrawable(hex, selected) }
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
                dp = { value -> mainUiKit.dp(value) },
                roundedBg = { fill, stroke, radius -> mainUiKit.roundedBg(fill, stroke, radius) },
                roundedFill = { color, radius -> mainUiKit.roundedFill(color, radius) },
                space = { value -> mainUiKit.space(value) },
                filterChip = { label, selected, onClick -> ledControlViews.filterChip(label, selected, onClick) },
                colorDot = { colorId, hex, onClick -> colorDot(colorId, hex, onClick) },
                colorDotDrawable = { hex, selected -> ledControlViews.colorDotDrawable(hex, selected) },
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
        return ledControlViews.fanPresetBubble(
            colors = hexes.toList(),
            selected = selectedOverride ?: {
                fanLedEffect == "preset:$presetValue"
            },
            onClick = onClick
        )
    }

    private fun colorDot(
        colorId: Int,
        hex: String,
        onClick: () -> Unit
    ): View {
        return ledControlViews.colorDot(
            hex = hex,
            selected = fanLedColor == colorId,
            onClick = onClick
        )
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

        fun replaceTab(
            oldTab: LinearLayout,
            newTab: LinearLayout
        ): LinearLayout {
            val index = parent.indexOfChild(oldTab)
            if (index < 0) {
                parent.addView(newTab)
                return newTab
            }

            parent.removeViewAt(index)
            parent.addView(newTab, index)
            return newTab
        }

        when (tab) {
            "cooling" -> {
                if (!coolingTabBuilt) {
                    coolingTab = replaceTab(
                        coolingTab,
                        createCoolingTab()
                    )
                    coolingTabBuilt = true
                }
            }

            "controls" -> {
                if (!controlsTabBuilt) {
                    controlsTab = replaceTab(
                        controlsTab,
                        createControlsTab()
                    )
                    controlsTabBuilt = true
                }
            }

            "hardware" -> {
                if (!hardwareTabBuilt) {
                    hardwareTab = replaceTab(
                        hardwareTab,
                        createHardwareTab()
                    )
                    hardwareTabBuilt = true
                }
            }

            "lighting" -> {
                if (!lightingTabBuilt) {
                    lightingTab = replaceTab(
                        lightingTab,
                        createLightingTab()
                    )
                    lightingTabBuilt = true
                }
            }
        }

        homeTab.visibility = if (tab == "home") View.VISIBLE else View.GONE
        coolingTab.visibility = if (tab == "cooling") View.VISIBLE else View.GONE
        controlsTab.visibility = if (tab == "controls") View.VISIBLE else View.GONE
        hardwareTab.visibility = if (tab == "hardware") View.VISIBLE else View.GONE
        lightingTab.visibility = if (tab == "lighting") View.VISIBLE else View.GONE

        bottomNavigation.select(tab)
    }

    private fun refreshStatus() {
        activityRuntime.requestStatusRefresh()
    }

    private fun applyStatusSnapshot(
        snapshot: MainStatusSnapshot
    ) {
        val telemetry = snapshot.telemetry
        val rpmRaw = telemetry.fanRpm
        val tempF = telemetry.temperatureF

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

        coolingTabActions.applyTemperature(tempF)

        deviceRomValue.text = snapshot.deviceInfo.rom
        deviceCpuValue.text = snapshot.deviceInfo.cpu
        deviceRamValue.text = snapshot.deviceInfo.ram

        if (::dashboardText.isInitialized) {
            dashboardText.text = snapshot.dashboardSummary
        }

        if (::activeModeText.isInitialized) {
            activeModeText.text = snapshot.activeModeSummary
        }

        if (::thermalHistoryView.isInitialized) {
            thermalHistoryView.setHistory(
                snapshot.temperatureHistory,
                useFahrenheit
            )
        }

    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Throwable) {
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
