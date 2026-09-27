package com.elitedarkkaiser.redmagic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCapabilityPolicyTest {
    @Test
    fun confirmedModelMakesSliderAvailableWithoutOptionalSignals() {
        assertTrue(
            DeviceCapabilityPolicy.sliderAvailable(
                supportedModel = true,
                sliderNodeAvailable = false,
                sliderPropertyPresent = false
            )
        )
    }

    @Test
    fun customRomCanExposeSliderThroughDetectedNode() {
        assertTrue(
            DeviceCapabilityPolicy.sliderAvailable(
                supportedModel = false,
                sliderNodeAvailable = true,
                sliderPropertyPresent = false
            )
        )
    }

    @Test
    fun stockSuiteRequiresFirmwareAndBothPackages() {
        assertTrue(
            DeviceCapabilityPolicy.stockGameSuiteAvailable(
                stockFirmware = true,
                gameSpaceInstalled = true,
                gameAssistInstalled = true
            )
        )
        assertFalse(
            DeviceCapabilityPolicy.stockGameSuiteAvailable(
                stockFirmware = true,
                gameSpaceInstalled = true,
                gameAssistInstalled = false
            )
        )
    }
}
