package com.elitedarkkaiser.redmagic

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

/**
 * Displays saved TGK targets during gameplay without accepting input.
 * The window is tied to the same foreground lifecycle as native TGK.
 */
object NativeTgkGameplayOverlay {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var overlayRoot: View? = null
    private var editRoot: View? = null

    fun show(
        context: Context,
        profile: NativeTgkProfile,
        mapping: NativeTgkOrientationMapping,
        orientation: NativeTgkOrientation,
        displayWidth: Int,
        displayHeight: Int
    ) {
        val appContext = context.applicationContext

        mainHandler.post {
            hideOnMainThread()

            if (
                !profile.showSavedTargets ||
                !mapping.isComplete() ||
                !Settings.canDrawOverlays(appContext)
            ) {
                return@post
            }

            val manager = appContext.getSystemService(
                WindowManager::class.java
            ) ?: return@post

            val root = FrameLayout(appContext).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isClickable = false
                isFocusable = false
            }

            addTarget(
                context = appContext,
                root = root,
                label = "L",
                color = Color.rgb(215, 45, 55),
                rect = mapping.left!!,
                displayWidth = displayWidth,
                displayHeight = displayHeight,
                opacityPercent =
                    profile.savedTargetOpacityPercent
            )

            addTarget(
                context = appContext,
                root = root,
                label = "R",
                color = Color.rgb(30, 120, 230),
                rect = mapping.right!!,
                displayWidth = displayWidth,
                displayHeight = displayHeight,
                opacityPercent =
                    profile.savedTargetOpacityPercent
            )

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START

                if (
                    android.os.Build.VERSION.SDK_INT >=
                    android.os.Build.VERSION_CODES.P
                ) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams
                            .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            runCatching {
                manager.addView(root, params)
            }.onSuccess {
                windowManager = manager
                overlayRoot = root

                showEditControl(
                    context = appContext,
                    manager = manager,
                    profile = profile,
                    orientation = orientation
                )
            }
        }
    }

    fun hide() {
        mainHandler.post {
            hideOnMainThread()
        }
    }

    private fun hideOnMainThread() {
        val root = overlayRoot
        val edit = editRoot
        val manager = windowManager

        overlayRoot = null
        editRoot = null
        windowManager = null

        if (root != null && manager != null) {
            runCatching {
                manager.removeViewImmediate(root)
            }
        }

        if (edit != null && manager != null) {
            runCatching {
                manager.removeViewImmediate(edit)
            }
        }
    }

    private fun showEditControl(
        context: Context,
        manager: WindowManager,
        profile: NativeTgkProfile,
        orientation: NativeTgkOrientation
    ) {
        val dragHandle = TextView(context).apply {
            text = "⠿"
            contentDescription = "Drag to move the edit control"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(
                dp(context, 9),
                dp(context, 7),
                dp(context, 7),
                dp(context, 7)
            )
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 13).toFloat()
                setColor(Color.argb(150, 255, 65, 90))
            }
        }

        val editAction = TextView(context).apply {
            text = "EDIT L/R"
            contentDescription = "Edit L and R trigger targets"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(
                dp(context, 8),
                dp(context, 7),
                dp(context, 10),
                dp(context, 7)
            )
            setOnClickListener {
                openEditor(
                    context = context,
                    profile = profile,
                    orientation = orientation
                )
            }
        }

        val edit = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            alpha = 0.48f
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 16).toFloat()
                setColor(Color.argb(220, 20, 20, 24))
                setStroke(
                    dp(context, 1),
                    Color.argb(210, 255, 255, 255)
                )
            }
            addView(dragHandle)
            addView(editAction)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = dp(context, 12)
        }

        placeEditControl(
            context = context,
            manager = manager,
            edit = edit,
            dragHandle = dragHandle,
            params = params,
            packageName = profile.packageName,
            orientation = orientation
        )

        runCatching {
            manager.addView(edit, params)
        }.onSuccess {
            editRoot = edit
        }
    }

    private fun openEditor(
        context: Context,
        profile: NativeTgkProfile,
        orientation: NativeTgkOrientation
    ) {
        val editorIntent = Intent(
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

        runCatching {
            context.startService(editorIntent)
        }.onFailure {
            Toast.makeText(
                context,
                "Could not reopen the trigger editor",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun placeEditControl(
        context: Context,
        manager: WindowManager,
        edit: View,
        dragHandle: View,
        params: WindowManager.LayoutParams,
        packageName: String,
        orientation: NativeTgkOrientation
    ) {
        edit.measure(
            View.MeasureSpec.makeMeasureSpec(
                0,
                View.MeasureSpec.UNSPECIFIED
            ),
            View.MeasureSpec.makeMeasureSpec(
                0,
                View.MeasureSpec.UNSPECIFIED
            )
        )

        val screen = overlayBounds(context, manager)
        val maxX = (screen.first - edit.measuredWidth).coerceAtLeast(0)
        val maxY = (screen.second - edit.measuredHeight).coerceAtLeast(0)
        val saved = GameplayEditPositionStorage.read(
            context,
            packageName,
            orientation
        )

        params.x = saved?.xFor(maxX)
            ?: (maxX - dp(context, 12)).coerceAtLeast(0)
        params.y = saved?.yFor(maxY)
            ?: dp(
                context,
                if (
                    orientation == NativeTgkOrientation.LANDSCAPE
                ) 52 else 12
            ).coerceAtMost(maxY)

        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false

        dragHandle.setOnTouchListener { view, event ->
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
                        (
                            kotlin.math.abs(deltaX) >= touchSlop ||
                                kotlin.math.abs(deltaY) >= touchSlop
                            )
                    ) {
                        dragging = true
                    }

                    if (dragging) {
                        params.x = (startX + deltaX.roundToInt())
                            .coerceIn(0, maxX)
                        params.y = (startY + deltaY.roundToInt())
                            .coerceIn(0, maxY)
                        runCatching {
                            manager.updateViewLayout(edit, params)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        GameplayEditPositionStorage.save(
                            context = context,
                            packageName = packageName,
                            orientation = orientation,
                            x = params.x,
                            y = params.y,
                            maxX = maxX,
                            maxY = maxY
                        )
                    } else {
                        view.performClick()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        GameplayEditPositionStorage.save(
                            context = context,
                            packageName = packageName,
                            orientation = orientation,
                            x = params.x,
                            y = params.y,
                            maxX = maxX,
                            maxY = maxY
                        )
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun overlayBounds(
        context: Context,
        manager: WindowManager
    ): Pair<Int, Int> {
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

    private fun addTarget(
        context: Context,
        root: FrameLayout,
        label: String,
        color: Int,
        rect: NativeTgkRect,
        displayWidth: Int,
        displayHeight: Int,
        opacityPercent: Int
    ) {
        val scaled = rect.scaledTo(
            displayWidth,
            displayHeight
        )
        val size = dp(context, 58)
        val centerX = (scaled[0] + scaled[2]) / 2
        val centerY = (scaled[1] + scaled[3]) / 2

        val target = TextView(context).apply {
            text = label
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            alpha = (
                opacityPercent.coerceIn(5, 30) / 100f
                )
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(
                    dp(context, 1),
                    Color.WHITE
                )
            }
            isClickable = false
            isFocusable = false
        }

        root.addView(
            target,
            FrameLayout.LayoutParams(size, size).apply {
                leftMargin = (
                    centerX - size / 2
                    ).coerceIn(
                        0,
                        (displayWidth - size).coerceAtLeast(0)
                    )
                topMargin = (
                    centerY - size / 2
                    ).coerceIn(
                        0,
                        (displayHeight - size).coerceAtLeast(0)
                    )
            }
        )
    }

    private fun dp(
        context: Context,
        value: Int
    ): Int {
        return (
            value * context.resources.displayMetrics.density
            ).roundToInt()
    }
}
