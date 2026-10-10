package com.elitedarkkaiser.redmagic

/** The proven NX809J three-region clear, with an aggregate shell receipt. */
internal object LedShutdownCommand {
    fun build(effectPath: String, cfgPath: String): String = buildString {
        append("( led_shutdown_status=0; ")
        for (zone in 1..3) {
            append("echo 0x${zone}000000 > $effectPath || led_shutdown_status=1; ")
            append("echo 1 > $cfgPath || led_shutdown_status=1; ")
        }
        append("exit \$led_shutdown_status )")
    }
}
