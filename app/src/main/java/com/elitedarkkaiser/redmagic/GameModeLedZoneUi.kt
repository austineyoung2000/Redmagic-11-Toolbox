package com.elitedarkkaiser.redmagic

import android.content.res.ColorStateList
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.checkbox.MaterialCheckBox

/** Builds the repeated logo/shoulder LED controls in the Game Mode dialog. */
internal object GameModeLedZoneUi {
    fun create(
        activity: MainActivity,
        deps: GameModeUi.Deps,
        title: String,
        enableLabel: String,
        initialEnabled: Boolean,
        initialEffect: String,
        initialColor: Int,
        shoulderZone: Boolean,
        onEnabledChanged: (Boolean) -> Unit,
        onEffectChanged: (String) -> Unit,
        onColorChanged: (Int) -> Unit
    ): List<View> {
        if (shoulderZone) {
            return listOf(TriggerLedProfileUi.create(activity, title, enableLabel,
                com.elitedarkkaiser.redmagic.state.LedState(initialEnabled, initialEffect, initialColor),
                TriggerLedProfileUi.Deps(deps.textPrimary, deps.textSecondary, deps.accent,
                    deps.panelPressed, deps.borderColor, deps.dp, deps.colorDotGeneric, deps.colorDotDrawable)
            ) { state ->
                onEnabledChanged(state.enabled)
                onEffectChanged(state.effect)
                onColorChanged(state.color)
            })
        }
        var effect = initialEffect
        var color = initialColor

        val label = TextView(activity).apply {
            text = title
            textSize = 13f
            setTextColor(deps.textSecondary)
            setPadding(0, deps.dp(12), 0, deps.dp(6))
        }
        val enabled = MaterialCheckBox(activity).apply {
            text = enableLabel
            isChecked = initialEnabled
            textSize = 14f
            setTextColor(deps.textPrimary)
            buttonTintList = ColorStateList.valueOf(deps.accent)
            setOnCheckedChangeListener { _, checked ->
                onEnabledChanged(checked)
            }
        }

        lateinit var steady: Button
        lateinit var breathe: Button
        lateinit var flashing: Button
        lateinit var rapid: Button

        fun refreshEffects() {
            if (shoulderZone) {
                GameModeActions.refreshShoulderEffectButtons(
                    selectedEffect = LedBrightness.effect(effect),
                    steadyBtn = steady,
                    breatheBtn = breathe,
                    flashingBtn = flashing,
                    rapidBtn = rapid,
                    roundedFill = deps.roundedFill,
                    selectedColor = deps.panelPressed,
                    unselectedColor = deps.panelColor
                )
            } else {
                GameModeActions.refreshLedEffectButtons(
                    selectedEffect = LedBrightness.effect(effect),
                    steadyBtn = steady,
                    breatheBtn = breathe,
                    flashingBtn = flashing,
                    rapidBtn = rapid,
                    roundedFill = deps.roundedFill,
                    selectedColor = deps.panelPressed,
                    unselectedColor = deps.panelColor
                )
            }
        }

        fun effectButton(text: String, value: String): Button {
            return deps.filterChip(text, LedBrightness.effect(effect) == value) {
                effect = LedBrightness.withEffect(effect, value)
                onEffectChanged(effect)
                refreshEffects()
            }
        }

        steady = effectButton("Steady", "steady")
        breathe = effectButton("Breathe", "breathe")
        flashing = effectButton("Flashing", "flashing")
        rapid = effectButton("Rapid", "rapid")

        val effectRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf(steady, breathe, flashing, rapid)
                .forEachIndexed { index, button ->
                    if (index > 0) addView(deps.space(deps.dp(8)))
                    addView(
                        button,
                        LinearLayout.LayoutParams(0, deps.dp(44), 1f)
                    )
                }
        }

        lateinit var firstColorRow: LinearLayout
        lateinit var secondColorRow: LinearLayout
        val choices = listOf(
            1 to "#FF0000",
            3 to "#FF8C00",
            4 to "#FFD600",
            5 to "#00E676",
            6 to "#00E5FF",
            7 to "#1565FF",
            8 to "#A020F0",
            9 to "#FF69B4"
        )

        fun refreshColors() {
            choices.take(4).forEachIndexed { index, choice ->
                firstColorRow.getChildAt(index * 2).background =
                    deps.colorDotDrawable(choice.second, color == choice.first)
            }
            choices.drop(4).forEachIndexed { index, choice ->
                secondColorRow.getChildAt(index * 2).background =
                    deps.colorDotDrawable(choice.second, color == choice.first)
            }
        }

        fun colorDot(id: Int, hex: String): View {
            return deps.colorDotGeneric(hex, color == id) {
                color = id
                onColorChanged(id)
                refreshColors()
            }
        }

        fun colorRow(items: List<Pair<Int, String>>): LinearLayout {
            return LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, deps.dp(10), 0, 0)
                items.forEachIndexed { index, choice ->
                    if (index > 0) addView(deps.space(deps.dp(10)))
                    addView(colorDot(choice.first, choice.second))
                }
            }
        }

        firstColorRow = colorRow(choices.take(4))
        secondColorRow = colorRow(choices.drop(4))
        refreshEffects()
        refreshColors()

        val brightnessPanel = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        LedBrightnessUi(activity, brightnessPanel, "logo", { effect }, { color },
            { effect = it; onEffectChanged(it) }, {}, deps.textSecondary, deps.accent, deps.dp)
        return listOf(
            label,
            enabled,
            effectRow,
            firstColorRow,
            secondColorRow,
            brightnessPanel
        )
    }
}
