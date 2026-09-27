package com.elitedarkkaiser.redmagic.ui

import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.DeviceCapabilities

data class CoolingTabDeps(
    val scrollTabContainer: () -> LinearLayout,
    val sectionPanel: () -> LinearLayout,
    val sectionHeader: (String, String) -> LinearLayout,
    val subtleLabel: (String) -> TextView,
    val bodyText: (String) -> TextView,
    val segmentedChip: (String, Boolean, () -> Unit) -> Button,
    val updateSelectableButton: (Button, Boolean) -> Unit,
    val actionButton: (String, Boolean, () -> Unit) -> Button,
    val row: (Button, Button) -> LinearLayout,
    val singleRow: (Button) -> LinearLayout,
    val space: (Int) -> TextView,
    val spacer: (Int) -> TextView,
    val dp: (Int) -> Int,
    val roundedBg: (Int, Int, Int) -> android.graphics.drawable.GradientDrawable,
    val runBackground: (() -> Unit) -> Boolean,
    val capabilities: DeviceCapabilities,

    val getSelectedCurve: () -> String,
    val setSelectedCurve: (String) -> Unit,
    val setSelectedCurveSaved: (String) -> Unit,

    val getAutoFanCurveEnabled: () -> Boolean,
    val setAutoFanCurveEnabled: (Boolean) -> Unit,
    val setAutoFanEnabledSaved: (Boolean) -> Unit,

    val getPumpEnabled: () -> Boolean,
    val setPumpEnabled: (Boolean) -> Unit,
    val getPumpProfile: () -> String,
    val setPumpProfileValue: (String) -> Unit,
    val getAutoPumpEnabled: () -> Boolean,
    val setAutoPumpEnabled: (Boolean) -> Unit,

    val startAutoFanService: () -> Unit,
    val stopAutoFanService: () -> Unit,
    val startAutoPumpService: () -> Unit,
    val stopAutoPumpService: () -> Unit,
    val savePumpState: () -> Unit,
    val saveAutoPumpState: () -> Unit,
    val refreshStatus: () -> Unit,
    val refreshSmartPumpStatusViews: () -> Unit,
    val buildAutoPumpStatusText: () -> Pair<String, String>,
    val applyPumpProfile: (String) -> Unit,
    val confirmExperimentalPumpThenApply: ((() -> Unit) -> Unit),
    val updateManualCurveUiState: () -> Unit
)
