package com.elitedarkkaiser.redmagic

import com.elitedarkkaiser.redmagic.state.LedState
import org.junit.Assert.*
import org.junit.Test

class TriggerLedProfileSelectionTest {
    @Test fun brightnessSurvivesProfileEffectColorAndSplitEdits() {
        val initial = LedState(true, LedBrightness.encode(96, ShoulderLedSplit.encode("breathe", 0xff0000, 0x0000ff)), 1)
        val editor = TriggerLedProfileSelection(initial)
        editor.effect = "rapid"
        editor.selectColor(true, 5)
        editor.brightness = 32
        val saved = editor.snapshot()
        assertEquals(32, LedBrightness.level(saved.effect))
        assertEquals(ShoulderLedSplit.Selection("rapid", 0x00ff00, 0x0000ff), ShoulderLedSplit.decode(saved.effect))
        assertEquals(saved, TriggerLedProfileSelection(saved).snapshot())
        assertEquals(96, LedBrightness.level(initial.effect))
        editor.split = false
        assertEquals("rapid", LedBrightness.effect(editor.snapshot().effect))
        assertEquals(32, LedBrightness.level(editor.snapshot().effect))
    }

    @Test fun effectChangesKeepBothPresetColors() {
        val editor = TriggerLedProfileSelection(LedState(true, "breathe", 5))
        editor.split = true
        editor.selectColor(true, 7)
        editor.selectColor(false, 8)
        for (effect in listOf("steady", "breathe", "flashing", "rapid")) {
            editor.effect = effect
            assertEquals(ShoulderLedSplit.Selection(effect, 0x0000ff, 0xff00ff), ShoulderLedSplit.decode(editor.snapshot().effect))
        }
    }
    @Test fun savedSplitSelectionReopensWithItsColorsAndEnabledState() {
        val saved = LedState(false, ShoulderLedSplit.encode("flashing", 0xffff00, 0x0000ff), 5)
        assertEquals(saved, TriggerLedProfileSelection(saved).snapshot())
    }
    @Test fun matchingTogglePreservesStagedPairUntilMatchingColorChanges() {
        val editor = TriggerLedProfileSelection(LedState(true, "breathe", 1))
        editor.split = true
        editor.selectColor(false, 7)
        val split = editor.snapshot()
        editor.split = false
        assertEquals("breathe", editor.snapshot().effect)
        editor.split = true
        assertEquals(split, editor.snapshot())
        editor.split = false
        editor.selectColor(null, 5)
        editor.split = true
        assertEquals(ShoulderLedSplit.Selection("breathe", 0x00ff00, 0x00ff00), ShoulderLedSplit.decode(editor.snapshot().effect))
    }
    @Test fun editingDoesNotMutateTheOriginalProfileBeforeSave() {
        val original = LedState(true, "steady", 1)
        val editor = TriggerLedProfileSelection(original)
        editor.enabled = false
        editor.split = true
        editor.selectColor(false, 8)
        assertEquals(LedState(true, "steady", 1), original)
        assertFalse(editor.snapshot().enabled)
    }
}
