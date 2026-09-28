package com.elitedarkkaiser.redmagic

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Game Space-style entry point for controls owned by an active native TGK
 * session. The handle and drawer deliberately remain children of the same
 * lifecycle owner as the saved target overlay; leaving the game, screen-off,
 * editor entry, or runtime recreation therefore removes the complete UI.
 */
internal class GameplaySpaceOverlay(
    private val context: Context,
    private val manager: WindowManager,
    private val profile: NativeTgkProfile,
    private val orientation: NativeTgkOrientation
) {
    private enum class Page(val title: String) {
        PERFORMANCE("PERFORMANCE"),
        TRIGGERS("TRIGGERS"),
        COOLING("COOLING"),
        LIGHTING("LIGHTING"),
        TOOLS("TOOLS")
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var leftHandleIndicator: View? = null
    private var rightHandleIndicator: View? = null
    private var leftHandleRoot: View? = null
    private var rightHandleRoot: View? = null
    private var drawerRoot: View? = null
    private var drawerContent: LinearLayout? = null
    private var currentPage = Page.PERFORMANCE
    private var currentTemperatureC: Float? = null
    private var temperatureSubscription:
        DeviceTemperatureMonitor.Subscription? = null

    fun isAttached(): Boolean {
        return leftHandleIndicator?.isAttachedToWindow == true &&
            rightHandleIndicator?.isAttachedToWindow == true &&
            leftHandleRoot?.isAttachedToWindow == true &&
            rightHandleRoot?.isAttachedToWindow == true
    }

    fun attach(): Boolean {
        if (isAttached()) return true

        val leftIndicator = edgeHandleIndicator(left = true)
        val rightIndicator = edgeHandleIndicator(left = false)
        val left = edgeGestureTarget(left = true, indicator = leftIndicator)
        val right = edgeGestureTarget(left = false, indicator = rightIndicator)

        return runCatching {
            manager.addView(
                leftIndicator,
                edgeHandleIndicatorParams(left = true)
            )
            leftHandleIndicator = leftIndicator
            manager.addView(
                rightIndicator,
                edgeHandleIndicatorParams(left = false)
            )
            rightHandleIndicator = rightIndicator
            manager.addView(left, edgeGestureTargetParams(left = true))
            leftHandleRoot = left
            manager.addView(right, edgeGestureTargetParams(left = false))
            rightHandleRoot = right
            true
        }.getOrElse { error ->
            listOfNotNull(
                leftHandleRoot,
                rightHandleRoot,
                leftHandleIndicator,
                rightHandleIndicator
            ).forEach { view ->
                runCatching { manager.removeViewImmediate(view) }
            }
            leftHandleIndicator = null
            rightHandleIndicator = null
            leftHandleRoot = null
            rightHandleRoot = null
            android.util.Log.e(
                TAG,
                "Could not attach GS edge handles for ${profile.packageName}",
                error
            )
            false
        }
    }

    fun hide() {
        hideDrawer()
        val handles = listOfNotNull(
            leftHandleRoot,
            rightHandleRoot,
            leftHandleIndicator,
            rightHandleIndicator
        )
        leftHandleIndicator = null
        rightHandleIndicator = null
        leftHandleRoot = null
        rightHandleRoot = null
        handles.forEach { handle ->
            runCatching { manager.removeViewImmediate(handle) }
        }
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun showDrawer(openedFromLeft: Boolean) {
        if (drawerRoot != null || !isAttached()) return

        val screen = overlayBounds()

        val backdrop = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(96, 0, 0, 0))
            isClickable = true
            contentDescription = "Game Space drawer background"
            setOnClickListener { hideDrawer() }
        }

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            elevation = dp(20).toFloat()
            setPadding(dp(10), dp(9), dp(10), dp(10))
            background = roundedBackground(
                color = Color.argb(246, 12, 13, 19),
                radius = dp(18).toFloat(),
                strokeColor = Color.argb(230, 164, 27, 58)
            )
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = TextView(context).apply {
            text = "REDMAGIC 11 TOOLBOX"
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
        }
        val close = actionButton("✕") { hideDrawer() }.apply {
            contentDescription = "Close Game Space drawer"
        }
        header.addView(
            heading,
            LinearLayout.LayoutParams(0, dp(34), 1f)
        )
        header.addView(
            close,
            LinearLayout.LayoutParams(dp(34), dp(32))
        )
        panel.addView(header)

        panel.addView(TextView(context).apply {
            text = "${profile.appLabel}  •  ${orientationLabel()}"
            textSize = 10f
            setTextColor(SECONDARY_TEXT)
            maxLines = 1
            setPadding(dp(2), 0, dp(2), dp(7))
        })

        val tabs = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val tabViews = linkedMapOf<Page, TextView>()
        Page.entries.forEach { page ->
            val tab = textControl(
                label = page.shortLabel(),
                contentDescription = "Open ${page.title.lowercase()} controls",
                textSizeSp = 9f
            )
            tabViews[page] = tab
            tabs.addView(
                tab,
                LinearLayout.LayoutParams(0, dp(32), 1f).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                }
            )
        }
        panel.addView(tabs)

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(9), dp(2), 0)
        }
        drawerContent = content
        panel.addView(
            content,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        fun selectPage(page: Page) {
            currentPage = page
            tabViews.forEach { (candidate, view) ->
                view.background = roundedBackground(
                    color = if (candidate == page) {
                        Color.argb(235, 150, 25, 54)
                    } else {
                        Color.argb(220, 29, 31, 42)
                    },
                    radius = dp(9).toFloat(),
                    strokeColor = if (candidate == page) {
                        Color.rgb(255, 65, 90)
                    } else {
                        Color.argb(180, 73, 77, 94)
                    }
                )
            }
            renderCurrentPage()
        }

        tabViews.forEach { (page, view) ->
            view.setOnClickListener { selectPage(page) }
        }

        backdrop.addView(
            panel,
            FrameLayout.LayoutParams(
                min(
                    dp(400),
                    (screen.first - dp(24)).coerceAtLeast(dp(280))
                ),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (if (openedFromLeft) Gravity.START else Gravity.END) or
                    Gravity.CENTER_VERTICAL
            ).apply {
                marginStart = dp(12)
                marginEnd = dp(12)
            }
        )

        val drawerParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val attached = runCatching {
            manager.addView(backdrop, drawerParams)
            drawerRoot = backdrop
            true
        }.getOrElse { error ->
            android.util.Log.e(TAG, "Could not attach GS drawer", error)
            false
        }

        if (!attached) {
            drawerContent = null
            return
        }

        selectPage(currentPage)
        temperatureSubscription = DeviceTemperatureMonitor.subscribe(
            context,
            DeviceTemperatureMonitor.SamplingMode.FOREGROUND
        ) { temperature ->
            currentTemperatureC = temperature
            mainHandler.post {
                if (
                    drawerRoot != null &&
                    currentPage in setOf(Page.PERFORMANCE, Page.COOLING)
                ) {
                    renderCurrentPage()
                }
            }
        }
    }

    private fun hideDrawer() {
        temperatureSubscription?.close()
        temperatureSubscription = null
        currentTemperatureC = null
        drawerContent = null

        val drawer = drawerRoot
        drawerRoot = null
        if (drawer != null) {
            runCatching { manager.removeViewImmediate(drawer) }
        }
    }

    private fun renderCurrentPage() {
        val content = drawerContent ?: return
        content.removeAllViews()
        content.addView(sectionHeading(currentPage.title))

        when (currentPage) {
            Page.PERFORMANCE -> renderPerformance(content)
            Page.TRIGGERS -> renderTriggers(content)
            Page.COOLING -> renderCooling(content)
            Page.LIGHTING -> renderLighting(content)
            Page.TOOLS -> renderTools(content)
        }
    }

    private fun renderPerformance(content: LinearLayout) {
        val displayRate = runCatching {
            context.getSystemService(DisplayManager::class.java)
                ?.getDisplay(Display.DEFAULT_DISPLAY)
                ?.refreshRate
                ?.roundToInt()
        }.getOrNull()
        val fps = VendorFpsMonitor.currentFps(profile.packageName)
        val refresh = RefreshRateStorage
            .getProfile(context, profile.packageName)
            ?.takeIf { it.enabled }
            ?.refreshRateHz
        val touch = TouchTuningStorage
            .getProfile(context, profile.packageName)
            ?.takeIf { it.enabled }
            ?.sampleRateHz
        val mode = PerformanceModeStorage
            .getProfile(context, profile.packageName)
            ?.takeIf { it.enabled }
            ?.mode
            ?.label

        val status = statusCard(listOfNotNull(
            fps?.let { "FPS  $it" },
            temperatureText(),
            displayRate?.let { "DISPLAY  $it Hz" },
            refresh?.let { "REFRESH SET  $it Hz" },
            touch?.let { "TOUCH SET  $it Hz" },
            mode?.let { "MODE  ${it.uppercase()}" }
        ).ifEmpty { listOf("No active performance readings") })
        renderDashboard(content, status, listOf(
            actionRow(
                "OPEN PERFORMANCE CONTROLS",
                "Configure refresh rate, touch response, and performance mode"
            ) { openToolbox("controls") }
        ))
    }

    private fun renderTriggers(content: LinearLayout) {
        val status = statusCard(listOf(
            "L  ${behaviorLabel(profile.effectiveLeftBehavior(), profile.effectiveLeftRapidFireCount())}",
            "R  ${behaviorLabel(profile.effectiveRightBehavior(), profile.effectiveRightRapidFireCount())}",
            "LAYOUT  ${orientationLabel().uppercase()}",
            "Native TGK mapping active"
        ))
        renderDashboard(content, status, listOf(
            actionRow(
                "EDIT L/R TARGETS",
                "Move targets and change trigger behavior"
            ) { openEditor() },
            actionRow(
                "OPEN TRIGGER PROFILES",
                "Manage layouts, haptics, visibility, and profiles"
            ) { openToolbox("controls") }
        ))
    }

    private fun renderCooling(content: LinearLayout) {
        val autoFan = isAutoFanEnabledStorage(context)
        val pump = savedPumpStateStorage(context)
        val status = statusCard(listOfNotNull(
            temperatureText(),
            "AUTO FAN  ${onOff(autoFan)}",
            "FAN CURVE  ${selectedCurveStorage(context).uppercase()}",
            "AUTO PUMP  ${onOff(pump.autoEnabled)}",
            "PUMP PROFILE  ${pump.profile.uppercase()}"
        ))
        val fanAction = actionRow(
            "AUTO FAN: ${onOff(autoFan)}",
            "Toggle automatic temperature-based fan control"
        ) {
            val enabled = !isAutoFanEnabledStorage(context)
            saveAutoFanEnabledStorage(context, enabled)
            if (enabled) {
                HardwareServiceActions.startAutoFan(context)
            } else {
                HardwareServiceActions.stopAutoFan(context)
            }
            renderCurrentPage()
        }
        val pumpAction = actionRow(
            "AUTO PUMP: ${onOff(pump.autoEnabled)}",
            "Toggle automatic liquid-pump control"
        ) {
            val enabled = !savedPumpStateStorage(context).autoEnabled
            saveAutoPumpStateStorage(context, enabled)
            if (enabled) {
                HardwareServiceActions.startAutoPump(context)
            } else {
                HardwareServiceActions.stopAutoPump(context)
            }
            renderCurrentPage()
        }
        renderDashboard(content, status, listOf(
            fanAction,
            pumpAction,
            actionRow(
                "OPEN COOLING CONTROLS",
                "Configure curves, manual fan levels, and pump profiles"
            ) { openToolbox("cooling") }
        ))
    }

    private fun renderLighting(content: LinearLayout) {
        val rgbEnabled = RgbStudioStorage.isEnabled(context)
        val owner = LedOwnership.current(context)
        val status = statusCard(listOf(
            "ACTIVE OWNER  ${owner.name.replace('_', ' ')}",
            "RGB STUDIO  ${RgbStudioStorage.summary(context)}",
            "Game Mode lighting retains priority while active"
        ))
        val rgbAction = actionRow(
            "RGB STUDIO: ${onOff(rgbEnabled)}",
            "Toggle saved RGB Studio animation settings"
        ) {
            val enabled = !RgbStudioStorage.isEnabled(context)
            RgbStudioStorage.setEnabled(context, enabled)
            if (enabled) {
                HardwareServiceActions.startRgbCycle(context)
            } else {
                HardwareServiceActions.stopRgbCycle(context)
            }
            renderCurrentPage()
        }
        renderDashboard(content, status, listOf(
            rgbAction,
            actionRow(
                "OPEN LIGHTING CONTROLS",
                "Configure fan, logo, shoulder, and RGB lighting"
            ) { openToolbox("lighting") }
        ))
    }

    private fun renderTools(content: LinearLayout) {
        val charge = ChargeSeparationController.read(context)
        val chargeText = when {
            charge.enabled == true -> "CHARGE SEPARATION  ON"
            charge.enabled == false -> "CHARGE SEPARATION  OFF"
            else -> "CHARGE SEPARATION  UNAVAILABLE"
        }
        val status = statusCard(listOf(
            chargeText,
            "PROFILE  ${profile.appLabel}",
            "GS follows the active gameplay lifecycle"
        ))
        renderDashboard(content, status, listOf(
            actionRow(
                "OPEN HARDWARE TOOLS",
                "Charge separation, fan hardware, triggers, and slider"
            ) { openToolbox("hardware") },
            actionRow(
                "OPEN TOOLBOX HOME",
                "View device status and active ownership"
            ) { openToolbox("home") }
        ))
    }

    private fun openEditor() {
        hideDrawer()
        val intent = Intent(
            context,
            NativeTgkEditorService::class.java
        ).apply {
            putExtra(
                NativeTgkEditorService.EXTRA_PACKAGE_NAME,
                profile.packageName
            )
            putExtra(
                NativeTgkEditorService.EXTRA_APP_LABEL,
                profile.appLabel
            )
            putExtra(
                NativeTgkEditorService.EXTRA_ORIENTATION,
                orientation.name
            )
            putExtra(
                NativeTgkEditorService.EXTRA_LAUNCH_TARGET,
                false
            )
        }
        runCatching { context.startService(intent) }.onFailure {
            Toast.makeText(
                context,
                "Could not open the trigger editor",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openToolbox(tabId: String) {
        hideDrawer()
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(MainActivity.EXTRA_OPEN_TAB, tabId)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            Toast.makeText(
                context,
                "Could not open Toolbox controls",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun edgeHandleIndicator(left: Boolean): TextView {
        return TextView(context).apply {
            text = if (left) "›" else "‹"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.argb(230, 255, 88, 112))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            alpha = HANDLE_RESTING_ALPHA
            elevation = dp(12).toFloat()
            background = edgeHandleBackground(left)
        }
    }

    private fun edgeGestureTarget(
        left: Boolean,
        indicator: View
    ): View {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f

        return View(context).apply {
            contentDescription = if (left) {
                "Swipe inward beside the left handle to open Game Space"
            } else {
                "Swipe inward beside the right handle to open Game Space"
            }
        }.also { target ->
            target.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        indicator.alpha = HANDLE_ACTIVE_ALPHA
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - downRawX
                        val deltaY = event.rawY - downRawY
                        val inward = if (left) deltaX else -deltaX
                        if (
                            inward > touchSlop &&
                            inward > abs(deltaY) * DIRECTION_BIAS
                        ) {
                            indicator.alpha = HANDLE_ACTIVE_ALPHA
                        }
                        true
                    }

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        val deltaX = event.rawX - downRawX
                        val deltaY = event.rawY - downRawY
                        val inward = if (left) deltaX else -deltaX
                        val shouldOpen =
                            event.actionMasked == MotionEvent.ACTION_UP &&
                                inward >= dp(34) &&
                                inward > abs(deltaY) * DIRECTION_BIAS

                        indicator.animate()
                            .alpha(HANDLE_RESTING_ALPHA)
                            .setDuration(120L)
                            .start()
                        if (shouldOpen) {
                            showDrawer(openedFromLeft = left)
                        }
                        true
                    }

                    else -> false
                }
            }
        }
    }

    private fun edgeHandleIndicatorParams(
        left: Boolean
    ): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            dp(8),
            dp(152),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (left) Gravity.START else Gravity.END) or
                Gravity.TOP
            x = 0
            y = 0
        }
    }

    private fun edgeGestureTargetParams(
        left: Boolean
    ): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            dp(18),
            dp(152),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (left) Gravity.START else Gravity.END) or
                Gravity.TOP
            x = systemBackGestureInset(left)
            y = 0
        }
    }

    private fun systemBackGestureInset(left: Boolean): Int {
        val detected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                manager.currentWindowMetrics.windowInsets
                    .getInsets(WindowInsets.Type.systemGestures())
                    .let { insets ->
                        if (left) insets.left else insets.right
                    }
            }.getOrNull()
        } else {
            null
        }

        return detected
            ?.takeIf { it > 0 }
            ?.coerceIn(dp(16), dp(48))
            ?: dp(24)
    }

    private fun renderDashboard(
        content: LinearLayout,
        status: View,
        actions: List<View>
    ) {
        content.addView(
            status,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        actions.forEach(content::addView)
    }

    private fun statusCard(lines: List<String>): TextView {
        return TextView(context).apply {
            text = lines.joinToString("\n")
            textSize = 11f
            setTextColor(Color.WHITE)
            setLineSpacing(0f, 1.16f)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = roundedBackground(
                color = Color.argb(235, 24, 26, 36),
                radius = dp(11).toFloat(),
                strokeColor = Color.argb(180, 70, 75, 92)
            )
        }
    }

    private fun actionRow(
        title: String,
        subtitle: String,
        action: () -> Unit
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = RippleDrawable(
                ColorStateList.valueOf(Color.argb(90, 255, 255, 255)),
                roundedBackground(
                    color = Color.argb(220, 31, 33, 45),
                    radius = dp(10).toFloat(),
                    strokeColor = Color.argb(180, 81, 85, 103)
                ),
                null
            )
            setOnClickListener { action() }
            addView(TextView(context).apply {
                text = title
                textSize = 10f
                setTextColor(Color.WHITE)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = subtitle
                textSize = 9f
                setTextColor(SECONDARY_TEXT)
                setPadding(0, dp(2), 0, 0)
            })
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }
    }

    private fun actionButton(
        label: String,
        action: () -> Unit
    ): TextView {
        return textControl(label, label, 10f).apply {
            background = RippleDrawable(
                ColorStateList.valueOf(Color.argb(90, 255, 255, 255)),
                roundedBackground(
                    Color.argb(230, 35, 37, 48),
                    dp(10).toFloat(),
                    Color.argb(190, 105, 110, 130)
                ),
                null
            )
            setOnClickListener { action() }
        }
    }

    private fun textControl(
        label: String,
        contentDescription: String,
        textSizeSp: Float
    ): TextView {
        return TextView(context).apply {
            text = label
            textSize = textSizeSp
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            this.contentDescription = contentDescription
            isClickable = true
            isFocusable = true
        }
    }

    private fun sectionHeading(label: String): TextView {
        return TextView(context).apply {
            text = label
            textSize = 10f
            setTextColor(Color.rgb(255, 76, 100))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(2), 0, dp(2), dp(6))
        }
    }

    private fun roundedBackground(
        color: Int,
        radius: Float,
        strokeColor: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
            setStroke(dp(1), strokeColor)
        }
    }

    private fun edgeHandleBackground(left: Boolean): GradientDrawable {
        val radius = dp(9).toFloat()
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.argb(205, 10, 11, 16))
            setStroke(dp(1), Color.argb(220, 196, 38, 67))
            cornerRadii = if (left) {
                floatArrayOf(
                    0f, 0f,
                    radius, radius,
                    radius, radius,
                    0f, 0f
                )
            } else {
                floatArrayOf(
                    radius, radius,
                    0f, 0f,
                    0f, 0f,
                    radius, radius
                )
            }
        }
    }

    private fun Page.shortLabel(): String {
        return when (this) {
            Page.PERFORMANCE -> "PERF"
            Page.TRIGGERS -> "TRIG"
            Page.COOLING -> "COOL"
            Page.LIGHTING -> "LIGHT"
            Page.TOOLS -> "TOOLS"
        }
    }

    private fun behaviorLabel(
        behavior: NativeTgkTriggerBehavior,
        rapidCount: Int
    ): String {
        return when (behavior) {
            NativeTgkTriggerBehavior.LONG_PRESS -> "Long Press"
            NativeTgkTriggerBehavior.RAPID_FIRE ->
                "Rapid Fire ×${rapidCount.coerceAtLeast(2)}"
            NativeTgkTriggerBehavior.SINGLE_TOUCH -> "Single Tap"
        }
    }

    private fun temperatureText(): String? {
        val temperature = currentTemperatureC ?: return null
        return if (isUseFahrenheitStorage(context)) {
            "TEMP  %.1f°F".format((temperature * 9f / 5f) + 32f)
        } else {
            "TEMP  %.1f°C".format(temperature)
        }
    }

    private fun orientationLabel(): String {
        return if (orientation == NativeTgkOrientation.PORTRAIT) {
            "Portrait"
        } else {
            "Landscape"
        }
    }

    private fun onOff(enabled: Boolean): String {
        return if (enabled) "ON" else "OFF"
    }

    private fun overlayBounds(): Pair<Int, Int> {
        return if (
            android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.R
        ) {
            manager.currentWindowMetrics.bounds.let {
                it.width() to it.height()
            }
        } else {
            @Suppress("DEPRECATION")
            context.resources.displayMetrics.let {
                it.widthPixels to it.heightPixels
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density)
            .roundToInt()
    }

    private companion object {
        private const val TAG = "RedmagicGameSpace"
        private const val HANDLE_RESTING_ALPHA = 0.58f
        private const val HANDLE_ACTIVE_ALPHA = 0.95f
        private const val DIRECTION_BIAS = 1.15f
        private val SECONDARY_TEXT = Color.rgb(181, 187, 201)
    }
}
