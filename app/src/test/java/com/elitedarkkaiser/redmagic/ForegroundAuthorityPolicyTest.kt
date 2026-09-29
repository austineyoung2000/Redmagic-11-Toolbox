package com.elitedarkkaiser.redmagic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundAuthorityPolicyTest {
    @Test
    fun liveMonitorWithRecentSampleIsAuthoritative() {
        assertTrue(
            ForegroundAuthorityPolicy.hasFreshRootSample(
                processAlive = true,
                lastSampleAtMs = 8_000L,
                nowMs = 10_000L,
                maximumAgeMs = 2_500L
            )
        )
    }

    @Test
    fun liveMonitorWithoutSampleAllowsFallback() {
        assertFalse(
            ForegroundAuthorityPolicy.hasFreshRootSample(
                processAlive = true,
                lastSampleAtMs = 0L,
                nowMs = 10_000L,
                maximumAgeMs = 2_500L
            )
        )
    }

    @Test
    fun liveMonitorWithStaleSampleAllowsFallback() {
        assertFalse(
            ForegroundAuthorityPolicy.hasFreshRootSample(
                processAlive = true,
                lastSampleAtMs = 7_499L,
                nowMs = 10_000L,
                maximumAgeMs = 2_500L
            )
        )
    }

    @Test
    fun stoppedMonitorAllowsFallbackEvenWithRecentSample() {
        assertFalse(
            ForegroundAuthorityPolicy.hasFreshRootSample(
                processAlive = false,
                lastSampleAtMs = 9_999L,
                nowMs = 10_000L,
                maximumAgeMs = 2_500L
            )
        )
    }

    @Test
    fun futureTimestampIsNotTrusted() {
        assertFalse(
            ForegroundAuthorityPolicy.hasFreshRootSample(
                processAlive = true,
                lastSampleAtMs = 10_001L,
                nowMs = 10_000L,
                maximumAgeMs = 2_500L
            )
        )
    }
}
