package com.elitedarkkaiser.redmagic

/** Split selections travel with the existing effect field through saved profiles/restoration. */
internal object ShoulderLedSplit {
    data class Selection(val effect: String, val topRgb: Int, val bottomRgb: Int)
    data class Color(val id: Int, val label: String, val hex: String)

    val colors = listOf(
        Color(1, "Red", "#FF0000"), Color(3, "Orange", "#FF8000"),
        Color(4, "Yellow", "#FFFF00"), Color(5, "Green", "#00FF00"),
        Color(6, "Cyan", "#00FFFF"), Color(7, "Blue", "#0000FF"),
        Color(8, "Purple", "#FF00FF"), Color(9, "Pink", "#FF007E")
    )
    private data class Program(val type: Int, val size: Int, val hash: String, val starts: List<Int>)
    private val programs = mapOf(
        "steady" to Program(2, 228, "01e64e48ee9b7012025868c885e0c12dbfa49d16877a668a258fd343d395cc93", listOf(183, 215)),
        "breathe" to Program(3, 396, "3827fd2731060da9c801866676ad56d083641da40097c2e6942df2ea6a7223a3", listOf(183, 211, 239, 267, 299, 327, 355, 383)),
        "flashing" to Program(4, 284, "d989f3d2443999e2b12a650309ca5483e308f793ee587004c2c9ba40149d8107", listOf(183, 211, 243, 271)),
        "rapid" to Program(10, 332, "0052635ab29486f4f3ec5c698dc1e767c245d892433e21d548c6f28aac5cb711", listOf(151, 179, 207, 235, 263, 291))
    )

    fun decode(value: String): Selection? {
        val parts = LedBrightness.effect(value).split(':')
        if (parts.size != 4 || parts[0] != "split" || parts[1] !in programs) return null
        val top = parseRgb(parts[2]) ?: return null
        val bottom = parseRgb(parts[3]) ?: return null
        return Selection(parts[1], top, bottom)
    }

    fun encode(effect: String, topRgb: Int, bottomRgb: Int): String {
        require(effect in programs && topRgb in 0..0xffffff && bottomRgb in 0..0xffffff)
        return "split:$effect:${hex(topRgb)}:${hex(bottomRgb)}"
    }

    fun parseRgb(value: String): Int? {
        val digits = value.removePrefix("#")
        if (!digits.matches(Regex("[0-9a-fA-F]{6}"))) return null
        return digits.toIntOrNull(16)
    }

    fun hex(rgb: Int): String = rgb.toString(16).padStart(6, '0').uppercase()
    fun presetRgb(id: Int): Int = parseRgb(colors.firstOrNull { it.id == id }?.hex ?: "#00FF00")!!

    fun baseEffect(value: String): String = decode(value)?.effect ?: LedBrightness.effect(value)

    /** Replay only an exact, validated vendor program; never modify vendor files or IMAX. */
    fun command(value: String): String? {
        val selection = decode(value) ?: return null
        val program = programs.getValue(selection.effect)
        fun component(rgb: Int, channel: Int) = hex(rgb).substring(channel * 2, channel * 2 + 2).lowercase()
        val offsets = program.starts.joinToString(" ")
        val half = program.starts.size / 2
        val type = program.type.toString(16)
        val effect = "0x200${type}007"
        // A subshell keeps traps/set -e out of the persistent RootShell session.
        return """
            (
            set -e
            d=/sys/class/leds/aw22xxx_led
            [ -w "${'$'}d/reg" ] && [ -w "${'$'}d/effect" ] || exit 1
            tmp=${'$'}(mktemp -d /data/local/tmp/redmagic-split.XXXXXX) || exit 1
            started=0
            success=0
            cleanup() {
                trap - EXIT INT TERM
                if [ "${'$'}started" = 1 ] && [ "${'$'}success" != 1 ]; then
                    printf 'ff 00\n' > "${'$'}d/reg"
                    printf '%s\n' "${'$'}saved" > "${'$'}d/effect"
                fi
                rm -rf "${'$'}tmp"
            }
            trap cleanup EXIT
            trap 'exit 130' INT TERM
            cp /vendor/firmware/aw_touch${type}_7.bin "${'$'}tmp/program"
            actual=${'$'}(sha256sum "${'$'}tmp/program" | awk '{print ${'$'}1}')
            [ "${'$'}actual" = '${program.hash}' ] || { echo 'Unsupported trigger LED program'; exit 1; }
            saved=${'$'}(cat "${'$'}d/effect" | awk '{print ${'$'}NF}')
            printf '%s\n' "${'$'}saved" | grep -Eq '^0x[0-9a-fA-F]{1,8}${'$'}' || exit 1
            od -An -v -tx1 "${'$'}tmp/program" > "${'$'}tmp/bytes"
            awk '
            BEGIN {
                split("$offsets", starts)
                for (j=1; j<=${program.starts.size}; j++) {
                    red[starts[j]]=j; green[starts[j]+4]=j; blue[starts[j]+8]=j
                }
            }
            {
                for (i=1; i<=NF; i++) {
                    if (n % 2 == 0) reg=${'$'}i
                    else {
                        value=${'$'}i
                        if (n in red) {
                            if (reg!="06" || value!="00") bad=1
                            value=(red[n]<=$half ? "${component(selection.topRgb, 0)}" : "${component(selection.bottomRgb, 0)}"); changed++
                        }
                        if (n in green) {
                            if (reg!="06" || value!="54") bad=1
                            value=(green[n]<=$half ? "${component(selection.topRgb, 1)}" : "${component(selection.bottomRgb, 1)}"); changed++
                        }
                        if (n in blue) {
                            if (reg!="06" || value!="ff") bad=1
                            value=(blue[n]<=$half ? "${component(selection.topRgb, 2)}" : "${component(selection.bottomRgb, 2)}"); changed++
                        }
                        print reg, value
                    }
                    n++
                }
            }
            END { if (bad || n!=${program.size} || changed!=${program.starts.size * 3}) exit 1 }
            ' "${'$'}tmp/bytes" > "${'$'}tmp/pairs"
            started=1
            printf '$effect\n' > "${'$'}d/effect"
            while read -r reg value; do
                printf '%s %s\n' "${'$'}reg" "${'$'}value" > "${'$'}d/reg" || exit 1
            done < "${'$'}tmp/pairs"
            success=1
            )
        """.trimIndent()
    }
}
