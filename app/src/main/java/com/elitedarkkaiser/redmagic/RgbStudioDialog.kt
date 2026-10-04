package com.elitedarkkaiser.redmagic

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.elitedarkkaiser.redmagic.ui.components.LedControlViewFactory
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

object RgbStudioDialog {
    data class Deps(
        val dp: (Int) -> Int,
        val filterChip: (String, Boolean, () -> Unit) -> Button,
        val updateSelectableButton: (Button, Boolean) -> Unit,
        val onSaveAndApply: (RgbStudioState) -> Unit,
        val onApplyToAll: (RgbStudioState) -> Unit,
        val onStopService: () -> Unit
    )

    private data class ColorChoice(
        val id: Int,
        val label: String,
        val hex: String
    )

    private val colorChoices = listOf(
        ColorChoice(1, "Red", "#FF1744"),
        ColorChoice(3, "Orange", "#FF8C00"),
        ColorChoice(4, "Yellow", "#FFD600"),
        ColorChoice(5, "Green", "#00E676"),
        ColorChoice(6, "Cyan", "#00E5FF"),
        ColorChoice(7, "Blue", "#2979FF"),
        ColorChoice(8, "Purple", "#A020F0"),
        ColorChoice(9, "Pink", "#FF69B4")
    )

    fun show(
        activity: Activity,
        initial: RgbStudioState,
        deps: Deps
    ) {
        var enabled = initial.enabled
        var syncZones = initial.syncZones
        var effect = initial.effect
        var logoBrightness = initial.logoBrightness.coerceIn(32,255)
        var barBrightness = initial.barBrightness.coerceIn(32,255)
        var logoEnabled = initial.logoEnabled
        var barEnabled = initial.barEnabled
        val barColors = initial.barColors.ifEmpty { initial.colors }.toMutableList()
        var shoulderBrightness = initial.shoulderBrightness.coerceIn(32,255)
        var fanBrightness = initial.fanBrightness.coerceIn(32,255)

        val colors = initial.colors.toMutableList()
        var splitTriggers = initial.splitTriggers
        val topColors = initial.topTriggerColors.toMutableList()
        val bottomColors = initial.bottomTriggerColors.toMutableList()
        var refreshApplyButton: (() -> Unit)? = null
        var timeoutMinutes = initial.screenOffTimeoutMinutes

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                deps.dp(18),
                deps.dp(8),
                deps.dp(18),
                deps.dp(12)
            )
        }

        fun label(text: String): TextView {
            return TextView(activity).apply {
                this.text = text
                textSize = 12f
                setTextColor(AppTheme.textSecondary)
                setPadding(0, deps.dp(14), 0, deps.dp(7))
            }
        }

        val enabledSwitch = MaterialSwitch(activity).apply {
            text = "Enable RGB color cycle"
            isChecked = enabled
            setTextColor(AppTheme.textPrimary)
            setOnCheckedChangeListener { _, checked ->
                enabled = checked
            }
        }
        content.addView(enabledSwitch)

        val syncSwitch = MaterialSwitch(activity).apply {
            text = "Synchronize all LED zones"
            isChecked = syncZones
            setTextColor(AppTheme.textPrimary)
        }
        content.addView(syncSwitch)

        val stopServiceButton = deps.filterChip(
            "Stop RGB service",
            false
        ) {
            enabled = false
            enabledSwitch.isChecked = false
            deps.onStopService()

            Toast.makeText(
                activity,
                "RGB service stopped",
                Toast.LENGTH_SHORT
            ).show()
        }

        content.addView(
            stopServiceButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                deps.dp(44)
            ).apply {
                topMargin = deps.dp(8)
            }
        )

        content.addView(label("Effect"))
        val effectRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val effectButtons = linkedMapOf<String, Button>()

        fun addEffect(label: String, value: String) {
            val button = deps.filterChip(label, effect == value) {
                effect = value
                effectButtons.forEach { (key, candidate) ->
                    deps.updateSelectableButton(candidate, key == effect)
                }
            }
            effectButtons[value] = button
            if (effectRow.childCount > 0) {
                effectRow.addView(View(activity), LinearLayout.LayoutParams(deps.dp(6), 1))
            }
            effectRow.addView(
                button,
                LinearLayout.LayoutParams(0, deps.dp(44), 1f)
            )
        }

        addEffect("Steady", "steady")
        addEffect("Breathe", "breathe")
        addEffect("Flash", "flashing")
        addEffect("Rapid", "rapid")
        content.addView(effectRow)
        content.addView(label("Zone brightness"))
        content.addView(MaterialCheckBox(activity).apply {
            text = "Enable logo LED"; isChecked = logoEnabled; setTextColor(AppTheme.textPrimary)
            setOnCheckedChangeListener { _, checked -> logoEnabled = checked }
        })
        content.addView(label("Logo"))
        LedBrightnessUi(activity, content, "logo", { LedBrightness.encode(logoBrightness, effect) }, { 7 },
            { logoBrightness = LedBrightness.level(it) }, {}, AppTheme.textSecondary, AppTheme.accentColor, deps.dp)
        content.addView(MaterialCheckBox(activity).apply {
            text = "Enable GAME MODE bar"; isChecked = barEnabled; setTextColor(AppTheme.textPrimary)
            setOnCheckedChangeListener { _, checked -> barEnabled = checked }
        })
        content.addView(label("GAME MODE bar"))
        LedBrightnessUi(activity, content, "logo", { LedBrightness.encode(barBrightness, effect) }, { 7 },
            { barBrightness = LedBrightness.level(it) }, {}, AppTheme.textSecondary, AppTheme.accentColor, deps.dp)
        content.addView(label("Triggers (top and bottom)"))
        LedBrightnessUi(activity, content, "triggers", { LedBrightness.encode(shoulderBrightness, effect) }, { 7 },
            { shoulderBrightness = LedBrightness.level(it) }, {}, AppTheme.textSecondary, AppTheme.accentColor, deps.dp)
        content.addView(label("Fan"))
        LedBrightnessUi(activity, content, "fan", { LedBrightness.encode(fanBrightness, effect) }, { 7 },
            { fanBrightness = LedBrightness.level(it) }, {}, AppTheme.textSecondary, AppTheme.accentColor, deps.dp)


        content.addView(label("Logo / fan / matching triggers color sequence"))
        content.addView(TextView(activity).apply {
            text = "White rings mark selected colors, played left to right, then the next row. Keep at least one color enabled."
            textSize = 12f
            setTextColor(AppTheme.textSecondary)
            setPadding(0, 0, 0, deps.dp(4))
        })

        fun addPalette(parent: LinearLayout, selectedColors: MutableList<Int>) {
            val swatches = LedControlViewFactory(activity)
            colorChoices.chunked(4).forEach { choices ->
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, deps.dp(6), 0, deps.dp(4))
                }
                choices.forEachIndexed { index, choice ->
                    if (index > 0) row.addView(View(activity), LinearLayout.LayoutParams(deps.dp(10), 1))
                    lateinit var dot: View
                    dot = swatches.colorDot(choice.hex, choice.id in selectedColors) {
                        if (choice.id in selectedColors) {
                            if (selectedColors.size > 1) selectedColors.remove(choice.id)
                            else Toast.makeText(activity, "Keep at least one cycle color", Toast.LENGTH_SHORT).show()
                        } else {
                            selectedColors.add(choice.id)
                            selectedColors.sortBy { id -> colorChoices.indexOfFirst { it.id == id } }
                        }
                        val selected = choice.id in selectedColors
                        dot.background = swatches.colorDotDrawable(choice.hex, selected)
                        dot.isSelected = selected
                        dot.contentDescription = "${choice.label}: ${if (selected) "selected" else "not selected"}"
                    }.apply {
                        isSelected = choice.id in selectedColors
                        isFocusable = true
                        contentDescription = "${choice.label}: ${if (isSelected) "selected" else "not selected"}"
                    }
                    row.addView(dot)
                }
                parent.addView(row)
            }
        }
        addPalette(content, colors)
        content.addView(label("GAME MODE bar color sequence"))
        addPalette(content, barColors)
        content.addView(label("Logo and GAME MODE bar share the effect. Each has its own color sequence and brightness."))

        val splitPanel = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val splitSwitch = MaterialSwitch(activity).apply {
            text = "Separate top and bottom trigger colors"
            isChecked = splitTriggers
            setTextColor(AppTheme.textPrimary)
            setOnCheckedChangeListener { _, checked ->
                splitTriggers = checked
                splitPanel.visibility = if (checked) View.VISIBLE else View.GONE
                refreshApplyButton?.invoke()
            }
        }
        content.addView(splitSwitch)
        splitPanel.addView(label("Top trigger color sequence"))
        addPalette(splitPanel, topColors)
        splitPanel.addView(label("Bottom trigger color sequence"))
        addPalette(splitPanel, bottomColors)
        splitPanel.addView(TextView(activity).apply {
            text = "Each trigger repeats its own sequence. Both share the selected effect and trigger cycle speed. Select one color in each list for a fixed combination."
            textSize = 12f
            setTextColor(AppTheme.textSecondary)
        })
        splitPanel.visibility = if (splitTriggers) View.VISIBLE else View.GONE
        content.addView(splitPanel)

        fun speedControl(
            title: String,
            initialMs: Long
        ): Pair<LinearLayout, Slider> {
            val valueText = TextView(activity).apply {
                text = String.format("%.1f s", initialMs / 1000f)
                textSize = 12f
                setTextColor(AppTheme.textSecondary)
                gravity = Gravity.END
            }
            val slider = Slider(activity).apply {
                valueFrom = 0.5f
                valueTo = 6.0f
                stepSize = 0.1f
                value = (initialMs / 1000f).coerceIn(valueFrom, valueTo)
                addOnChangeListener { _, newValue, _ ->
                    valueText.text = String.format("%.1f s", newValue)
                }
            }
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(activity).apply {
                        text = title
                        textSize = 13f
                        setTextColor(AppTheme.textPrimary)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(valueText)
                })
                addView(slider)
            }
            return row to slider
        }

        content.addView(label("Cycle speed"))
        val (logoSpeedRow, logoSpeed) = speedControl(
            "Synchronized / Logo",
            initial.logoSpeedMs
        )
        val (shoulderSpeedRow, shoulderSpeed) = speedControl(
            "Shoulder LEDs",
            initial.shoulderSpeedMs
        )
        val (fanSpeedRow, fanSpeed) = speedControl(
            "Fan LED",
            initial.fanSpeedMs
        )
        val (barSpeedRow, barSpeed) = speedControl("GAME MODE bar", initial.barSpeedMs)
        content.addView(logoSpeedRow)
        content.addView(barSpeedRow)
        content.addView(shoulderSpeedRow)
        content.addView(fanSpeedRow)

        fun refreshSpeedVisibility() {
            barSpeedRow.visibility = if (syncZones) View.GONE else View.VISIBLE
            shoulderSpeedRow.visibility = if (syncZones) View.GONE else View.VISIBLE
            fanSpeedRow.visibility = if (syncZones) View.GONE else View.VISIBLE
        }
        syncSwitch.setOnCheckedChangeListener { _, checked ->
            syncZones = checked
            refreshSpeedVisibility()
        }
        refreshSpeedVisibility()

        content.addView(label("Screen-off timeout"))
        content.addView(TextView(activity).apply {
            text = "The cycle pauses after this time with the screen off. Charging, calls, and Game Mode keep their existing priority."
            textSize = 12f
            setTextColor(AppTheme.textSecondary)
            setPadding(0, 0, 0, deps.dp(7))
        })

        val timeoutRows = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val timeoutButtons = linkedMapOf<Int, Button>()
        val timeoutOptions = listOf(
            0 to "Immediate",
            1 to "1 min",
            5 to "5 min",
            15 to "15 min",
            30 to "30 min"
        )
        timeoutOptions.chunked(3).forEach { options ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                if (timeoutRows.childCount > 0) {
                    setPadding(0, deps.dp(6), 0, 0)
                }
            }
            options.forEach { (minutes, title) ->
                val button = deps.filterChip(
                    title,
                    timeoutMinutes == minutes
                ) {
                    timeoutMinutes = minutes
                    timeoutButtons.forEach { (value, candidate) ->
                        deps.updateSelectableButton(
                            candidate,
                            value == timeoutMinutes
                        )
                    }
                }
                timeoutButtons[minutes] = button
                if (row.childCount > 0) {
                    row.addView(View(activity), LinearLayout.LayoutParams(deps.dp(6), 1))
                }
                row.addView(
                    button,
                    LinearLayout.LayoutParams(0, deps.dp(42), 1f)
                )
            }
            timeoutRows.addView(row)
        }
        content.addView(timeoutRows)

        fun buildState(forceEnabled: Boolean? = null): RgbStudioState {
            val logoMs = (logoSpeed.value * 1000f).toLong()
            val shoulderMs = if (syncZones) {
                logoMs
            } else {
                (shoulderSpeed.value * 1000f).toLong()
            }
            val fanMs = if (syncZones) {
                logoMs
            } else {
                (fanSpeed.value * 1000f).toLong()
            }

            return RgbStudioState(
                enabled = forceEnabled ?: enabled,
                syncZones = syncZones,
                effect = effect,
                logoBrightness = logoBrightness,
                logoEnabled = logoEnabled,
                barEnabled = barEnabled,
                barColors = barColors.toList(),
                barBrightness = barBrightness,
                barSpeedMs = if (syncZones) logoMs else (barSpeed.value * 1000f).toLong(),
                shoulderBrightness = shoulderBrightness,
                fanBrightness = fanBrightness,

                colors = colors.toList(),
                splitTriggers = splitTriggers,
                topTriggerColors = topColors.toList(),
                bottomTriggerColors = bottomColors.toList(),
                logoSpeedMs = logoMs,
                shoulderSpeedMs = shoulderMs,
                fanSpeedMs = fanMs,
                screenOffTimeoutMinutes = timeoutMinutes
            )
        }

        val scroll = ScrollView(activity).apply {
            addView(content)
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle("RGB Studio")
            .setView(scroll)
            .setNegativeButton("CANCEL", null)
            .setNeutralButton("APPLY TO ALL") { _, _ ->
                deps.onApplyToAll(buildState())
            }
            .setPositiveButton("SAVE & APPLY") { _, _ ->
                deps.onSaveAndApply(buildState())
            }
            .create()
        refreshApplyButton = {
            dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).visibility =
                if (splitTriggers) View.GONE else View.VISIBLE
        }
        dialog.setOnShowListener { refreshApplyButton?.invoke() }
        dialog.show()
    }
}
