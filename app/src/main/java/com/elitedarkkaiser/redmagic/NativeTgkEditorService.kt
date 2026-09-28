package com.elitedarkkaiser.redmagic

import android.app.Service
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
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
        private const val TAG = "RedmagicTargetEditor"

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

    private var editedLeftBehavior =
        NativeTgkTriggerBehavior.SINGLE_TOUCH
    private var editedRightBehavior =
        NativeTgkTriggerBehavior.SINGLE_TOUCH
    private var editedLeftRapidFireCount = 0
    private var editedRightRapidFireCount = 0

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
            runCatching {
                showEditorOverlay()
            }.onFailure { error ->
                Log.e(
                    TAG,
                    "Could not construct target editor overlay",
                    error
                )
                Toast.makeText(
                    this,
                    "Could not display the trigger editor",
                    Toast.LENGTH_LONG
                ).show()
                finishWithoutMapping()
            }
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

        val storedProfile = NativeTgkStorage.getProfile(
            this,
            targetPackage
        )
        editedLeftBehavior = storedProfile
            ?.effectiveLeftBehavior()
            ?: NativeTgkTriggerBehavior.SINGLE_TOUCH
        editedRightBehavior = storedProfile
            ?.effectiveRightBehavior()
            ?: NativeTgkTriggerBehavior.SINGLE_TOUCH
        editedLeftRapidFireCount = storedProfile
            ?.effectiveLeftRapidFireCount()
            ?: 0
        editedRightRapidFireCount = storedProfile
            ?.effectiveRightRapidFireCount()
            ?: 0

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
        }

        val panelBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val leftSelector = behaviorSelector(
            left = true,
            accentColor = Color.rgb(225, 26, 66)
        )
        val rightSelector = behaviorSelector(
            left = false,
            accentColor = Color.rgb(20, 105, 235)
        )

        var activeMenu: LinearLayout? = null
        var activeMenuIsLeft: Boolean? = null

        fun closeMenu() {
            activeMenu?.let { menu ->
                runCatching { root.removeView(menu) }
            }
            activeMenu = null
            activeMenuIsLeft = null
            refreshBehaviorSelector(leftSelector, true, false)
            refreshBehaviorSelector(rightSelector, false, false)
        }

        fun toggleMenu(
            left: Boolean,
            selector: TextView,
            accentColor: Int
        ) {
            val closeOnly = activeMenuIsLeft == left
            closeMenu()
            if (closeOnly) {
                return
            }

            val menu = behaviorMenu(
                left = left,
                accentColor = accentColor,
                onSelected = { closeMenu() }
            )
            activeMenu = menu
            activeMenuIsLeft = left
            refreshBehaviorSelector(selector, left, true)

            root.addView(
                menu,
                FrameLayout.LayoutParams(
                    selector.width.coerceAtLeast(dp(150)),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            menu.post {
                if (activeMenu !== menu || menu.parent !== root) {
                    return@post
                }

                val selectorLocation = IntArray(2)
                val rootLocation = IntArray(2)
                selector.getLocationOnScreen(selectorLocation)
                root.getLocationOnScreen(rootLocation)

                val horizontalMargin = dp(6)
                val verticalGap = dp(3)
                val selectorX =
                    selectorLocation[0] - rootLocation[0]
                val selectorY =
                    selectorLocation[1] - rootLocation[1]
                val maximumX = (
                    root.width - menu.width - horizontalMargin
                    ).coerceAtLeast(horizontalMargin)
                val belowY =
                    selectorY + selector.height + verticalGap
                val aboveY =
                    selectorY - menu.height - verticalGap
                val maximumY = (
                    root.height - menu.height - horizontalMargin
                    ).coerceAtLeast(horizontalMargin)

                menu.x = selectorX
                    .coerceIn(horizontalMargin, maximumX)
                    .toFloat()
                menu.y = (
                    if (belowY <= maximumY) belowY else aboveY
                    ).coerceIn(horizontalMargin, maximumY)
                    .toFloat()
            }
        }

        leftSelector.setOnClickListener {
            toggleMenu(
                left = true,
                selector = leftSelector,
                accentColor = Color.rgb(225, 26, 66)
            )
        }
        rightSelector.setOnClickListener {
            toggleMenu(
                left = false,
                selector = rightSelector,
                accentColor = Color.rgb(20, 105, 235)
            )
        }
        root.setOnClickListener { closeMenu() }

        val selectors = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(2))

            addView(
                leftSelector,
                LinearLayout.LayoutParams(
                    0,
                    dp(36),
                    1f
                ).apply {
                    marginEnd = dp(4)
                }
            )
            addView(
                rightSelector,
                LinearLayout.LayoutParams(
                    0,
                    dp(36),
                    1f
                ).apply {
                    marginStart = dp(4)
                }
            )
        }

        val subtitle = TextView(this).apply {
            text = buildString {
                append(targetLabel)
                append(" • ")
                append(
                    if (
                        requestedOrientation ==
                        NativeTgkOrientation.PORTRAIT
                    ) "Portrait" else "Landscape"
                )
                append(" • Drag L/R onto controls")
            }
            textSize = 9f
            setTextColor(Color.rgb(178, 184, 198))
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(8), dp(1), dp(8), dp(4))
        }

        panelBody.addView(
            selectors,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        panelBody.addView(
            subtitle,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val title = TextView(this).apply {
            text = "⠿  Shoulder Triggers"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), 0, dp(3), 0)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            contentDescription =
                "Drag to move the shoulder trigger editor"
            isClickable = true
        }

        val help = editorActionButton("?", false).apply {
            contentDescription = "Trigger editor help"
            setOnClickListener {
                Toast.makeText(
                    this@NativeTgkEditorService,
                    "Drag this panel by its title. Drag L and R to the game controls, choose each behavior, then tap Save.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        val collapse = editorActionButton("▴", false).apply {
            contentDescription = "Collapse trigger controls"
        }
        val save = editorActionButton("✓ SAVE", true).apply {
            contentDescription = "Save trigger targets and behavior"
            setOnClickListener { saveTargets() }
        }
        val cancel = editorActionButton("✕", false).apply {
            contentDescription = "Cancel target editing"
            setOnClickListener { cancelEditing() }
        }

        var expanded = true
        collapse.setOnClickListener {
            expanded = !expanded
            panelBody.visibility = if (expanded) View.VISIBLE else View.GONE
            collapse.text = if (expanded) "▴" else "▾"
            collapse.contentDescription = if (expanded) {
                "Collapse trigger controls"
            } else {
                "Expand trigger controls"
            }
            if (!expanded) {
                closeMenu()
            }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), dp(4), dp(7), dp(4))
            addView(title, LinearLayout.LayoutParams(0, dp(28), 1f))
            addView(help, LinearLayout.LayoutParams(dp(28), dp(28)))
            addView(
                collapse,
                LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                    marginStart = dp(3)
                }
            )
            addView(
                save,
                LinearLayout.LayoutParams(dp(62), dp(28)).apply {
                    marginStart = dp(4)
                }
            )
            addView(
                cancel,
                LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                    marginStart = dp(3)
                }
            )
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(
                Color.argb(238, 12, 13, 19),
                dp(11).toFloat(),
                Color.rgb(125, 24, 48)
            )
            elevation = dp(12).toFloat()
            addView(
                header,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                View(this@NativeTgkEditorService).apply {
                    setBackgroundColor(Color.argb(120, 125, 24, 48))
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(1)
                )
            )
            addView(
                panelBody,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val availableWidth = (
            resources.displayMetrics.widthPixels - dp(16)
            ).coerceAtLeast(dp(280))
        val preferredWidth = if (
            requestedOrientation == NativeTgkOrientation.LANDSCAPE
        ) dp(400) else dp(340)

        root.addView(
            controls,
            FrameLayout.LayoutParams(
                min(availableWidth, preferredWidth),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {
                topMargin = dp(6)
            }
        )

        attachPanelDragHandle(
            handle = title,
            panel = controls,
            bounds = root,
            onDragStarted = { closeMenu() }
        )

        val targetSize = dp(38)

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
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = roundedBackground(
                color,
                targetSize / 2f
            )
            elevation = dp(7).toFloat()
        }

        var offsetX = 0f
        var offsetY = 0f

        target.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    offsetX = view.x - event.rawX
                    offsetY = view.y - event.rawY
                    view.elevation = dp(12).toFloat()
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
                    view.elevation = dp(7).toFloat()
                    true
                }

                else -> false
            }
        }

        return target
    }

    private fun attachPanelDragHandle(
        handle: View,
        panel: View,
        bounds: View,
        onDragStarted: () -> Unit
    ) {
        val touchSlop = ViewConfiguration
            .get(this)
            .scaledTouchSlop

        var panelOffsetX = 0f
        var panelOffsetY = 0f
        var downRawX = 0f
        var downRawY = 0f
        var boundsScreenX = 0
        var boundsScreenY = 0
        var dragging = false

        handle.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val boundsLocation = IntArray(2)
                    bounds.getLocationOnScreen(boundsLocation)
                    boundsScreenX = boundsLocation[0]
                    boundsScreenY = boundsLocation[1]

                    downRawX = event.rawX
                    downRawY = event.rawY
                    panelOffsetX =
                        panel.x - (event.rawX - boundsScreenX)
                    panelOffsetY =
                        panel.y - (event.rawY - boundsScreenY)
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (
                        !dragging &&
                        (
                            abs(event.rawX - downRawX) > touchSlop ||
                                abs(event.rawY - downRawY) > touchSlop
                            )
                    ) {
                        dragging = true
                        onDragStarted()
                        panel.elevation = dp(18).toFloat()
                    }

                    if (dragging) {
                        val edgeMargin = dp(4)
                        val maximumX = (
                            bounds.width - panel.width - edgeMargin
                            ).coerceAtLeast(edgeMargin)
                        val maximumY = (
                            bounds.height - panel.height - edgeMargin
                            ).coerceAtLeast(edgeMargin)

                        panel.x = (
                            event.rawX - boundsScreenX + panelOffsetX
                            ).coerceIn(
                                edgeMargin.toFloat(),
                                maximumX.toFloat()
                            )
                        panel.y = (
                            event.rawY - boundsScreenY + panelOffsetY
                            ).coerceIn(
                                edgeMargin.toFloat(),
                                maximumY.toFloat()
                            )
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    panel.elevation = dp(12).toFloat()
                    if (!dragging) {
                        view.performClick()
                    }
                    dragging = false
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    panel.elevation = dp(12).toFloat()
                    dragging = false
                    true
                }

                else -> false
            }
        }
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
            .withTriggerBehavior(
                left = true,
                behavior = editedLeftBehavior,
                count = editedLeftRapidFireCount
            )
            .withTriggerBehavior(
                left = false,
                behavior = editedRightBehavior,
                count = editedRightRapidFireCount
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
            "Saved ${requestedOrientation.name.lowercase()} L/R controls",
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

    private fun editorActionButton(
        label: String,
        emphasized: Boolean
    ): TextView {
        val fill = if (emphasized) {
            getColor(R.color.redmagic_accent)
        } else {
            getColor(R.color.redmagic_panel_pressed)
        }
        val stroke = if (emphasized) {
            getColor(R.color.redmagic_accent)
        } else {
            getColor(R.color.redmagic_border)
        }

        return TextView(this).apply {
            text = label
            textSize = 9f
            gravity = Gravity.CENTER
            minWidth = 0
            minHeight = 0
            setPadding(dp(5), 0, dp(5), 0)
            setTextColor(
                if (emphasized) {
                    Color.WHITE
                } else {
                    getColor(R.color.redmagic_text_primary)
                }
            )
            background = RippleDrawable(
                ColorStateList.valueOf(
                    getColor(R.color.redmagic_ripple)
                ),
                roundedBackground(
                    color = fill,
                    radius = dp(8).toFloat(),
                    strokeColor = stroke
                ),
                null
            )
            isClickable = true
            isFocusable = true
        }
    }

    private data class BehaviorOption(
        val label: String,
        val behavior: NativeTgkTriggerBehavior,
        val rapidFireCount: Int
    )

    private fun behaviorOptions(): List<BehaviorOption> {
        return listOf(
            BehaviorOption(
                "Single Tap",
                NativeTgkTriggerBehavior.SINGLE_TOUCH,
                0
            ),
            BehaviorOption(
                "Long Press",
                NativeTgkTriggerBehavior.LONG_PRESS,
                0
            ),
            BehaviorOption(
                "Rapid Fire ×2",
                NativeTgkTriggerBehavior.RAPID_FIRE,
                2
            ),
            BehaviorOption(
                "Rapid Fire ×5",
                NativeTgkTriggerBehavior.RAPID_FIRE,
                5
            ),
            BehaviorOption(
                "Rapid Fire ×10",
                NativeTgkTriggerBehavior.RAPID_FIRE,
                10
            )
        )
    }

    private fun behaviorSelector(
        left: Boolean,
        accentColor: Int
    ): TextView {
        return TextView(this).apply {
            textSize = 11f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.WHITE)
            setPadding(dp(10), 0, dp(8), 0)
            background = RippleDrawable(
                ColorStateList.valueOf(Color.argb(90, 255, 255, 255)),
                roundedBackground(
                    color = Color.argb(225, 22, 24, 34),
                    radius = dp(9).toFloat(),
                    strokeColor = accentColor
                ),
                null
            )
            isClickable = true
            isFocusable = true
            refreshBehaviorSelector(this, left, false)
        }
    }

    private fun refreshBehaviorSelector(
        selector: TextView,
        left: Boolean,
        expanded: Boolean
    ) {
        val behavior = if (left) {
            editedLeftBehavior
        } else {
            editedRightBehavior
        }
        val count = if (left) {
            editedLeftRapidFireCount
        } else {
            editedRightRapidFireCount
        }
        val mode = behaviorOptions().firstOrNull {
            it.behavior == behavior && it.rapidFireCount == count
        }?.label ?: "Single Tap"
        selector.text = buildString {
            append(if (left) "L   " else "R   ")
            append(mode)
            append(if (expanded) "   ▴" else "   ▾")
        }
        selector.contentDescription = buildString {
            append(if (left) "Left" else "Right")
            append(" trigger behavior: ")
            append(mode)
        }
    }

    private fun behaviorMenu(
        left: Boolean,
        accentColor: Int,
        onSelected: () -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            elevation = dp(16).toFloat()
            setPadding(dp(3), dp(3), dp(3), dp(3))
            background = roundedBackground(
                Color.argb(248, 25, 27, 38),
                dp(8).toFloat(),
                Color.argb(
                    190,
                    Color.red(accentColor),
                    Color.green(accentColor),
                    Color.blue(accentColor)
                )
            )

            behaviorOptions().forEach { option ->
                val item = TextView(this@NativeTgkEditorService).apply {
                    text = option.label
                    textSize = 11f
                    gravity = Gravity.CENTER_VERTICAL
                    setTextColor(Color.WHITE)
                    setPadding(dp(10), 0, dp(7), 0)
                    background = RippleDrawable(
                        ColorStateList.valueOf(
                            Color.argb(90, 255, 255, 255)
                        ),
                        roundedBackground(
                            Color.TRANSPARENT,
                            dp(6).toFloat(),
                            Color.TRANSPARENT
                        ),
                        null
                    )
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        if (left) {
                            editedLeftBehavior = option.behavior
                            editedLeftRapidFireCount =
                                option.rapidFireCount
                        } else {
                            editedRightBehavior = option.behavior
                            editedRightRapidFireCount =
                                option.rapidFireCount
                        }
                        onSelected()
                    }
                }
                addView(
                    item,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(30)
                    )
                )
            }
        }
    }

    private fun roundedBackground(
        color: Int,
        radius: Float,
        strokeColor: Int = Color.argb(180, 255, 255, 255)
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
            setStroke(
                dp(1),
                strokeColor
            )
        }
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
            ).roundToInt()
    }
}
