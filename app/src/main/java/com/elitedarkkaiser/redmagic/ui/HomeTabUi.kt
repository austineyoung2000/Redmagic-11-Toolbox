package com.elitedarkkaiser.redmagic.ui

import android.content.res.ColorStateList
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.DashboardSnapshot
import com.elitedarkkaiser.redmagic.R
import com.google.android.material.button.MaterialButton

object HomeTabUi {
    data class Refs(
        val deviceRomValue: TextView,
        val deviceCpuValue: TextView,
        val deviceRamValue: TextView,
        val dashboardText: TextView,
        val activeModeText: TextView,
        val thermalHistoryView: ThermalHistoryView
    )

    data class Result(
        val view: LinearLayout,
        val refs: Refs
    )

    fun create(deps: HomeTabDeps): Result {
        val container = deps.scrollTabContainer()

        if (!deps.hasUsageStatsPermission()) {
            val usageCard = deps.sectionPanel().apply {
                val title = TextView(context).apply {
                    text = "Game Mode Permission Required"
                    textSize = 16f
                    setTextColor(AppTheme.textPrimary)
                    typeface = android.graphics.Typeface.create(AppTheme.appTypeface, android.graphics.Typeface.BOLD)
                }

                val desc = TextView(context).apply {
                    text = "Grant Usage Access so Game Mode can detect running games."
                    textSize = 13f
                    setTextColor(AppTheme.textSecondary)
                    setPadding(0, deps.dp(6), 0, deps.dp(10))
                }

                val btn = deps.actionButton(
                    "Grant Usage Access",
                    false
                ) {
                    deps.openUsageStatsAccessSettings()
                }

                addView(title)
                addView(desc)
                addView(btn)
            }

            container.addView(usageCard)

            val gameSelectBtn = deps.actionButton(
                "Select Games for Game Mode",
                false
            ) {
                deps.showGamePickerDialog()
            }

            container.addView(gameSelectBtn)

            val gameStatus = TextView(container.context).apply {
                textSize = 13f
                setTextColor(AppTheme.textSecondary)
                setPadding(0, deps.dp(8), 0, deps.dp(8))
            }

            deps.updateGameModeStatusUI(gameStatus)
            container.addView(gameStatus)
        }

        val welcomeCard = deps.sectionPanel().apply {
            val headerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val iconView = ImageView(context).apply {
                setImageResource(R.mipmap.ic_launcher)
                layoutParams = LinearLayout.LayoutParams(deps.dp(60), deps.dp(60))
            }

            val titleView = deps.ledTitleText("Redmagic 11 Toolbox")

            titleView.layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

            val settingsButton = MaterialButton(context).apply {
                text = "⚙"
                textSize = 22f
                contentDescription = "Open settings"
                minWidth = deps.dp(48)
                minHeight = deps.dp(48)
                insetTop = 0
                insetBottom = 0
                setPadding(0, 0, 0, 0)
                backgroundTintList = ColorStateList.valueOf(
                    AppTheme.chipOnColor
                )
                rippleColor = ColorStateList.valueOf(
                    AppTheme.rippleColor
                )
                setTextColor(AppTheme.textPrimary)
                cornerRadius = deps.dp(16)
                setOnClickListener { deps.openSettings() }
            }

            headerRow.addView(iconView)
            headerRow.addView(titleView)
            headerRow.addView(settingsButton)

            addView(headerRow)
            addView(deps.subtitleText("Cooling, lighting, triggers and hardware controls for Redmagic 11 Pro"))
        }

        val summaryCard = deps.sectionPanel().apply {
            addView(deps.sectionHeader("⌂", "WELCOME"))
            addView(deps.bodyText("RedMagic HW Controls is a root-powered control center for RedMagic 11 Pro that brings key hardware features into one place with a cleaner interface than stock tools."))
            addView(deps.bodyText("It lets you manage cooling behavior, fan profiles, micropump control, fan LED effects, logo lighting, shoulder LED strips, trigger tools, and slider actions directly from the app."))
            addView(deps.bodyText("The app is built around real device paths and behavior confirmed on hardware so the controls feel practical, focused, and close to an OEM-style utility."))

            val linksRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, deps.dp(14), 0, 0)
            }

