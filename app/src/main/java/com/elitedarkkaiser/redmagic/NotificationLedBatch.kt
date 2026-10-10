package com.elitedarkkaiser.redmagic

/** NX809J Lights HAL sequence, submitted through one serialized root invocation. */
internal object NotificationLedBatch {
    data class Zone(val name: String, val effect: String, val color: Int)
    private val regions = mapOf("logo" to 1, "triggers" to 2, "fan" to 3)
    private val effects = mapOf("steady" to 2, "breathe" to 3, "flashing" to 4, "rapid" to 10)
    private val colors = setOf(1, 3, 4, 5, 6, 7, 8, 9)
    private fun write(region: Int, effect: Int, color: Int): String {
        val packed = (region shl 24) or (effect shl 12) or color
        // Matches set_light_aw22xxx: rgb indices 0/1/2, packed effect, cfg=2.
        return listOf(
            "printf '0 %x\\n' $region > /sys/class/leds/aw22xxx_led/rgb",
            "printf '1 %x\\n' $effect > /sys/class/leds/aw22xxx_led/rgb",
            "printf '2 %x\\n' $color > /sys/class/leds/aw22xxx_led/rgb",
            "printf '%x\\n' $packed > /sys/class/leds/aw22xxx_led/effect",
            "printf '2\\n' > /sys/class/leds/aw22xxx_led/cfg"
        ).joinToString("\n")
    }
    fun offCommand(): String = (1..3).joinToString("\n") { region ->
        // Proven screen-off shutdown: effect=zone/off, then cfg=1.
        "echo 0x${region}000000 > /sys/class/leds/aw22xxx_led/effect; " +
            "echo 1 > /sys/class/leds/aw22xxx_led/cfg"
    }
    fun command(zones: List<Zone>, stopCooling: Boolean): String? {
        if (zones.isEmpty() || zones.map { it.name }.distinct().size != zones.size) return null
        val writes = zones.map { zone ->
            val region = regions[zone.name] ?: return null
            val effect = effects[zone.effect] ?: return null
            if (zone.color !in colors) return null
            write(region, effect, zone.color)
        }
        val dollar = '$'
        return """
            (
            set -e
            d=/sys/class/leds/aw22xxx_led
            [ -w "${dollar}d/rgb" ] && [ -w "${dollar}d/effect" ] && [ -w "${dollar}d/cfg" ] || exit 1
            started=0
            success=0
            cleanup() {
                status=${dollar}?
                trap - EXIT INT TERM
                set +e
                if [ "${dollar}started" = 1 ] && [ "${dollar}success" != 1 ]; then
                    ${offCommand()}
                fi
                if [ "${dollar}started" = 1 ]; then
                    ${if (stopCooling) "printf '0\\n' > '${DeviceCompatibility.Paths.FAN_ENABLE}' || status=1; printf '0\\n' > '${DeviceCompatibility.Paths.PUMP_ENABLE}' || status=1" else ":"}
                fi
                exit "${dollar}status"
            }
            trap cleanup EXIT
            trap 'exit 130' INT TERM
            started=1
            ${offCommand()}
            ${if (zones.any { it.name == "fan" || it.name == "triggers" }) "printf '1\\n' > '${DeviceCompatibility.Paths.FAN_ENABLE}'" else ":"}
            ${writes.joinToString("\n")}
            success=1
            )
        """.trimIndent()
    }
}
