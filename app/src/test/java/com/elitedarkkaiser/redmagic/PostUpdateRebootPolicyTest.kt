package com.elitedarkkaiser.redmagic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostUpdateRebootPolicyTest {
    @Test
    fun freshInstallDoesNotPrompt() {
        assertFalse(
            PostUpdateRebootPolicy.shouldPrompt(
                firstInstallTimeMs = 2_000L,
                lastUpdateTimeMs = 2_000L,
                previouslyObservedUpdateTimeMs = 0L,
                bootStartedAtMs = 1_000L
            )
        )
    }

    @Test
    fun existingInstallUpdatedDuringCurrentBootPrompts() {
        assertTrue(
            PostUpdateRebootPolicy.shouldPrompt(
                firstInstallTimeMs = 500L,
                lastUpdateTimeMs = 2_000L,
                previouslyObservedUpdateTimeMs = 0L,
                bootStartedAtMs = 1_000L
            )
        )
    }

    @Test
    fun observedInstallUpdatedDuringCurrentBootPrompts() {
        assertTrue(
            PostUpdateRebootPolicy.shouldPrompt(
                firstInstallTimeMs = 500L,
                lastUpdateTimeMs = 3_000L,
                previouslyObservedUpdateTimeMs = 2_000L,
                bootStartedAtMs = 1_000L
            )
        )
    }

    @Test
    fun updateInstalledBeforeCurrentBootDoesNotPrompt() {
        assertFalse(
            PostUpdateRebootPolicy.shouldPrompt(
                firstInstallTimeMs = 500L,
                lastUpdateTimeMs = 2_000L,
                previouslyObservedUpdateTimeMs = 1_500L,
                bootStartedAtMs = 2_500L
            )
        )
    }

    @Test
    fun unchangedUpdateDoesNotPromptAgain() {
        assertFalse(
            PostUpdateRebootPolicy.shouldPrompt(
                firstInstallTimeMs = 500L,
                lastUpdateTimeMs = 2_000L,
                previouslyObservedUpdateTimeMs = 2_000L,
                bootStartedAtMs = 1_000L
            )
        )
    }
}
