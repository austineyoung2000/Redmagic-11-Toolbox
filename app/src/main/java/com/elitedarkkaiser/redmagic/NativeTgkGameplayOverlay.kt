package com.elitedarkkaiser.redmagic

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/**
 * Displays saved TGK targets and owns the Game Space entry point during
 * gameplay. Both windows are tied to the same foreground lifecycle as TGK.
 */
object NativeTgkGameplayOverlay {
    private const val TAG = "RedmagicGameplayOverlay"

    private val mainHandler = Handler(Looper.getMainLooper())

    /* Ignore main-thread work posted by an older foreground session. */
    private val requestGeneration = AtomicLong(0L)

    @Volatile
    private var windowManager: WindowManager? = null

    @Volatile
    private var overlayRoot: View? = null

    @Volatile
    private var gameSpaceOverlay: GameplaySpaceOverlay? = null

    @Volatile
    private var ownerPackageName: String? = null

    @Volatile
    private var ownerOrientation: NativeTgkOrientation? = null

    fun isVisibleFor(
        packageName: String,
        orientation: NativeTgkOrientation
    ): Boolean {
        val targets = overlayRoot
        val gameSpace = gameSpaceOverlay

        return ownerPackageName == packageName &&
            ownerOrientation == orientation &&
            targets?.isAttachedToWindow == true &&
            gameSpace?.isAttached() == true
    }

    fun show(
        context: Context,
        profile: NativeTgkProfile,
        mapping: NativeTgkOrientationMapping,
        orientation: NativeTgkOrientation,
        displayWidth: Int,
        displayHeight: Int
    ) {
        val appContext = context.applicationContext
        val generation = requestGeneration.incrementAndGet()

        mainHandler.post {
            runCatching {
                if (requestGeneration.get() != generation) {
                    return@runCatching
                }

                hideOnMainThread()

                if (
                    !mapping.isComplete() ||
                    !Settings.canDrawOverlays(appContext)
                ) {
                    return@runCatching
                }

                val manager = appContext.getSystemService(
                    WindowManager::class.java
                ) ?: return@runCatching

                val root = FrameLayout(appContext).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                    isClickable = false
                    isFocusable = false
                }

                if (profile.showSavedTargets) {
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
                }

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
                    if (requestGeneration.get() != generation) {
                        false
                    } else {
                        manager.addView(root, params)
                        true
                    }
                }.onSuccess { targetAdded ->
                    if (targetAdded) {
                        windowManager = manager
                        overlayRoot = root

                        val gameSpace = GameplaySpaceOverlay(
                            context = appContext,
                            manager = manager,
                            profile = profile,
                            orientation = orientation
                        )
                        if (gameSpace.attach()) {
                            gameSpaceOverlay = gameSpace
                            ownerPackageName = profile.packageName
                            ownerOrientation = orientation
                        } else {
                            hideOnMainThread()
                        }
                    }
                }.onFailure { error ->
                    Log.e(
                        TAG,
                        "Could not attach saved targets for " +
                            profile.packageName,
                        error
                    )
                }
            }.onFailure { error ->
                hideOnMainThread()
                Log.e(
                    TAG,
                    "Contained gameplay overlay failure for " +
                        profile.packageName,
                    error
                )
            }
        }
    }

    fun hide() {
        val generation = requestGeneration.incrementAndGet()

        mainHandler.post {
            if (requestGeneration.get() == generation) {
                hideOnMainThread()
            }
        }
    }

    private fun hideOnMainThread() {
        val root = overlayRoot
        val gameSpace = gameSpaceOverlay
        val manager = windowManager

        overlayRoot = null
        gameSpaceOverlay = null
        windowManager = null
        ownerPackageName = null
        ownerOrientation = null

        if (root != null && manager != null) {
            runCatching {
                manager.removeViewImmediate(root)
            }
        }

        gameSpace?.hide()
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
