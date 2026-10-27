package com.elitedarkkaiser.redmagic

import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

internal object TriggerSafetyDialog {
    data class Deps(
        val textPrimary: Int,
        val textSecondary: Int,
        val panelColor: Int,
        val borderColor: Int,
        val panelPressed: Int,
        val accent: Int,
        val typeface: Typeface?,
        val dp: (Int) -> Int,
        val roundedBg: (Int, Int, Int) -> Drawable,
        val space: (Int) -> View
    )

    fun show(
        activity: MainActivity,
        deps: Deps,
        onSaved: () -> Unit
    ) {
        val saved = readTriggerSafetyConfig(activity)
        var mode = saved.mode
        var holdDurationMs = saved.holdDurationMs
        var unlockTimeoutMs = saved.unlockTimeoutMs
        var leftTapCount = saved.leftUnlockTapCount
        var rightTapCount = saved.rightUnlockTapCount
        val style = TriggerDialogUi.Style(
            textPrimary = deps.textPrimary,
            textSecondary = deps.textSecondary,
            panelColor = deps.panelColor,
            borderColor = deps.borderColor,
            accent = deps.accent,
            typeface = deps.typeface,
            dp = deps.dp,
            roundedBg = deps.roundedBg
        )

        fun settingLabel(textValue: String) =
            TextView(activity).apply {
                text = textValue
                textSize = 12f
                setTextColor(deps.textSecondary)
                setPadding(0, deps.dp(8), 0, deps.dp(6))
            }

        fun safetySwitch(
            textValue: String,
            checked: Boolean
        ) = MaterialSwitch(activity).apply {
            text = textValue
            textSize = 14f
            setTextColor(deps.textPrimary)
            isChecked = checked
            minHeight = deps.dp(48)
        }

        fun <T> renderChoiceRow(
            row: LinearLayout,
            options: List<Pair<T, String>>,
            selected: () -> T,
            update: (T) -> Unit
        ) {
            row.removeAllViews()
            options.forEachIndexed { index, (value, label) ->
                val button = TriggerDialogUi.optionButton(
                    activity,
                    style,
                    label,
                    value == selected()
                ) {
                    update(value)
                    renderChoiceRow(
                        row,
                        options,
                        selected,
                        update
                    )
                }
                TriggerDialogUi.addWeightedOption(
                    row,
                    button,
                    style,
                    addGap = index > 0
                )
            }
        }

        val modeSection = TriggerDialogUi.section(
            activity,
            style,
            "PROTECTION MODE",
            "Choose how a physical press becomes an action."
        )
        val modeGrid = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val modeOptions = listOf(
            TriggerSafetyConfig.MODE_OFF to "Off",
            TriggerSafetyConfig.MODE_INTENT to "Intent Unlock",
            TriggerSafetyConfig.MODE_HOLD to "Hold",
            TriggerSafetyConfig.MODE_INTENT_HOLD to "Intent + Hold"
        )

        var holdSectionRef: LinearLayout? = null
        var intentSectionRef: LinearLayout? = null

        fun refreshModeVisibility() {
            val usesIntent =
                mode == TriggerSafetyConfig.MODE_INTENT ||
                    mode == TriggerSafetyConfig.MODE_INTENT_HOLD
            val usesHold =
                mode == TriggerSafetyConfig.MODE_HOLD ||
                    mode == TriggerSafetyConfig.MODE_INTENT_HOLD

            holdSectionRef?.visibility =
                if (usesHold) View.VISIBLE else View.GONE
            intentSectionRef?.visibility =
                if (usesIntent) View.VISIBLE else View.GONE
        }

        fun renderModeGrid() {
            modeGrid.removeAllViews()
            modeOptions.chunked(2).forEachIndexed {
                    rowIndex,
                    options ->
                val row = TriggerDialogUi.optionRow(
                    activity,
                    style
                )
                options.forEachIndexed { columnIndex, option ->
                    val button = TriggerDialogUi.optionButton(
                        activity,
                        style,
                        option.second,
                        option.first == mode
                    ) {
                        mode = option.first
                        renderModeGrid()
                        refreshModeVisibility()
                    }
                    TriggerDialogUi.addWeightedOption(
                        row,
                        button,
                        style,
                        addGap = columnIndex > 0
                    )
                }
                if (rowIndex > 0) {
                    modeGrid.addView(
                        TriggerDialogUi.space(
                            activity,
                            deps.dp(8)
                        )
                    )
                }
                modeGrid.addView(row)
            }
        }
        renderModeGrid()
        modeSection.addView(modeGrid)

        val holdSection = TriggerDialogUi.section(
            activity,
            style,
            "HOLD FILTER",
            "Ignore touches released before this duration."
        )
        holdSectionRef = holdSection
        val holdRow = TriggerDialogUi.optionRow(activity, style)
        renderChoiceRow(
            holdRow,
            TriggerSafetyConfig.ALLOWED_HOLD_DURATIONS
                .sorted()
                .map { it to "${it}ms" },
            { holdDurationMs },
            { holdDurationMs = it }
        )
        holdSection.addView(holdRow)

        val intentSection = TriggerDialogUi.section(
            activity,
            style,
            "INTENT UNLOCK",
            "Require deliberate taps before trigger actions unlock."
        )
        intentSectionRef = intentSection
        val leftTapRow = TriggerDialogUi.optionRow(activity, style)
        val rightTapRow = TriggerDialogUi.optionRow(activity, style)
        val timeoutRow = TriggerDialogUi.optionRow(activity, style)

        intentSection.addView(settingLabel("Left / Top taps"))
        intentSection.addView(leftTapRow)
        intentSection.addView(settingLabel("Right / Bottom taps"))
        intentSection.addView(rightTapRow)
        intentSection.addView(settingLabel("Unlocked duration"))
        intentSection.addView(timeoutRow)

        renderChoiceRow(
            leftTapRow,
            listOf(
                1 to "1",
                2 to "2",
                3 to "3",
                4 to "4"
            ),
            { leftTapCount },
            { leftTapCount = it }
        )
        renderChoiceRow(
            rightTapRow,
            listOf(
                2 to "2",
                3 to "3",
                4 to "4"
            ),
            { rightTapCount },
            { rightTapCount = it }
        )
        renderChoiceRow(
            timeoutRow,
            listOf(
                1_500L to "1.5s",
                2_500L to "2.5s",
                5_000L to "5s",
                10_000L to "10s"
            ),
            { unlockTimeoutMs },
            { unlockTimeoutMs = it }
        )

        val restrictionsSection = TriggerDialogUi.section(
            activity,
            style,
            "ADDITIONAL GUARDS",
            "These restrictions apply independently of the mode above."
        )
        val lockScreenSwitch = safetySwitch(
            "Block actions on lock screen",
            saved.blockOnLockScreen
        )
        val gameModeSwitch = safetySwitch(
            "Only while Game Mode is active",
            saved.gameModeOnly
        )
        val leftUnlockSwitch = safetySwitch(
            "Either trigger unlocks both",
            saved.leftUnlocksRight
        )
        restrictionsSection.addView(lockScreenSwitch)
        restrictionsSection.addView(gameModeSwitch)
        restrictionsSection.addView(TextView(activity).apply {
            text = gameModeAppsSummaryStorage(activity)
            textSize = 11f
            setTextColor(deps.textSecondary)
            setPadding(deps.dp(4), 0, 0, deps.dp(4))
        })
        restrictionsSection.addView(leftUnlockSwitch)

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                deps.dp(18),
                deps.dp(18),
                deps.dp(18),
                deps.dp(16)
            )
            background = deps.roundedBg(
                deps.panelColor,
                deps.borderColor,
                22
            )
            addView(
                TriggerDialogUi.title(
                    activity,
                    style,
                    "Trigger Safety"
                )
            )
            addView(
                TriggerDialogUi.subtitle(
                    activity,
                    style,
                    "Prevent accidental shoulder-trigger actions."
                )
            )
            addView(modeSection)
            addView(TriggerDialogUi.space(activity, deps.dp(10)))
            addView(holdSection)
            addView(TriggerDialogUi.space(activity, deps.dp(10)))
            addView(intentSection)
            addView(TriggerDialogUi.space(activity, deps.dp(10)))
            addView(restrictionsSection)
        }
        refreshModeVisibility()

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        val root = TriggerDialogUi.dialogRoot(
            activity,
            style,
            scroll
        )
        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(root)
            .setCancelable(true)
            .create()
        val buttonRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, deps.dp(14), 0, 0)
        }
        val cancelButton = TriggerDialogUi.actionButton(
            activity,
            style,
            "Cancel",
            primary = false
        ) {
            dialog.dismiss()
        }
        val saveButton = TriggerDialogUi.actionButton(
            activity,
            style,
            "Save safety",
            primary = true
        ) {
            saveTriggerSafetyConfig(
                activity,
                TriggerSafetyConfig(
                    mode = mode,
                    holdDurationMs = holdDurationMs,
                    unlockTimeoutMs = unlockTimeoutMs,
                    blockOnLockScreen = lockScreenSwitch.isChecked,
                    gameModeOnly = gameModeSwitch.isChecked,
                    leftUnlocksRight = leftUnlockSwitch.isChecked,
                    leftUnlockTapCount = leftTapCount,
                    rightUnlockTapCount = rightTapCount
                )
            )
            onSaved()
            Toast.makeText(
                activity,
                "Trigger Safety settings saved",
                Toast.LENGTH_SHORT
            ).show()
            dialog.dismiss()
        }
        TriggerDialogUi.addWeightedOption(
            buttonRow,
            cancelButton,
            style,
            addGap = false
        )
        TriggerDialogUi.addWeightedOption(
            buttonRow,
            saveButton,
            style,
            addGap = true
        )
        content.addView(buttonRow)

        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(
                ColorDrawable(android.graphics.Color.TRANSPARENT)
            )
            setDimAmount(0.65f)
        }
    }
}
