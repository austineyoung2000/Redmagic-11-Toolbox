package com.elitedarkkaiser.redmagic.ui

import android.content.res.ColorStateList
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.R
import com.google.android.material.button.MaterialButton

object HomeTabUi {
    data class Refs(
        val deviceRomValue: TextView,
        val deviceCpuValue: TextView,
        val deviceRamValue: TextView,
        val dashboardText: TextView,
        val quickStatusText: TextView,
        val rootChip: TextView,
        val fanChip: TextView,
        val rpmChip: TextView,
        val tempChip: TextView,
        val activeModeText: TextView,
        val thermalHistoryView: ThermalHistoryView,
        val gameModeStatusText: TextView?
    )

    data class Result(
        val view: LinearLayout,
        val refs: Refs
    )

    fun create(deps: HomeTabDeps): Result {
        val container = deps.scrollTabContainer()
        var gameModeStatusText: TextView? = null

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
            gameModeStatusText = gameStatus
        }

        val header = LinearLayout(container.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, deps.dp(6), 0, deps.dp(14))
            addView(TextView(context).apply {
                text = "REDMAGIC Toolbox"
                textSize = 23f
                setTypeface(AppTheme.appTypeface, android.graphics.Typeface.BOLD)
                setTextColor(AppTheme.textPrimary)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(MaterialButton(context).apply {
                text = "⚙"
                textSize = 20f
                contentDescription = "Open settings"
                minWidth = deps.dp(48)
                minHeight = deps.dp(48)
                insetTop = 0
                insetBottom = 0
                setPadding(0, 0, 0, 0)
                backgroundTintList = ColorStateList.valueOf(AppTheme.panelColor)
                setTextColor(AppTheme.textPrimary)
                cornerRadius = deps.dp(16)
                setOnClickListener { deps.openSettings() }
            })
        }
        val deviceCard = deps.sectionPanel().apply {
            addView(TextView(context).apply {
                text = "REDMAGIC 11 Pro"
                textSize = 18f
                setTypeface(AppTheme.appTypeface, android.graphics.Typeface.BOLD)
                setTextColor(AppTheme.textPrimary)
            })
            addView(deps.subtitleText("NX809J  •  Device information"))
        }
        fun chip(value: String): TextView = TextView(container.context).apply {
            text = value
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(AppTheme.textPrimary)
            setPadding(deps.dp(4), deps.dp(12), deps.dp(4), deps.dp(12))
            background = AppTheme.roundedBg(AppTheme.panelColor, AppTheme.borderColor, deps.dp(14).toFloat())
        }
        val rootChip = chip("ROOT\nChecking")
        val fanChip = chip("FAN\n--")
        val rpmChip = chip("RPM\n--")
        val tempChip = chip("TEMP\n--")
        val chipRow = LinearLayout(container.context).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf(rootChip, fanChip, rpmChip, tempChip).forEachIndexed { index, view ->
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (index > 0) lp.marginStart = deps.dp(5)
                addView(view, lp)
            }
            setPadding(0, deps.dp(8), 0, deps.dp(12))
        }

        fun dashboardTile(title: String, description: String, icon: String, tab: String): LinearLayout {
            return deps.sectionPanel().apply {
                isClickable = true
                isFocusable = true
                contentDescription = "$title, $description"
                setOnClickListener { deps.openSection(tab) }
                addView(deps.sectionHeader(icon, title))
                addView(deps.subtitleText(description))
            }
        }

        val navigationCard = deps.sectionPanel().apply {
            fun tileRow(first: LinearLayout, second: LinearLayout) {
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                row.addView(first, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(deps.space(deps.dp(8)))
                row.addView(second, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(row)
            }
            tileRow(dashboardTile("Cooling", "Fan and pump", "❄", "cooling"),
                dashboardTile("Lighting", "LED zones and effects", "✦", "lighting"))
            tileRow(dashboardTile("Gaming & hardware", "Triggers and profiles", "⌥", "hardware"),
                dashboardTile("System", "Magic Key and controls", "⌘", "controls"))
        }

        val deviceRomValue = deps.infoValue()
        val deviceCpuValue = deps.infoValue()
        val deviceRamValue = deps.infoValue()

        val diagnosticsCard = deps.sectionPanel().apply {
            addView(deps.sectionHeader("⌁", "DIAGNOSTICS"))
            addView(deps.bodyText(deps.deviceScanSummary()))
        }

        container.addView(header)
        container.addView(deviceCard)
        container.addView(chipRow)

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

        val quickStatusText = TextView(container.context).apply {
            text = "Loading device status…"
            textSize = 16f
            setTextColor(AppTheme.textPrimary)
            setPadding(0, deps.dp(8), 0, deps.dp(8))
        }
        val quickStatusCard = deps.sectionPanel().apply {
            addView(deps.sectionHeader("❄", "COOLING SYSTEM"))
            addView(quickStatusText)
            isClickable = true
            isFocusable = true
            contentDescription = "Open cooling controls"
            setOnClickListener { deps.openSection("cooling") }
        }

        val dashboardCard = deps.sectionPanel().apply {
            addView(
                deps.sectionHeader(
                    "◈",
                    "LIVE DASHBOARD"
                )
            )

            val refreshBtn = deps.actionButton(
                "REFRESH DASHBOARD",
                false
            ) {
                deps.requestStatusRefresh()
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

        }

        container.addView(quickStatusCard)
        container.addView(navigationCard)
        val details = deps.actionButton("DEVICE DETAILS & DIAGNOSTICS", false) {
            val visible = dashboardCard.visibility != android.view.View.VISIBLE
            dashboardCard.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
            activeModeCard.visibility = dashboardCard.visibility
            diagnosticsCard.visibility = dashboardCard.visibility
        }
        dashboardCard.visibility = android.view.View.GONE
        activeModeCard.visibility = android.view.View.GONE
        diagnosticsCard.visibility = android.view.View.GONE
        container.addView(deps.singleRow(details))
        container.addView(dashboardCard)
        container.addView(activeModeCard)
        container.addView(diagnosticsCard)

        return Result(
            view = container,
            refs = Refs(
                deviceRomValue = deviceRomValue,
                deviceCpuValue = deviceCpuValue,
                deviceRamValue = deviceRamValue,
                dashboardText = dashboardText,
                quickStatusText = quickStatusText,
                rootChip = rootChip,
                fanChip = fanChip,
                rpmChip = rpmChip,
                tempChip = tempChip,
                activeModeText = activeModeText,
                thermalHistoryView = thermalHistoryView,
                gameModeStatusText = gameModeStatusText
            )
        )
    }
}
