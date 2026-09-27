package com.elitedarkkaiser.redmagic.ui

import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

data class HomeTabDeps(
    val scrollTabContainer: () -> LinearLayout,
    val sectionPanel: () -> LinearLayout,
    val sectionHeader: (String, String) -> LinearLayout,
    val subtitleText: (String) -> TextView,
    val bodyText: (String) -> TextView,
    val ledTitleText: (String) -> TextView,
    val infoValue: () -> TextView,
    val infoRow: (String, TextView) -> LinearLayout,
    val statusChip: (String) -> TextView,
    val actionButton: (String, Boolean, () -> Unit) -> Button,
    val singleRow: (Button) -> LinearLayout,
    val segmentedChip: (String, Boolean, () -> Unit) -> Button,
    val space: (Int) -> TextView,
    val dp: (Int) -> Int,
    val requestStatusRefresh: () -> Unit,
    val hasUsageStatsPermission: () -> Boolean,
    val openUsageStatsAccessSettings: () -> Unit,
    val showGamePickerDialog: () -> Unit,
    val updateGameModeStatusUI: (TextView) -> Unit,
    val openSettings: () -> Unit,
    val openUrl: (String) -> Unit,
    val deviceScanSummary: () -> String,
    val activeModeSummary: () -> String
)
