package com.elitedarkkaiser.redmagic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootAvailabilityPolicyTest {
    @Test fun automaticRecoveryRequiresUnavailableThenVerified() {
        assertFalse(RootAvailabilityPolicy.shouldRecover(null, true, false))
        assertFalse(RootAvailabilityPolicy.shouldRecover(null, false, false))
        assertFalse(RootAvailabilityPolicy.shouldRecover(false, false, false))
        assertTrue(RootAvailabilityPolicy.shouldRecover(false, true, false))
        assertFalse(RootAvailabilityPolicy.shouldRecover(true, true, false))
    }

    @Test fun duplicateRootHelpersDoNotForceRecoveryTwice() {
        assertTrue(RootAvailabilityPolicy.shouldForceHelper(true, false))
        assertFalse(RootAvailabilityPolicy.shouldForceHelper(true, true))
        assertFalse(RootAvailabilityPolicy.shouldForceHelper(false, false))
    }

    @Test fun manualRecheckOnlyRecoversAfterVerification() {
        assertTrue(RootAvailabilityPolicy.shouldRecover(null, true, true))
        assertTrue(RootAvailabilityPolicy.shouldRecover(true, true, true))
        assertFalse(RootAvailabilityPolicy.shouldRecover(false, false, true))
    }
}
