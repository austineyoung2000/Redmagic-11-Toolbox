package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class NotificationLedBatchTest {
    private val zones = listOf("logo", "triggers", "fan").map {
        NotificationLedBatch.Zone(it, LedBrightness.encode(128, "breathe"), 3)
    }
    @Test fun completeVendorProtocolsReplayInOrderAndCoolingStopsInSameCommand() = withDevice { dir ->
        val result = execute(rendered(dir, true), dir)
        assertEquals(result.second, 0, result.first)
        val lines = dir.resolve("device/reg").toFile().readLines()
        assertEquals("05 82", lines[396 / 2 - 1])
        assertEquals("05 80", lines[396 - 1])
        assertEquals("05 81", lines.last())
        assertEquals(listOf("05 82", "05 80", "05 81"),
            lines.filter { it in listOf("05 80", "05 81", "05 82") })
        assertEquals((396 + 396 + 256) / 2, lines.size)
        // Effect selection is necessary controller setup, not a cfg reload.
        assertEquals("0x3003007\n", dir.resolve("device/effect").toFile().readText())
        assertEquals("0\n", dir.resolve("fan_enable").toFile().readText())
        assertEquals("0\n", dir.resolve("pump_enable").toFile().readText())
        assertTrue(dir.resolve("temp").toFile().listFiles()!!.isEmpty())
    }
    @Test fun badLastTemplateRejectsEntireBatchBeforeAnyHardwareOrPowerWrite() = withDevice { dir ->
        val fan = dir.resolve("firmware/aw_fan3_7.bin")
        val bytes = Files.readAllBytes(fan)
        bytes[20] = (bytes[20].toInt() xor 1).toByte()
        Files.write(fan, bytes)
        assertNotEquals(0, execute(rendered(dir, true), dir).first)
        assertEquals("", dir.resolve("device/reg").toFile().readText())
        assertEquals("before\n", dir.resolve("device/effect").toFile().readText())
        assertEquals("before\n", dir.resolve("fan_enable").toFile().readText())
        assertEquals("before\n", dir.resolve("pump_enable").toFile().readText())
        assertTrue(dir.resolve("temp").toFile().listFiles()!!.isEmpty())
    }
    @Test fun splitProgramsRemainSupportedAndUnsupportedZonesRejectWithoutFallback() {
        val splitLogo = LogoBarSelection("breathe", true, 96, true, 5, 128).encode()
        val splitTriggers = LedBrightness.encode(96, ShoulderLedSplit.encode("breathe", 0xff0000, 0x0000ff))
        assertNotNull(NotificationLedBatch.command(listOf(NotificationLedBatch.Zone("logo", splitLogo, 3),
            NotificationLedBatch.Zone("triggers", splitTriggers, 3)), false))
        assertNull(NotificationLedBatch.command(emptyList(), false))
        assertNull(NotificationLedBatch.command(listOf(NotificationLedBatch.Zone("unknown", "breathe", 3)), false))
        assertNull(NotificationLedBatch.command(listOf(zones.first(), zones.first()), false))
        assertNull(NotificationLedBatch.command(listOf(NotificationLedBatch.Zone("fan", "unknown", 3)), false))
    }
    private fun rendered(dir: Path, stopCooling: Boolean): String = NotificationLedBatch.command(zones, stopCooling)!!
        .replace("/sys/class/leds/aw22xxx_led", dir.resolve("device").toString())
        .replace("/vendor/firmware", dir.resolve("firmware").toString())
        .replace("/data/local/tmp", dir.resolve("temp").toString())
        .replace(DeviceCompatibility.Paths.FAN_ENABLE, dir.resolve("fan_enable").toString())
        .replace(DeviceCompatibility.Paths.PUMP_ENABLE, dir.resolve("pump_enable").toString())
        .replace("exec 9>\"", "exec 9>>\"")
    private fun execute(command: String, dir: Path): Pair<Int, String> {
        val out = dir.resolve("output").toFile()
        val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).redirectOutput(out).start()
        assertTrue(process.waitFor(10, TimeUnit.SECONDS))
        return process.exitValue() to out.readText()
    }
    private fun withDevice(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("notification-batch-test")
        try {
            for (p in listOf("device", "firmware", "temp")) Files.createDirectories(dir.resolve(p))
            for (p in listOf("device/effect", "device/cfg", "fan_enable", "pump_enable"))
                dir.resolve(p).toFile().writeText("before\n")
            dir.resolve("device/reg").toFile().writeText("")
            for (file in listOf("aw_cfg3_7", "aw_fan3_7")) {
                val hex = javaClass.getResourceAsStream("/led-brightness/$file.hex")!!.bufferedReader().use { it.readText().trim() }
                Files.write(dir.resolve("firmware/$file.bin"), hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            }
            Files.write(dir.resolve("firmware/aw_touch3_7.bin"), javaClass.getResourceAsStream("/trigger-led/aw_touch3_7.bin")!!.use { it.readBytes() })
            block(dir)
        } finally { dir.toFile().deleteRecursively() }
    }
}
