package com.elitedarkkaiser.redmagic

/** Reject malformed saved selections before any root LED command is constructed. */
internal object LedProfileValidation {
    private val zones = setOf("logo", "triggers", "fan")
    private val colors = setOf(1, 3, 4, 5, 6, 7, 8, 9)
    private val effects = setOf("steady", "breathe", "flashing", "blink", "rapid")

    fun isValid(zone: String, value: String, color: Int): Boolean {
        if (zone !in zones || value.length !in 1..160) return false
        val effect = if (zone == "fan") FanLedPalette.normalizeEffect(value) else value
        val normalizedColor = if (zone == "fan") FanLedPalette.normalizeColor(value, color) else color
        if (normalizedColor !in colors && !(zone == "fan" && FanLedPalette.isPalette(normalizedColor))) return false
        if (effect.startsWith("areas:")) return zone == "logo" && LogoBarSelection.decode(effect) != null
        val base = if (effect.startsWith("dim:")) LedBrightness.decode(effect)?.effect ?: return false else effect
        if (base.startsWith("split:")) return zone == "triggers" && ShoulderLedSplit.decode(base) != null
        if (base.lowercase() !in effects) return false
        // Dimmed selections require an inspected program; never fall back to full brightness.
        return !effect.startsWith("dim:") || LedBrightness.supported(zone, effect, normalizedColor)
    }
}
