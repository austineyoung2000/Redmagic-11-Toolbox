package com.elitedarkkaiser.redmagic

import com.elitedarkkaiser.redmagic.state.LedState

/** Editable preset selection shared by Game, Charging, and Call profiles. */
internal class TriggerLedProfileSelection(initial: LedState) {
    var brightness = LedBrightness.level(initial.effect)
    var enabled = initial.enabled
    var effect = ShoulderLedSplit.baseEffect(initial.effect)
        .takeIf { it in setOf("steady", "breathe", "flashing", "rapid") } ?: "steady"
    var color = initial.color
    var split = ShoulderLedSplit.decode(initial.effect) != null
    var topRgb = ShoulderLedSplit.decode(initial.effect)?.topRgb ?: ShoulderLedSplit.presetRgb(initial.color)
    var bottomRgb = ShoulderLedSplit.decode(initial.effect)?.bottomRgb ?: topRgb

    fun selectColor(top: Boolean?, id: Int) {
        val rgb = ShoulderLedSplit.presetRgb(id)
        when (top) {
            true -> topRgb = rgb
            false -> bottomRgb = rgb
            null -> { color = id; topRgb = rgb; bottomRgb = rgb }
        }
    }

    private val originallyDimmed = initial.effect.startsWith("dim:")
    private fun selectionEffect(): String {
        val base = if (split) ShoulderLedSplit.encode(effect, topRgb, bottomRgb) else effect
        return if (brightness != 255 || originallyDimmed) LedBrightness.encode(brightness, base) else base
    }

    fun snapshot(): LedState = LedState(enabled,
        selectionEffect(), color)
}
