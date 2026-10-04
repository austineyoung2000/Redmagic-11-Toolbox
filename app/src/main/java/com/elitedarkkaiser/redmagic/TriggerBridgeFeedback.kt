package com.elitedarkkaiser.redmagic

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.*
import java.util.concurrent.atomic.AtomicInteger

/** Read-only observer: never grabs devices or injects touch events. */
object TriggerBridgeFeedback {
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger()
    private var process: Process? = null
    private var view: GlowView? = null
    private var manager: WindowManager? = null

    fun show(context: Context) {
        val token = generation.incrementAndGet()
        main.post {
            stop()
            if (generation.get() != token ||
                NativeTgkRuntimeState.activeBackend() != TriggerMappingBackend.MODULE_BACKEND) return@post
            val wm = context.getSystemService(WindowManager::class.java)
            val glow = GlowView(context)
            val params = WindowManager.LayoutParams(
                -1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                // Below Android's maximum obscuring opacity for touch-through windows.
                alpha = 0.5f
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (runCatching { wm.addView(glow, params) }.isFailure) return@post
            manager = wm
            view = glow
            Thread({
                val command = "for n in /sys/class/input/event*/device/name; do " +
                    "if [ \"\$(cat \"\$n\")\" = redmagic_trigger_bridge ]; then " +
                    "e=\${n%/device/name}; exec getevent \"/dev/input/\${e##*/}\"; fi; done"
                val child = runCatching { ProcessBuilder("su", "-c", command).start() }.getOrNull()
                main.post {
                    if (generation.get() == token) process = child
                    else child?.destroy()
                }
                val decoder = BridgeContactDecoder()
                runCatching {
                    child?.inputStream?.bufferedReader()?.useLines { lines ->
                        lines.forEach { line ->
                            if (generation.get() != token) {
                                child?.destroy()
                                return@forEach
                            }
                            decoder.accept(line)?.let { state ->
                                main.post {
                                    if (generation.get() == token && view === glow) {
                                        glow.setPressed(state.first, state.second)
                                    }
                                }
                            }
                        }
                    }
                }
                child?.destroy()
                main.post { if (generation.get() == token) stop() }
            }, "BridgeVisualFeedback").start()
        }
    }

    fun hide() {
        generation.incrementAndGet()
        main.post { stop() }
    }

    private fun stop() {
        process?.destroy()
        process = null
        view?.animate()?.cancel()
        view?.let { runCatching { manager?.removeView(it) } }
        view = null
        manager = null
    }

    private class GlowView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var left = false
        private var right = false
        fun setPressed(l: Boolean, r: Boolean) {
            animate().cancel()
            if (l || r) { left = l; right = r; alpha = 1f; invalidate() }
            else animate().alpha(0f).setDuration(120).start()
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val rotation = display?.rotation ?: Surface.ROTATION_0
            val landscape = width > height
            val reversed = rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_180
            val depth = 24f * resources.displayMetrics.density
            fun draw(active: Boolean, fraction: Float, color: Int) {
                if (!active) return
                val position = fraction
                canvas.save()
                if (!landscape) {
                    if (reversed) { canvas.translate(width.toFloat(), 0f); canvas.rotate(90f) }
                    else { canvas.translate(0f, height.toFloat()); canvas.rotate(-90f) }
                } else if (reversed) {
                    canvas.translate(width.toFloat(), height.toFloat()); canvas.rotate(180f)
                }
                val span = if (landscape) width.toFloat() else height.toFloat()
                val x = span * position
                paint.shader = RadialGradient(x, 0f, span * 0.12f,
                    intArrayOf(color, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                canvas.drawRect(x - span * 0.12f, 0f, x + span * 0.12f, depth, paint)
                canvas.restore()
            }
            // Retain the last frame during release fade.
            draw(left, 0.16f, Color.RED)
            draw(right, 0.84f, Color.rgb(30, 120, 255))
        }
    }
}

/** Module reserves 65535/65534; physical contacts use separate IDs. */
internal class BridgeContactDecoder {
    private var slot = 0
    private val ids = mutableMapOf<Int, Long>()
    private var previous = false to false
    fun accept(line: String): Pair<Boolean, Boolean>? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size != 3) return null
        val type = parts[0].toIntOrNull(16) ?: return null
        val code = parts[1].toIntOrNull(16) ?: return null
        val value = parts[2].toLongOrNull(16) ?: return null
        if (type == 3 && code == 0x2f) slot = value.toInt()
        if (type == 3 && code == 0x39) {
            if (value == 0xffffffffL) ids.remove(slot) else ids[slot] = value
        }
        if (type != 0 || code != 0) return null
        val current = ids.containsValue(65535L) to ids.containsValue(65534L)
        if (current == previous) return null
        previous = current
        return current
    }
}
