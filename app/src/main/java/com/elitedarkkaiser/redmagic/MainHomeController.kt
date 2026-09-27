package com.elitedarkkaiser.redmagic

import android.content.Intent
import android.net.Uri
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.ui.HomeTabDeps
import com.elitedarkkaiser.redmagic.ui.HomeTabUi
import com.elitedarkkaiser.redmagic.ui.components.MainActivityUiKit

/** Owns Home-tab composition and every view reference updated by telemetry. */
internal class MainHomeController(
    private val activity: MainActivity,
    private val uiKit: MainActivityUiKit,
    private val requestStatusRefresh: () -> Unit,
    private val gameModeActions: MainGameModeActions
) {
    private var refs: HomeTabUi.Refs? = null

    fun createView(): LinearLayout {
        val result = HomeTabUi.create(createDependencies())
        refs = result.refs
        return result.view
    }

    fun applyStatusSnapshot(
        snapshot: MainStatusSnapshot,
        useFahrenheit: Boolean
    ) {
        val current = refs ?: return
        current.deviceRomValue.text = snapshot.deviceInfo.rom
        current.deviceCpuValue.text = snapshot.deviceInfo.cpu
        current.deviceRamValue.text = snapshot.deviceInfo.ram
        current.dashboardText.text = snapshot.dashboardSummary
        current.activeModeText.text = snapshot.activeModeSummary
        current.thermalHistoryView.setHistory(
            snapshot.temperatureHistory,
            useFahrenheit
        )
    }

    private fun createDependencies(): HomeTabDeps {
        return HomeTabDeps(
            scrollTabContainer = { uiKit.scrollTabContainer() },
            sectionPanel = { uiKit.sectionPanel() },
            sectionHeader = { icon, text ->
                uiKit.sectionHeader(icon, text)
            },
            subtitleText = { text -> uiKit.subtitleText(text) },
            bodyText = { text -> uiKit.bodyText(text) },
            ledTitleText = { text -> uiKit.ledTitleText(text) },
            infoValue = { uiKit.infoValue() },
            infoRow = { label, value ->
                uiKit.infoRow(label, value)
            },
            statusChip = { text -> uiKit.statusChip(text) },
            actionButton = { text, danger, onClick ->
                uiKit.actionButton(text, danger, onClick)
            },
            singleRow = { button -> uiKit.singleRow(button) },
            segmentedChip = { label, selected, onClick ->
                uiKit.segmentedChip(label, selected, onClick)
            },
            space = { width -> uiKit.space(width) },
            dp = { value -> uiKit.dp(value) },
            requestStatusRefresh = requestStatusRefresh,
            hasUsageStatsPermission = {
                PermissionActions.hasUsageStatsPermission(activity)
            },
            openUsageStatsAccessSettings = {
                PermissionActions.openUsageStatsAccessSettings(activity)
            },
            showGamePickerDialog = { showGamePickerDialog() },
            updateGameModeStatusUI = { view ->
                updateGameModeStatus(view)
            },
            openSettings = {
                activity.startActivity(
                    Intent(activity, SettingsActivity::class.java)
                )
            },
            openUrl = { url -> openUrl(url) },
            deviceScanSummary = {
                deviceScanSummaryStorage(activity)
            },
            activeModeSummary = {
                ActiveModeInspector.summary(activity)
            }
        )
    }

    private fun showGamePickerDialog() {
        gameModeActions.showAppPicker {
            refs?.gameModeStatusText?.let(::updateGameModeStatus)
        }
    }

    private fun updateGameModeStatus(view: TextView) {
        view.text = getGameModeStatusTextStorage(activity)
    }

    private fun openUrl(url: String) {
        runCatching {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            )
        }
    }
}
