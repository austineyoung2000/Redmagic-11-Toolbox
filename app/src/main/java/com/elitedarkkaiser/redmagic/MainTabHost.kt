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
    private var dashboardEntry = "home"
    private var magicKeyReturnToHardware = false

    private fun withBack(view: LinearLayout, fallbackTitle: String): LinearLayout {
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(16))
        }
        val back = android.widget.TextView(activity).apply {
            text = "‹"
            textSize = 32f
            setTextColor(com.elitedarkkaiser.redmagic.ui.AppTheme.textPrimary)
            contentDescription = "Back"
            isClickable = true
            isFocusable = true
            setOnClickListener { if (!handleBack()) select("home") }
        }
        val title = android.widget.TextView(activity).apply {
            text = fallbackTitle
            textSize = 24f
            setTypeface(com.elitedarkkaiser.redmagic.ui.AppTheme.appTypeface, android.graphics.Typeface.BOLD)
            setTextColor(com.elitedarkkaiser.redmagic.ui.AppTheme.textPrimary)
        }
        header.addView(back, LinearLayout.LayoutParams(dp(44), dp(48)))
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        com.elitedarkkaiser.redmagic.ui.InsidePages.bindHeader(view) { title.text = it }
        view.addView(header, 0)
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

        select("home")
    }

    fun openControlsPage(index: Int) {
        tabs["controls"]?.view?.let { com.elitedarkkaiser.redmagic.ui.InsidePages.open(it, index) }
    }

    fun openMagicKeyFromHardware() {
        select("controls")
        openControlsPage(1)
        magicKeyReturnToHardware = true
    }

    fun handleBack(): Boolean {
        if (magicKeyReturnToHardware && currentTab == "controls") {
            select("hardware")
            return true
        }
        if (dashboardEntry == "gaming" || dashboardEntry == "profiles") {
            select("home")
            return true
        }
        val view = tabs[currentTab]?.view
        if (view != null && com.elitedarkkaiser.redmagic.ui.InsidePages.back(view)) return true
        if (currentTab == "home") return false
        select("home")
        return true
    }

    fun refreshBuiltTabs() {
        tabs.filter { (id, state) -> id != "home" && state.built }
            .forEach { (id, state) ->
                state.view = replace(
                    state.view,
                    withBack(state.factory(), id.replaceFirstChar { it.uppercase() }),
                    preserveVisibility = true
                )
            }
    }

    fun select(tabId: String) {
        magicKeyReturnToHardware = false
        val resolved = when (tabId) {
            "gaming", "profiles" -> "hardware"
            "system" -> "controls"
            else -> tabId
        }
        val selected = tabs[resolved] ?: return

        if (!selected.built) {
            selected.view = replace(
                selected.view,
                withBack(selected.factory(), resolved.replaceFirstChar { it.uppercase() }),
                preserveVisibility = false
            )
            selected.built = true
        }

        tabs.forEach { (id, state) ->
            state.view.visibility =
                if (id == resolved) View.VISIBLE else View.GONE
        }
        currentTab = resolved
        dashboardEntry = tabId
        navigation.select(resolved)
        com.elitedarkkaiser.redmagic.ui.InsidePages.back(selected.view)
        if (resolved == "lighting") com.elitedarkkaiser.redmagic.ui.InsidePages.refresh(selected.view)
        when (tabId) {
            "gaming" -> com.elitedarkkaiser.redmagic.ui.InsidePages.open(selected.view, 0)
            "profiles" -> com.elitedarkkaiser.redmagic.ui.InsidePages.open(selected.view, 6)
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
