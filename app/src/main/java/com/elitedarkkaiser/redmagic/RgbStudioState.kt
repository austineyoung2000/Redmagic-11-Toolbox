package com.elitedarkkaiser.redmagic

data class RgbStudioState(
    val enabled: Boolean = false,
    val syncZones: Boolean = true,
    val effect: String = "steady",
    val colors: List<Int> = DEFAULT_COLORS,
    val logoSpeedMs: Long = DEFAULT_SPEED_MS,
    val shoulderSpeedMs: Long = DEFAULT_SPEED_MS,
    val fanSpeedMs: Long = DEFAULT_SPEED_MS,
    val screenOffTimeoutMinutes: Int = 0,
    val splitTriggers: Boolean = false,
    val topTriggerColors: List<Int> = DEFAULT_COLORS,
    val bottomTriggerColors: List<Int> = DEFAULT_COLORS,
    val logoBrightness: Int = 255,
    val shoulderBrightness: Int = 255,
    val fanBrightness: Int = 255
) {
    fun triggerPair(index: Int): Pair<Int, Int> {
        val top = topTriggerColors.ifEmpty { DEFAULT_COLORS }
        val bottom = bottomTriggerColors.ifEmpty { DEFAULT_COLORS }
        return top[Math.floorMod(index, top.size)] to bottom[Math.floorMod(index, bottom.size)]
    }

    fun advanceTriggerIndex(index: Int): Int = if (splitTriggers) {
        (index + 1) % (topTriggerColors.size.coerceAtLeast(1) * bottomTriggerColors.size.coerceAtLeast(1))
    } else (index + 1) % colors.size.coerceAtLeast(1)

    companion object {
        val DEFAULT_COLORS = listOf(1, 3, 4, 5, 6, 7, 8, 9)
        const val DEFAULT_SPEED_MS = 1_500L
    }
}
