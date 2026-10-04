package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class ShoulderLedSplitTest {
    @Test fun arbitraryColorsRoundTripAndRejectInvalidSelections() {
        for (effect in listOf("steady", "breathe", "flashing", "rapid")) {
            val value = ShoulderLedSplit.encode(effect, 0x123456, 0xfedcba)
            assertEquals(ShoulderLedSplit.Selection(effect, 0x123456, 0xfedcba), ShoulderLedSplit.decode(value))
            assertEquals(effect, ShoulderLedSplit.baseEffect(value))
        }
        for (value in listOf("steady", "split:unknown:000000:ffffff", "split:breathe:00000:ffffff",
            "split:breathe:gggggg:ffffff", "split:breathe:000000:ffffff;id", "split:steady:000000:ffffff:extra")) {
            assertNull(ShoulderLedSplit.decode(value))
            assertNull(ShoulderLedSplit.command(value))
        }
        assertEquals(0xffffff, ShoulderLedSplit.parseRgb("#FFFFFF"))
        assertEquals(0, ShoulderLedSplit.parseRgb("000000"))
        assertNull(ShoulderLedSplit.parseRgb("1000000"))
    }

    @Test fun replayChangesOnlyColorBytesForEveryEffect() {
        val starts = mapOf(
            "steady" to listOf(183, 215),
            "breathe" to listOf(183, 211, 239, 267, 299, 327, 355, 383),
            "flashing" to listOf(183, 211, 243, 271),
            "rapid" to listOf(151, 179, 207, 235, 263, 291)
        )
        for ((effect, offsets) in starts) {
            withFakeDevice(effect) { dir, original, command ->
                val result = execute(command, dir)
                assertEquals(result.second, 0, result.first)
                val pairs = FileAccess(dir.resolve("device/reg").toFile()).bytes()
                val expected = original.clone()
                offsets.forEachIndexed { index, offset ->
                    val rgb = if (index < offsets.size / 2) 0x123456 else 0xfedcba
                    expected[offset] = (rgb shr 16).toByte()
                    expected[offset + 4] = (rgb shr 8).toByte()
                    expected[offset + 8] = rgb.toByte()
                }
                assertArrayEquals("$effect must preserve all routing/timing/control bytes", expected, pairs)
                assertEquals(0, dir.resolve("temp").toFile().listFiles()!!.size)
            }
        }
    }

    @Test fun unknownFirmwareIsRejectedBeforeHardwareWrites() {
        withFakeDevice("breathe") { dir, original, command ->
            original[10] = (original[10].toInt() xor 1).toByte()
            Files.write(dir.resolve("firmware/aw_touch3_7.bin"), original)
            val result = execute(command, dir)
            assertNotEquals(result.second, 0, result.first)
            assertEquals("", dir.resolve("device/reg").toFile().readText())
            assertEquals("effect = 0x2000000\n", dir.resolve("device/effect").toFile().readText())
            assertEquals(0, dir.resolve("temp").toFile().listFiles()!!.size)
        }
    }

    @Test fun writeFailureRestoresPreviousEffect() {
        withFakeDevice("steady") { dir, _, command ->
            val failing = command.replace(
                "printf '%s %s\\n' \"\$reg\" \"\$value\" >> \"\$d/reg\" || exit 1",
                "false || exit 1"
            )
            assertNotEquals("failure must be injected", command, failing)
            assertNotEquals(0, execute(failing, dir).first)
            assertEquals("0x2000000\n", dir.resolve("device/effect").toFile().readText())
            assertEquals(0, dir.resolve("temp").toFile().listFiles()!!.size)
        }
    }

    private fun withFakeDevice(effect: String, block: (java.nio.file.Path, ByteArray, String) -> Unit) {
        val dir = Files.createTempDirectory("trigger-led-test")
        try {
            val name = when (effect) { "steady" -> "2"; "breathe" -> "3"; "flashing" -> "4"; else -> "a" }
            val file = "aw_touch${name}_7.bin"
            val original = javaClass.getResourceAsStream("/trigger-led/$file")!!.use { it.readBytes() }
            Files.createDirectories(dir.resolve("firmware"))
            Files.createDirectories(dir.resolve("device"))
            Files.createDirectories(dir.resolve("temp"))
            Files.write(dir.resolve("firmware/$file"), original)
            dir.resolve("device/effect").toFile().writeText("effect = 0x2000000\n")
            dir.resolve("device/reg").toFile().writeText("")
            val command = ShoulderLedSplit.command(ShoulderLedSplit.encode(effect, 0x123456, 0xfedcba))!!
                .replace("/sys/class/leds/aw22xxx_led", dir.resolve("device").toString())
                .replace("/vendor/firmware", dir.resolve("firmware").toString())
                .replace("/data/local/tmp", dir.resolve("temp").toString())
                .replace("> \"\$d/reg\"", ">> \"\$d/reg\"")
            block(dir, original, command)
        } finally { dir.toFile().deleteRecursively() }
    }

    private fun execute(command: String, dir: java.nio.file.Path): Pair<Int, String> {
        val log = dir.resolve("output").toFile()
        val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).redirectOutput(log).start()
        assertTrue("command must terminate", process.waitFor(10, TimeUnit.SECONDS))
        return process.exitValue() to log.readText()
    }

    private class FileAccess(private val file: java.io.File) {
        fun bytes(): ByteArray = file.readLines().flatMap { line ->
            line.trim().split(Regex("\\s+")).map { it.toInt(16).toByte() }
        }.toByteArray()
    }
}
