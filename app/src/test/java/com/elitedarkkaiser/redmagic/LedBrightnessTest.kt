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
        assertFalse(LedBrightness.supported("logo", "blink", 7))
        assertNull(LedBrightness.command("logo", "dim:32:blink", 7))
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
        ) + (0x101..0x108).map { Case("fan", "steady", it, "aw_fan2_${it.toString(16)}", listOf(255,287,319,351)) } + listOf(
            Case("fan", "breathe", 7, "aw_fan3_7", listOf(159,187,215,243)),
            Case("fan", "flashing", 7, "aw_fan4_7", listOf(159,187)),
            Case("fan", "blink", 7, "aw_fan6_7", listOf(255,283,311,339,367,399,427,455,483,511,543,571,599,627,655,687,715,743,771,799)),
            Case("fan", "rapid", 7, "aw_fana_7", listOf(195,223)),
            Case("logo", "flashing", 7, "aw_cfg4_7", listOf(183,211,243,271)),
            Case("logo", "rapid", 7, "aw_cfga_7", listOf(183,211,239,267,295,323,351,383,411,439,467,495,523,551))
        ) + (0x101..0x108).map { Case("fan", "breathe", it, "aw_fan3_${it.toString(16)}", listOf(255,283,311,339,371,399,427,455,487,515,543,571,603,631,659,687)) } + (0x101..0x108).map { Case("fan", "flashing", it, "aw_fan4_${it.toString(16)}", listOf(255,283,315,343,375,403,435,463)) } + (0x101..0x108).map { Case("fan", "blink", it, "aw_fan6_${it.toString(16)}", listOf(255,283,311,339,367,399,427,455,483,511,543,571,599,627,655,687,715,743,771,799)) } + (0x101..0x108).map { Case("fan", "rapid", it, "aw_fana_${it.toString(16)}", listOf(291,319,351,379,411,439,471,499)) }
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
                val type = when (case.effect) { "breathe" -> "003"; "flashing" -> "004"; "blink" -> "006"; "rapid" -> "00a"; else -> "002" }
                val lamp = if (case.zone == "logo") "1" else "3"
                val color = if (case.color >= 0x101) case.color.toString(16) else "007"
                assertEquals("0x$lamp$type$color\n", dir.resolve("device/effect").toFile().readText())
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

    @Test fun rgbCycleUsesIndependentBrightnessAndOnlyDueZones() {
        val case = Case("logo", "steady", 7, "aw_cfg2_7", listOf(183,215))
        withDevice(case) { dir, logo ->
            val fanCase = Case("fan", "steady", 7, "aw_fan2_7", listOf(159))
            val fan = fixture(fanCase)
            Files.write(dir.resolve("firmware/${fanCase.file}.bin"), fan)
            for ((logoLevel, fanLevel) in listOf(32 to 128, 255 to 255)) {
                dir.resolve("device/reg").toFile().writeText("")
                val rendered = LedBrightness.cycleCommand("steady", 7, null, 7,
                    logoBrightness=logoLevel, fanBrightness=fanLevel)!!
                val result = execute(simulated(rendered, dir), dir)
                assertEquals(result.second, 0, result.first)
                val expected = logo.clone() + fan.clone()
                for ((offsets, start, level) in listOf(Triple(case.offsets, 0, logoLevel), Triple(fanCase.offsets, logo.size, fanLevel))) {
                    for (o in offsets) for (k in listOf(0,4,8)) {
                        val i = start+o+k
                        expected[i] = (((expected[i].toInt() and 255)*level+127)/255).toByte()
                    }
                }
                assertArrayEquals(expected, replayed(dir))
            }
        }
        assertNull(LedBrightness.cycleCommand("steady", 7, null, null, logoBrightness=31))
        assertEquals("", LedBrightness.cycleCommand("steady", null, null, null))
        val split = LedBrightness.cycleCommand("breathe", null, 7, null, 8, triggerBrightness=96)!!
        assertEquals(LedBrightness.command("triggers", LedBrightness.encode(96,
            ShoulderLedSplit.encode("breathe", 0x0000ff, 0xff00ff)), 7), split)
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

    @Test fun logoBarProgramsPreserveRoutingAndIndependentColorsBrightnessAndOff() {
        val cases = listOf(
            Case("logo", "steady", 1, "aw_cfg2_7", listOf(183,215)),
            Case("logo", "breathe", 1, "aw_cfg3_7", listOf(183,211,239,267,299,327,355,383)),
            Case("logo", "flashing", 1, "aw_cfg4_7", listOf(183,211,243,271)),
            Case("logo", "rapid", 1, "aw_cfga_7", listOf(183,211,239,267,295,323,351,383,411,439,467,495,523,551))
        )
        for (case in cases) withDevice(case) { dir, original ->
            for ((logoOn, barOn) in listOf(true to true, false to true, true to false, false to false)) {
                for ((logoLevel, barLevel) in listOf(32 to 255, 255 to 32, 128 to 96)) {
                    dir.resolve("device/reg").toFile().writeText("")
                    val selection = LogoBarSelection(case.effect, logoOn, logoLevel, barOn, 5, barLevel)
                    val rendered = LedBrightness.command("logo", selection.encode(), 1)!!
                    val result = execute(simulated(rendered, dir), dir)
                    assertEquals(result.second, 0, result.first)
                    val expected = original.clone()
                    case.offsets.forEachIndexed { index, o ->
                        val bar = index >= case.offsets.size / 2
                        val rgb = if (bar) { if (barOn) LedBrightness.scale(0x06eb00, barLevel) else 0 }
                            else { if (logoOn) LedBrightness.scale(0xff0000, logoLevel) else 0 }
                        expected[o] = (rgb shr 16).toByte()
                        expected[o+4] = (rgb shr 8).toByte()
                        expected[o+8] = rgb.toByte()
                    }
                    assertArrayEquals("${case.effect}: only mapped RGB groups may change", expected, replayed(dir))
                }
            }
        }
    }

    @Test fun barCycleComposesBothAreasAndRejectsMalformedProfilesBeforeWrites() {
        val selection = LogoBarSelection("breathe", true, 32, true, 5, 255)
        assertEquals(LedBrightness.command("logo", selection.encode(), 1),
            LedBrightness.cycleCommand("breathe", 1, null, null,
                logoBrightness=32, barColor=5, barBrightness=255))
        for (bad in listOf("areas:steady:1:31:1:5:255", "areas:blink:1:255:1:5:255",
            "areas:steady:1:255:1:2:255", "areas:steady:2:255:1:5:255", "areas:steady")) {
            assertNull(LogoBarSelection.decode(bad))
            assertNull(LedBrightness.command("logo", bad, 1))
        }
        assertNull(LedBrightness.command("fan", selection.encode(), 1))
        val case = Case("logo", "steady", 1, "aw_cfg2_7", listOf(183,215))
        withDevice(case) { dir, original ->
            original[20] = (original[20].toInt() xor 1).toByte()
            Files.write(dir.resolve("firmware/${case.file}.bin"), original)
            assertNotEquals(0, execute(simulated(LedBrightness.command("logo",
                selection.copy(effect="steady").encode(), 1)!!, dir), dir).first)
            assertEquals("", dir.resolve("device/reg").toFile().readText())
            assertEquals("effect = 0x1002001\n", dir.resolve("device/effect").toFile().readText())
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
            .let { simulated(it, dir) }
    private fun simulated(rendered: String, dir: java.nio.file.Path): String = rendered
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
