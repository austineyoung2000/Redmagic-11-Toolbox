package com.elitedarkkaiser.redmagic

import com.elitedarkkaiser.redmagic.state.LedState
import org.junit.Assert.*
import org.junit.Test

class LogoBarSelectionTest {
    @Test fun legacyProfilesKeepTheirMatchingColorBrightnessEffectAndEnableState() {
        for (enabled in listOf(true, false)) {
            val original = LedState(enabled, "dim:128:breathe", 7)
            val pair = LogoBarSelection.from(original)
            assertEquals(enabled, pair.logoEnabled)
            assertEquals(enabled, pair.barEnabled)
            assertEquals(128, pair.logoBrightness)
            assertEquals(128, pair.barBrightness)
            assertEquals(7, pair.barColor)
            assertEquals("breathe", pair.effect)
            assertEquals(pair, LogoBarSelection.decode(pair.encode()))
        }
    }
    @Test fun editingOneAreaKeepsTheOtherAndSharedProgramEnabled() {
        val original = LogoBarSelection("rapid", true, 32, true, 5, 255)
        val barOff = original.copy(barEnabled=false, barBrightness=96)
        assertEquals(original.logoEnabled, barOff.logoEnabled)
        assertEquals(original.logoBrightness, barOff.logoBrightness)
        assertTrue(barOff.state(1).enabled)
        val logoOff = original.copy(logoEnabled=false)
        assertTrue(logoOff.state(1).enabled)
        assertFalse(logoOff.copy(barEnabled=false).state(1).enabled)
        assertEquals("rapid", LedBrightness.effect(barOff.encode()))
        assertEquals(original, LogoBarSelection.from(original.state(1)))
    }
    @Test fun oldRgbStudioSettingsMirrorLogoAndBarUntilEdited() {
        val old = RgbStudioState(colors=listOf(1,7), logoBrightness=96, logoSpeedMs=2500)
        assertEquals(old.colors, old.barColors)
        assertEquals(old.logoBrightness, old.barBrightness)
        assertEquals(old.logoSpeedMs, old.barSpeedMs)
        val next = old.copy(barColors=listOf(5), barBrightness=32, barSpeedMs=1000)
        assertEquals(listOf(1,7), next.colors)
        assertEquals(96, next.logoBrightness)
        assertEquals(2500L, next.logoSpeedMs)
    }
}
