package com.elitedarkkaiser.redmagic

import com.elitedarkkaiser.redmagic.state.LedState

/** Two physical areas share one vendor program; retain both in the existing profile effect field. */
internal data class LogoBarSelection(
    val effect: String,
    val logoEnabled: Boolean,
    val logoBrightness: Int,
    val barEnabled: Boolean,
    val barColor: Int,
    val barBrightness: Int
) {
    fun encode(): String = "areas:$effect:${if (logoEnabled) 1 else 0}:$logoBrightness:${if (barEnabled) 1 else 0}:$barColor:$barBrightness"
    fun state(logoColor: Int) = LedState(logoEnabled || barEnabled, encode(), logoColor)
    companion object {
        val colors = listOf(1,3,4,5,6,7,8,9)
        val effects = listOf("steady", "breathe", "flashing", "rapid")
        fun decode(value: String): LogoBarSelection? {
            if (!value.startsWith("areas:")) return null
            val fields = value.split(':')
            if (fields.size != 7 || fields[1] !in effects || fields[2] !in listOf("0","1") || fields[4] !in listOf("0","1")) return null
            val logoLevel = fields[3].toIntOrNull()?.takeIf { it in 32..255 } ?: return null
            val barColor = fields[5].toIntOrNull()?.takeIf { it in colors } ?: return null
            val barLevel = fields[6].toIntOrNull()?.takeIf { it in 32..255 } ?: return null
            return LogoBarSelection(fields[1], fields[2] == "1", logoLevel, fields[4] == "1", barColor, barLevel)
        }
        fun from(state: LedState): LogoBarSelection = decode(state.effect) ?: LogoBarSelection(
            LedBrightness.effect(state.effect).takeIf { it in effects } ?: "steady",
            state.enabled, LedBrightness.level(state.effect), state.enabled,
            state.color.takeIf { it in colors } ?: 1, LedBrightness.level(state.effect))
    }
}
