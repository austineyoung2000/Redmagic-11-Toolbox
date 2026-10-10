package com.elitedarkkaiser.redmagic

/** One root invocation: prepare every zone, then replay complete validated vendor sequences. */
internal object NotificationLedBatch {
    data class Zone(val name: String, val effect: String, val color: Int)
    fun command(zones: List<Zone>, stopCooling: Boolean): String? {
        if (zones.isEmpty() || zones.map { it.name }.distinct().size != zones.size) return null
        val starts = mapOf("triggers" to "80", "fan" to "81", "logo" to "82")
        val prepared = zones.map { zone ->
            if (zone.name !in starts) return null
            LedBrightness.command(zone.name, zone.effect, zone.color, prepareOnly = true) ?: return null
        }
        val dollar = '$'
        val off = (1..3).joinToString("\n") {
            "printf '0x${it}000000\\n' > \"${dollar}d/effect\"; printf '1\\n' > \"${dollar}d/cfg\""
        }
        val preflight = zones.joinToString("\n") { zone ->
            """
            [ "${dollar}(tail -n 1 "${dollar}batch/${zone.name}.pairs")" = '05 ${starts.getValue(zone.name)}' ] || exit 1
            grep -Eq '^0x[0-9a-fA-F]{7}${dollar}' "${dollar}batch/${zone.name}.effect" || exit 1
            """.trimIndent()
        }
        val loads = zones.joinToString("\n") { zone ->
            // Preserve controller protocol: the complete final write belongs
            // to this program and must precede the next program's setup.
            """
            cat "${dollar}batch/${zone.name}.effect" > "${dollar}d/effect"
            while IFS= read -r pair; do printf '%s\n' "${dollar}pair" >&9 || exit 1; done < "${dollar}batch/${zone.name}.pairs"
            """.trimIndent()
        }
        val fanEnable = DeviceCompatibility.Paths.FAN_ENABLE
        val pumpEnable = DeviceCompatibility.Paths.PUMP_ENABLE
        return """
            (
            set -e
            d=/sys/class/leds/aw22xxx_led
            [ -w "${dollar}d/reg" ] && [ -w "${dollar}d/effect" ] && [ -w "${dollar}d/cfg" ] || exit 1
            batch=${dollar}(mktemp -d /data/local/tmp/redmagic-notification.XXXXXX) || exit 1
            export batch
            started=0
            success=0
            cleanup() {
                status=${dollar}?
                trap - EXIT INT TERM
                set +e
                if [ "${dollar}started" = 1 ] && [ "${dollar}success" != 1 ]; then
                    $off
                fi
                if [ "${dollar}started" = 1 ]; then
                    ${if (stopCooling) "printf '0\\n' > '$fanEnable' || status=1; printf '0\\n' > '$pumpEnable' || status=1" else ":"}
                fi
                rm -rf "${dollar}batch"
                exit "${dollar}status"
            }
            trap cleanup EXIT
            trap 'exit 130' INT TERM
            ${prepared.joinToString("\n") { "$it || exit 1" }}
            $preflight
            started=1
            $off
            ${if (zones.any { it.name == "triggers" || it.name == "fan" }) "printf '1\\n' > '$fanEnable'" else ":"}
            exec 9>"${dollar}d/reg"
            $loads
            exec 9>&-
            success=1
            )
        """.trimIndent()
    }
}
