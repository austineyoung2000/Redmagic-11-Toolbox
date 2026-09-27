package com.elitedarkkaiser.redmagic.ui

import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.HardwareController
import com.google.android.material.materialswitch.MaterialSwitch

internal object CoolingPumpSection {
    data class Result(
        val card: LinearLayout,
        val statusView: TextView,
        val speedView: TextView
    )

    fun create(deps: CoolingTabDeps): Result {
        lateinit var smartPumpStatusView: TextView
        lateinit var smartPumpSpeedView: TextView

        val card = deps.sectionPanel().apply {
            addView(deps.sectionHeader("◉", "PUMP"))
            addView(deps.bodyText("Liquid cooling pump control with manual speed, auto temperature control, and live diagnostics."))
            addView(deps.spacer(deps.dp(10)))

            lateinit var pumpPowerSwitch: MaterialSwitch
            lateinit var autoPumpSwitch: MaterialSwitch
            var updatingPumpSwitches = false

            fun syncPumpSwitches() {
                updatingPumpSwitches = true
                try {
                    pumpPowerSwitch.isChecked = deps.getPumpEnabled()
                    autoPumpSwitch.isChecked = deps.getAutoPumpEnabled()
                } finally {
                    updatingPumpSwitches = false
                }
            }

            fun manualSpeedLabel(): String {
                return deps.getPumpProfile().replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase() else it.toString()
                }
            }

            fun manualSpeedValue(): Int {
                return when (deps.getPumpProfile().lowercase()) {
                    "slow" -> 40
                    "medium" -> 60
                    "quick" -> 80
                    "experimental" -> 90
                    else -> 80
                }
            }

            fun refreshPumpDiagnostics() {
                if (deps.getAutoPumpEnabled()) {
                    val status = deps.buildAutoPumpStatusText()
                    smartPumpStatusView.text = status.first
                    smartPumpSpeedView.text = status.second
                } else {
                    smartPumpStatusView.text = if (deps.getPumpEnabled()) {
                        "Pump Mode: MANUAL • ${manualSpeedLabel()}"
                    } else {
                        "Pump Mode: OFF"
                    }
                    smartPumpSpeedView.text = if (deps.getPumpEnabled()) {
                        "Speed: ${manualSpeedValue()} • Freq: 4"
                    } else {
                        "Speed: 0 • Freq: 0"
                    }
                }
            }

            fun setManualControlsEnabled(enabled: Boolean) {
                pumpPowerSwitch.isEnabled = enabled
            }

            val pumpPowerTitle = TextView(context).apply {
                text = "Pump Power"
                textSize = 15f
                setTextColor(AppTheme.textPrimary)
                typeface = Typeface.create(AppTheme.appTypeface, Typeface.BOLD)
            }

            val pumpPowerDesc = TextView(context).apply {
                text = "Turn the liquid cooling micropump on or off."
                textSize = 12f
                setTextColor(AppTheme.textSecondary)
                setPadding(0, deps.dp(4), 0, 0)
            }

            pumpPowerSwitch = MaterialSwitch(context).apply {
                isChecked = deps.getPumpEnabled()
                setOnCheckedChangeListener { _, checked ->
                    if (updatingPumpSwitches) {
                        return@setOnCheckedChangeListener
                    }

                    deps.setPumpEnabled(checked)
                    deps.savePumpState()

                    if (checked) {
                        deps.runBackground {
                            HardwareController.setPumpProfile(
                                deps.getPumpProfile()
                            )
                            deps.refreshStatus()
                        }
                    } else {
                        deps.setAutoPumpEnabled(false)
                        deps.saveAutoPumpState()
                        /*
                         * Let the Auto listener restore the manual
                         * controls. The unchanged Pump switch value
                         * prevents a recursive Pump callback.
                         */
                        autoPumpSwitch.isChecked = false

                        deps.runBackground {
                            deps.stopAutoPumpService()
                            HardwareController.enablePump(false)
                            deps.refreshStatus()
                        }
                    }

                    syncPumpSwitches()
                    refreshPumpDiagnostics()
                    deps.refreshSmartPumpStatusViews()
                }
            }

            val powerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(pumpPowerTitle)
                    addView(pumpPowerDesc)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(pumpPowerSwitch)
            }

            addView(powerRow)
            addView(deps.spacer(deps.dp(14)))
            addView(deps.subtleLabel("Manual pump speed"))

            val speedRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val chipParams = LinearLayout.LayoutParams(0, deps.dp(42), 1f)
            val gapParams = LinearLayout.LayoutParams(deps.dp(6), 1)

