package com.elitedarkkaiser.redmagic

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.roundToInt

object RefreshRateOverlay {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var overlayView: TextView? = null
    private var appContext: Context? = null
    private var foregroundPackage: String? = null

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateText()
            if (overlayView != null) {
                mainHandler.postDelayed(this, UPDATE_INTERVAL_MS)
            }
        }
    }

    fun show(
        context: Context,
        packageName: String
    ) {
        val contextForApp = context.applicationContext
        mainHandler.post {
            if (!Settings.canDrawOverlays(contextForApp)) {
                hideOnMainThread()
                return@post
            }

            appContext = contextForApp
            foregroundPackage = packageName
            if (overlayView == null) {
                val manager = contextForApp.getSystemService(
                    Context.WINDOW_SERVICE
                ) as? WindowManager ?: return@post
                val view = buildView(contextForApp)
                val added = runCatching {
                    manager.addView(view, layoutParams(contextForApp))
                }.isSuccess
                if (!added) return@post

                windowManager = manager
                overlayView = view
            }

            VendorFpsMonitor.start(packageName)
            PerformanceOverlayTelemetry.start(contextForApp)
            overlayView?.visibility = View.VISIBLE
            mainHandler.removeCallbacks(updateRunnable)
            updateRunnable.run()
        }
    }

    fun hide() {
        mainHandler.post { hideOnMainThread() }
    }

    fun refresh() {
        mainHandler.post {
            if (overlayView != null) {
                updateText()
            }
        }
    }

    private fun hideOnMainThread() {
        mainHandler.removeCallbacks(updateRunnable)
        val view = overlayView
        val manager = windowManager
        overlayView = null
        windowManager = null
        appContext = null
        foregroundPackage = null
        VendorFpsMonitor.stop()
        PerformanceOverlayTelemetry.stop()
        if (view != null && manager != null) {
            runCatching { manager.removeViewImmediate(view) }
        }
    }

    private fun updateText() {
        val context = appContext ?: return
        val manager = context.getSystemService(
            Context.DISPLAY_SERVICE
        ) as? DisplayManager ?: return
        val display = manager.getDisplay(Display.DEFAULT_DISPLAY) ?: return
        val displayLine =
            "DISPLAY ${display.refreshRate.roundToInt()} Hz"
        val touchLine = foregroundPackage
            ?.let { TouchTuningStorage.getProfile(context, it) }
            ?.takeIf { it.enabled }
            ?.let { "TOUCH ${it.sampleRateHz} Hz" }
        val performanceLine = foregroundPackage
            ?.let { PerformanceModeCoordinator.activeLabel(it) }
            ?.let { "MODE ${it.uppercase()}" }
        val fpsLine = foregroundPackage
            ?.let { VendorFpsMonitor.currentFps(it) }
            ?.let { "FPS $it" }
        val telemetry = PerformanceOverlayTelemetry.snapshot()
        val temperatureLine = telemetry.temperatureC?.let {
            if (isUseFahrenheitStorage(context)) {
                "TEMP %.1f°F".format((it * 9f / 5f) + 32f)
            } else {
                "TEMP %.1f°C".format(it)
            }
        }
        val fanLine = telemetry.fanRpm
            ?.takeIf { it >= 0 }
            ?.let { "FAN $it RPM" }

        val readings = listOfNotNull(
            fpsLine,
            touchLine,
            temperatureLine,
            displayLine,
            performanceLine,
            fanLine
        )
        val landscape = context.resources.configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE

        overlayView?.apply {
            text = readings.joinToString(
                if (landscape) "  •  " else "\n"
            )
            setSingleLine(landscape)
            ellipsize = if (landscape) {
                TextUtils.TruncateAt.END
            } else {
                null
            }
        }
    }

    private fun buildView(context: Context): TextView {
        return TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            alpha = 0.78f
            setPadding(
                dp(context, 14),
                dp(context, 5),
                dp(context, 14),
                dp(context, 5)
            )
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 14).toFloat()
                setColor(Color.argb(190, 20, 20, 24))
                setStroke(dp(context, 1), Color.argb(150, 255, 65, 90))
            }
        }
    }

    private fun layoutParams(
        context: Context
    ): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dp(context, 12)
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density)
            .roundToInt()
    }

    private const val UPDATE_INTERVAL_MS = 1_000L
}
