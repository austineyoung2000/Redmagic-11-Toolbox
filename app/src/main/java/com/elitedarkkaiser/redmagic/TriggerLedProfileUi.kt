package com.elitedarkkaiser.redmagic

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.elitedarkkaiser.redmagic.state.LedState
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Preset-only trigger editor; mode owners save/apply the published state. */
internal object TriggerLedProfileUi {
    data class Deps(
        val textPrimary: Int,
        val textSecondary: Int,
        val accent: Int,
        val panelPressed: Int,
        val borderColor: Int,
        val dp: (Int) -> Int,
        val colorDotGeneric: (String, Boolean, () -> Unit) -> View,
        val colorDotDrawable: (String, Boolean) -> Drawable
    )

    fun create(activity: Activity, title: String, enableLabel: String, initial: LedState,
        deps: Deps, onChanged: (LedState) -> Unit): LinearLayout {
        val selection = TriggerLedProfileSelection(initial)
        val panel = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        fun label(text: String) = TextView(activity).apply {
            this.text = text
            textSize = 13f
            setTextColor(deps.textSecondary)
            setPadding(0, deps.dp(12), 0, deps.dp(6))
        }
        panel.addView(label(title))
        panel.addView(MaterialCheckBox(activity).apply {
            text = enableLabel
            isChecked = selection.enabled
            setTextColor(deps.textPrimary)
            buttonTintList = ColorStateList.valueOf(deps.accent)
            setOnCheckedChangeListener { _, checked ->
                selection.enabled = checked
                onChanged(selection.snapshot())
            }
        })
        val singlePanel = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val splitPanel = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val refreshDots = mutableListOf<() -> Unit>()
        fun refreshColors() {
            singlePanel.visibility = if (selection.split) View.GONE else View.VISIBLE
            splitPanel.visibility = if (selection.split) View.VISIBLE else View.GONE
            refreshDots.forEach { it() }
        }
        panel.addView(MaterialCheckBox(activity).apply {
            text = "Separate top and bottom colors"
            isChecked = selection.split
            setTextColor(deps.textPrimary)
            buttonTintList = ColorStateList.valueOf(deps.accent)
            setOnCheckedChangeListener { _, checked ->
                selection.split = checked
                refreshColors()
                onChanged(selection.snapshot())
            }
        })
        val effectRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val effectButtons = mutableListOf<Pair<String, MaterialButton>>()
        fun refreshEffects() {
            effectButtons.forEach { (value, button) ->
                val selected = selection.effect == value
                button.isSelected = selected
                button.backgroundTintList = ColorStateList.valueOf(if (selected) deps.panelPressed else Color.TRANSPARENT)
                button.strokeColor = ColorStateList.valueOf(if (selected) deps.accent else deps.borderColor)
            }
        }
        listOf("steady" to "Steady", "breathe" to "Breathe", "flashing" to "Flashing", "rapid" to "Rapid").forEach { (value, title) ->
            val button = MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = title
                textSize = 12f
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
                setTextColor(deps.textPrimary)
                setOnClickListener {
                    selection.effect = value
                    refreshEffects()
                    onChanged(selection.snapshot())
                }
            }
            effectButtons.add(value to button)
            effectRow.addView(button, LinearLayout.LayoutParams(0, deps.dp(48), 1f))
        }
        panel.addView(effectRow)
        fun palette(parent: LinearLayout, title: String, top: Boolean?) {
            parent.addView(label(title))
            ShoulderLedSplit.colors.chunked(4).forEach { colors ->
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, deps.dp(6), 0, 0)
                }
                colors.forEachIndexed { index, color ->
                    val rgb = ShoulderLedSplit.presetRgb(color.id)
                    fun selected() = when (top) {
                        true -> selection.topRgb == rgb
                        false -> selection.bottomRgb == rgb
                        null -> selection.color == color.id
                    }
                    if (index > 0) row.addView(View(activity), LinearLayout.LayoutParams(deps.dp(10), 1))
                    val dot = deps.colorDotGeneric(color.hex, selected()) {
                        selection.selectColor(top, color.id)
                        refreshColors()
                        onChanged(selection.snapshot())
                    }.apply { contentDescription = "$title: ${color.label}" }
                    row.addView(dot)
                    refreshDots.add { dot.background = deps.colorDotDrawable(color.hex, selected()) }
                }
                parent.addView(row)
            }
        }
        palette(singlePanel, "Color", null)
        palette(splitPanel, "Top trigger", true)
        palette(splitPanel, "Bottom trigger", false)
        panel.addView(singlePanel)
        panel.addView(splitPanel)
        refreshEffects()
        refreshColors()
        return panel
    }

    fun show(activity: Activity, title: String, subtitle: String, initial: LedState,
        deps: Deps, onSave: (LedState) -> Unit) {
        var edited = initial
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(deps.dp(22), deps.dp(8), deps.dp(22), deps.dp(12))
            addView(TextView(activity).apply { text = subtitle; setTextColor(deps.textSecondary) })
            addView(create(activity, "Trigger LEDs", "Enable for charging mode", initial, deps) { edited = it })
        }
        MaterialAlertDialogBuilder(activity).setTitle(title)
            .setView(ScrollView(activity).apply { addView(container) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ -> onSave(edited) }
            .show()
    }
}
