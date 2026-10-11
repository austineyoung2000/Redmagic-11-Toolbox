package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Test

class LedProfileValidationTest {
    @Test fun preserveStockColorsEffectsAndLegacyFanPalettes() {
        for (zone in listOf("logo", "triggers", "fan"))
            for (effect in listOf("steady", "breathe", "flashing", "blink", "rapid"))
                for (color in listOf(1, 3, 4, 5, 6, 7, 8, 9))
                    assertTrue(LedProfileValidation.isValid(zone, effect, color))
        for (color in 0x101..0x108) {
            assertTrue(LedProfileValidation.isValid("fan", "dim:128:breathe", color))
            assertTrue(LedProfileValidation.isValid("fan", "preset:0x3002${color.toString(16)}", -1))
            assertFalse(LedProfileValidation.isValid("logo", "steady", color))
        }
    }
    @Test fun preserveSplitAndBrightnessButEnforceZoneRouting() {
        val split = ShoulderLedSplit.encode("breathe", 0xff0000, 0x00ffff)
        val areas = LogoBarSelection("breathe", true, 128, true, 7, 32).encode()
        assertTrue(LedProfileValidation.isValid("triggers", "dim:32:$split", 7))
        assertTrue(LedProfileValidation.isValid("logo", areas, 1))
        assertFalse(LedProfileValidation.isValid("fan", split, 7))
        assertFalse(LedProfileValidation.isValid("triggers", areas, 7))
        assertFalse(LedProfileValidation.isValid("logo", "dim:32:blink", 7))
    }
    @Test fun rejectUnknownMalformedAndCommandLikeValues() {
        for (value in listOf("", "unknown", "steady; reboot", "\$(reboot)", "dim:31:steady",
            "dim:256:steady", "dim:128:dim:128:steady", "split:breathe:bad:FFFFFF",
            "areas:breathe:1:128:1:99:128", "steady".repeat(100)))
            assertFalse(value, LedProfileValidation.isValid("logo", value, 7))
        for (color in listOf(-1, 0, 2, 10, Int.MAX_VALUE))
            assertFalse(LedProfileValidation.isValid("fan", "steady", color))
        assertFalse(LedProfileValidation.isValid("unknown", "steady", 7))
    }
}
