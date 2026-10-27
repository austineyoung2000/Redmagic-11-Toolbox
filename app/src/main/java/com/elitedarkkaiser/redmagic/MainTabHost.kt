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
    private var currentTab = "home"

    private fun withBack(view: LinearLayout): LinearLayout {
        val button = android.widget.TextView(activity).apply {
            text = "‹  Dashboard"
            textSize = 16f
            setTextColor(com.elitedarkkaiser.redmagic.ui.AppTheme.textPrimary)
            contentDescription = "Back to dashboard"
            isClickable = true
            isFocusable = true
            setPadding(0, dp(8), 0, dp(14))
            setOnClickListener { select("home") }
        }
        view.addView(button, 0)
        return view
    }

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

        activity.onBackPressedDispatcher.addCallback(activity, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val view = tabs[currentTab]?.view
                if (view != null && com.elitedarkkaiser.redmagic.ui.InsidePages.back(view)) return
                if (currentTab != "home") select("home") else activity.finish()
            }
        })
        select("home")
    }

    fun refreshBuiltTabs() {
        tabs.values
            .filter { it.built }
            .filterNot { it === tabs["home"] }
            .forEach { state ->
                state.view = replace(
                    state.view,
                    withBack(state.factory()),
                    preserveVisibility = true
                )
            }
    }

    fun select(tabId: String) {
        val resolved = when (tabId) {
            "gaming", "profiles" -> "hardware"
            "system" -> "controls"
            else -> tabId
        }
        val selected = tabs[resolved] ?: return

        if (!selected.built) {
            selected.view = replace(
                selected.view,
                withBack(selected.factory()),
                preserveVisibility = false
            )
            selected.built = true
        }

        tabs.forEach { (id, state) ->
            state.view.visibility =
                if (id == resolved) View.VISIBLE else View.GONE
        }
        currentTab = resolved
        navigation.select(resolved)
        com.elitedarkkaiser.redmagic.ui.InsidePages.back(selected.view)
        when (tabId) {
            "gaming" -> com.elitedarkkaiser.redmagic.ui.InsidePages.open(selected.view, 0)
            "profiles" -> com.elitedarkkaiser.redmagic.ui.InsidePages.open(selected.view, 5)
            "system" -> com.elitedarkkaiser.redmagic.ui.InsidePages.open(selected.view, 0)
        }
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
