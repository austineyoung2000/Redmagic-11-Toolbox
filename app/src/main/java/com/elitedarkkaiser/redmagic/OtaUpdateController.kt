package com.elitedarkkaiser.redmagic

/** Explicit user actions only: package state is Android-owned, never replayed at startup. */
internal object OtaUpdateController {
    data class Result(val commandSucceeded: Boolean, val snapshot: OtaUpdatePolicy.Snapshot?) {
        fun confirmed(enabled: Boolean): Boolean = commandSucceeded && snapshot?.matches(enabled) == true
    }

    fun read(): OtaUpdatePolicy.Snapshot? {
        if (!DeviceCompatibility.isSupportedDevice()) return null
        return OtaUpdatePolicy.parse(RootShell.execForOutput(OtaUpdatePolicy.readCommand()))
    }

    fun setEnabled(enabled: Boolean): Result {
        if (!DeviceCompatibility.isSupportedDevice()) return Result(false, null)
        val succeeded = RootShell.exec(OtaUpdatePolicy.changeCommand(enabled))
        return Result(succeeded, read())
    }
}
