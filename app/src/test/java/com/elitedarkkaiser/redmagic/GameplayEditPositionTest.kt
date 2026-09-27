package com.elitedarkkaiser.redmagic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameplayEditPositionTest {
    @Test
    fun normalizedPositionScalesToNewLandscapeBounds() {
        val position = GameplayEditPosition(
            xFraction = 0.75f,
            yFraction = 0.25f
        )

        assertEquals(750, position.xFor(1_000))
        assertEquals(125, position.yFor(500))
    }

    @Test
    fun restoredCoordinatesStayInsideAvailableBounds() {
        val position = GameplayEditPosition(
            xFraction = 1f,
            yFraction = 1f
        )

        assertEquals(800, position.xFor(800))
        assertEquals(400, position.yFor(400))
    }

    @Test
    fun invalidStoredFractionsAreRejected() {
        assertFalse(GameplayEditPosition(-0.1f, 0.5f).isValid())
        assertFalse(GameplayEditPosition(0.5f, 1.1f).isValid())
        assertTrue(GameplayEditPosition(0.5f, 0.5f).isValid())
    }
}
