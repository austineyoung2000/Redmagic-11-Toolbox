package com.elitedarkkaiser.redmagic.ui

import org.junit.Assert.*
import org.junit.Test

class ThermalGraphBoundsTest {
    @Test fun changingHistoryUsesMostOfTheCompactGraph() {
        val (low, high) = ThermalGraphBounds.compact(85.6f, 98.2f, true)
        assertTrue(low < 85.6f && high > 98.2f)
        assertTrue((98.2f - 85.6f) / (high - low) > 0.8f)
    }

    @Test fun stableHistoryKeepsEquivalentMinimumRanges() {
        val (cLow, cHigh) = ThermalGraphBounds.compact(30f, 30f, false)
        val (fLow, fHigh) = ThermalGraphBounds.compact(86f, 86f, true)
        assertEquals(4.8f, cHigh - cLow, 0.001f)
        assertEquals(8.64f, fHigh - fLow, 0.001f)
        assertEquals(cLow * 9f / 5f + 32f, fLow, 0.001f)
        assertEquals(cHigh * 9f / 5f + 32f, fHigh, 0.001f)
    }
}
