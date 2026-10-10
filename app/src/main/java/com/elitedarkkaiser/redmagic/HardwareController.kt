package com.elitedarkkaiser.redmagic

import kotlin.math.abs
import kotlin.math.round
import java.util.concurrent.ConcurrentHashMap

object HardwareController {

    private data class RecentHardwareWrite(
        val command: String,
        val completedAtMs: Long
    )

    private val recentHardwareWrites =
        ConcurrentHashMap<String, RecentHardwareWrite>()

    private const val DUPLICATE_WRITE_SKIP_MS = 2_000L

    @Synchronized
    private fun execHardwareWrite(
        resource: String,
        command: String,
        rootSession: RootShell.Session? = null
    ): Boolean {
        if (!DeviceCompatibility.isSupportedDevice()) {
            android.util.Log.e(
                "HardwareController",
                "Blocked $resource write on unsupported device"
            )
            return if (resource == "led_control") LedWriteReceipt.record(false) else false
        }

        val now = android.os.SystemClock.elapsedRealtime()
        val previous = recentHardwareWrites[resource]

        if (
            previous != null &&
            previous.command == command &&
            (now - previous.completedAtMs) < DUPLICATE_WRITE_SKIP_MS
        ) {
            android.util.Log.d(
                "HardwareController",
                "skip duplicate write resource=$resource"
            )
            return true
        }

        // Legacy LED commands use semicolons: do not mistake the last cfg
        // write succeeding for a complete profile succeeding. Existing batch
        // scripts already manage set -e and their cleanup trap themselves.
        val checkedCommand = if (resource == "led_control" && !command.contains("set -e"))
            "( set -e; $command )" else command
        val succeeded = rootSession?.exec(checkedCommand) ?: RootShell.exec(checkedCommand)
        // Even a failed script may already have changed cooling power before
        // a later LED write failed. Invalidate those receipts on every attempt.
        if (resource == "led_control" && command.contains(FAN_ENABLE)) {
            recentHardwareWrites.remove("fan_control")
            DashboardSnapshot.invalidateHardwareCache()
        }
        if (resource == "led_control" && command.contains(PUMP_ENABLE)) {
            recentHardwareWrites.remove("pump_control")
            DashboardSnapshot.invalidateHardwareCache()
        }
        if (succeeded) {
            if (
                resource == "fan_control" ||
                resource == "pump_control"
            ) {
                DashboardSnapshot.invalidateHardwareCache()
            }
            recentHardwareWrites[resource] = RecentHardwareWrite(
                command = command,
                completedAtMs = android.os.SystemClock.elapsedRealtime()
            )
        }

        return if (resource == "led_control") LedWriteReceipt.record(succeeded) else succeeded
    }

    private const val FAN_ENABLE =
        DeviceCompatibility.Paths.FAN_ENABLE
    private const val FAN_LEVEL =
        DeviceCompatibility.Paths.FAN_LEVEL
    private const val FAN_PWM =
        DeviceCompatibility.Paths.FAN_PWM
    private const val FAN_RPM =
        DeviceCompatibility.Paths.FAN_RPM

    private const val PUMP_ENABLE =
        DeviceCompatibility.Paths.PUMP_ENABLE
    private const val PUMP_FREQ =
        DeviceCompatibility.Paths.PUMP_FREQ
    private const val PUMP_SPEED =
        DeviceCompatibility.Paths.PUMP_SPEED

    private const val LED_EFFECT =
        DeviceCompatibility.Paths.LED_EFFECT
    private const val LED_CFG =
        DeviceCompatibility.Paths.LED_CFG

    private const val SAR0_MODE =
        DeviceCompatibility.Paths.SAR0_MODE
    private const val SAR1_MODE =
        DeviceCompatibility.Paths.SAR1_MODE


    fun enableFan(enabled: Boolean): Boolean {
        return execHardwareWrite("fan_control", "echo ${if (enabled) 1 else 0} > $FAN_ENABLE")
    }

    fun isFanEnabled(): Boolean {
        return HardwareTelemetry.read().fanEnabled == true
    }

    fun setFanLevel(level: Int): Boolean {
        val safe = level.coerceIn(0, 5)
        val cmds = if (safe == 0) {
            "echo 0 > $FAN_LEVEL; echo 0 > $FAN_ENABLE"
        } else {
            "echo 1 > $FAN_ENABLE; echo $safe > $FAN_LEVEL"
        }
        return execHardwareWrite("fan_control", cmds)
    }

