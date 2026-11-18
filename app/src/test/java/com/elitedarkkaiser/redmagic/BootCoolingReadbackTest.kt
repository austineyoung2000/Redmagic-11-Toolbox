package com.elitedarkkaiser.redmagic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootCoolingReadbackTest {
    @Test fun fanRestoreNeedsMatchingPowerAndLevel() {
        assertTrue(CoolingStateMatch.fan(true, 3, 3))
        assertFalse(CoolingStateMatch.fan(true, 2, 3))
        assertFalse(CoolingStateMatch.fan(null, 3, 3))
        assertTrue(CoolingStateMatch.fan(false, null, 0))
    }

    @Test fun pumpRestoreIncludesExperimentalAndRejectsMissingReadback() {
        assertTrue(CoolingStateMatch.pump("1", "4", "90", "experimental"))
        assertFalse(CoolingStateMatch.pump("1", "4", "80", "experimental"))
        assertFalse(CoolingStateMatch.pump(null, null, null, "off"))
        assertTrue(CoolingStateMatch.pump("0", null, null, "off"))
    }
}
