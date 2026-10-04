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
 * session. The button and drawer deliberately remain children of the same
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

    private var buttonRoot: View? = null
    private var drawerRoot: View? = null
    private var drawerContent: LinearLayout? = null
    private var currentPage = Page.PERFORMANCE
    private var currentTemperatureC: Float? = null
    private var temperatureSubscription:
        DeviceTemperatureMonitor.Subscription? = null

    fun isAttached(): Boolean {
        return buttonRoot?.isAttachedToWindow == true
    }

    fun attach(): Boolean {
        if (isAttached()) return true

        val dragControl = textControl(
            label = "⠿",
            contentDescription = "Drag the Game Space button",
            textSizeSp = 11f
        ).apply {
            setPadding(dp(9), dp(7), dp(7), dp(7))
            background = roundedBackground(
                color = Color.argb(190, 214, 31, 67),
                radius = dp(14).toFloat(),
                strokeColor = Color.argb(220, 255, 95, 120)
            )
        }

        val openAction = textControl(
            label = "GS",
            contentDescription = "Open Game Space controls",
            textSizeSp = 10f
        ).apply {
            setPadding(dp(9), dp(7), dp(11), dp(7))
            setOnClickListener { toggleDrawer() }
        }

        val button = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            alpha = BUTTON_RESTING_ALPHA
            elevation = dp(12).toFloat()
            background = roundedBackground(
                color = Color.argb(235, 14, 15, 21),
                radius = dp(17).toFloat(),
                strokeColor = Color.argb(220, 255, 65, 90)
            )
            addView(dragControl)
            addView(openAction)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        placeButton(button, dragControl, params)

        return runCatching {
            manager.addView(button, params)
            buttonRoot = button
            true
        }.getOrElse { error ->
            android.util.Log.e(
                TAG,
                "Could not attach GS button for ${profile.packageName}",
                error
            )
            false
        }
    }

    fun hide() {
        hideDrawer()
        val button = buttonRoot
        buttonRoot = null
        if (button != null) {
            runCatching { manager.removeViewImmediate(button) }
        }
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun toggleDrawer() {
        if (drawerRoot != null) {
            hideDrawer()
        } else {
            showDrawer()
        }
    }

    private fun showDrawer() {
        if (drawerRoot != null || !isAttached()) return

        val screen = overlayBounds()
        val button = buttonRoot ?: return
        val buttonLocation = IntArray(2)
        button.getLocationOnScreen(buttonLocation)
        val buttonOnRight =
            buttonLocation[0] + button.width / 2f >= screen.first / 2f
        val gestureInsets = horizontalSystemGestureInsets()

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
            contentDescription = "Drag the Game Space drawer"
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
            panel.post { clampDrawerPanel(backdrop, panel) }
        }

        tabViews.forEach { (page, view) ->
            view.setOnClickListener { selectPage(page) }
        }

        backdrop.addView(
            panel,
            FrameLayout.LayoutParams(
                min(
                    dp(400),
                    (
                        screen.first - gestureInsets.first -
                            gestureInsets.second - dp(24)
                    ).coerceAtLeast(dp(280))
                ),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            )
        )

        val drawerParams = WindowManager.LayoutParams(
            (
                screen.first - gestureInsets.first - gestureInsets.second
            ).coerceAtLeast(dp(280)),
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = gestureInsets.first
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
        makeDrawerMovable(
            backdrop = backdrop,
            panel = panel,
            dragSurface = heading,
            defaultOnRight = buttonOnRight
        )
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
            "${NativeTgkRuntimeState.activeBackend() ?: "Trigger backend unavailable"} mapping active"
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

    private fun placeButton(
        button: View,
        dragControl: View,
        params: WindowManager.LayoutParams
    ) {
        button.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val screen = overlayBounds()
        val gestureInsets = horizontalSystemGestureInsets()
        val minimumX = gestureInsets.first
        val maximumX = (
            screen.first - gestureInsets.second - button.measuredWidth
        ).coerceAtLeast(minimumX)
        val minimumY = 0
        val maximumY = (screen.second - button.measuredHeight)
            .coerceAtLeast(minimumY)
        val saved = readOverlayPosition(BUTTON_POSITION_SLOT)

        params.x = saved?.first
            ?.toCoordinate(minimumX, maximumX)
            ?: (maximumX - dp(12)).coerceAtLeast(minimumX)
        params.y = saved?.second
            ?.toCoordinate(minimumY, maximumY)
            ?: dp(
                if (orientation == NativeTgkOrientation.LANDSCAPE) 52 else 12
            ).coerceIn(minimumY, maximumY)

        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false

        dragControl.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (
                        !dragging &&
                        (abs(deltaX) >= touchSlop || abs(deltaY) >= touchSlop)
                    ) {
                        dragging = true
                        hideDrawer()
                        button.alpha = 1f
                    }
                    if (dragging) {
                        params.x = (startX + deltaX.roundToInt())
                            .coerceIn(minimumX, maximumX)
                        params.y = (startY + deltaY.roundToInt())
                            .coerceIn(minimumY, maximumY)
                        runCatching { manager.updateViewLayout(button, params) }
                    }
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    button.alpha = BUTTON_RESTING_ALPHA
                    if (dragging) {
                        saveOverlayPosition(
                            slot = BUTTON_POSITION_SLOT,
                            x = params.x,
                            y = params.y,
                            minimumX = minimumX,
                            maximumX = maximumX,
                            minimumY = minimumY,
                            maximumY = maximumY
                        )
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        view.performClick()
                    }
                    dragging = false
                    true
                }

                else -> false
            }
        }
    }

    private fun makeDrawerMovable(
        backdrop: View,
        panel: View,
        dragSurface: View,
        defaultOnRight: Boolean
    ) {
        panel.post {
            val bounds = drawerMovementBounds(backdrop, panel)
            val saved = readOverlayPosition(DRAWER_POSITION_SLOT)
            panel.x = saved?.first
                ?.toCoordinate(bounds[0], bounds[1])
                ?.toFloat()
                ?: if (defaultOnRight) {
                    bounds[1].toFloat()
                } else {
                    bounds[0].toFloat()
                }
            panel.y = saved?.second
                ?.toCoordinate(bounds[2], bounds[3])
                ?.toFloat()
                ?: ((bounds[2] + bounds[3]) / 2f)
        }

        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false

        dragSurface.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = panel.x
                    startY = panel.y
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (
                        !dragging &&
                        (abs(deltaX) >= touchSlop || abs(deltaY) >= touchSlop)
                    ) {
                        dragging = true
                        panel.alpha = 0.94f
                    }
                    if (dragging) {
                        val bounds = drawerMovementBounds(backdrop, panel)
                        panel.x = (startX + deltaX)
                            .coerceIn(bounds[0].toFloat(), bounds[1].toFloat())
                        panel.y = (startY + deltaY)
                            .coerceIn(bounds[2].toFloat(), bounds[3].toFloat())
                    }
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    panel.alpha = 1f
                    if (dragging) {
                        val bounds = drawerMovementBounds(backdrop, panel)
                        saveOverlayPosition(
                            slot = DRAWER_POSITION_SLOT,
                            x = panel.x.roundToInt(),
                            y = panel.y.roundToInt(),
                            minimumX = bounds[0],
                            maximumX = bounds[1],
                            minimumY = bounds[2],
                            maximumY = bounds[3]
                        )
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        view.performClick()
                    }
                    dragging = false
                    true
                }

                else -> false
            }
        }
    }

    private fun clampDrawerPanel(backdrop: View, panel: View) {
        if (backdrop.width <= 0 || panel.width <= 0) return
        val bounds = drawerMovementBounds(backdrop, panel)
        panel.x = panel.x.coerceIn(
            bounds[0].toFloat(),
            bounds[1].toFloat()
        )
        panel.y = panel.y.coerceIn(
            bounds[2].toFloat(),
            bounds[3].toFloat()
        )
    }

    private fun drawerMovementBounds(backdrop: View, panel: View): IntArray {
        val margin = dp(12)
        val maximumX = (backdrop.width - panel.width - margin)
            .coerceAtLeast(margin)
        val maximumY = (backdrop.height - panel.height - margin)
            .coerceAtLeast(margin)
        return intArrayOf(margin, maximumX, margin, maximumY)
    }

    private fun horizontalSystemGestureInsets(): Pair<Int, Int> {
        val detected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                manager.currentWindowMetrics.windowInsets
                    .getInsets(WindowInsets.Type.systemGestures())
            }.getOrNull()
        } else {
            null
        }

        fun safeInset(value: Int?): Int {
            return value
                ?.takeIf { it > 0 }
                ?.coerceIn(dp(16), dp(48))
                ?: dp(24)
        }

        return safeInset(detected?.left) to safeInset(detected?.right)
    }

    private fun readOverlayPosition(slot: String): Pair<Float, Float>? {
        val prefix = overlayPositionKey(slot)
        val prefs = context.getSharedPreferences(
            POSITION_PREFS,
            Context.MODE_PRIVATE
        )
        if (!prefs.contains("${prefix}_x") || !prefs.contains("${prefix}_y")) {
            return null
        }

        val x = prefs.getFloat("${prefix}_x", -1f)
        val y = prefs.getFloat("${prefix}_y", -1f)
        return (x to y).takeIf {
            it.first in 0f..1f && it.second in 0f..1f
        }
    }

    private fun saveOverlayPosition(
        slot: String,
        x: Int,
        y: Int,
        minimumX: Int,
        maximumX: Int,
        minimumY: Int,
        maximumY: Int
    ) {
        val rangeX = (maximumX - minimumX).coerceAtLeast(1)
        val rangeY = (maximumY - minimumY).coerceAtLeast(1)
        val prefix = overlayPositionKey(slot)

        context.getSharedPreferences(POSITION_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(
                "${prefix}_x",
                (x.coerceIn(minimumX, maximumX) - minimumX).toFloat() /
                    rangeX.toFloat()
            )
            .putFloat(
                "${prefix}_y",
                (y.coerceIn(minimumY, maximumY) - minimumY).toFloat() /
                    rangeY.toFloat()
            )
            .apply()
    }

    private fun overlayPositionKey(slot: String): String {
        return "${profile.packageName}_${orientation.name.lowercase()}_$slot"
    }

    private fun Float.toCoordinate(minimum: Int, maximum: Int): Int {
        val range = (maximum - minimum).coerceAtLeast(0)
        return (minimum + this.coerceIn(0f, 1f) * range)
            .roundToInt()
            .coerceIn(minimum, maximum)
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
        private const val BUTTON_RESTING_ALPHA = 0.72f
        private const val POSITION_PREFS = "gameplay_space_overlay_positions"
        private const val BUTTON_POSITION_SLOT = "button"
        private const val DRAWER_POSITION_SLOT = "drawer"
        private val SECONDARY_TEXT = Color.rgb(181, 187, 201)
    }
}
