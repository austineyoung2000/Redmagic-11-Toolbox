package com.elitedarkkaiser.redmagic

import android.os.Build
import java.io.File
import java.util.Locale

/**
 * Single source of truth for the supported device identity and
 * the NX809J vendor/kernel interface layout.
 */
object DeviceCompatibility {
    const val REQUIRED_MODEL = "NX809J"

    data class Identity(
        val buildModel: String,
        val productModel: String,
        val vendorModel: String,
        val odmModel: String,
        val productName: String,
        val deviceName: String,
        val marketName: String
    ) {
        val detectedModel: String
            get() = listOf(
                buildModel,
                productModel,
                vendorModel,
                odmModel,
                productName,
                deviceName
            ).firstOrNull {
                DeviceCompatibility
                    .matchesRequiredModel(it)
            } ?: buildModel.ifBlank { "Unknown" }

        val supported: Boolean
            get() = listOf(
                buildModel,
                productModel,
                vendorModel,
                odmModel,
                productName,
                deviceName
            ).any {
                DeviceCompatibility
                    .matchesRequiredModel(it)
            }
    }

    object Paths {
        const val FAN_ENABLE =
            "/sys/kernel/fan/fan_enable"
        const val FAN_LEVEL =
            "/sys/kernel/fan/fan_speed_level"
        const val FAN_PWM =
            "/sys/kernel/fan/fan_speed_pwm"
        const val FAN_RPM =
            "/sys/kernel/fan/fan_speed_count"

        const val PUMP_ENABLE =
            "/proc/driver/micropump/enable"
        const val PUMP_FREQ =
            "/proc/driver/micropump/freq"
        const val PUMP_SPEED =
            "/proc/driver/micropump/speed"

        const val LED_EFFECT =
            "/sys/class/leds/aw22xxx_led/effect"
        const val LED_CFG =
            "/sys/class/leds/aw22xxx_led/cfg"

        const val SAR0_MODE =
            "/sys/class/leds/sar0/mode_operation"
        const val SAR1_MODE =
            "/sys/class/leds/sar1/mode_operation"

        const val GYRO_ENABLE =
            "/sys/class/sensors_sensitivity/gyro/gyro_enable"
        const val GYRO_X =
            "/sys/class/sensors_sensitivity/gyro/gyro_x"
        const val GYRO_Y =
            "/sys/class/sensors_sensitivity/gyro/gyro_y"
    }

    fun isStockRedmagicFirmware(): Boolean {
        val fingerprint = Build.FINGERPRINT.orEmpty()
        val brand = Build.BRAND.orEmpty()
        val manufacturer = Build.MANUFACTURER.orEmpty()

        return isSupportedDevice() &&
            Build.VERSION.SDK_INT >= 36 &&
            fingerprint.startsWith(
                "REDMAGIC/NX809J",
                ignoreCase = true
            ) &&
            (
                brand.equals("REDMAGIC", ignoreCase = true) ||
                    manufacturer.equals(
                        "REDMAGIC",
                        ignoreCase = true
                    ) ||
                    manufacturer.equals("nubia", ignoreCase = true) ||
                    manufacturer.equals("ZTE", ignoreCase = true)
                )
    }

    @Volatile
    private var cachedIdentity: Identity? = null

    fun identity(): Identity {
        cachedIdentity?.let { return it }

        return synchronized(this) {
            cachedIdentity ?: Identity(
                buildModel = Build.MODEL.orEmpty(),
                productModel = property(
                    "ro.product.model"
                ),
                vendorModel = property(
                    "ro.product.vendor.model"
                ),
                odmModel = property(
                    "ro.product.odm.model"
                ),
                productName = Build.PRODUCT.orEmpty()
                    .ifBlank {
                        property("ro.product.name")
                    },
                deviceName = Build.DEVICE.orEmpty()
                    .ifBlank {
                        property("ro.product.device")
                    },
                marketName = property(
                    "ro.product.marketname"
                )
            ).also {
                cachedIdentity = it
            }
        }
    }

    fun isSupportedDevice(): Boolean {
        val frameworkIdentity = listOf(
            Build.MODEL.orEmpty(),
            Build.PRODUCT.orEmpty(),
            Build.DEVICE.orEmpty()
        )

        if (frameworkIdentity.any(::matchesRequiredModel)) {
            return true
        }

        return identity().supported
    }

    fun property(name: String): String {
        return runCatching {
            ProcessBuilder(
                "/system/bin/getprop",
                name
            )
                .redirectErrorStream(true)
                .start()
                .inputStream
                .bufferedReader()
                .use { it.readText().trim() }
        }.getOrDefault("")
    }

    /**
     * Thermal-zone numbering may be presented differently by
     * stock Android and custom ROM framework layers. Prefer the
     * confirmed NX809J CPU LLC sensor by its type, then CPU
     * sensors, before falling back to the known zone numbers.
     */
    fun temperaturePaths(): List<String> {
        val detected = sequenceOf(
            File("/sys/class/thermal"),
            File("/sys/devices/virtual/thermal")
        ).flatMap { root ->
            runCatching {
                root.listFiles()
                    .orEmpty()
                    .asSequence()
            }.getOrDefault(emptySequence())
                .filter {
                    runCatching {
                        it.isDirectory &&
                            it.name.startsWith(
                                "thermal_zone"
                            )
                    }.getOrDefault(false)
                }
        }.mapNotNull { zone ->
            val type = runCatching {
                File(zone, "type")
                    .readText()
                    .trim()
                    .lowercase(Locale.ROOT)
            }.getOrNull() ?: return@mapNotNull null

            val rank = when {
                type == "cpullc-0-0" -> 0
                type.startsWith("cpullc") -> 1
                type.startsWith("cpu-") -> 2
                else -> return@mapNotNull null
            }

            rank to File(zone, "temp").absolutePath
        }.sortedBy { it.first }
            .map { it.second }
            .toList()

        val knownFallbacks = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/class/thermal/thermal_zone2/temp",
            "/sys/class/thermal/thermal_zone3/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/devices/virtual/thermal/thermal_zone1/temp",
            "/sys/devices/virtual/thermal/thermal_zone2/temp",
            "/sys/devices/virtual/thermal/thermal_zone3/temp"
        )

        return (detected + knownFallbacks).distinct()
    }

    private fun matchesRequiredModel(value: String): Boolean {
        val normalized = value
            .trim()
            .uppercase(Locale.ROOT)

        return normalized == REQUIRED_MODEL ||
            normalized.matches(
                Regex("^${REQUIRED_MODEL}-[A-Z0-9]+$")
            )
    }
}
