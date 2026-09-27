package com.elitedarkkaiser.redmagic.ui

import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.MasterProfile
import com.elitedarkkaiser.redmagic.DeviceCapabilities
import com.elitedarkkaiser.redmagic.ChargeSeparationResult

data class HardwareTabDeps(
    val scrollTabContainer: () -> LinearLayout,
    val sectionPanel: () -> LinearLayout,
    val sectionHeader: (String, String) -> LinearLayout,
    val bodyText: (String) -> TextView,
    val subtleLabel: (String) -> TextView,
    val actionButton: (String, Boolean, () -> Unit) -> Button,
    val singleRow: (Button) -> LinearLayout,
    val row: (Button, Button) -> LinearLayout,
    val space: (Int) -> TextView,
    val dp: (Int) -> Int,
    val capabilities: DeviceCapabilities,

    val showTriggerSetupDialog: () -> Unit,
    val showNativeTgkProfileDialog: () -> Unit,
    val showNativeTgkDiagnosticsDialog: () -> Unit,
    val triggerSafetySummary: () -> String,
    val showTriggerSafetyDialog: ((() -> Unit) -> Unit),
    val enableTriggersAndService: ((Boolean) -> Unit) -> Unit,
    val disableTriggersAndService: ((Boolean) -> Unit) -> Unit,

    val readChargeSeparation:
        ((ChargeSeparationResult) -> Unit) -> Unit,
    val setChargeSeparation:
        (Boolean, (ChargeSeparationResult) -> Unit) -> Unit,
    val showRefreshRateProfiles: () -> Unit,
    val showTouchTuningProfiles: () -> Unit,

    val loadMasterProfiles: () -> List<MasterProfile>,
    val saveMasterProfile:
        (String, (Boolean) -> Unit) -> Unit,
    val applyMasterProfile: (MasterProfile) -> Unit,
    val deleteMasterProfile: (String) -> Unit,
    val exportMasterBackup: () -> Unit,
    val importMasterBackup: () -> Unit,

    val automationRulesSummary: () -> String,
    val showAutomationRulesDialog:
        ((() -> Unit) -> Unit)
)
