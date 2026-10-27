package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.widget.LinearLayout
import com.elitedarkkaiser.redmagic.MainActivity

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
        InsidePages.install(container, "Hardware", listOf(
            InsidePages.Page("Gaming", "Trigger mapping and game performance", emptyList(), icon = "🎮", factory = { listOf(GamingHubUi.create(activity, deps)) }, hidden = true),
            InsidePages.Page("Shoulder triggers", "Mapping, controls and safety", triggers, icon = "⌥"),
            InsidePages.Page("Gaming performance", "Refresh rate, touch response and performance mode", performance.subList(1, 4), icon = "🎮", hidden = true),
            InsidePages.Page("Haptic feedback", "Vibration controls", listOf(performance[4]), icon = "〰"),
            InsidePages.Page("Charge separation", "Charging controls", listOf(performance[0]), icon = "⚡"),
            InsidePages.Page("Profiles & automation", "Master profiles and rules", profiles, icon = "◆"),
            InsidePages.Page("Magic Key", "Button and slider functions", emptyList(), action = { (activity as? MainActivity)?.openMagicKeyFromGaming() }, icon = "⌘")
        ))
        return container
    }
}
