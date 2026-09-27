package com.elitedarkkaiser.redmagic

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.roundToInt

object NativeTgkEditorRuntime {
    private val editing = AtomicBoolean(false)

    @Volatile
    private var targetPackageName: String? = null

    @Volatile
    private var startedAtElapsed = 0L

    fun isEditing(): Boolean {
        return editing.get()
    }

    fun shouldStopForForeground(
        packageName: String
    ): Boolean {
        val target = targetPackageName ?: return false
        return editing.get() &&
            packageName != target &&
            SystemClock.elapsedRealtime() - startedAtElapsed >=
            FOREGROUND_LAUNCH_GRACE_MS
    }

    internal fun begin(packageName: String) {
        targetPackageName = packageName
        startedAtElapsed = SystemClock.elapsedRealtime()
        editing.set(true)
    }

    internal fun end() {
        editing.set(false)
        targetPackageName = null
        startedAtElapsed = 0L
    }

    private const val FOREGROUND_LAUNCH_GRACE_MS = 3_000L
}

class NativeTgkEditorService : Service() {

    companion object {
        const val EXTRA_PACKAGE_NAME =
            "native_tgk_editor_package"
        const val EXTRA_APP_LABEL =
            "native_tgk_editor_app_label"
        const val EXTRA_ORIENTATION =
            "native_tgk_editor_orientation"
        const val EXTRA_LAUNCH_TARGET =
            "native_tgk_editor_launch_target"

        private const val ORIENTATION_RETRY_MS = 250L
        private const val MAX_ORIENTATION_RETRIES = 120
    }

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var windowManager: WindowManager

    private var editorRoot: FrameLayout? = null
    private var leftTarget: TextView? = null
    private var rightTarget: TextView? = null

    private var targetPackage = ""
    private var targetLabel = ""
    private var requestedOrientation =
        NativeTgkOrientation.LANDSCAPE

    private var finishing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(
            WindowManager::class.java
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        val packageName = intent
            ?.getStringExtra(EXTRA_PACKAGE_NAME)
            .orEmpty()

        val appLabel = intent
            ?.getStringExtra(EXTRA_APP_LABEL)
            .orEmpty()

        val orientationName = intent
            ?.getStringExtra(EXTRA_ORIENTATION)
            .orEmpty()

        val orientation = runCatching {
            NativeTgkOrientation.valueOf(
                orientationName
            )
        }.getOrNull()

        if (
            packageName.isBlank() ||
            appLabel.isBlank() ||
            orientation == null
        ) {
            Toast.makeText(
                this,
                "Invalid game trigger editor request",
                Toast.LENGTH_LONG
            ).show()
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Allow display over other apps before editing trigger targets",
                Toast.LENGTH_LONG
            ).show()
            stopSelf(startId)
            return START_NOT_STICKY
        }

        targetPackage = packageName
        targetLabel = appLabel
        requestedOrientation = orientation
        finishing = false

        NativeTgkEditorRuntime.begin(targetPackage)
        NativeTgkRuntimeState.clear()

        Thread(
            {
                NativeTgkCoordinator.disable(
                    applicationContext,
                    "target editor opened"
                )
            },
            "RedMagicTgkEditorCleanup"
        ).start()

        val launchTarget = intent?.getBooleanExtra(
            EXTRA_LAUNCH_TARGET,
            true
        ) ?: true

