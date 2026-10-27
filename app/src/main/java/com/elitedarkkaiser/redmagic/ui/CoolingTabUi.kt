package com.elitedarkkaiser.redmagic.ui

import com.elitedarkkaiser.redmagic.ui.components.SelectionControlStyle
import android.widget.Button
import android.widget.CheckBox
import com.google.android.material.checkbox.MaterialCheckBox
import android.widget.LinearLayout
import com.google.android.material.slider.Slider
import android.widget.TextView
import com.elitedarkkaiser.redmagic.HardwareController

object CoolingTabUi {
    data class Refs(
        val tempText: TextView,
        val rpmText: TextView,
        val curveStatusText: TextView,
        val fanSeek: Slider,
        val autoCurveCheck: CheckBox,
        val quietCurveButton: Button,
        val balancedCurveButton: Button,
        val turboCurveButton: Button,
        val smartPumpStatusView: TextView,
        val smartPumpSpeedView: TextView
    )

    data class Result(
        val view: LinearLayout,
        val refs: Refs
    )

    fun create(deps: CoolingTabDeps): Result {
        val container = deps.scrollTabContainer()

        val tempText = TextView(container.context).apply {
            text = "Current temp: --"
            textSize = 13f
            setTextColor(AppTheme.textSecondary)
            setPadding(0, deps.dp(6), 0, deps.dp(4))
        }

        val rpmText = TextView(container.context).apply {
            text = "Fan speed: -- RPM"
            textSize = 16f
            setTextColor(AppTheme.textPrimary)
            setPadding(0, deps.dp(4), 0, deps.dp(8))
        }
        lateinit var curveStatusText: TextView
        lateinit var autoCurveCheck: CheckBox

        val fanSeek = SelectionControlStyle.slider(container.context).apply {
            valueFrom = 0f
            valueTo = 5f
            stepSize = 1f
            value = 0f

            addOnSliderTouchListener(
                object : Slider.OnSliderTouchListener {
                    override fun onStartTrackingTouch(
                        slider: Slider
                    ) = Unit

                    override fun onStopTrackingTouch(
                        slider: Slider
                    ) {
                        if (deps.getAutoFanCurveEnabled()) {
                            return
                        }

                        val level = slider.value.toInt()
                        deps.runBackground {
                            HardwareController.setFanLevel(level)
                            deps.refreshStatus()
                        }
                    }
                }
            )
        }

        val fanOnBtn = deps.actionButton("FAN ON", false) {
            deps.runBackground {
                HardwareController.enableFan(true)
                deps.refreshStatus()
            }
        }

        val fanOffBtn = deps.actionButton("FAN OFF", true) {
            deps.runBackground {
                HardwareController.enableFan(false)
                deps.refreshStatus()
            }
        }

        val rpmBtn = deps.actionButton("READ RPM", false) {
            deps.refreshStatus()
        }

        val modeRow = LinearLayout(container.context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        lateinit var quietChip: Button
        lateinit var balancedChip: Button
        lateinit var turboChip: Button

        fun refreshFanCurveButtons() {
            val selectedCurve = deps.getSelectedCurve()
            deps.updateSelectableButton(quietChip, selectedCurve == "quiet")
            deps.updateSelectableButton(balancedChip, selectedCurve == "balanced")
            deps.updateSelectableButton(turboChip, selectedCurve == "turbo")
        }

        fun applyFanCurve(
            curve: String,
            displayName: String
        ) {
            if (deps.getAutoFanCurveEnabled()) return

            deps.setSelectedCurve(curve)
            deps.setSelectedCurveSaved(curve)
            refreshFanCurveButtons()
            curveStatusText.text =
                "Selected curve: $displayName • Applying…"

            val submitted = deps.runBackground {
                val level =
                    HardwareController.applyFanCurve(curve)

                curveStatusText.post {
                    /*
                     * Ignore a completion from an older queued request
                     * when the user has already selected another curve.
                     */
                    if (deps.getSelectedCurve() != curve) {
                        return@post
                    }

                    if (level != null) {
                        fanSeek.value = level.toFloat()
                        curveStatusText.text =
                            "Selected curve: $displayName • Applied"
                    } else {
                        curveStatusText.text =
                            "Selected curve: $displayName • " +
                                "Temperature unavailable"
                    }
                }

                deps.refreshStatus()
            }

            if (!submitted) {
                curveStatusText.text =
                    "Selected curve: $displayName • " +
                        "Unable to apply"
            }
        }

        quietChip = deps.segmentedChip(
            "Quiet",
            deps.getSelectedCurve() == "quiet"
        ) {
            applyFanCurve("quiet", "Quiet")
        }

        balancedChip = deps.segmentedChip(
            "Balanced",
            deps.getSelectedCurve() == "balanced"
        ) {
            applyFanCurve("balanced", "Balanced")
        }

        turboChip = deps.segmentedChip(
            "Turbo",
            deps.getSelectedCurve() == "turbo"
        ) {
            applyFanCurve("turbo", "Turbo")
        }

        listOf(quietChip, balancedChip, turboChip).forEach { button ->
            button.minHeight = deps.dp(72)
            button.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        modeRow.addView(quietChip)
        modeRow.addView(deps.space(deps.dp(8)))
        modeRow.addView(balancedChip)
        modeRow.addView(deps.space(deps.dp(8)))
        modeRow.addView(turboChip)

        curveStatusText = TextView(container.context).apply {
            text = "Selected curve: ${deps.getSelectedCurve().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}"
            textSize = 13f
            setTextColor(AppTheme.textSecondary)
            setPadding(0, deps.dp(6), 0, deps.dp(4))
        }

        autoCurveCheck = MaterialCheckBox(container.context).apply {
            text = "Automatic fan control based on temperature"
            setTextColor(AppTheme.textPrimary)
            textSize = 13f
            setPadding(0, deps.dp(6), 0, deps.dp(4))
            setOnCheckedChangeListener { _, checked ->
                deps.setAutoFanCurveEnabled(checked)
                deps.setAutoFanEnabledSaved(checked)
                deps.updateManualCurveUiState()

                if (checked) {
                    deps.startAutoFanService()
                    curveStatusText.text = "Auto fan curve active • Running in background service"
                } else {
                    deps.stopAutoFanService()
                    curveStatusText.text = "Selected curve: ${deps.getSelectedCurve()} • Manual control"
                }

                deps.refreshStatus()
            }
        }

        val pumpSection = CoolingPumpSection.create(deps)
        val smartPumpStatusView = pumpSection.statusView
        val smartPumpSpeedView = pumpSection.speedView
        val pumpCard = pumpSection.card

        val coolingCard = deps.sectionPanel().apply {
            addView(deps.sectionHeader("❄", "COOLING SYSTEM"))
            addView(tempText)
            addView(rpmText)
            addView(deps.spacer(deps.dp(12)))
            addView(deps.sectionHeader("▦", "FAN PRESET"))
            addView(modeRow)
            addView(curveStatusText)
            addView(deps.subtleLabel("Quiet: low noise  •  Balanced: everyday use  •  Turbo: maximum cooling"))
            addView(deps.spacer(deps.dp(12)))
            addView(autoCurveCheck)
            addView(deps.subtleLabel("Automatically adjust fan speed based on temperature"))
            addView(deps.spacer(deps.dp(12)))
            addView(deps.subtleLabel("Manual fan level"))
            addView(fanSeek)
            addView(deps.row(fanOnBtn, fanOffBtn))
            addView(deps.singleRow(rpmBtn))
        }

        if (
            deps.capabilities.scanComplete &&
            !deps.capabilities.fanAvailable
        ) {
            coolingCard.addView(
                deps.bodyText(
                    "Fan controls unavailable: required " +
                        "vendor/kernel interfaces were not detected."
                ),
                1
            )

            listOf(
                fanSeek,
                fanOnBtn,
                fanOffBtn,
                rpmBtn,
                autoCurveCheck,
                modeRow
            ).forEach {
                CapabilityUi.disableInteractions(it)
            }
        } else if (
            deps.capabilities.scanComplete &&
            !deps.capabilities.fanRpmAvailable
        ) {
            rpmBtn.isEnabled = false
            rpmBtn.alpha = 0.5f
            coolingCard.addView(
                deps.subtleLabel(
                    "Fan control is available, but RPM telemetry " +
                        "was not detected on this ROM."
                ),
                2
            )
        }

        if (
            deps.capabilities.scanComplete &&
            !deps.capabilities.pumpAvailable
        ) {
            pumpCard.addView(
                deps.bodyText(
                    "Micropump controls unavailable: required " +
                        "vendor interfaces were not detected."
                ),
                1
            )
            CapabilityUi.disableInteractions(pumpCard)
        }

        container.addView(coolingCard)
        container.addView(deps.spacer(deps.dp(16)))
        container.addView(pumpCard)

        return Result(
            view = container,
            refs = Refs(
                tempText = tempText,
                rpmText = rpmText,
                curveStatusText = curveStatusText,
                fanSeek = fanSeek,
                autoCurveCheck = autoCurveCheck,
                quietCurveButton = quietChip,
                balancedCurveButton = balancedChip,
                turboCurveButton = turboChip,
                smartPumpStatusView = smartPumpStatusView,
                smartPumpSpeedView = smartPumpSpeedView
            )
        )
    }
}
