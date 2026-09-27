package com.elitedarkkaiser.redmagic

internal object DeviceCapabilityPolicy {
    fun sliderAvailable(
        supportedModel: Boolean,
        sliderNodeAvailable: Boolean,
        sliderPropertyPresent: Boolean
    ): Boolean {
        return supportedModel ||
            sliderNodeAvailable ||
            sliderPropertyPresent
    }

    fun stockGameSuiteAvailable(
        stockFirmware: Boolean,
        gameSpaceInstalled: Boolean,
        gameAssistInstalled: Boolean
    ): Boolean {
        return stockFirmware &&
            gameSpaceInstalled &&
            gameAssistInstalled
    }
}
