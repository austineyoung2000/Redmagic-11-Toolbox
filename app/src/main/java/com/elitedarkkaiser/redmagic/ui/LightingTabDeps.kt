package com.elitedarkkaiser.redmagic.ui

import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.DeviceCapabilities

data class LightingTabDeps(
    val scrollTabContainer: () -> LinearLayout,
    val sectionPanel: () -> LinearLayout,
    val sectionHeader: (String, String) -> LinearLayout,
    val bodyText: (String) -> TextView,
    val subtleLabel: (String) -> TextView,
    val infoRow: (String, TextView) -> LinearLayout,
    val actionButton: (String, Boolean, () -> Unit) -> Button,
    val filterChip: (String, Boolean, () -> Unit) -> Button,
    val updateSelectableButton: (Button, Boolean) -> Unit,
    val singleRow: (Button) -> LinearLayout,
    val row: (Button, Button) -> LinearLayout,
    val dp: (Int) -> Int,
    val capabilities: DeviceCapabilities,

    val getRealTimePreviewEnabled: () -> Boolean,
    val setRealTimePreviewEnabled: (Boolean) -> Unit,
    val saveRealTimePreviewEnabled: (Boolean) -> Unit,

    val showFanLedDialog: () -> Unit,
    val showLogoLedDialog: () -> Unit,
    val showGameModeBarDialog: () -> Unit,
    val showShoulderLedDialog: () -> Unit,
    val rgbStudioSummary: () -> String,
    val showRgbStudioDialog: (() -> Unit) -> Unit,
    val showGameModeAppPicker: () -> Unit,
    val showGameModeProfileDialog: () -> Unit,
    val gameModeAppsSummary: () -> String,

    val getChargingLedEnabled: () -> Boolean,
    val setChargingLedEnabled: (Boolean) -> Unit,
    val showChargingFanLedDialog: () -> Unit,
    val showChargingLogoLedDialog: () -> Unit,
    val showChargingGameModeBarDialog: () -> Unit,
    val showChargingShoulderLedDialog: () -> Unit,

    val getCallLightingEnabled: () -> Boolean,
    val setCallLightingEnabled: (Boolean) -> Unit,
    val getPauseFanDuringCalls: () -> Boolean,
    val setPauseFanDuringCalls: (Boolean) -> Unit,
    val showIncomingCallProfileDialog: () -> Unit,
    val showConnectedCallProfileDialog: () -> Unit
)
