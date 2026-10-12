package com.elitedarkkaiser.redmagic

/** Off paths attempt both writes; any failure remains visible to the state cache. */
internal object CoolingShutdownCommand {
    fun fan(valuePath: String, enablePath: String): String {
        fun quote(path: String) = "'" + path.replace("'", "'\"'\"'") + "'"
        return "( redmagic_fan_off_status=0; echo 0 > ${quote(valuePath)} || redmagic_fan_off_status=1; " +
            "echo 0 > ${quote(enablePath)} || redmagic_fan_off_status=1; exit ${'$'}redmagic_fan_off_status )"
    }
}
