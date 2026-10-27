package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.materialswitch.MaterialSwitch
import com.elitedarkkaiser.redmagic.TriggerSafetyConfig
import com.elitedarkkaiser.redmagic.readTriggerSafetyConfig
import com.elitedarkkaiser.redmagic.saveTriggerSafetyConfig
import com.elitedarkkaiser.redmagic.NativeTgkCoordinator
import com.elitedarkkaiser.redmagic.NativeTgkDiagnostics
import com.elitedarkkaiser.redmagic.ui.components.SelectionControlStyle

internal object HardwareTriggerSections {
    fun createCards(
        activity: Activity,
        deps: HardwareTabDeps
    ): List<LinearLayout> {
        return listOf(
            createHero(activity, deps),
            createTriggerControls(activity, deps),
            createQuickActions(activity, deps),
            createSafetyCard(activity, deps),
            createDiagnostics(activity, deps)
        )
    }

    private fun createHero(activity: Activity, deps: HardwareTabDeps): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val frame = android.widget.FrameLayout(activity)
            frame.addView(android.widget.ImageView(activity).apply {
                setImageResource(com.elitedarkkaiser.redmagic.R.drawable.rgb_silver_hero)
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                contentDescription = "Silver REDMAGIC phone with illuminated shoulder triggers"
            }, android.widget.FrameLayout.LayoutParams(-1, -1))
            frame.addView(android.view.View(activity).apply {
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT, AppTheme.bgColor))
            }, android.widget.FrameLayout.LayoutParams(-1, -1))
            addView(frame, LinearLayout.LayoutParams(-1, deps.dp(130)))
        }

    private fun createTriggerControls(
        activity: Activity,
        deps: HardwareTabDeps
    ): LinearLayout {
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
            addView(deps.sectionHeader("⌥", "TRIGGER CONTROL"))
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

    private fun compactChoice(button: Button, deps: HardwareTabDeps) {
        button.minHeight = deps.dp(40)
        button.minimumHeight = deps.dp(40)
        button.setPadding(deps.dp(3), 0, deps.dp(3), 0)
        if (button is com.google.android.material.button.MaterialButton) {
            button.cornerRadius = deps.dp(12)
        }
    }

    private fun createQuickActions(activity: Activity, deps: HardwareTabDeps): LinearLayout {
        val prefs = activity.getSharedPreferences("triggers", android.content.Context.MODE_PRIVATE)
        val values = listOf("NONE", "VOL_UP", "VOL_DOWN", "MEDIA_PLAY_PAUSE", "MEDIA_NEXT", "MEDIA_PREVIOUS")
        val labels = listOf("None", "Volume Up", "Volume Down", "Play / Pause", "Next Track", "Previous Track")
        val card = deps.sectionPanel()
        fun render() {
            card.removeAllViews()
            card.addView(deps.sectionHeader("⌥", "QUICK ACTIONS"))
            card.addView(deps.bodyText("Assign an action to each shoulder trigger."))
            listOf(Triple("Left / Top trigger", "left_trigger", "VOL_DOWN"), Triple("Right / Bottom trigger", "right_trigger", "VOL_UP")).forEach { (title, key, default) ->
                card.addView(deps.subtleLabel(title))
                val selected = prefs.getString(key, default) ?: default
                labels.indices.toList().chunked(3).forEach { indices ->
                    val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                    indices.forEachIndexed { index, i ->
                        val button = deps.actionButton(labels[i], false) {
                            prefs.edit().putString(key, values[i]).apply()
                            render()
                        }
                        button.isAllCaps = false
                        SelectionControlStyle.apply(button, values[i] == selected)
                        compactChoice(button, deps)
                        row.addView(button, LinearLayout.LayoutParams(0, deps.dp(40), 1f).apply { if (index > 0) marginStart = deps.dp(6); bottomMargin = deps.dp(6) })
                    }
                    card.addView(row)
                }
            }
            if (deps.capabilities.scanComplete && !deps.capabilities.triggersAvailable) CapabilityUi.disableInteractions(card)
        }
        render()
        return card
    }

    private fun createSafetyCard(activity: Activity, deps: HardwareTabDeps): LinearLayout {
        val card = deps.sectionPanel()
        var config = readTriggerSafetyConfig(activity)
        fun update(next: TriggerSafetyConfig) { config = next; saveTriggerSafetyConfig(activity, config) }
        lateinit var render: () -> Unit
        fun <T> choices(title: String, options: List<Pair<T, String>>, selected: T, choose: (T) -> Unit) {
            card.addView(deps.subtleLabel(title))
            options.chunked(if (title == "Protection mode") 2 else 4).forEach { group ->
                val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                group.forEachIndexed { index, (value, label) ->
                    val button = deps.actionButton(label, false) { choose(value); render() }
                    button.isAllCaps = false
                    SelectionControlStyle.apply(button, value == selected)
                    compactChoice(button, deps)
                    row.addView(button, LinearLayout.LayoutParams(0, deps.dp(40), 1f).apply { if (index > 0) marginStart = deps.dp(6); bottomMargin = deps.dp(6) })
                }
                card.addView(row)
            }
        }
        render = {
            card.removeAllViews()
            card.addView(deps.sectionHeader("🛡", "TRIGGER SAFETY"))
            card.addView(deps.bodyText("Prevent accidental shoulder-trigger actions."))
            choices("Protection mode", listOf(TriggerSafetyConfig.MODE_OFF to "Off", TriggerSafetyConfig.MODE_INTENT to "Intent Unlock", TriggerSafetyConfig.MODE_HOLD to "Hold", TriggerSafetyConfig.MODE_INTENT_HOLD to "Intent + Hold"), config.mode) { update(config.copy(mode = it)) }
            if (config.usesHold()) choices("Hold duration", TriggerSafetyConfig.ALLOWED_HOLD_DURATIONS.sorted().map { it to "${it} ms" }, config.holdDurationMs) { update(config.copy(holdDurationMs = it)) }
            if (config.usesIntentUnlock()) {
                choices("Left / Top taps", (1..4).map { it to it.toString() }, config.leftUnlockTapCount) { update(config.copy(leftUnlockTapCount = it)) }
                choices("Right / Bottom taps", (2..4).map { it to it.toString() }, config.rightUnlockTapCount) { update(config.copy(rightUnlockTapCount = it)) }
                choices("Unlocked duration", listOf(1500L to "1.5 s", 2500L to "2.5 s", 5000L to "5 s", 10000L to "10 s"), config.unlockTimeoutMs) { update(config.copy(unlockTimeoutMs = it)) }
            }
            card.addView(deps.subtleLabel("Additional guards"))
            card.addView(switchRow(activity, "Block actions on lock screen", config.blockOnLockScreen, deps) { update(config.copy(blockOnLockScreen = it)) })
            card.addView(switchRow(activity, "Only while Game Mode is active", config.gameModeOnly, deps) { update(config.copy(gameModeOnly = it)) })
            card.addView(switchRow(activity, "Left trigger also unlocks right", config.leftUnlocksRight, deps) { update(config.copy(leftUnlocksRight = it)) })
            if (deps.capabilities.scanComplete && !deps.capabilities.triggersAvailable) CapabilityUi.disableInteractions(card)
        }
        render()
        return card
    }

    private fun createDiagnostics(activity: Activity, deps: HardwareTabDeps): LinearLayout {
        val card = deps.sectionPanel()
        val report = deps.bodyText("Reading trigger backend state…").apply { setTextIsSelectable(true) }
        var currentReport = ""
        val refresh = deps.actionButton("Refresh", false) {
            report.text = "Reading trigger backend state…"
            Thread {
                val value = runCatching { NativeTgkDiagnostics.buildReport(activity, NativeTgkCoordinator.readActiveState(activity)) }
                    .getOrElse { "Unable to read trigger diagnostics: ${it.message ?: it.javaClass.simpleName}" }
                activity.runOnUiThread { currentReport = value; report.text = value }
            }.start()
        }
        val copy = deps.actionButton("Copy report", false) {
            if (currentReport.isNotBlank()) {
                val clipboard = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("REDMAGIC trigger backend diagnostics", currentReport))
                Toast.makeText(activity, "Trigger diagnostic report copied", Toast.LENGTH_SHORT).show()
            }
        }
        card.addView(deps.sectionHeader("⌥", "BACKEND DIAGNOSTICS"))
        val details = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
            addView(report)
            addView(deps.row(refresh, copy))
        }
        val expand = TextView(activity).apply {
            text = "Show report  ›"
            textSize = 13f
            setTextColor(AppTheme.accentColor)
            setPadding(0, deps.dp(10), 0, deps.dp(6))
            isClickable = true; isFocusable = true
            setOnClickListener {
                val showing = details.visibility == android.view.View.VISIBLE
                details.visibility = if (showing) android.view.View.GONE else android.view.View.VISIBLE
                text = if (showing) "Show report  ›" else "Hide report  ∨"
            }
        }
        card.addView(expand)
        card.addView(details)
        card.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: android.view.View) { refresh.performClick() }
            override fun onViewDetachedFromWindow(view: android.view.View) = Unit
        })
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
