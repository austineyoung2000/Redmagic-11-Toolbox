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
                cached != null && cachedGeneration == generation &&
                now - cachedSnapshotAtMs <
                HARDWARE_CACHE_MS
            ) {
                return@synchronized cached.copy(
                    temperatureC = temperature
                )
            }

            /*
             * Only hardware values that require root remain
             * in this command. Temperature is read directly
             * through DeviceTemperatureMonitor.
             */
            val command = buildString {
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

            val output =
                RootShell.execForOutput(command)

            if (output == null) {
                return@synchronized (
                    cached ?: emptySnapshot()
                ).copy(
                    temperatureC = temperature
                )
            }

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

            val fresh = HardwareTelemetrySnapshot(
                fanEnabled =
                    values["fan_enabled"]
                        ?.toIntOrNull()
                        ?.let { it != 0 },

                fanLevel =
                    values["fan_level"]
                        ?.toIntOrNull()
                        ?.coerceIn(0, 5),

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

            cachedSnapshot = fresh
            cachedSnapshotAtMs = now
            cachedGeneration = generation
            fresh
        }
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
