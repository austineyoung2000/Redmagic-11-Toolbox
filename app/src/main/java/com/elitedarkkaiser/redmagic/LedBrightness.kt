package com.elitedarkkaiser.redmagic

/** Brightness lives with the normal-zone effect so Save/Cancel restore the complete selection. */
internal object LedBrightness {
    const val MIN = 32
    const val MAX = 255
    data class Selection(val level: Int, val effect: String)
    fun decode(value: String): Selection? {
        if (!value.startsWith("dim:")) return null
        val parts = value.split(':', limit = 3)
        val level = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in MIN..MAX } ?: return null
        val effect = parts.getOrNull(2)?.takeIf { it.isNotBlank() && !it.startsWith("dim:") } ?: return null
        return Selection(level, effect)
    }
    fun level(value: String) = decode(value)?.level ?: MAX
    fun effect(value: String) = decode(value)?.effect ?: value
    fun encode(level: Int, effect: String): String {
        require(level in MIN..MAX)
        val base = this.effect(effect)
        require(base.isNotBlank() && !base.startsWith("dim:"))
        return "dim:$level:$base"
    }
    fun withEffect(previous: String, effect: String): String =
        if (previous.startsWith("dim:")) encode(level(previous), effect) else effect
    fun scale(rgb: Int, level: Int): Int {
        require(rgb in 0..0xffffff && level in MIN..MAX)
        fun channel(shift: Int) = (((rgb shr shift) and 255) * level + 127) / 255
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
    // Stock color payloads observed in the supplied vendor dumps. Split colors retain their own RGB.
    private fun stockRgb(color: Int): Int = when (color) {
        1 -> 0xff0000; 3 -> 0xff4000; 4 -> 0xffd700; 5 -> 0x06eb00
        6 -> 0x00ffff; 7 -> 0x0054ff; 8 -> 0xff00ff; 9 -> 0xf00020
        else -> 0x06eb00
    }
    private data class Program(val file: String, val size: Int, val hash: String, val offsets: List<Int>)
    private val single = mapOf(
        "logo:steady" to Program("aw_cfg2_7.bin", 228, "989f6b94603c742c0868d90135e3bbd486ee4ecaa85bcb77b7aae31575665090", listOf(183,215)),
        "logo:breathe" to Program("aw_cfg3_7.bin", 396, "f5efcf845344d8ff624e6b4977bd3581b15d822bc744754bb4449f295af91303", listOf(183,211,239,267,299,327,355,383)),
        "fan:steady" to Program("aw_fan2_7.bin", 172, "7341ef29c7d6b3dc0ccd51c10cead411b512e10007de480c517199ebed10c93b", listOf(159))
    )
    private val paletteHashes = listOf(
        "2e10910ecb73f7a685bc874dedf22d9bd9bfad07fc0d7f9edf99b9a3d147e163",
        "be0e161ac85d27209f152c1cbe08672ede12e17a5f73df374621c98db3f29b9a",
        "b7b7daeae63bf46af5210be694d9527cdcd10883ac03feb6fbd42067fda21a0d",
        "e1726ae8de8ee4fa2125d00da53c936a7b4fefea406f1786e3b8b8c4b3b85ee7",
        "d6bccfaff756872d61e22bd1d6a00726d9d071ddbcaed729c95f6e3a277cd468",
        "3d04f7ba4b383ff8c5f0238da8dd3665c7bedaee08b70da7e57cad7d03c16cea",
        "4c976bfcc1c943fca70b66e90f11c80da7bfeb5ca230b703fac9702aca2e194e",
        "6b000ca54abde045b2a2f8d4188e487a9689166eb5d0337fb12a7aa722a852ce"
    )
    fun supported(zone: String, value: String, color: Int): Boolean {
        val base = effect(value)
        if (color !in listOf(1,3,4,5,6,7,8,9) && !(zone == "fan" && FanLedPalette.isPalette(color))) return false
        if (zone == "triggers") return ShoulderLedSplit.baseEffect(base) in listOf("steady", "breathe", "flashing", "rapid")
        return if (zone == "fan" && FanLedPalette.isPalette(color)) base == "steady"
        else "$zone:$base" in single
    }
    /** Null means unsupported; callers must reject dimmed requests rather than silently use stock output. */
    fun command(zone: String, value: String, color: Int): String? {
        if (value.startsWith("dim:") && decode(value) == null) return null
        if (!supported(zone, value, color)) return null
        val base = effect(value)
        val intensity = level(value)
        if (zone == "triggers") {
            val split = ShoulderLedSplit.decode(base)
            val rgb = stockRgb(color)
            return ShoulderLedSplit.command(ShoulderLedSplit.encode(
                split?.effect ?: base, scale(split?.topRgb ?: rgb, intensity),
                scale(split?.bottomRgb ?: rgb, intensity)))
        }
        val palette = zone == "fan" && FanLedPalette.isPalette(color)
        val program = if (palette) Program("aw_fan2_${color.toString(16)}.bin", 364,
            paletteHashes[color - 0x101], listOf(255,287,319,351)) else single.getValue("$zone:$base")
        val rgb = scale(stockRgb(color), intensity)
        val components = listOf((rgb shr 16) and 255, (rgb shr 8) and 255, rgb and 255)
        val effectValue = "0x" + (if (zone == "logo") "1" else "3") +
            (if (base == "breathe") "003" else "002") + (if (palette) color.toString(16) else "007")
        return """
            (
            set -e
            d=/sys/class/leds/aw22xxx_led
            [ -w "${'$'}d/reg" ] && [ -w "${'$'}d/effect" ] || exit 1
            tmp=${'$'}(mktemp -d /data/local/tmp/redmagic-brightness.XXXXXX) || exit 1
            trap 'rm -rf "${'$'}tmp"' EXIT
            trap 'exit 130' INT TERM
            cp /vendor/firmware/${program.file} "${'$'}tmp/template"
            hash=${'$'}(sha256sum "${'$'}tmp/template" | awk '{print ${'$'}1}')
            [ "${'$'}hash" = '${program.hash}' ] || { echo 'Unsupported LED brightness template'; exit 1; }
            od -An -tu1 -v "${'$'}tmp/template" > "${'$'}tmp/bytes"
            awk '
            { for (i=1; i<=NF; i++) b[n++]=${'$'}i }
            END {
                if (n!=${program.size}) exit 1
                count=split("${program.offsets.joinToString(" ")}", offsets, " ")
                for (j=1; j<=count; j++) {
                    o=offsets[j]+0
                    if (b[o-1]!=6 || b[o+3]!=6 || b[o+7]!=6) exit 1
                    ${if (palette) "for (k=0; k<=8; k+=4) b[o+k]=int((b[o+k]*$intensity+127)/255)" else "if (b[o]!=0 || b[o+4]!=84 || b[o+8]!=255) exit 1; b[o]=${components[0]}; b[o+4]=${components[1]}; b[o+8]=${components[2]}"}
                }
                for (i=0; i<n; i+=2) printf "%02x %02x\n", b[i], b[i+1]
            }' "${'$'}tmp/bytes" > "${'$'}tmp/pairs"
            printf '$effectValue\n' > "${'$'}d/effect"
            exec 9>"${'$'}d/reg"
            while IFS= read -r pair; do printf '%s\n' "${'$'}pair" >&9 || exit 1; done < "${'$'}tmp/pairs"
            exec 9>&-
            )
        """.trimIndent()
    }
}
