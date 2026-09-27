package com.elitedarkkaiser.redmagic

/**
 * Fan multicolor palettes occupy the low 12-bit color field used by the
 * stock ColorfulLight protocol. Older toolbox versions stored a complete
 * steady-effect command in the effect field; keep decoding that format so
 * existing preferences and imported profiles remain usable.
 */
internal object FanLedPalette {
    private const val LEGACY_PREFIX = "preset:"
    private const val STOCK_STEADY_PREFIX = "0x3002"
    private const val FIRST_PALETTE = 0x101
    private const val LAST_PALETTE = 0x108

    fun fromStockPreset(value: String): Int? {
        val normalized = value.lowercase()
        if (
            normalized.length != 9 ||
            !normalized.startsWith(STOCK_STEADY_PREFIX)
        ) {
            return null
        }

        return normalized.takeLast(3).toIntOrNull(16)
            ?.takeIf { it in FIRST_PALETTE..LAST_PALETTE }
    }

    fun fromLegacyEffect(effect: String): Int? {
        if (!effect.startsWith(LEGACY_PREFIX)) return null
        return fromStockPreset(effect.removePrefix(LEGACY_PREFIX))
    }

    fun normalizeEffect(effect: String): String {
        return if (fromLegacyEffect(effect) != null) "steady" else effect
    }

    fun normalizeColor(effect: String, color: Int): Int {
        return fromLegacyEffect(effect) ?: color
    }

    fun isPalette(color: Int): Boolean {
        return color in FIRST_PALETTE..LAST_PALETTE
    }

    fun isSelected(effect: String, color: Int, presetValue: String): Boolean {
        val palette = fromStockPreset(presetValue) ?: return false
        return normalizeColor(effect, color) == palette
    }
}
