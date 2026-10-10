package com.elitedarkkaiser.redmagic

/** Bookkeeping only: LED register commands and their order are not rewritten. */
internal object LightingRootCommand {
    data class Identity(val pid: Long, val startTicks: Long, val bootId: String)
    fun identity(line: String): Identity? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size != 3) return null
        val pid = parts[0].toLongOrNull()?.takeIf { it > 1 } ?: return null
        val ticks = parts[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val boot = parts[2].takeIf { it.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) } ?: return null
        return Identity(pid, ticks, boot)
    }
    fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    fun instrument(command: String, registry: String, revoked: String): String {
        val dollar = '$'
        val birth = """
            read redmagic_led_boot < /proc/sys/kernel/random/boot_id || exit 125
            read redmagic_led_stat < /proc/self/stat || exit 125
            redmagic_led_pid=${dollar}{redmagic_led_stat%% *}
            redmagic_led_fields=${dollar}{redmagic_led_stat##*) }
            set -- ${dollar}redmagic_led_fields
            shift 19 || exit 125
            printf '%s %s %s\n' "${dollar}redmagic_led_pid" "${dollar}1" "${dollar}redmagic_led_boot" >> ${quote(registry)} || exit 125
            [ ! -e ${quote(revoked)} ] || exit 125
        """.trimIndent()
        // Generated firmware programs use their own subshells. Register each
        // actual shell PID, not $$ (which can name the parent in a subshell).
        val registered = Regex("(?m)^(\\s*)\\((?=\\s)").replace(command) {
            "${it.groupValues[1]}(\n$birth\n"
        }
        return "$birth\n(\n$registered\n)"
    }

    fun cancel(identities: List<Identity>): String {
        val dollar = '$'
        return buildString {
            append("read redmagic_led_boot < /proc/sys/kernel/random/boot_id || exit 1\n")
            append("redmagic_led_cancel_failed=0\n")
            for (identity in identities.distinct().asReversed()) {
                append("""
                    if [ "${dollar}redmagic_led_boot" = '${identity.bootId}' ] && [ -e /proc/${identity.pid}/stat ]; then
                        if read redmagic_led_stat < /proc/${identity.pid}/stat 2>/dev/null; then
                            redmagic_led_fields=${dollar}{redmagic_led_stat##*) }
                            set -- ${dollar}redmagic_led_fields
                            shift 19 || exit 1
                            if [ "${dollar}1" = '${identity.startTicks}' ]; then
                                kill -KILL ${identity.pid} 2>/dev/null || {
                                    [ ! -e /proc/${identity.pid}/stat ] || redmagic_led_cancel_failed=1
                                }
                            fi
                        else
                            [ ! -e /proc/${identity.pid}/stat ] || redmagic_led_cancel_failed=1
                        fi
                    fi
                """.trimIndent())
                append('\n')
            }
            append("exit ${dollar}redmagic_led_cancel_failed")
        }
    }
}
