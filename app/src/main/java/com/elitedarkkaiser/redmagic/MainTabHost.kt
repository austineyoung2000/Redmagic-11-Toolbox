package com.elitedarkkaiser.redmagic

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.elitedarkkaiser.redmagic.ui.components.MainBottomNavigation

/** Owns lazy tab creation, replacement, visibility, and bottom navigation. */
internal class MainTabHost(
    private val activity: MainActivity,
    private val topInset: Int,
    private val backgroundColor: Int,
    private val dp: (Int) -> Int,
    private val createHome: () -> LinearLayout,
    private val createCooling: () -> LinearLayout,
    private val createControls: () -> LinearLayout,
    private val createHardware: () -> LinearLayout,
    private val createLighting: () -> LinearLayout
) {
    private data class TabState(
        var view: LinearLayout,
        var built: Boolean,
        val factory: () -> LinearLayout
    )

    private val navigation = MainBottomNavigation(activity, ::select)
    private val tabs = linkedMapOf<String, TabState>()

    fun launch() {
        val result = MainUiLauncher.launch(
            activity = activity,
            topInset = topInset,
            bgColor = backgroundColor,
            dp = dp,
            createHomeTab = createHome,
            createCoolingTab = createCooling,
            createControlsTab = createControls,
            createHardwareTab = createHardware,
            createLightingTab = createLighting,
            bottomNavBar = { navigation.createView() }
        )

        tabs.clear()
        tabs["home"] = TabState(result.homeTab, true, createHome)
        tabs["cooling"] =
            TabState(result.coolingTab, false, createCooling)
        tabs["controls"] =
            TabState(result.controlsTab, false, createControls)
        tabs["hardware"] =
            TabState(result.hardwareTab, false, createHardware)
        tabs["lighting"] =
            TabState(result.lightingTab, false, createLighting)

        select("home")
    }

    fun refreshBuiltTabs() {
        tabs.values
            .filter { it.built }
            .filterNot { it === tabs["home"] }
            .forEach { state ->
                state.view = replace(
                    state.view,
                    state.factory(),
                    preserveVisibility = true
                )
            }
    }

    fun select(tabId: String) {
        val selected = tabs[tabId] ?: return

        if (!selected.built) {
            selected.view = replace(
                selected.view,
                selected.factory(),
                preserveVisibility = false
            )
            selected.built = true
        }

        tabs.forEach { (id, state) ->
            state.view.visibility =
                if (id == tabId) View.VISIBLE else View.GONE
        }
        navigation.select(tabId)
    }

    private fun replace(
        oldView: LinearLayout,
        newView: LinearLayout,
        preserveVisibility: Boolean
    ): LinearLayout {
        val parent = oldView.parent as? ViewGroup
            ?: return oldView
        val index = parent.indexOfChild(oldView)

        if (index < 0) return oldView
        if (preserveVisibility) {
            newView.visibility = oldView.visibility
        }

        parent.removeViewAt(index)
        parent.addView(newView, index)
        return newView
    }
}
