package com.elitedarkkaiser.redmagic

internal object MainUiStartup {

    fun applySavedHardwareState(
        applySavedFanLedStateOnLaunch: () -> Unit,
        applySavedLogoLedStateOnLaunch: () -> Unit,
        applySavedShoulderLedStateOnLaunch: () -> Unit,
        applySavedPumpStateOnLaunch: () -> Unit,
        setRealTimePreviewEnabled: (Boolean) -> Unit,
        isRealTimePreviewEnabledSaved: () -> Boolean,
        setUseFahrenheit: (Boolean) -> Unit,
        isUseFahrenheitSaved: () -> Boolean,
        setAutoPumpEnabled: (Boolean) -> Unit,
        isAutoPumpEnabledSaved: () -> Boolean
    ) {
        // UI launch should restore UI state only.
        // Hardware writes are owned by explicit actions/services, not app open.
        applySavedFanLedStateOnLaunch()
        applySavedLogoLedStateOnLaunch()
        applySavedShoulderLedStateOnLaunch()
        applySavedPumpStateOnLaunch()

        setRealTimePreviewEnabled(isRealTimePreviewEnabledSaved())
        setUseFahrenheit(isUseFahrenheitSaved())
        setAutoPumpEnabled(isAutoPumpEnabledSaved())
    }
}