        if (launchTarget) {
            launchTargetApp()
        }

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(
            {
                waitForRequestedOrientation(0)
            },
            if (launchTarget) 700L else 150L
        )

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeEditorOverlay()
        NativeTgkEditorRuntime.end()
        super.onDestroy()
    }

    private fun launchTargetApp() {
        val launchIntent = packageManager
            .getLaunchIntentForPackage(targetPackage)

        if (launchIntent == null) {
            Toast.makeText(
                this,
                "Could not launch $targetLabel",
                Toast.LENGTH_LONG
            ).show()
            finishWithoutMapping()
            return
        }

        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )

        runCatching {
            startActivity(launchIntent)
        }.onFailure {
            Toast.makeText(
                this,
                "Could not launch $targetLabel",
                Toast.LENGTH_LONG
            ).show()
            finishWithoutMapping()
        }
    }

    private fun waitForRequestedOrientation(
        attempt: Int
    ) {
        if (finishing) {
            return
        }

        val current =
            NativeTgkCoordinator.currentOrientation(this)

        if (current == requestedOrientation) {
            showEditorOverlay()
            return
        }

        if (attempt == 0) {
            val label = when (requestedOrientation) {
                NativeTgkOrientation.PORTRAIT ->
                    "portrait"
                NativeTgkOrientation.LANDSCAPE ->
                    "landscape"
            }

            Toast.makeText(
                this,
                "Rotate the device to $label to place L and R",
                Toast.LENGTH_LONG
            ).show()
        }

        if (attempt >= MAX_ORIENTATION_RETRIES) {
            Toast.makeText(
                this,
                "Trigger editor closed because the requested orientation was not entered",
                Toast.LENGTH_LONG
            ).show()
            finishWithoutMapping()
            return
        }

        handler.postDelayed(
            {
                waitForRequestedOrientation(attempt + 1)
            },
            ORIENTATION_RETRY_MS
        )
    }

    private fun showEditorOverlay() {
        if (editorRoot != null || finishing) {
            return
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
        }

        val instruction = TextView(this).apply {
            text = buildString {
                append("Place L and R for ")
                append(targetLabel)
                append(" • ")
                append(
                    when (requestedOrientation) {
                        NativeTgkOrientation.PORTRAIT ->
                            "Portrait"
                        NativeTgkOrientation.LANDSCAPE ->
                            "Landscape"
                    }
                )
            }
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(
                dp(14),
                dp(10),
                dp(14),
                dp(10)
            )
        }

        val cancel = Button(this).apply {
            text = "Cancel"
            isAllCaps = false
            setOnClickListener {
                cancelEditing()
            }
        }

        val save = Button(this).apply {
            text = "Save targets"
            isAllCaps = false
            setOnClickListener {
                saveTargets()
            }
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                dp(8),
                dp(6),
                dp(8),
                dp(6)
            )
            background = roundedBackground(
                Color.argb(230, 24, 24, 28),
                dp(18).toFloat()
            )

            addView(
                cancel,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0.8f
                )
            )
            addView(
                instruction,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    2f
                )
            )
            addView(
                save,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
        }

        root.addView(
            controls,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ).apply {
                leftMargin = dp(14)
                rightMargin = dp(14)
                topMargin = dp(18)
            }
        )

        val targetSize = dp(58)

        val left = createTarget(
            label = "L",
            color = Color.rgb(215, 45, 55),
            targetSize = targetSize,
            root = root
        )

        val right = createTarget(
            label = "R",
            color = Color.rgb(30, 120, 230),
            targetSize = targetSize,
            root = root
        )

        root.addView(
            left,
            FrameLayout.LayoutParams(
                targetSize,
                targetSize
            )
        )

        root.addView(
            right,
            FrameLayout.LayoutParams(
                targetSize,
                targetSize
            )
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
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

        try {
            windowManager.addView(root, params)
        } catch (error: Throwable) {
            Toast.makeText(
                this,
                "Could not display the trigger editor",
                Toast.LENGTH_LONG
            ).show()
            finishWithoutMapping()
            return
        }

        editorRoot = root
        leftTarget = left
        rightTarget = right

        root.post {
            restoreOrPlaceDefaults(
                root,
                left,
                right
            )
        }
    }

    private fun createTarget(
        label: String,
        color: Int,
        targetSize: Int,
        root: FrameLayout
    ): TextView {
        val target = TextView(this).apply {
            text = label
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = roundedBackground(
                color,
                targetSize / 2f
            )
            elevation = dp(10).toFloat()
        }

        var offsetX = 0f
        var offsetY = 0f

        target.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    offsetX = view.x - event.rawX
                    offsetY = view.y - event.rawY
                    view.elevation = dp(18).toFloat()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val maximumX = (
                        root.width - view.width
                        ).coerceAtLeast(0)
                    val maximumY = (
                        root.height - view.height
                        ).coerceAtLeast(0)

                    view.x = (
                        event.rawX + offsetX
                        ).coerceIn(
                            0f,
                            maximumX.toFloat()
                        )

                    view.y = (
                        event.rawY + offsetY
                        ).coerceIn(
                            0f,
                            maximumY.toFloat()
                        )
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    view.elevation = dp(10).toFloat()
                    true
                }

                else -> false
            }
        }

        return target
    }

    private fun restoreOrPlaceDefaults(
        root: FrameLayout,
        left: View,
        right: View
    ) {
        val mapping = NativeTgkStorage
            .getProfile(this, targetPackage)
            ?.mappingFor(requestedOrientation)

        placeTarget(
            view = left,
            rect = mapping?.left,
            rootWidth = root.width,
            rootHeight = root.height,
            defaultCenterX = root.width * 0.35f,
            defaultCenterY = root.height * 0.58f
        )

        placeTarget(
            view = right,
            rect = mapping?.right,
            rootWidth = root.width,
            rootHeight = root.height,
            defaultCenterX = root.width * 0.65f,
            defaultCenterY = root.height * 0.58f
        )
    }

    private fun placeTarget(
        view: View,
        rect: NativeTgkRect?,
        rootWidth: Int,
        rootHeight: Int,
        defaultCenterX: Float,
        defaultCenterY: Float
    ) {
        val scaled = rect?.takeIf {
            it.isValid()
        }?.scaledTo(
            rootWidth,
            rootHeight
        )

        val centerX = if (scaled != null) {
            (scaled[0] + scaled[2]) / 2f
        } else {
            defaultCenterX
        }

        val centerY = if (scaled != null) {
            (scaled[1] + scaled[3]) / 2f
        } else {
            defaultCenterY
        }

        view.x = (
            centerX - view.width / 2f
            ).coerceIn(
                0f,
                (rootWidth - view.width)
                    .coerceAtLeast(0)
                    .toFloat()
            )

        view.y = (
            centerY - view.height / 2f
            ).coerceIn(
                0f,
                (rootHeight - view.height)
                    .coerceAtLeast(0)
                    .toFloat()
            )
    }

    private fun saveTargets() {
        val root = editorRoot ?: return
        val left = leftTarget ?: return
        val right = rightTarget ?: return

        if (root.width <= 1 || root.height <= 1) {
            Toast.makeText(
                this,
                "Display dimensions are unavailable",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val mapping = NativeTgkOrientationMapping(
            left = targetRect(
                left,
                root.width,
                root.height
            ),
            right = targetRect(
                right,
                root.width,
                root.height
            )
        )

        val existing = NativeTgkStorage.getProfile(
            this,
            targetPackage
        )

        val baseProfile = existing ?: NativeTgkProfile(
            packageName = targetPackage,
            appLabel = targetLabel
        )

        val updated = baseProfile
            .copy(appLabel = targetLabel)
            .withMapping(
                requestedOrientation,
                mapping
            )

        if (!NativeTgkStorage.saveProfile(this, updated)) {
            Toast.makeText(
                this,
                "Could not save trigger targets",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        Toast.makeText(
            this,
            "Saved ${requestedOrientation.name.lowercase()} L/R targets",
            Toast.LENGTH_SHORT
        ).show()

        finishAndApply()
    }

    private fun targetRect(
        target: View,
        width: Int,
        height: Int
    ): NativeTgkRect {
        val centerX = (
            target.x + target.width / 2f
            ).roundToInt()
        val centerY = (
            target.y + target.height / 2f
            ).roundToInt()

        val rectSize = (
            min(width, height) * 0.069f
            ).roundToInt()
            .coerceIn(56, 128)

        val half = rectSize / 2

        val left = (
            centerX - half
            ).coerceIn(0, width - 2)
        val top = (
            centerY - half
            ).coerceIn(0, height - 2)
        val right = (
            centerX + half
            ).coerceIn(left + 1, width - 1)
        val bottom = (
            centerY + half
            ).coerceIn(top + 1, height - 1)

        return NativeTgkRect(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            captureWidth = width,
            captureHeight = height
        )
    }

    private fun cancelEditing() {
        Toast.makeText(
            this,
            "Trigger target editing cancelled",
            Toast.LENGTH_SHORT
        ).show()

        finishAndApply()
    }

    private fun finishAndApply() {
        if (finishing) {
            return
        }

        finishing = true
        handler.removeCallbacksAndMessages(null)
        removeEditorOverlay()
        NativeTgkEditorRuntime.end()

        val profile = NativeTgkStorage.getProfile(
            this,
            targetPackage
        )
        val mappingReady =
            profile?.enabled == true &&
                profile.hasCompleteMapping(
                    requestedOrientation
                )

        if (!mappingReady) {
            NativeTgkRuntimeState.clear()
            stopSelf()
            return
        }

        /*
         * The editor is kept alive only while its target application
         * is foreground. Apply immediately after Save so there is no
         * gap where the editor has ended but the foreground monitor
         * has not yet configured TGK. Marking the mapping active first
         * also prevents the legacy F7/F8 service from running its
         * Volume actions during the vendor configuration delay.
         */
        NativeTgkRuntimeState.markActive(
            targetPackage,
            requestedOrientation
        )

        Thread(
            {
                val result =
                    NativeTgkCoordinator.applyForegroundMapping(
                        context = applicationContext,
                        packageName = targetPackage,
                        orientation = requestedOrientation
                    )

                if (!result.success) {
                    NativeTgkRuntimeState.clearIfMatches(
                        targetPackage,
                        requestedOrientation
                    )
                }

                stopSelf()
            },
            "RedMagicTgkEditorApply"
        ).start()
    }

    private fun finishWithoutMapping() {
        if (finishing) {
            return
        }

        finishing = true
        handler.removeCallbacksAndMessages(null)
        removeEditorOverlay()
        NativeTgkEditorRuntime.end()
        NativeTgkRuntimeState.clear()
        stopSelf()
    }

    private fun removeEditorOverlay() {
        val root = editorRoot ?: return

        runCatching {
            windowManager.removeView(root)
        }

        editorRoot = null
        leftTarget = null
        rightTarget = null
    }

    private fun roundedBackground(
        color: Int,
        radius: Float
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
            setStroke(
                dp(1),
                Color.argb(180, 255, 255, 255)
            )
        }
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
            ).roundToInt()
    }
}