            lateinit var slowBtn: Button
            lateinit var mediumBtn: Button
            lateinit var quickBtn: Button
            lateinit var experimentalBtn: Button

            fun refreshPumpProfileButtons() {
                val selectedProfile = deps.getPumpProfile()
                deps.updateSelectableButton(
                    slowBtn,
                    selectedProfile == "slow"
                )
                deps.updateSelectableButton(
                    mediumBtn,
                    selectedProfile == "medium"
                )
                deps.updateSelectableButton(
                    quickBtn,
                    selectedProfile == "quick"
                )
                deps.updateSelectableButton(
                    experimentalBtn,
                    selectedProfile == "experimental"
                )
            }

            slowBtn = deps.segmentedChip("Slow", deps.getPumpProfile() == "slow") {
                deps.applyPumpProfile("slow")
                refreshPumpProfileButtons()
                syncPumpSwitches()
                refreshPumpDiagnostics()
            }

            mediumBtn = deps.segmentedChip("Medium", deps.getPumpProfile() == "medium") {
                deps.applyPumpProfile("medium")
                refreshPumpProfileButtons()
                syncPumpSwitches()
                refreshPumpDiagnostics()
            }

            quickBtn = deps.segmentedChip("Quick", deps.getPumpProfile() == "quick") {
                deps.applyPumpProfile("quick")
                refreshPumpProfileButtons()
                syncPumpSwitches()
                refreshPumpDiagnostics()
            }

            experimentalBtn = deps.segmentedChip("OC", deps.getPumpProfile() == "experimental") {
                deps.confirmExperimentalPumpThenApply {
                    refreshPumpProfileButtons()
                    syncPumpSwitches()
                    refreshPumpDiagnostics()
                }
            }

            speedRow.addView(slowBtn, chipParams)
            speedRow.addView(deps.space(deps.dp(6)), gapParams)
            speedRow.addView(mediumBtn, chipParams)
            speedRow.addView(deps.space(deps.dp(6)), gapParams)
            speedRow.addView(quickBtn, chipParams)
            speedRow.addView(deps.space(deps.dp(6)), gapParams)
            speedRow.addView(experimentalBtn, chipParams)

            addView(speedRow)
            addView(deps.spacer(deps.dp(14)))

            val autoTitle = TextView(context).apply {
                text = "Auto Pump Speed"
                textSize = 15f
                setTextColor(AppTheme.textPrimary)
                typeface = Typeface.create(AppTheme.appTypeface, Typeface.BOLD)
            }

            val autoDesc = TextView(context).apply {
                text = "Automatically adjusts pump speed based on device temperature and keeps running after app close."
                textSize = 12f
                setTextColor(AppTheme.textSecondary)
                setPadding(0, deps.dp(4), 0, 0)
            }

            autoPumpSwitch = MaterialSwitch(context).apply {
                isChecked = deps.getAutoPumpEnabled()
                setOnCheckedChangeListener { _, checked ->
                    if (updatingPumpSwitches) {
                        return@setOnCheckedChangeListener
                    }

                    deps.setAutoPumpEnabled(checked)
                    deps.saveAutoPumpState()

                    if (checked) {
                        deps.setPumpEnabled(true)
                        deps.savePumpState()

                        deps.runBackground {
                            HardwareController.setPumpProfile(
                                deps.getPumpProfile()
                            )
                            deps.startAutoPumpService()
                            deps.refreshStatus()
                        }
                    } else {
                        deps.stopAutoPumpService()
                    }

                    syncPumpSwitches()
                    setManualControlsEnabled(!checked)
                    refreshPumpDiagnostics()
                    deps.refreshSmartPumpStatusViews()
                }
            }

            val autoRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(autoTitle)
                    addView(autoDesc)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(autoPumpSwitch)
            }

            addView(autoRow)
            addView(deps.spacer(deps.dp(14)))
            addView(deps.subtleLabel("Live diagnostics"))

            smartPumpStatusView = TextView(context).apply {
                textSize = 12f
                setTextColor(AppTheme.textPrimary)
                setPadding(0, deps.dp(6), 0, 0)
            }

            smartPumpSpeedView = TextView(context).apply {
                textSize = 12f
                setTextColor(AppTheme.textSecondary)
                setPadding(0, deps.dp(4), 0, 0)
            }

            addView(smartPumpStatusView)
            addView(smartPumpSpeedView)

            setManualControlsEnabled(!deps.getAutoPumpEnabled())
            refreshPumpDiagnostics()
        }

        return Result(
            card = card,
            statusView = smartPumpStatusView,
            speedView = smartPumpSpeedView
        )
    }
}
