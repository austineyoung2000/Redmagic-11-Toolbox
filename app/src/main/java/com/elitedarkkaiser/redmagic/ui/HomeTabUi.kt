package com.elitedarkkaiser.redmagic.ui

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
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
        val coolingTemp: TextView,
        val coolingRpm: TextView,
        val rootChip: TextView,
        val fanChip: TextView,
        val rpmChip: TextView,
        val tempChip: TextView,
        val activeModeText: TextView,
        val thermalHistoryView: ThermalHistoryView,
        val historySummary: TextView,
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
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(ImageView(context).apply {
                setImageResource(R.drawable.dashboard_phone)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = null
            }, LinearLayout.LayoutParams(deps.dp(56), deps.dp(72)))
            row.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(deps.dp(14), 0, 0, 0)
                addView(TextView(context).apply {
                    text = "REDMAGIC 11 Pro"
                    textSize = 18f
                    setTypeface(AppTheme.appTypeface, android.graphics.Typeface.BOLD)
                    setTextColor(AppTheme.textPrimary)
                })
                addView(deps.subtitleText(android.os.Build.DISPLAY?.takeIf { it.isNotBlank() } ?: "Android ${android.os.Build.VERSION.RELEASE}").apply {
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
            })
            addView(row)
        }
        fun chip(value: String): TextView = TextView(container.context).apply {
            text = value
            textSize = 11f
            maxLines = 2
            setTextColor(AppTheme.textPrimary)
        }
        val rootChip = chip("ROOT\nChecking")
        val fanChip = chip("FAN\n--")
        val rpmChip = chip("RPM\n--")
        val tempChip = chip("TEMP\n--")
        val chipRow = LinearLayout(container.context).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf(rootChip to "root", fanChip to "fan", rpmChip to "rpm", tempChip to "temp").forEachIndexed { index, entry ->
                val item = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(deps.dp(5), deps.dp(10), deps.dp(5), deps.dp(10))
                    background = AppTheme.roundedBg(AppTheme.panelColor, AppTheme.borderColor, deps.dp(14).toFloat())
                    addView(ToolboxIcons.create(context, entry.second).apply {
                        imageTintList = ColorStateList.valueOf(AppTheme.textPrimary)
                    }, LinearLayout.LayoutParams(deps.dp(18), deps.dp(18)).apply { marginEnd = deps.dp(4) })
                    addView(entry.first, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                }
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (index > 0) lp.marginStart = deps.dp(5)
                addView(item, lp)
            }
            setPadding(0, deps.dp(8), 0, deps.dp(12))
        }

        fun dashboardTile(title: String, description: String, icon: String, tab: String): LinearLayout {
            return deps.sectionPanel().apply {
                isClickable = true
                isFocusable = true
                contentDescription = "$title, $description"
                setOnClickListener { deps.openSection(tab) }
                val line = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(ToolboxIcons.create(context, title), LinearLayout.LayoutParams(deps.dp(28), deps.dp(28)))
                    addView(TextView(context).apply {
                        text = title
                        textSize = 15f
                        setTextColor(AppTheme.textPrimary)
                        setTypeface(AppTheme.appTypeface, android.graphics.Typeface.BOLD)
                        setPadding(deps.dp(10), 0, 0, 0)
                    })
                }
                addView(line)
                addView(deps.subtitleText(description).apply { setPadding(deps.dp(38), deps.dp(5), 0, 0) })
            }
        }

        val navigationCard = LinearLayout(container.context).apply {
            orientation = LinearLayout.VERTICAL
            fun tileRow(first: LinearLayout, second: LinearLayout) {
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                row.addView(first, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(deps.space(deps.dp(8)))
                row.addView(second, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(row, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = deps.dp(10) })
            }
            tileRow(dashboardTile("Cooling", "Fan and pump", "❄", "cooling"),
                dashboardTile("Lighting", "LED zones and effects", "✦", "lighting"))
            tileRow(dashboardTile("Gaming", "Game controls", "🎮", "gaming"),
                dashboardTile("Hardware", "Triggers, Magic Key, and haptics", "⌥", "hardware"))
            tileRow(dashboardTile("Profiles", "Saved settings and rules", "◆", "profiles"),
                dashboardTile("System", "System controls", "⚙", "system"))
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

        fun metric(initial: String, size: Float): TextView = TextView(container.context).apply {
            text = initial
            textSize = size
            setTypeface(AppTheme.appTypeface, android.graphics.Typeface.BOLD)
            setTextColor(AppTheme.textPrimary)
        }
        val coolingTemp = metric("--", 27f)
        val coolingRpm = metric("-- RPM", 20f)
        val thermalHistoryView = ThermalHistoryView(container.context, compact = true)
        val historySummary = TextView(container.context).apply {
            text = "Now --   Min --   Max --"
            textSize = 11f
            setTextColor(AppTheme.textSecondary)
            maxLines = 1
            contentDescription = "Temperature history is collecting samples"
        }
        val quickStatusCard = deps.sectionPanel().apply {
            setPadding(0, 0, 0, 0)
            val frame = android.widget.FrameLayout(context)
            frame.addView(ImageView(context).apply {
                setImageResource(R.drawable.cooling_hero)
                scaleType = ImageView.ScaleType.CENTER_CROP
                alpha = 0.38f
                contentDescription = null
            }, android.widget.FrameLayout.LayoutParams(-1, deps.dp(180)))
            frame.addView(View(context).apply {
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(android.graphics.Color.argb(220, 35, 8, 14), android.graphics.Color.argb(130, 5, 8, 12))
                )
            }, android.widget.FrameLayout.LayoutParams(-1, deps.dp(180)))
            frame.addView(thermalHistoryView, android.widget.FrameLayout.LayoutParams(-1, -1).apply {
                topMargin = deps.dp(86)
                bottomMargin = deps.dp(8)
            })
            val body = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(deps.dp(18), deps.dp(16), deps.dp(18), deps.dp(16))
                addView(metric("Cooling System  ›", 16f))
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, deps.dp(18), 0, deps.dp(10))
                    val left = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(coolingTemp)
                        addView(deps.subtitleText("Current temperature"))
                    }
                    val right = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(coolingRpm)
                        addView(deps.subtitleText("Fan speed"))
                    }
                    addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                }
                addView(row)
                addView(android.widget.Space(context), LinearLayout.LayoutParams(1, 0, 1f))
                addView(historySummary)
            }
            frame.addView(body, android.widget.FrameLayout.LayoutParams(-1, -1))
            addView(frame, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            isClickable = true
            isFocusable = true
            contentDescription = "Open cooling controls"
            setOnClickListener { deps.openSection("cooling") }
        }

        val deviceInfoCard = deps.sectionPanel().apply {
            addView(deps.sectionHeader("⌁", "DEVICE INFORMATION"))
            addView(deps.infoRow("ROM", deviceRomValue))
            addView(deps.infoRow("CPU", deviceCpuValue))
            addView(deps.infoRow("RAM", deviceRamValue))
            addView(deps.singleRow(deps.actionButton("REFRESH", false) { deps.requestStatusRefresh() }))
        }

        container.addView(quickStatusCard)
        container.addView(navigationCard)
        val details = deps.actionButton("DEVICE DETAILS & DIAGNOSTICS", false) {
            val visible = deviceInfoCard.visibility != android.view.View.VISIBLE
            deviceInfoCard.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
            activeModeCard.visibility = deviceInfoCard.visibility
            diagnosticsCard.visibility = deviceInfoCard.visibility
        }
        deviceInfoCard.visibility = android.view.View.GONE
        activeModeCard.visibility = android.view.View.GONE
        diagnosticsCard.visibility = android.view.View.GONE
        container.addView(deps.singleRow(details))
        container.addView(deviceInfoCard)
        container.addView(activeModeCard)
        container.addView(diagnosticsCard)

        return Result(
            view = container,
            refs = Refs(
                deviceRomValue = deviceRomValue,
                deviceCpuValue = deviceCpuValue,
                deviceRamValue = deviceRamValue,
                coolingTemp = coolingTemp,
                coolingRpm = coolingRpm,
                rootChip = rootChip,
                fanChip = fanChip,
                rpmChip = rpmChip,
                tempChip = tempChip,
                activeModeText = activeModeText,
                thermalHistoryView = thermalHistoryView,
                historySummary = historySummary,
                gameModeStatusText = gameModeStatusText
            )
        )
    }
}