    fun setFanPwm(value: Int): Boolean {
        val safe = value.coerceIn(0, 255)
        val cmds = if (safe == 0) {
            "echo 0 > $FAN_PWM; echo 0 > $FAN_ENABLE"
        } else {
            "echo 1 > $FAN_ENABLE; echo $safe > $FAN_PWM"
        }
        return execHardwareWrite("fan_control", cmds)
    }

    fun readFanRpm(): Int? {
        return HardwareTelemetry.read().fanRpm
    }

    fun readFanLevel(): Int? {
        return HardwareTelemetry.read().fanLevel
    }

    fun enablePump(enabled: Boolean): Boolean {
        return execHardwareWrite("pump_control", "echo ${if (enabled) 1 else 0} > $PUMP_ENABLE")
    }

    fun setPumpProfile(profile: String): Boolean {
        val cmd = when (profile.lowercase()) {
            "slow" -> "echo 1 > $PUMP_ENABLE; echo 4 > $PUMP_FREQ; echo 40 > $PUMP_SPEED"
            "medium" -> "echo 1 > $PUMP_ENABLE; echo 4 > $PUMP_FREQ; echo 60 > $PUMP_SPEED"
            "quick" -> "echo 1 > $PUMP_ENABLE; echo 4 > $PUMP_FREQ; echo 80 > $PUMP_SPEED"
            "experimental" -> "echo 1 > $PUMP_ENABLE; echo 4 > $PUMP_FREQ; echo 90 > $PUMP_SPEED"
            "off" -> "echo 0 > $PUMP_ENABLE"
            else -> "echo 1 > $PUMP_ENABLE; echo 4 > $PUMP_FREQ; echo 80 > $PUMP_SPEED"
        }
        return execHardwareWrite("pump_control", cmd)
    }

    fun readPumpEnabled(): String? =
        HardwareTelemetry.read().pumpEnabled

    fun readPumpFreq(): String? =
        HardwareTelemetry.read().pumpFreq

    fun readPumpSpeed(): String? =
        HardwareTelemetry.read().pumpSpeed

    fun setFanLedEnabled(enabled: Boolean): Boolean {
        return if (enabled) {
            execHardwareWrite("led_control", "echo 0x3002005 > $LED_EFFECT; echo 1 > $LED_CFG")
        } else {
            execHardwareWrite("led_control", "echo 0x3000000 > $LED_EFFECT; echo 1 > $LED_CFG")
        }
    }

    fun setFanLedStockPreset(effectValue: String): Boolean {
        val safeEffectValue = effectValue.takeIf { it in FAN_LED_STOCK_PRESETS } ?: return LedWriteReceipt.record(false)
        return execHardwareWrite("led_control", "echo 1 > $FAN_ENABLE; echo $safeEffectValue > $LED_EFFECT; echo 1 > $LED_CFG")
    }

    fun setLogoLedEnabled(enabled: Boolean): Boolean {
        return if (enabled) {
            execHardwareWrite("led_control", "echo 0x1002001 > $LED_EFFECT; echo 1 > $LED_CFG")
        } else {
            execHardwareWrite("led_control", "echo 0x1000000 > $LED_EFFECT; echo 1 > $LED_CFG")
        }
    }

    fun setShoulderLedEnabled(enabled: Boolean): Boolean {
        return if (enabled) {
            execHardwareWrite("led_control", "echo 1 > $FAN_ENABLE; echo 0x2002005 > $LED_EFFECT; echo 1 > $LED_CFG")
        } else {
            execHardwareWrite("led_control", "echo 1 > $FAN_ENABLE; echo 0x2000000 > $LED_EFFECT; echo 1 > $LED_CFG")
        }
    }

    private val FAN_LED_STOCK_PRESETS = setOf(
        "0x3002101",
        "0x3002102",
        "0x3002103",
        "0x3002104",
        "0x3002105",
        "0x3002106",
        "0x3002107",
        "0x3002108"
    )

    private enum class LedZone(val zonePrefix: String, val enableFanFirst: Boolean) {
        LOGO("1", false),
        SHOULDER("2", true),
        FAN("3", true)
    }

