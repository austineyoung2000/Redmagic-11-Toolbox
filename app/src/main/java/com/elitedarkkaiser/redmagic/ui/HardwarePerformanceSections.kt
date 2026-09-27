package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import com.elitedarkkaiser.redmagic.ChargeSeparationResult
import com.elitedarkkaiser.redmagic.HapticFeedback
import com.google.android.material.materialswitch.MaterialSwitch

internal object HardwarePerformanceSections {
    fun createCards(
        activity: Activity,
        deps: HardwareTabDeps
    ): List<LinearLayout> {
        return listOf(
            createChargeSeparationCard(activity, deps),
            createRefreshRateCard(deps),
            createTouchTuningCard(deps),
            createPerformanceModeCard(deps),
            createHapticCard(activity, deps)
        )
    }

    private fun createChargeSeparationCard(
        activity: Activity,
        deps: HardwareTabDeps
    ): LinearLayout {
        var updating = true
        val status = deps.subtleLabel(
            "Reading stock charge-separation state…"
        )
        val toggle = MaterialSwitch(activity).apply {
            text = "Power device directly while charging"
            textSize = 14f
            setTextColor(AppTheme.textPrimary)
            isEnabled = false
        }

        fun render(result: ChargeSeparationResult) {
            updating = true
            result.enabled?.let { toggle.isChecked = it }
            status.text = buildString {
                append(result.message)
                result.backend?.let {
                    append(" • Backend: ").append(it)
                }
            }
            toggle.isEnabled =
                deps.capabilities.scanComplete &&
                    deps.capabilities.chargeSeparationAvailable
            updating = false
        }

        toggle.setOnCheckedChangeListener { _, checked ->
            if (updating) return@setOnCheckedChangeListener

            updating = true
            toggle.isEnabled = false
            status.text = if (checked) {
                "Enabling and verifying charge separation…"
            } else {
                "Disabling and verifying charge separation…"
            }
            deps.setChargeSeparation(checked) { result ->
                render(result)
                Toast.makeText(
                    activity,
                    result.message,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val card = deps.sectionPanel().apply {
            addView(deps.sectionHeader("⚡", "CHARGE SEPARATION"))
            addView(
                deps.bodyText(
                    "Use the stock REDMAGIC charging controller to " +
                        "power the phone directly from a connected " +
                        "charger, reducing battery cycling and gaming heat."
                )
            )
            addView(deps.space(deps.dp(8)))
            addView(toggle)
            addView(deps.space(deps.dp(6)))
            addView(status)
            addView(deps.space(deps.dp(6)))
            addView(
                deps.bodyText(
                    "Enabling requires a connected charger and at least " +
                        "20% battery. Android is tried first; root is used " +
                        "only if the protected setting rejects the write."
                )
            )
        }

        if (
            !deps.capabilities.scanComplete ||
            !deps.capabilities.chargeSeparationAvailable
        ) {
            status.text = unavailableMessage(
                deps,
                "stock REDMAGIC firmware, its charge-separation " +
                    "provider, and a readable system interface are required"
            )
            CapabilityUi.disableInteractions(card)
        } else {
            deps.readChargeSeparation { result ->
                render(result)
            }
        }

        return card
    }

    private fun createRefreshRateCard(
        deps: HardwareTabDeps
    ): LinearLayout {
        val button = deps.actionButton(
            "MANAGE APP REFRESH RATES",
            false,
            deps.showRefreshRateProfiles
        )
        val card = deps.sectionPanel().apply {
            addView(deps.sectionHeader("↻", "PER-APP REFRESH RATE"))
            addView(
                deps.bodyText(
                    "Assign a supported display refresh rate to each app. " +
                        "The stock display service applies it only while " +
                        "that app is foreground."
                )
            )
            addView(deps.space(deps.dp(8)))
            addView(deps.singleRow(button))
            addView(deps.space(deps.dp(6)))
            addView(
                deps.bodyText(
                    "Uses the ordinary vendor API without root. Choose " +
                        "Follow system to keep Android's current rate."
                )
            )
        }

        if (
            !deps.capabilities.scanComplete ||
            !deps.capabilities.refreshRateAvailable
        ) {
            card.addView(
                deps.bodyText(
                    unavailableMessage(
                        deps,
                        "stock REDMAGIC firmware and the display " +
                            "refresh-rate service are required"
                    )
                )
            )
            CapabilityUi.disableInteractions(card)
        }
        return card
    }

    private fun createTouchTuningCard(
        deps: HardwareTabDeps
    ): LinearLayout {
        val button = deps.actionButton(
            "MANAGE APP TOUCH RESPONSE",
            false,
            deps.showTouchTuningProfiles
        )
        val card = deps.sectionPanel().apply {
            addView(deps.sectionHeader("☝", "GAME TOUCH RESPONSE"))
            addView(
                deps.bodyText(
                    "Configure per-app touch sampling, sensitivity, follow " +
                        "response, stability, and edge mistouch protection."
                )
            )
            addView(deps.space(deps.dp(8)))
            addView(deps.singleRow(button))
            addView(deps.space(deps.dp(6)))
            addView(
                deps.bodyText(
                    "Saved values follow the selected foreground app. " +
                        "Direct settings access is tried before the " +
                        "batched root fallback."
                )
            )
        }

        if (
            !deps.capabilities.scanComplete ||
            !deps.capabilities.touchTuningAvailable
        ) {
            card.addView(
                deps.bodyText(
                    unavailableMessage(
                        deps,
                        "the stock per-app touch controller was not detected"
                    )
                )
            )
            CapabilityUi.disableInteractions(card)
        }
        return card
    }

    private fun createPerformanceModeCard(
        deps: HardwareTabDeps
    ): LinearLayout {
        val button = deps.actionButton(
            "MANAGE APP PERFORMANCE MODES",
            false,
            deps.showPerformanceModeProfiles
        )
        val card = deps.sectionPanel().apply {
            addView(deps.sectionHeader("⚙", "GAME PERFORMANCE MODE"))
            addView(
                deps.bodyText(
                    "Assign the verified stock Eco, Balance, or Rise mode " +
                        "to each app. The selected mode is active only " +
                        "while that app is foreground."
                )
            )
            addView(deps.space(deps.dp(8)))
            addView(deps.singleRow(button))
            addView(deps.space(deps.dp(6)))
            addView(
                deps.bodyText(
                    "Uses Android settings first and one batched root " +
                        "fallback only when protected settings reject " +
                        "an ordinary app write."
                )
            )
        }

        if (
            !deps.capabilities.scanComplete ||
            !deps.capabilities.performanceModesAvailable
        ) {
            card.addView(
                deps.bodyText(
                    unavailableMessage(
                        deps,
                        "stock REDMAGIC performance services are required"
                    )
                )
            )
            CapabilityUi.disableInteractions(card)
        }
        return card
    }

    private fun createHapticCard(
        activity: Activity,
        deps: HardwareTabDeps
    ): LinearLayout {
        val summary = deps.subtleLabel("")
        var selected = HapticFeedback.Strength.fromKey(
            HapticFeedback.read(activity).strength
        )
        val buttons = linkedMapOf<HapticFeedback.Strength, Button>()

        fun renderSelection() {
            buttons.forEach { (strength, button) ->
                button.backgroundTintList = ColorStateList.valueOf(
                    if (strength == selected) {
                        AppTheme.chipActiveColor
                    } else {
                        AppTheme.chipOnColor
                    }
                )
                button.setTextColor(AppTheme.textPrimary)
            }
            summary.text =
                "Current strength: ${selected.label}. " +
                    "Feedback is event-driven; no polling is used."
        }

        val strengthRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        HapticFeedback.Strength.entries.forEach { strength ->
            val button = deps.actionButton(strength.label, false) {
                selected = strength
                HapticFeedback.setStrength(activity, strength)
                renderSelection()
                deps.testHapticStrength(strength)
            }
            buttons[strength] = button
            strengthRow.addView(
                button,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginEnd = if (
                        strength != HapticFeedback.Strength.HIGH
                    ) deps.dp(6) else 0
                }
            )
        }

        val toggle = MaterialSwitch(activity).apply {
            text = "Hardware action feedback"
            textSize = 14f
            setTextColor(AppTheme.textPrimary)
            isChecked = HapticFeedback.read(activity).enabled
            setOnCheckedChangeListener { _, checked ->
                HapticFeedback.setEnabled(activity, checked)
                Toast.makeText(
                    activity,
                    "Hardware haptic feedback " +
                        if (checked) "enabled" else "disabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        renderSelection()

        return deps.sectionPanel().apply {
            addView(deps.sectionHeader("〰", "HAPTIC FEEDBACK"))
            addView(
                deps.bodyText(
                    "Add a short hardware vibration to trigger actions, " +
                        "dual-app slider launches, and Master Profile " +
                        "application."
                )
            )
            addView(deps.space(deps.dp(8)))
            addView(toggle)
            addView(deps.space(deps.dp(8)))
            addView(deps.subtleLabel("Strength — tap to select and test"))
            addView(deps.space(deps.dp(6)))
            addView(strengthRow)
            addView(deps.space(deps.dp(8)))
            addView(summary)
        }
    }

    private fun unavailableMessage(
        deps: HardwareTabDeps,
        requirement: String
    ): String {
        return if (deps.capabilities.scanComplete) {
            "Unavailable: $requirement."
        } else {
            "Unavailable until the compatibility scan completes."
        }
    }
}
