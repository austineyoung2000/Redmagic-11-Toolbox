package com.elitedarkkaiser.redmagic

import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView

internal object MainUiLauncher {
    data class Result(
        val homeTab: LinearLayout,
        val coolingTab: LinearLayout,
        val controlsTab: LinearLayout,
        val hardwareTab: LinearLayout,
        val lightingTab: LinearLayout
    )

    fun launch(
        activity: MainActivity,
        topInset: Int,
        bgColor: Int,
        dp: (Int) -> Int,
        createHomeTab: () -> LinearLayout,
        createCoolingTab: () -> LinearLayout,
        createControlsTab: () -> LinearLayout,
        createHardwareTab: () -> LinearLayout,
        createLightingTab: () -> LinearLayout,
        bottomNavBar: () -> LinearLayout
    ): Result {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }

        val contentFrame = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), topInset + dp(10), dp(16), dp(24))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val contentScroll = ScrollView(activity).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            addView(contentFrame)
        }

        val homeTab = createHomeTab()
        val coolingTab = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val controlsTab = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val hardwareTab = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val lightingTab = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

        contentFrame.addView(homeTab)
        contentFrame.addView(coolingTab)
        contentFrame.addView(controlsTab)
        contentFrame.addView(hardwareTab)
        contentFrame.addView(lightingTab)

        val navWrap = LinearLayout(activity).apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), 0, dp(18), dp(18))
            addView(bottomNavBar())
        }

        root.addView(contentScroll)

        activity.setContentView(root)

        return Result(
            homeTab = homeTab,
            coolingTab = coolingTab,
            controlsTab = controlsTab,
            hardwareTab = hardwareTab,
            lightingTab = lightingTab
        )
    }
}