    private fun mapUnifiedLedColor(color: Int, allowFanPalette: Boolean): Int {
        return when (color) {
            1 -> 1  // red
            3 -> 3  // orange
            4 -> 4  // yellow
            5 -> 5  // green
            6 -> 6  // cyan
            7 -> 7  // blue
            8 -> 8  // purple
            9 -> 9  // pink
            in 0x101..0x108 -> if (allowFanPalette) color else 1
            else -> 1
        }
    }

    private fun mapUnifiedLedEffect(effectName: String): Int {
        return when (effectName.lowercase()) {
            "steady" -> 0x002
            "breathe" -> 0x003
            "flashing" -> 0x004
            "blink" -> 0x006
            "rapid" -> 0x00a
            else -> 0x002
        }
    }

    private fun buildUnifiedLedEffectValue(zone: LedZone, effectName: String, color: Int): String {
        val colorCode = mapUnifiedLedColor(
            color,
            allowFanPalette = zone == LedZone.FAN
        )
        val effectCode = mapUnifiedLedEffect(effectName)
        return "0x${zone.zonePrefix}" +
            effectCode.toString(16).padStart(3, '0') +
            colorCode.toString(16).padStart(3, '0')
    }

    private fun setUnifiedLedEffect(zone: LedZone, effectName: String, color: Int): Boolean {
        val effectValue = buildUnifiedLedEffectValue(zone, effectName, color)
        val cmd = if (zone.enableFanFirst) {
            "echo 1 > $FAN_ENABLE; echo $effectValue > $LED_EFFECT; echo 1 > $LED_CFG"
        } else {
            "echo $effectValue > $LED_EFFECT; echo 1 > $LED_CFG"
        }
        return execHardwareWrite("led_control", cmd)
    }

    private val lastZoneCommands = mutableMapOf<String, String>()

    @Synchronized
    private fun applyZoneEffect(zone: LedZone, effectName: String, color: Int): Boolean {
        val name = when (zone) { LedZone.LOGO -> "logo"; LedZone.FAN -> "fan"; LedZone.SHOULDER -> "triggers" }
        val base = LedBrightness.effect(effectName)
        val normalizedEffect = if (zone == LedZone.FAN) FanLedPalette.normalizeEffect(base) else base
        val normalizedColor = if (zone == LedZone.FAN) FanLedPalette.normalizeColor(base, color) else color
        val selection = if (effectName.startsWith("areas:")) {
            if (zone != LedZone.LOGO || LogoBarSelection.decode(effectName) == null) return LedWriteReceipt.record(false)
            effectName
        } else if (effectName.startsWith("dim:")) {
            if (LedBrightness.decode(effectName) == null) return LedWriteReceipt.record(false)
            LedBrightness.encode(LedBrightness.level(effectName), normalizedEffect)
        } else normalizedEffect
        val replay = LedBrightness.command(name, selection, normalizedColor)
        if (replay == null && (LedBrightness.level(selection) != 255 || base.startsWith("split:") || effectName.startsWith("areas:"))) return LedWriteReceipt.record(false)
        val stock = "echo ${buildUnifiedLedEffectValue(zone, normalizedEffect, normalizedColor)} > $LED_EFFECT; echo 1 > $LED_CFG"
        val command = (if (zone.enableFanFirst) "echo 1 > $FAN_ENABLE;\n" else "") + (replay ?: stock)
        val previous = lastZoneCommands[name]
        val success = execHardwareWrite("led_control", command)
        if (success) lastZoneCommands[name] = command
        else if (previous != null) {
            recentHardwareWrites.remove("led_control")
            execHardwareWrite("led_control", previous)
        }
        return success
    }

    @Synchronized
    internal fun applyNotificationLeds(zones: List<NotificationLedBatch.Zone>, stopCooling: Boolean): Boolean {
        val command = NotificationLedBatch.command(zones, stopCooling) ?: return LedWriteReceipt.record(false)
        // One serialized write also invalidates fan-power cache through the
        // existing LED side-effect handling; never use per-zone fallback writes.
        recentHardwareWrites.remove("led_control")
        return try { execHardwareWrite("led_control", command) }
        finally {
            // Failed batches can also change power before their cleanup runs.
            recentHardwareWrites.remove("fan_control")
            recentHardwareWrites.remove("pump_control")
            DashboardSnapshot.invalidateHardwareCache()
        }
    }

