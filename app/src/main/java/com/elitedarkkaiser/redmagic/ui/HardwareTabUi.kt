package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.widget.LinearLayout

/** Composes focused Hardware-tab section builders in display order. */
object HardwareTabUi {
    fun create(
        activity: Activity,
        deps: HardwareTabDeps
    ): LinearLayout {
        return deps.scrollTabContainer().apply {
            HardwareTriggerSections
                .createCards(activity, deps)
                .forEach(::addView)
            HardwarePerformanceSections
                .createCards(activity, deps)
                .forEach(::addView)
            HardwareProfileSections
                .createCards(activity, deps)
                .forEach(::addView)
        }
    }
}
