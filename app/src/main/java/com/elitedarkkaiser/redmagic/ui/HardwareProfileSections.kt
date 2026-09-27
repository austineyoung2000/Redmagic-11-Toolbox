package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import com.elitedarkkaiser.redmagic.ProfileDialogs

internal object HardwareProfileSections {
    fun createCards(
        activity: Activity,
        deps: HardwareTabDeps
    ): List<LinearLayout> {
        return listOf(
            createMasterProfilesCard(activity, deps),
            createAutomationCard(deps)
        )
    }

    private fun createMasterProfilesCard(
        activity: Activity,
        deps: HardwareTabDeps
    ): LinearLayout {
        return deps.sectionPanel().apply {
            addView(deps.sectionHeader("◆", "MASTER PROFILES"))
            addView(
                deps.bodyText(
                    "Save complete app settings or export a portable " +
                        "JSON backup for another installation."
                )
            )

            val profileList = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, deps.dp(10), 0, 0)
            }
            var renderGeneration = 0

            fun renderProfiles() {
                renderGeneration += 1
                val generation = renderGeneration
                profileList.removeAllViews()
                profileList.addView(
                    deps.subtleLabel("Loading master profiles…")
                )

                deps.loadMasterProfiles profileLoad@ { profiles ->
                    if (generation != renderGeneration) {
                        return@profileLoad
                    }

                    profileList.removeAllViews()

                    if (profiles.isEmpty()) {
                        profileList.addView(
                            deps.subtleLabel(
                                "No saved master profiles yet"
                            )
                        )
                        return@profileLoad
                    }

                    profiles.forEach { profile ->
                        val row = LinearLayout(activity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                        }
                        val applyButton = deps.actionButton(
                            profile.name,
                            false
                        ) {
                            deps.applyMasterProfile(profile)
                        }.apply {
                            setPadding(
                                deps.dp(16),
                                deps.dp(10),
                                deps.dp(16),
                                deps.dp(10)
                            )
                        }
                        val deleteButton = deps.actionButton("DEL", true) {
                            deps.deleteMasterProfile(profile.name) {
                                renderProfiles()
                            }
                        }.apply {
                            setPadding(
                                deps.dp(14),
                                deps.dp(10),
                                deps.dp(14),
                                deps.dp(10)
                            )
                        }

                        row.addView(
                            applyButton,
                            LinearLayout.LayoutParams(
                                0,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                1f
                            )
                        )
                        row.addView(deps.space(deps.dp(8)))
                        row.addView(deleteButton)
                        profileList.addView(row)
                        profileList.addView(deps.space(deps.dp(10)))
                    }
                }
            }

            val saveButton = deps.actionButton(
                "SAVE MASTER PROFILE",
                false
            ) {
                ProfileDialogs.showStyledNameOnlyDialog(
                    context = activity,
                    title = "Save Master Profile",
                    hint = "Master profile name",
                    textPrimary = AppTheme.textPrimary,
                    textSecondary = AppTheme.textSecondary,
                    panelColor = AppTheme.panelColor,
                    borderColor = AppTheme.borderColor,
                    dp = { value -> deps.dp(value) },
                    roundedBg = { fill, stroke, radius ->
                        AppTheme.roundedBg(
                            fill,
                            stroke,
                            radius.toFloat()
                        )
                    },
                    actionButton = { text, danger, onClick ->
                        deps.actionButton(text, danger, onClick)
                    },
                    space = { value -> deps.space(value) },
                    onSave = { name ->
                        deps.saveMasterProfile(name) { saved ->
                            if (saved) renderProfiles()
                        }
                    }
                )
            }

            addView(deps.singleRow(saveButton))
            addView(deps.space(deps.dp(8)))
            addView(
                deps.row(
                    deps.actionButton("EXPORT BACKUP", false) {
                        deps.exportMasterBackup()
                    },
                    deps.actionButton("IMPORT BACKUP", false) {
                        deps.importMasterBackup()
                    }
                )
            )
            renderProfiles()
            addView(profileList)
        }
    }

    private fun createAutomationCard(
        deps: HardwareTabDeps
    ): LinearLayout {
        val summary = deps.subtleLabel(deps.automationRulesSummary())

        return deps.sectionPanel().apply {
            addView(deps.sectionHeader("⚙", "AUTOMATION RULES"))
            addView(
                deps.bodyText(
                    "Apply saved Master Profiles when Android reports " +
                        "power, battery, or restart events. No continuous " +
                        "polling is used."
                )
            )
            addView(deps.space(deps.dp(8)))
            addView(summary)
            addView(deps.space(deps.dp(8)))
            addView(
                deps.singleRow(
                    deps.actionButton(
                        "CONFIGURE AUTOMATION",
                        false
                    ) {
                        deps.showAutomationRulesDialog {
                            summary.text = deps.automationRulesSummary()
                        }
                    }
                )
            )
        }
    }
}
