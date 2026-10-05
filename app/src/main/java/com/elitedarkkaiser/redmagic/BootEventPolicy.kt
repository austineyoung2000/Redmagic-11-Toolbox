package com.elitedarkkaiser.redmagic

internal enum class BootEvent {
    ROOT_STARTUP,
    BOOT_COMPLETED,
    USER_UNLOCKED
}

internal data class BootEventDecision(
    val startCoreServices: Boolean,
    val resetManualTriggerPause: Boolean,
    val startTriggers: Boolean,
    val runUnlockAutomation: Boolean
) {
    val needsAsyncWork: Boolean
        get() = startTriggers || runUnlockAutomation
}

internal object BootEventPolicy {
    fun decide(
        event: BootEvent,
        coreServicesAlreadyStarted: Boolean,
        triggersAutoStart: Boolean,
        hasUnlockAutomation: Boolean
    ): BootEventDecision {
        val startCore = !coreServicesAlreadyStarted

        return BootEventDecision(
            startCoreServices = startCore,
            resetManualTriggerPause =
                event == BootEvent.BOOT_COMPLETED || startCore,
            startTriggers = startCore && triggersAutoStart,
            runUnlockAutomation =
                event == BootEvent.USER_UNLOCKED &&
                    hasUnlockAutomation
        )
    }
}
