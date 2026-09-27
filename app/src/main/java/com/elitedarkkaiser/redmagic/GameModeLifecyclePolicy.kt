package com.elitedarkkaiser.redmagic

internal object GameModeLifecyclePolicy {
    fun acceptsWork(
        stopping: Boolean,
        pausedForScreenOff: Boolean = false
    ): Boolean {
        return !stopping && !pausedForScreenOff
    }

    fun needsRestore(
        activePackage: String?,
        ledOverrideActive: Boolean
    ): Boolean {
        return activePackage != null || ledOverrideActive
    }
}