    fun setShoulderLedEffect(effectName: String, color: Int): Boolean = applyZoneEffect(LedZone.SHOULDER, effectName, color)
    fun setLogoLedEffect(effectName: String, color: Int): Boolean = applyZoneEffect(LedZone.LOGO, effectName, color)
    fun setFanLedEffect(effectName: String, color: Int): Boolean = applyZoneEffect(LedZone.FAN, effectName, color)

    @Synchronized
    fun setRgbCycleFrame(
        effectName: String,
        logoColor: Int?,
        shoulderColor: Int?,
        fanColor: Int?,
        rootSession: RootShell.Session? = null,
        shoulderBottomColor: Int? = null,
        logoBrightness: Int = 255,
        shoulderBrightness: Int = 255,
        fanBrightness: Int = 255,
        barColor: Int? = null,
        barBrightness: Int = logoBrightness,
        logoEnabled: Boolean = true,
        barEnabled: Boolean = true
    ): Boolean {
        val commands = LedBrightness.cycleCommand(effectName, logoColor, shoulderColor, fanColor,
            shoulderBottomColor, logoBrightness, shoulderBrightness, fanBrightness,
            barColor, barBrightness, logoEnabled, barEnabled) ?: return LedWriteReceipt.record(false)
        if (commands.isEmpty()) return true
        val enable = if (shoulderColor != null || fanColor != null) "echo 1 > $FAN_ENABLE &&\n" else ""
        return execHardwareWrite("led_control", enable + commands, rootSession)
    }

    fun turnOffAllLeds(
        rootSession: RootShell.Session? = null
    ): Boolean {
        // Never let a previous successful shutdown suppress a new physical clear.
        recentHardwareWrites.remove("led_control")
        val cmd = LedShutdownCommand.build(LED_EFFECT, LED_CFG)
        return execHardwareWrite(
            "led_control",
            cmd,
            rootSession
        )
    }

    fun enableTriggers(
        rootSession: RootShell.Session? = null
    ): Boolean {
        return execHardwareWrite(
            "trigger_control",
            "echo 1 > $SAR0_MODE; echo 1 > $SAR1_MODE",
            rootSession
        )
    }

    fun disableTriggers(): Boolean {
        return execHardwareWrite("trigger_control", "echo 0 > $SAR0_MODE; echo 0 > $SAR1_MODE")
    }

    fun areTriggersEnabled(): Boolean {
        if (!DeviceCompatibility.isSupportedDevice()) {
            return false
        }

        val output = RootShell.execForOutput(
            "cat $SAR0_MODE 2>/dev/null; " +
                "cat $SAR1_MODE 2>/dev/null"
        ) ?: return false

        /*
         * NX809J reports each state as:
         * mode : 1, REG_WST(0x1a14) :0x1000000
         *
         * Parse the mode field instead of treating the complete
         * diagnostic line as an integer.
         */
        val states = Regex(
            """mode\s*:\s*(\d+)"""
        ).findAll(output)
            .mapNotNull {
                it.groupValues[1].toIntOrNull()
            }
            .toList()

        return states.size >= 2 &&
            states.take(2).all { it != 0 }
    }

    fun injectTap(x: Int, y: Int): Boolean {
        if (!DeviceCompatibility.isSupportedDevice()) {
            return false
        }
        return RootShell.exec("input tap $x $y")
    }

    private fun setSliderStockFunction(value: Int): Boolean {
        val cmd =
            "settings put system physical_key_function_app_value " +
                "cn.nubia.gamelauncher; " +
                "settings put system " +
                "fourth_physical_key_function_value $value"
        return execHardwareWrite("slider_control", cmd)
    }

    fun setSliderOpenCamera(): Boolean = setSliderStockFunction(1)

    fun setSliderOpenGameSpace(): Boolean = setSliderStockFunction(2)

    fun setSliderSoundMode(): Boolean = setSliderStockFunction(3)

    fun setSliderFlashlight(): Boolean = setSliderStockFunction(4)

    fun setSliderVoiceRecorder(): Boolean = setSliderStockFunction(5)

