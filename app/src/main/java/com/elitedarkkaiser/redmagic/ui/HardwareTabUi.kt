package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.widget.LinearLayout

/** Composes focused Hardware-tab section builders in display order. */
object HardwareTabUi {
    fun create(
        activity: Activity,
        deps: HardwareTabDeps
    ): LinearLayout {
        val container = deps.scrollTabContainer()
        val triggers = HardwareTriggerSections.createCards(activity, deps)
        val performance = HardwarePerformanceSections.createCards(activity, deps)
        val profiles = HardwareProfileSections.createCards(activity, deps)
        InsidePages.install(container, "Hardware & gaming", listOf(
            InsidePages.Page("Shoulder triggers", "Mapping, controls and safety", triggers),
            InsidePages.Page("Gaming performance", "Refresh rate, touch response and performance mode", performance.subList(1, 4)),
            InsidePages.Page("Haptic feedback", "Vibration controls", listOf(performance[4])),
            InsidePages.Page("Charge separation", "Charging controls", listOf(performance[0])),
            InsidePages.Page("Profiles & automation", "Master profiles and rules", profiles)
        ))
        return container
    }
}
