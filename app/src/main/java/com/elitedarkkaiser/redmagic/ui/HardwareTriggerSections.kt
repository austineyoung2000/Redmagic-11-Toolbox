package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.materialswitch.MaterialSwitch

internal object HardwareTriggerSections {
    fun createCards(
        activity: Activity,
        deps: HardwareTabDeps
    ): List<LinearLayout> {
        return listOf(
            createTriggerControls(activity, deps),
            createMappingCard(deps),
            createSafetyCard(deps)
        )
    }

    private fun createTriggerControls(
        activity: Activity,
        deps: HardwareTabDeps
    ): LinearLayout {
        val configureButton = deps.actionButton(
            "CONFIGURE TRIGGERS",
            false,
            deps.showTriggerSetupDialog
        )

        lateinit var enableButton: Button
        enableButton = deps.actionButton("ENABLE TRIGGERS", false) {
            enableButton.isEnabled = false
            enableButton.text = "ENABLING…"
            deps.enableTriggersAndService { enabled ->
                enableButton.isEnabled = true
                enableButton.text = "ENABLE TRIGGERS"
                Toast.makeText(
                    activity,
                    if (enabled) {
                        "Triggers enabled"
                    } else {
                        "Failed to enable triggers"
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        lateinit var disableButton: Button
        disableButton = deps.actionButton(
            "DISABLE TRIGGERS",
            true
        ) {
            disableButton.isEnabled = false
            disableButton.text = "DISABLING…"
            deps.disableTriggersAndService { disabled ->
                disableButton.isEnabled = true
                disableButton.text = "DISABLE TRIGGERS"
                Toast.makeText(
                    activity,
                    if (disabled) {
                        "Triggers disabled until enabled or restarted"
                    } else {
                        "Service stopped, but hardware disable failed"
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val card = deps.sectionPanel().apply {
            addView(deps.sectionHeader("⌥", "TRIGGERS"))
            addView(
                deps.bodyText(
                    "Map shoulder triggers to quick actions or " +
                        "re-enable them if the system has disabled them."
                )
            )
            addView(deps.space(deps.dp(10)))
            addView(
                switchRow(
                    activity = activity,
                    label = "Auto-start triggers",
                    checked = deps.triggersAutoStartEnabled(),
                    deps = deps
                ) { checked ->
                    deps.setTriggersAutoStartEnabled(checked)
                    if (checked) {
                        deps.enableTriggersAndService { enabled ->
                            Toast.makeText(
                                activity,
                                if (enabled) {
                                    "Auto-start triggers enabled"
                                } else {
                                    "Auto-start saved, but triggers " +
                                        "could not be enabled"
                                },
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            activity,
                            "Auto-start triggers disabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
            addView(deps.space(deps.dp(4)))
            addView(
                deps.bodyText(
                    "Automatically enable triggers and start the " +
                        "service on boot or when the app launches. " +
                        "Manual Disable pauses Auto Start until Enable " +
                        "Triggers is pressed or the phone restarts."
                )
            )
            addView(deps.singleRow(configureButton))
            addView(deps.space(deps.dp(8)))
            addView(deps.row(enableButton, disableButton))
        }

        if (
            deps.capabilities.scanComplete &&
            !deps.capabilities.triggersAvailable
        ) {
            card.addView(
                deps.bodyText(
                    "Shoulder trigger controls unavailable: both " +
                        "trigger interfaces were not detected."
                ),
                1
            )
            CapabilityUi.disableInteractions(card)
        }

        return card
    }

    private fun createMappingCard(
        deps: HardwareTabDeps
    ): LinearLayout {
        val mappingButton = deps.actionButton(
            "MANAGE GAME TRIGGER MAPPINGS",
            false,
            deps.showNativeTgkProfileDialog
        )
        val diagnosticsButton = deps.actionButton(
            "TGK DIAGNOSTICS",
            false,
            deps.showNativeTgkDiagnosticsDialog
        )
        val card = deps.sectionPanel().apply {
            addView(deps.sectionHeader("🎯", "GAME TRIGGER MAPPING"))
            addView(
                deps.bodyText(
                    "Place native L and R touch targets for individual " +
                        "apps. Portrait and landscape mappings are " +
                        "stored separately and Game Space is not required."
                )
            )
            addView(deps.space(deps.dp(8)))
            addView(deps.singleRow(mappingButton))
            addView(deps.space(deps.dp(8)))
            addView(deps.singleRow(diagnosticsButton))
        }

        if (
            deps.capabilities.scanComplete &&
            !deps.capabilities.nativeTgkAvailable
        ) {
            card.addView(
                deps.bodyText(
                    "Native TGK is unavailable on this firmware. The " +
                        "REDMAGIC InputManager interface must be present " +
                        "and readable."
                )
            )
            CapabilityUi.disableInteractions(card)
        }

        return card
    }

    private fun createSafetyCard(
        deps: HardwareTabDeps
    ): LinearLayout {
        val summary = deps.subtleLabel(deps.triggerSafetySummary())
        val configureButton = deps.actionButton(
            "CONFIGURE TRIGGER SAFETY",
            false
        ) {
            deps.showTriggerSafetyDialog {
                summary.text = deps.triggerSafetySummary()
            }
        }
        val card = deps.sectionPanel().apply {
            addView(deps.sectionHeader("🛡", "TRIGGER SAFETY"))
            addView(
                deps.bodyText(
                    "Prevent accidental shoulder-trigger actions with " +
                        "intent taps, hold filtering, lock-screen " +
                        "blocking, or Game Mode gating."
                )
            )
            addView(deps.space(deps.dp(8)))
            addView(summary)
            addView(deps.singleRow(configureButton))
        }

        if (
            deps.capabilities.scanComplete &&
            !deps.capabilities.triggersAvailable
        ) {
            CapabilityUi.disableInteractions(card)
        }

        return card
    }

    private fun switchRow(
        activity: Activity,
        label: String,
        checked: Boolean,
        deps: HardwareTabDeps,
        onChanged: (Boolean) -> Unit
    ): LinearLayout {
        val switch = MaterialSwitch(activity).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value ->
                onChanged(value)
            }
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, deps.dp(8), 0, 0)
            addView(
                TextView(activity).apply {
                    text = label
                    textSize = 14f
                    setTextColor(AppTheme.textPrimary)
                },
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
            addView(switch)
        }
    }
}