    fun setSliderLaunchApp(pkg: String): Boolean {
        if (
            !pkg.matches(
                Regex(
                    "[A-Za-z0-9_]+" +
                        "(?:\\.[A-Za-z0-9_]+)+"
                )
            )
        ) {
            return false
        }

        /*
         * Store the package first and switch to Launch App last.
         * Stock and app modes therefore transition as one ordered
         * root operation without exposing a stale stock target.
         */
        val cmd =
            "settings put system physical_key_function_app_value " +
                "$pkg; settings put system " +
                "fourth_physical_key_function_value 16"
        return execHardwareWrite("slider_control", cmd)
    }

    fun setSliderLaunchShortcut(
        packageName: String,
        shortcutId: String
    ): Boolean {
        val validPackage = packageName.matches(
            Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
        )
        val validShortcut =
            isValidMagicKeyShortcutId(shortcutId)

        if (!validPackage || !validShortcut) {
            return false
        }

        val target = "$packageName;$shortcutId"
        val quotedTarget = "'" +
            target.replace("'", "'\"'\"'") +
            "'"
        val cmd =
            "settings put system " +
                "physical_key_function_shortcut_value " +
                "$quotedTarget; settings put system " +
                "fourth_physical_key_function_value 17"

        return execHardwareWrite("slider_control", cmd)
    }

    fun disableSliderSystemHandling(): Boolean {
        return execHardwareWrite("slider_control", "settings put system fourth_physical_key_function_value 0")
    }

    fun readSliderState(): String? {
        if (!DeviceCompatibility.isSupportedDevice()) {
            return null
        }
        return RootShell.execForOutput("settings get global zte_keypad_slide_on_or_off")?.trim()
    }

    fun readTemperatureC(): Float? {
        return HardwareTelemetry.readTemperatureC()
    }

    fun readTemperatureF(): Float? {
        val c = readTemperatureC() ?: return null
        return (c * 9f / 5f) + 32f
    }

    fun chooseFanLevelForTempF(tempF: Float, curve: String): Int {
        return when (curve.toLowerCase()) {
            "quiet" -> when {
                tempF < 95f -> 0
                else -> 1
            }
            "turbo" -> when {
                tempF < 100f -> 4
                else -> 5
            }
            else -> when {
                tempF < 100f -> 2
                else -> 3
            }
        }
    }

    fun chooseAutoFanLevelForTempF(tempF: Float): Int {
        return when {
            tempF < 95f -> 0
            tempF < 104f -> 1
            tempF < 113f -> 2
            tempF < 122f -> 3
            tempF < 131f -> 4
            else -> 5
        }
    }

    fun applyFanCurve(curve: String): Int? {
        val tempF = readTemperatureF() ?: return null
        val level = chooseFanLevelForTempF(tempF, curve)
        setFanLevel(level)
        return level
    }

    fun applyAutoFanCurve(): Int? {
        val tempF = readTemperatureF() ?: return null
        val level = chooseAutoFanLevelForTempF(tempF)
        setFanLevel(level)
        return level
    }

    fun readCpuModel(): String {
        return "Snapdragon 8 Elite Gen 5"
    }

    fun readRamInfo(): String {
        val memInfo = RootShell.execForOutput("cat /proc/meminfo 2>/dev/null") ?: return "Unknown"
        val totalLine = memInfo.lines().firstOrNull { it.startsWith("MemTotal:") } ?: return "Unknown"
        val kb = totalLine.substringAfter("MemTotal:").trim().substringBefore(" ").toLongOrNull() ?: return "Unknown"

        val gb = kb / 1024.0 / 1024.0
        val rounded = round(gb).toInt()
        val supported = listOf(12, 16, 24)
        val nearest = supported.minByOrNull { abs(it - rounded) } ?: rounded

        return "$nearest GB"
    }

    fun readShortRomFingerprint(): String {
        val fp = RootShell.execForOutput("getprop ro.build.fingerprint")?.trim().orEmpty()
        if (fp.isNotBlank()) return fp

        val displayId = RootShell.execForOutput("getprop ro.build.display.id")?.trim().orEmpty()
        if (displayId.isNotBlank()) return displayId

        val incremental = RootShell.execForOutput("getprop ro.build.version.incremental")?.trim().orEmpty()
        if (incremental.isNotBlank()) return incremental

        return "Unknown"
    }


}
