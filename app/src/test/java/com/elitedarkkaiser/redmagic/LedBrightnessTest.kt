package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class LedBrightnessTest {
    @Test fun selectionsPreserveEffectSplitColorsAndPaletteIdentity() {
        val split = ShoulderLedSplit.encode("breathe", 0x123456, 0xabcdef)
        for (level in listOf(32, 33, 127, 128, 254, 255)) {
            val encoded = LedBrightness.encode(level, split)
            assertEquals(level, LedBrightness.level(encoded))
            assertEquals(split, LedBrightness.effect(encoded))
            assertEquals(ShoulderLedSplit.decode(split), ShoulderLedSplit.decode(encoded))
            assertEquals("breathe", ShoulderLedSplit.baseEffect(encoded))
            assertEquals("dim:$level:steady", LedBrightness.withEffect(encoded, "steady"))
        }
        assertEquals(255, LedBrightness.level("steady"))
        assertEquals("steady", LedBrightness.withEffect("breathe", "steady"))
        for (bad in listOf("dim:31:steady", "dim:256:steady", "dim:no:steady", "dim:32:", "dim:32:dim:32:steady")) {
            assertNull(LedBrightness.decode(bad))
            assertNull(LedBrightness.command("fan", bad, 7))
        }
        assertEquals(0x102, FanLedPalette.normalizeColor("dim:32:preset:0x3002102", 1))
        assertEquals("dim:32:steady", FanLedPalette.normalizeEffect("dim:32:preset:0x3002102"))
        assertFalse(LedBrightness.supported("fan", "breathe", 0x102))
        assertNull(LedBrightness.command("fan", "dim:32:breathe", 0x102))
    }

    @Test fun scalingPreservesZeroChannelsAndMixedColorRatios() {
        assertEquals(0x202000, LedBrightness.scale(0xffff00, 32))
        assertEquals(0x008080, LedBrightness.scale(0x00ffff, 128))
        assertEquals(0x123456, LedBrightness.scale(0x123456, 255))
        for (level in 32..255) {
            assertEquals(level shl 16, LedBrightness.scale(0xff0000, level))
        }
    }

    @Test fun allInspectedProgramsChangeOnlyRgbAndReplayFullOutputAt255() {
        val cases = listOf(
            Case("logo", "steady", 7, "aw_cfg2_7", listOf(183,215)),
            Case("logo", "breathe", 7, "aw_cfg3_7", listOf(183,211,239,267,299,327,355,383)),
            Case("fan", "steady", 7, "aw_fan2_7", listOf(159))
        ) + (0x101..0x108).map { Case("fan", "steady", it, "aw_fan2_${it.toString(16)}", listOf(255,287,319,351)) }
        for (case in cases) withDevice(case) { dir, original ->
            for (level in listOf(32,128,255)) {
                dir.resolve("device/reg").toFile().writeText("")
                val result = execute(command(case, level, dir), dir)
                assertEquals(result.second, 0, result.first)
                val expected = original.clone()
                for (offset in case.offsets) for (delta in listOf(0,4,8)) {
                    expected[offset+delta] = (((original[offset+delta].toInt() and 255)*level+127)/255).toByte()
                }
                assertArrayEquals("${case.file}: preserve routing and timing at $level", expected, replayed(dir))
                assertEquals(0, dir.resolve("temp").toFile().listFiles()!!.size)
            }
        }
    }

    @Test fun restoringOriginalReplaysItsPaletteEffectAndBrightnessRatherThanTestPalette() {
        val originalCase = Case("fan", "steady", 0x104, "aw_fan2_104", listOf(255,287,319,351))
        withDevice(originalCase) { dir, original ->
            val previewCase = originalCase.copy(color=0x102, file="aw_fan2_102")
            Files.write(dir.resolve("firmware/${previewCase.file}.bin"), fixture(previewCase))
            val preview = execute(command(previewCase, 128, dir), dir)
            assertEquals(preview.second, 0, preview.first)
            dir.resolve("device/reg").toFile().writeText("")
            val restored = execute(command(originalCase, 32, dir), dir)
            assertEquals(restored.second, 0, restored.first)
            val expected = original.clone()
            for (offset in originalCase.offsets) for (delta in listOf(0,4,8)) {
                expected[offset+delta] = (((original[offset+delta].toInt() and 255)*32+127)/255).toByte()
            }
            assertArrayEquals(expected, replayed(dir))
            assertEquals("0x3002104\n", dir.resolve("device/effect").toFile().readText())
        }
    }

    @Test fun modifiedTemplateFailsBeforeEffectOrRegisterWrites() {
        val case = Case("logo", "steady", 7, "aw_cfg2_7", listOf(183,215))
        withDevice(case) { dir, original ->
            original[20] = (original[20].toInt() xor 1).toByte()
            Files.write(dir.resolve("firmware/${case.file}.bin"), original)
            assertNotEquals(0, execute(command(case, 32, dir), dir).first)
            assertEquals("", dir.resolve("device/reg").toFile().readText())
            assertEquals("effect = 0x1002001\n", dir.resolve("device/effect").toFile().readText())
            assertEquals(0, dir.resolve("temp").toFile().listFiles()!!.size)
        }
    }

    private data class Case(val zone: String, val effect: String, val color: Int, val file: String, val offsets: List<Int>)
    private fun fixture(case: Case): ByteArray = javaClass.getResourceAsStream("/led-brightness/${case.file}.hex")!!
        .bufferedReader().use { it.readText().trim() }.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun withDevice(case: Case, block: (java.nio.file.Path, ByteArray) -> Unit) {
        val dir = Files.createTempDirectory("led-brightness-test")
        try {
            for (path in listOf("device","firmware","temp")) Files.createDirectories(dir.resolve(path))
            val original = fixture(case)
            Files.write(dir.resolve("firmware/${case.file}.bin"), original)
            dir.resolve("device/reg").toFile().writeText("")
            dir.resolve("device/effect").toFile().writeText("effect = 0x1002001\n")
            block(dir, original)
        } finally { dir.toFile().deleteRecursively() }
    }
    private fun command(case: Case, level: Int, dir: java.nio.file.Path): String =
        LedBrightness.command(case.zone, LedBrightness.encode(level, case.effect), case.color)!!
            .replace("/sys/class/leds/aw22xxx_led", dir.resolve("device").toString())
            .replace("/vendor/firmware", dir.resolve("firmware").toString())
            .replace("/data/local/tmp", dir.resolve("temp").toString())
            .replace("exec 9>\"", "exec 9>>\"")
    private fun replayed(dir: java.nio.file.Path): ByteArray = dir.resolve("device/reg").toFile().readText()
        .trim().split(Regex("\\s+")).map { it.toInt(16).toByte() }.toByteArray()
    private fun execute(command: String, dir: java.nio.file.Path): Pair<Int,String> {
        val log = dir.resolve("output").toFile()
        val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).redirectOutput(log).start()
        assertTrue(process.waitFor(10, TimeUnit.SECONDS))
        return process.exitValue() to log.readText()
    }
}
