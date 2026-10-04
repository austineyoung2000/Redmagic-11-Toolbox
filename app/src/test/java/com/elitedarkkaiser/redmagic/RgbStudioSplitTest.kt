package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Test

class RgbStudioSplitTest {
    @Test fun unequalSequencesWrapIndependently() {
        val state = RgbStudioState(splitTriggers = true, topTriggerColors = listOf(1, 5), bottomTriggerColors = listOf(7, 8, 9))
        var index = 0
        val actual = (0..6).map { state.triggerPair(index).also { index = state.advanceTriggerIndex(index) } }
        assertEquals(listOf(1 to 7, 5 to 8, 1 to 9, 5 to 7, 1 to 8, 5 to 9, 1 to 7), actual)
    }
    @Test fun fixedPairStaysFixedAndPreviousFrameWraps() {
        val state = RgbStudioState(splitTriggers = true, topTriggerColors = listOf(7), bottomTriggerColors = listOf(8))
        assertEquals(7 to 8, state.triggerPair(-1))
        assertEquals(0, state.advanceTriggerIndex(0))
    }
    @Test fun oldSettingsKeepMatchingTriggerCycle() {
        val state = RgbStudioState(colors = listOf(1, 5, 7))
        assertFalse(state.splitTriggers)
        assertEquals(0, state.advanceTriggerIndex(2))
    }
}
