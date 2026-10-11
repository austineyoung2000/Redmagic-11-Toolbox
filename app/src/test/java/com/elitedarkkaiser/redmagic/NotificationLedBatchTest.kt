package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Test

class NotificationLedBatchTest {
    @Test fun stockSequenceUsesSeparateRgbWritesAndCfgTwo() {
        val cmd = NotificationLedBatch.command(listOf(NotificationLedBatch.Zone("fan", "breathe", 3)), false)!!
        val region = cmd.lastIndexOf("printf '0 %x\\n' 3")
        val effect = cmd.lastIndexOf("printf '1 %x\\n' 3")
        val color = cmd.lastIndexOf("printf '2 %x\\n' 3")
        val packed = cmd.indexOf("50343939", color)
        val cfg = cmd.indexOf("printf '2\\n'", packed)
        assertTrue(region >= 0 && effect > region && color > effect && packed > color && cfg > packed)
        assertFalse(cmd.contains("/reg"))
        assertFalse(cmd.contains("mktemp"))
    }
    @Test fun unsupportedOrDuplicateZonesCannotTouchHardware() {
        assertNull(NotificationLedBatch.command(listOf(NotificationLedBatch.Zone("other", "steady", 1)), false))
        assertNull(NotificationLedBatch.command(listOf(NotificationLedBatch.Zone("logo", "dim:128:breathe", 3)), false))
        assertNull(NotificationLedBatch.command(listOf(NotificationLedBatch.Zone("fan", "steady", 999)), false))
        val zone = NotificationLedBatch.Zone("logo", "steady", 1)
        assertNull(NotificationLedBatch.command(listOf(zone, zone), false))
    }
    @Test fun stockOffDoesNotChangeCoolingPower() {
        val cmd = NotificationLedBatch.offCommand()
        assertFalse(cmd.contains("fan_enable"))
        assertFalse(cmd.contains("pump_enable"))
        assertEquals(3, Regex("/cfg").findAll(cmd).count())
    }
}