            val githubBtn = deps.segmentedChip("GitHub", false) {
                deps.openUrl("https://github.com/austineyoung2000/Red")
            }

            val referenceBtn = deps.segmentedChip("Reference", false) {
                deps.openUrl("https://www.reddit.com/r/RedMagic/comments/1rtoako/red_magic_11_pro_hardware_control_guide_for/")
            }

            linksRow.addView(githubBtn)
            linksRow.addView(deps.space(deps.dp(8)))
            linksRow.addView(referenceBtn)

            addView(linksRow)
        }

        val deviceRomValue = deps.infoValue()
        val deviceCpuValue = deps.infoValue()
        val deviceRamValue = deps.infoValue()

        val diagnosticsCard = deps.sectionPanel().apply {
            addView(deps.sectionHeader("⌁", "DIAGNOSTICS"))
            addView(deps.bodyText(deps.deviceScanSummary()))
        }

        container.addView(welcomeCard)

        val dashboardText =
            TextView(container.context).apply {
                text = "Loading dashboard…"
                textSize = 13f
                setTextColor(AppTheme.textPrimary)
                setLineSpacing(0f, 1.15f)
                setPadding(0, 0, 0, deps.dp(12))
            }

        lateinit var thermalHistoryView:
            ThermalHistoryView

        val activeModeText =
            TextView(container.context).apply {
                text = deps.activeModeSummary()
                textSize = 13f
                setTextColor(AppTheme.textPrimary)
                setLineSpacing(0f, 1.15f)
                setPadding(0, 0, 0, deps.dp(12))
            }

        val activeModeCard = deps.sectionPanel().apply {
            addView(
                deps.sectionHeader(
                    "◎",
                    "ACTIVE MODE INSPECTOR"
                )
            )
            addView(activeModeText)
        }

        val dashboardCard = deps.sectionPanel().apply {
            addView(
                deps.sectionHeader(
                    "◈",
                    "LIVE DASHBOARD"
                )
            )

            lateinit var refreshBtn: Button

            fun refreshDashboard() {
                refreshBtn.isEnabled = false
                refreshBtn.text = "REFRESHING…"

                val submitted = deps.runBackground {
                    val summary =
                        DashboardSnapshot.buildSummary(context)

                    dashboardText.post {
                        dashboardText.text = summary
                        activeModeText.text =
                            deps.activeModeSummary()
                        refreshBtn.text = "REFRESH DASHBOARD"
                        refreshBtn.isEnabled = true
                    }
                }

                if (!submitted) {
                    dashboardText.text =
                        "Dashboard refresh unavailable"
                    refreshBtn.text = "REFRESH DASHBOARD"
                    refreshBtn.isEnabled = true
                }
            }

            refreshBtn = deps.actionButton(
                "REFRESH DASHBOARD",
                false
            ) {
                refreshDashboard()
            }

            addView(dashboardText)
            addView(
                deps.sectionHeader(
                    "⌁",
                    "THERMAL HISTORY"
                )
            )
            thermalHistoryView =
                ThermalHistoryView(context)
            addView(thermalHistoryView)
            addView(deps.space(deps.dp(12)))
            addView(
                deps.infoRow(
                    "ROM",
                    deviceRomValue
                )
            )
            addView(
                deps.infoRow(
                    "CPU",
                    deviceCpuValue
                )
            )
            addView(
                deps.infoRow(
                    "RAM",
                    deviceRamValue
                )
            )
            addView(deps.space(deps.dp(8)))
            addView(deps.singleRow(refreshBtn))

            dashboardText.post {
                refreshDashboard()
            }
        }

        container.addView(summaryCard)
        container.addView(activeModeCard)
        container.addView(dashboardCard)
        container.addView(diagnosticsCard)

        return Result(
            view = container,
            refs = Refs(
                deviceRomValue = deviceRomValue,
                deviceCpuValue = deviceCpuValue,
                deviceRamValue = deviceRamValue,
                dashboardText = dashboardText,
                activeModeText = activeModeText,
                thermalHistoryView = thermalHistoryView
            )
        )
    }
}
