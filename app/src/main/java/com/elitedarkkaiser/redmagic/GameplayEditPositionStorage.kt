package com.elitedarkkaiser.redmagic

import android.content.Context
import kotlin.math.roundToInt

internal data class GameplayEditPosition(
    val xFraction: Float,
    val yFraction: Float
) {
    fun isValid(): Boolean {
        return xFraction in 0f..1f && yFraction in 0f..1f
    }

    fun xFor(maxX: Int): Int {
        return (xFraction * maxX.coerceAtLeast(0))
            .roundToInt()
            .coerceIn(0, maxX.coerceAtLeast(0))
    }

    fun yFor(maxY: Int): Int {
        return (yFraction * maxY.coerceAtLeast(0))
            .roundToInt()
            .coerceIn(0, maxY.coerceAtLeast(0))
    }
}

/** Persists the movable gameplay edit control per app and orientation. */
internal object GameplayEditPositionStorage {
    private const val PREFS = "gameplay_edit_control_positions"

    @Synchronized
    fun read(
        context: Context,
        packageName: String,
        orientation: NativeTgkOrientation
    ): GameplayEditPosition? {
        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
        val prefix = keyPrefix(packageName, orientation)
        if (
            !prefs.contains("${prefix}_x") ||
            !prefs.contains("${prefix}_y")
        ) {
            return null
        }

        return GameplayEditPosition(
            xFraction = prefs.getFloat("${prefix}_x", -1f),
            yFraction = prefs.getFloat("${prefix}_y", -1f)
        ).takeIf { it.isValid() }
    }

    @Synchronized
    fun save(
        context: Context,
        packageName: String,
        orientation: NativeTgkOrientation,
        x: Int,
        y: Int,
        maxX: Int,
        maxY: Int
    ) {
        val safeMaxX = maxX.coerceAtLeast(1)
        val safeMaxY = maxY.coerceAtLeast(1)
        val prefix = keyPrefix(packageName, orientation)

        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).edit()
            .putFloat(
                "${prefix}_x",
                x.coerceIn(0, maxX.coerceAtLeast(0)).toFloat() /
                    safeMaxX.toFloat()
            )
            .putFloat(
                "${prefix}_y",
                y.coerceIn(0, maxY.coerceAtLeast(0)).toFloat() /
                    safeMaxY.toFloat()
            )
            .apply()
    }

    private fun keyPrefix(
        packageName: String,
        orientation: NativeTgkOrientation
    ): String {
        return "${packageName}_${orientation.name.lowercase()}"
    }
}
