package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.elitedarkkaiser.redmagic.HapticFeedback
import com.elitedarkkaiser.redmagic.ProfileDialogs
import com.google.android.material.materialswitch.MaterialSwitch

object HardwareTabUi {
    fun create(activity: Activity, deps: HardwareTabDeps): LinearLayout {
        val container = deps.scrollTabContainer()

        val configureTriggersBtn = deps.actionButton("CONFIGURE TRIGGERS", false) {
            deps.showTriggerSetupDialog()
        }

        lateinit var trigEnableBtn: Button
        trigEnableBtn = deps.actionButton(
            "ENABLE TRIGGERS",
            false
        ) {
            trigEnableBtn.isEnabled = false
            trigEnableBtn.text = "ENABLING…"

            deps.enableTriggersAndService { enabled ->
                trigEnableBtn.isEnabled = true
                trigEnableBtn.text = "ENABLE TRIGGERS"

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

        lateinit var trigDisableBtn: Button
        trigDisableBtn = deps.actionButton(
            "DISABLE TRIGGERS",
            true
        ) {
            trigDisableBtn.isEnabled = false
            trigDisableBtn.text = "DISABLING…"

            deps.disableTriggersAndService { disabled ->
                trigDisableBtn.isEnabled = true
                trigDisableBtn.text = "DISABLE TRIGGERS"

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

        val triggerCard = deps.sectionPanel().apply {
            addView(deps.sectionHeader("⌥", "TRIGGERS"))
            addView(deps.bodyText("Map shoulder triggers to quick actions or re-enable them if the system has disabled them."))
            addView(deps.space(deps.dp(10)))

            addView(switchRow(
                activity = activity,
                label = "Auto-start triggers",
                prefsName = "triggers",
                key = "triggers_auto_start",
                defaultValue = false,
                deps = deps
            ) { checked ->
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
            })

            addView(deps.space(deps.dp(4)))
            addView(deps.bodyText("Automatically enable triggers and start the service on boot or when the app launches. Manual Disable pauses Auto Start until Enable Triggers is pressed or the phone restarts."))
            addView(deps.singleRow(configureTriggersBtn))
            addView(deps.space(deps.dp(8)))
            addView(deps.row(trigEnableBtn, trigDisableBtn))
        }

        if (
            deps.capabilities.scanComplete &&
            !deps.capabilities.triggersAvailable
        ) {
            triggerCard.addView(
                deps.bodyText(
                    "Shoulder trigger controls unavailable: both " +
                        "trigger interfaces were not detected."
                ),
                1
            )
            CapabilityUi.disableInteractions(triggerCard)
        }

        container.addView(triggerCard)

        val nativeMappingButton = deps.actionButton(
            "MANAGE GAME TRIGGER MAPPINGS",
            false
        ) {
            deps.showNativeTgkProfileDialog()
        }
        val nativeDiagnosticsButton = deps.actionButton(
            "TGK DIAGNOSTICS",
            false
        ) {
            deps.showNativeTgkDiagnosticsDialog()
        }

        val nativeMappingCard = deps.sectionPanel().apply {
            addView(
                deps.sectionHeader(
                    "🎯",
                    "GAME TRIGGER MAPPING"
                )
            )
            addView(
                deps.bodyText(
                    "Place native L and R touch targets for " +
                        "individual apps. Portrait and landscape " +
                        "mappings are stored separately and Game " +
                        "Space is not required."
                )
            )
            addView(deps.space(deps.dp(8)))
            addView(deps.singleRow(nativeMappingButton))
            addView(deps.space(deps.dp(8)))
            addView(deps.singleRow(nativeDiagnosticsButton))
        }

        if (
            deps.capabilities.scanComplete &&
            !deps.capabilities.nativeTgkAvailable
        ) {
            nativeMappingCard.addView(
                deps.bodyText(
                    "Native TGK is unavailable on this firmware. " +
                        "The REDMAGIC InputManager TGK interface " +
                        "must be present and readable."
                )
            )
            CapabilityUi.disableInteractions(nativeMappingCard)
        }

        container.addView(nativeMappingCard)

        val triggerSafetySummary =
            deps.subtleLabel(deps.triggerSafetySummary())
        val configureSafetyButton = deps.actionButton(
            "CONFIGURE TRIGGER SAFETY",
            false
        ) {
            deps.showTriggerSafetyDialog {
                triggerSafetySummary.text =
                    deps.triggerSafetySummary()
            }
        }
        val triggerSafetyCard = deps.sectionPanel().apply {
            addView(deps.sectionHeader("🛡", "TRIGGER SAFETY"))
            addView(
                deps.bodyText(
                    "Prevent accidental shoulder-trigger actions " +
                        "with intent taps, hold filtering, " +
                        "lock-screen blocking, or Game Mode gating."
                )
            )
            addView(deps.space(deps.dp(8)))
            addView(triggerSafetySummary)
            addView(deps.singleRow(configureSafetyButton))
        }

        if (
            deps.capabilities.scanComplete &&
            !deps.capabilities.triggersAvailable
        ) {
            CapabilityUi.disableInteractions(triggerSafetyCard)
        }

        container.addView(triggerSafetyCard)

        val hapticSummary = deps.subtleLabel("")
        var selectedHapticStrength =
            HapticFeedback.Strength.fromKey(
                HapticFeedback.read(activity).strength
            )

        val hapticStrengthButtons = LinkedHashMap<
            HapticFeedback.Strength,
            Button
        >()

        fun refreshHapticStrengthButtons() {
            hapticStrengthButtons.forEach { (strength, button) ->
                button.backgroundTintList =
                    ColorStateList.valueOf(
                        if (strength == selectedHapticStrength) {
                            AppTheme.chipActiveColor
                        } else {
                            AppTheme.chipOnColor
                        }
                    )
                button.setTextColor(AppTheme.textPrimary)
            }

            hapticSummary.text =
                "Current strength: " +
                    selectedHapticStrength.label +
                    ". Feedback is event-driven; no polling is used."
        }

        val hapticStrengthRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        HapticFeedback.Strength.entries.forEach { strength ->
            val button = deps.actionButton(
                strength.label,
                false
            ) {
                selectedHapticStrength = strength
                HapticFeedback.setStrength(activity, strength)
                refreshHapticStrengthButtons()

                Thread(
                    {
                        HapticFeedback.testPulse(strength)
                    },
                    "RedMagicHapticTest"
                ).start()
            }

            hapticStrengthButtons[strength] = button
            hapticStrengthRow.addView(
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

        val hapticSwitch = MaterialSwitch(activity).apply {
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

        refreshHapticStrengthButtons()

        val hapticCard = deps.sectionPanel().apply {
            addView(deps.sectionHeader("〰", "HAPTIC FEEDBACK"))
            addView(deps.bodyText(
                "Add a short hardware vibration to trigger actions, " +
                    "dual-app slider launches, and Master Profile " +
                    "application."
            ))
            addView(deps.space(deps.dp(8)))
            addView(hapticSwitch)
            addView(deps.space(deps.dp(8)))
            addView(deps.subtleLabel("Strength — tap to select and test"))
            addView(deps.space(deps.dp(6)))
            addView(hapticStrengthRow)
            addView(deps.space(deps.dp(8)))
            addView(hapticSummary)
        }

        container.addView(hapticCard)

        val masterProfilesCard = deps.sectionPanel().apply {
            addView(deps.sectionHeader("◆", "MASTER PROFILES"))
            addView(deps.bodyText("Save complete app settings or export a portable JSON backup for another installation."))

            val masterProfileList = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, deps.dp(10), 0, 0)
            }

            fun renderMasterProfiles() {
                masterProfileList.removeAllViews()
                val profiles = deps.loadMasterProfiles()

                if (profiles.isEmpty()) {
                    masterProfileList.addView(deps.subtleLabel("No saved master profiles yet"))
                    return
                }

                profiles.forEach { profile ->
                    val row = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }

                    val applyBtn = deps.actionButton(profile.name, false) {
                        deps.applyMasterProfile(profile)
                    }.apply {
                        setPadding(deps.dp(16), deps.dp(10), deps.dp(16), deps.dp(10))
                    }

                    val deleteBtn = deps.actionButton("DEL", true) {
                        deps.deleteMasterProfile(profile.name)
                        renderMasterProfiles()
                    }.apply {
                        setPadding(deps.dp(14), deps.dp(10), deps.dp(14), deps.dp(10))
                    }

                    row.addView(
                        applyBtn,
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    )
                    row.addView(deps.space(deps.dp(8)))
                    row.addView(deleteBtn)

                    masterProfileList.addView(row)
                    masterProfileList.addView(deps.space(deps.dp(10)))
                }
            }

            val saveMasterBtn = deps.actionButton("SAVE MASTER PROFILE", false) {
                ProfileDialogs.showStyledNameOnlyDialog(
                    context = activity,
                    title = "Save Master Profile",
                    hint = "Master profile name",
                    textPrimary = AppTheme.textPrimary,
                    textSecondary = AppTheme.textSecondary,
                    panelColor = AppTheme.panelColor,
                    borderColor = AppTheme.borderColor,
                    dp = { value -> deps.dp(value) },
                    roundedBg = { fill, stroke, radius -> AppTheme.roundedBg(fill, stroke, radius.toFloat()) },
                    actionButton = { text, isDanger, onClick -> deps.actionButton(text, isDanger, onClick) },
                    space = { value -> deps.space(value) },
                    onSave = { name ->
                        deps.saveMasterProfile(name) { saved ->
                            if (saved) {
                                renderMasterProfiles()
                            }
                        }
                    }
                )
            }

            addView(deps.singleRow(saveMasterBtn))
            addView(deps.space(deps.dp(8)))
            addView(deps.row(
                deps.actionButton("EXPORT BACKUP", false) {
                    deps.exportMasterBackup()
                },
                deps.actionButton("IMPORT BACKUP", false) {
                    deps.importMasterBackup()
                }
            ))
            renderMasterProfiles()
            addView(masterProfileList)
        }

        container.addView(masterProfilesCard)

        val automationSummary = deps.subtleLabel(
            deps.automationRulesSummary()
        )

        val automationCard = deps.sectionPanel().apply {
            addView(
                deps.sectionHeader(
                    "⚙",
                    "AUTOMATION RULES"
                )
            )
            addView(
                deps.bodyText(
                    "Apply saved Master Profiles when Android reports power, battery, or restart events. No continuous polling is used."
                )
            )
            addView(deps.space(deps.dp(8)))
            addView(automationSummary)
            addView(deps.space(deps.dp(8)))
            addView(
                deps.singleRow(
                    deps.actionButton(
                        "CONFIGURE AUTOMATION",
                        false
                    ) {
                        deps.showAutomationRulesDialog {
                            automationSummary.text =
                                deps.automationRulesSummary()
                        }
                    }
                )
            )
        }

        container.addView(automationCard)

        return container
    }

    private fun switchRow(
        activity: Activity,
        label: String,
        prefsName: String,
        key: String,
        defaultValue: Boolean,
        deps: HardwareTabDeps,
        onChanged: (Boolean) -> Unit
    ): LinearLayout {
        val prefs = activity.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

        val switch = MaterialSwitch(activity).apply {
            isChecked = prefs.getBoolean(key, defaultValue)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(key, checked).apply()
                onChanged(checked)
            }
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, deps.dp(8), 0, 0)

            addView(TextView(activity).apply {
                text = label
                textSize = 14f
                setTextColor(AppTheme.textPrimary)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            addView(switch)
        }
    }
}
