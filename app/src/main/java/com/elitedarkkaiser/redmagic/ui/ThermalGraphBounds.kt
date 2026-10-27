package com.elitedarkkaiser.redmagic.ui

import kotlin.math.max

/** Scale the compact chart to recorded values, with a minimum range for stability. */
internal object ThermalGraphBounds {
    fun compact(minimum: Float, maximum: Float, useFahrenheit: Boolean): Pair<Float, Float> {
        require(minimum.isFinite() && maximum.isFinite() && minimum <= maximum)
        val minimumSpan = if (useFahrenheit) 7.2f else 4f
        val span = max(maximum - minimum, minimumSpan)
        val midpoint = (minimum + maximum) / 2f
        val halfRange = span * 0.6f
        return (midpoint - halfRange) to (midpoint + halfRange)
    }
}
