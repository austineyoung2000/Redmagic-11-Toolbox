package com.elitedarkkaiser.redmagic

data class HardwareTelemetrySnapshot(
    val fanEnabled: Boolean?,
    val fanLevel: Int?,
    val fanRpm: Int?,
    val pumpEnabled: String?,
    val pumpFreq: String?,
    val pumpSpeed: String?,
    val temperatureC: Float?
) {
    val temperatureF: Float?
        get() = temperatureC?.let {
            (it * 9f / 5f) + 32f
        }
}

object HardwareTelemetry {
    private const val HARDWARE_CACHE_MS = 30_000L

    private val readLock = Any()

    private var cachedSnapshot:
        HardwareTelemetrySnapshot? = null

    private var cachedSnapshotAtMs = 0L
    private val cacheGeneration = java.util.concurrent.atomic.AtomicLong()
    private var cachedGeneration = -1L
    private val retry = ReadRetryBackoff(5_000, 60_000)
    private var cachedRootGeneration = -1L
    private var controlSnapshot: HardwareTelemetrySnapshot? = null
    private var controlAtMs = 0L
    private var controlGeneration = -1L
    private var controlRootGeneration = -1L

    internal fun retryStatus(): String = "retryInMs=${retry.remainingMs(android.os.SystemClock.elapsedRealtime())}"

    /** Fresh control readback; never return stale display telemetry as proof of cooling state. */
    internal fun readForControl(): HardwareTelemetrySnapshot? = synchronized(readLock) {
        if (!DeviceCompatibility.isSupportedDevice()) return@synchronized null
        val now = android.os.SystemClock.elapsedRealtime()
        val generation = cacheGeneration.get()
        val rootGeneration = RootShell.stateGeneration()
        if (controlGeneration == generation && controlRootGeneration == rootGeneration && now - controlAtMs < 1_000) {
            return@synchronized controlSnapshot
        }
        if (!retry.canAttempt(now)) return@synchronized null
        val fresh = RootShell.execForOutput(hardwareCommand())?.let { parse(it, null) }?.takeIf(::hasHardware)
        if (fresh == null) retry.failed(android.os.SystemClock.elapsedRealtime())
        else {
            retry.succeeded()
            controlSnapshot = fresh
            controlAtMs = android.os.SystemClock.elapsedRealtime()
            controlGeneration = generation
            controlRootGeneration = rootGeneration
        }
        fresh
    }

    fun read(): HardwareTelemetrySnapshot {
        if (!DeviceCompatibility.isSupportedDevice()) {
            return emptySnapshot()
        }

        return synchronized(readLock) {
            val now =
                android.os.SystemClock.elapsedRealtime()
            val generation = cacheGeneration.get()
            val cached = cachedSnapshot
            val temperature =
                DeviceTemperatureMonitor.readTemperatureC()

            if (
                cached != null && cachedGeneration == generation && cachedRootGeneration == RootShell.stateGeneration() &&
                now - cachedSnapshotAtMs <
                HARDWARE_CACHE_MS
            ) {
                return@synchronized cached.copy(
                    temperatureC = temperature
                )
            }

            if (!retry.canAttempt(now)) return@synchronized (cached ?: emptySnapshot()).copy(temperatureC = temperature)
            val command = hardwareCommand()

            val output =
                RootShell.execForOutput(command)

            if (output == null) {
                retry.failed(android.os.SystemClock.elapsedRealtime())
                return@synchronized (
                    cached ?: emptySnapshot()
                ).copy(
                    temperatureC = temperature
                )
            }

            val fresh = parse(output, temperature)
            if (!hasHardware(fresh)) {
                retry.failed(android.os.SystemClock.elapsedRealtime())
                return@synchronized (cached ?: emptySnapshot()).copy(temperatureC = temperature)
            }
            retry.succeeded()
            cachedSnapshot = fresh
            cachedSnapshotAtMs = android.os.SystemClock.elapsedRealtime()
            cachedGeneration = generation
            cachedRootGeneration = RootShell.stateGeneration()
            fresh
        }
    }

    private fun hasHardware(value: HardwareTelemetrySnapshot): Boolean =
        value.fanEnabled != null || value.fanLevel != null || value.fanRpm != null ||
        value.pumpEnabled != null || value.pumpFreq != null || value.pumpSpeed != null

    private fun hardwareCommand(): String {
        return buildString {
            appendRead(
                "fan_enabled",
                DeviceCompatibility.Paths.FAN_ENABLE
            )
            appendRead(
                "fan_level",
                DeviceCompatibility.Paths.FAN_LEVEL
            )
            appendRead(
                "fan_rpm",
                DeviceCompatibility.Paths.FAN_RPM
            )
            appendRead(
                "pump_enabled",
                DeviceCompatibility.Paths.PUMP_ENABLE
            )
            appendRead(
                "pump_freq",
                DeviceCompatibility.Paths.PUMP_FREQ
            )
            appendRead(
                "pump_speed",
                DeviceCompatibility.Paths.PUMP_SPEED
            )
        }

    }

    private fun parse(output: String, temperature: Float?): HardwareTelemetrySnapshot {
        val values = output.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=')

                if (separator <= 0) {
                    null
                } else {
                    line.substring(0, separator) to
                        line.substring(
                            separator + 1
                        ).trim()
                }
            }
            .toMap()

        return HardwareTelemetrySnapshot(
            fanEnabled =
                values["fan_enabled"]
                    ?.toIntOrNull()
                    ?.takeIf { it == 0 || it == 1 }
                    ?.let { it == 1 },

            fanLevel =
                values["fan_level"]
                    ?.toIntOrNull()
                    ?.takeIf { it in 0..5 },

            fanRpm =
                values["fan_rpm"]
                    ?.toIntOrNull(),

            pumpEnabled =
                values["pump_enabled"]
                    .nonBlankOrNull(),

            pumpFreq =
                values["pump_freq"]
                    .nonBlankOrNull(),

            pumpSpeed =
                values["pump_speed"]
                    .nonBlankOrNull(),

            temperatureC = temperature
        )

    }

    fun readTemperatureC(): Float? {
        return DeviceTemperatureMonitor
            .readTemperatureC()
    }

    fun invalidateHardwareCache() {
        cacheGeneration.incrementAndGet()
    }

    private fun StringBuilder.appendRead(
        label: String,
        path: String
    ) {
        append(
            "value=\$(cat '$path' 2>/dev/null); "
        )
        append(
            "printf '$label=%s\\n' \"\$value\"; "
        )
    }

    private fun String?.nonBlankOrNull(): String? {
        return this?.takeIf {
            it.isNotBlank()
        }
    }

    private fun emptySnapshot():
        HardwareTelemetrySnapshot {
        return HardwareTelemetrySnapshot(
            fanEnabled = null,
            fanLevel = null,
            fanRpm = null,
            pumpEnabled = null,
            pumpFreq = null,
            pumpSpeed = null,
            temperatureC =
                DeviceTemperatureMonitor
                    .readTemperatureC()
        )
    }
}
