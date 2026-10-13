package com.elitedarkkaiser.redmagic

/** Fixed NX809J OTA packages; no package names come from profile or UI input. */
internal object OtaUpdatePolicy {
    val packages: List<String> = listOf(
        "com.zte.zdm",
        "com.zte.zdmdaemon",
        "com.zte.zdmdaemon.install"
    )

    enum class State { ENABLED, DISABLED, MISSING }

    data class Snapshot(val states: Map<String, State>) {
        fun matches(enabled: Boolean): Boolean = packages.all {
            states[it] == if (enabled) State.ENABLED else State.DISABLED
        }

        fun summary(): String = when {
            matches(true) -> "OTA: Enabled"
            matches(false) -> "OTA: Disabled"
            states.values.any { it == State.MISSING } -> "OTA: Some packages unavailable"
            else -> "OTA: Mixed package states"
        }

        fun details(): String = packages.joinToString("\n") { pkg ->
            "$pkg: ${when (states[pkg]) {
                State.ENABLED -> "Enabled"
                State.DISABLED -> "Disabled"
                else -> "Not installed for user 0"
            }}"
        }
    }

    fun changeCommand(enabled: Boolean): String = buildString {
        append("redmagic_ota_failed=0\n")
        for (pkg in packages) {
            append("pm ${if (enabled) "enable" else "disable-user"} --user 0 $pkg || redmagic_ota_failed=1\n")
        }
        append("(exit \"${'$'}redmagic_ota_failed\")")
    }

    fun readCommand(): String = """
        redmagic_ota_enabled=${'$'}(pm list packages --user 0 -e com.zte.zdm 2>/dev/null) || exit 1
        redmagic_ota_disabled=${'$'}(pm list packages --user 0 -d com.zte.zdm 2>/dev/null) || exit 1
        printf '%s\n' '__REDMAGIC_OTA_ENABLED__' "${'$'}redmagic_ota_enabled" '__REDMAGIC_OTA_DISABLED__' "${'$'}redmagic_ota_disabled" '__REDMAGIC_OTA_END__'
    """.trimIndent()

    fun parse(output: String?): Snapshot? {
        val lines = output?.lineSequence()?.map(String::trim)?.filter(String::isNotEmpty)?.toList() ?: return null
        if (lines.firstOrNull() != "__REDMAGIC_OTA_ENABLED__" || lines.lastOrNull() != "__REDMAGIC_OTA_END__") return null
        val separator = lines.indexOf("__REDMAGIC_OTA_DISABLED__")
        if (separator <= 0 || separator >= lines.lastIndex) return null
        val enabled = decodePackages(lines.subList(1, separator)) ?: return null
        val disabled = decodePackages(lines.subList(separator + 1, lines.lastIndex)) ?: return null
        if (enabled.intersect(disabled).isNotEmpty()) return null
        return Snapshot(packages.associateWith { pkg ->
            when (pkg) {
                in enabled -> State.ENABLED
                in disabled -> State.DISABLED
                else -> State.MISSING
            }
        })
    }

    private fun decodePackages(lines: List<String>): Set<String>? {
        if (lines.any { !it.matches(Regex("package:[A-Za-z0-9_.]+")) }) return null
        return lines.map { it.removePrefix("package:") }.toSet()
    }
}
