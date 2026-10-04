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
    fun effect(value: String) = LogoBarSelection.decode(value)?.effect ?: decode(value)?.effect ?: value
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
        "fan:steady" to Program("aw_fan2_7.bin", 172, "7341ef29c7d6b3dc0ccd51c10cead411b512e10007de480c517199ebed10c93b", listOf(159)),
        "fan:breathe" to Program("aw_fan3_7.bin", 256, "c4cb1789e1c512e599879becf04d9f76b307dac10489bfd40a88fc459e6ae1e6", listOf(159,187,215,243)),
        "fan:flashing" to Program("aw_fan4_7.bin", 200, "bd9706d9eea83da5cef2798e775e57db3027385bc3f90368a7396270c590da31", listOf(159,187)),
        "fan:blink" to Program("aw_fan6_7.bin", 812, "1dd15210707d0f8dad04a2cc0aa9d59cd3bf5081a716ebad44f63a177e97a88f", listOf(255,283,311,339,367,399,427,455,483,511,543,571,599,627,655,687,715,743,771,799)),
        "fan:rapid" to Program("aw_fana_7.bin", 280, "359209ea93069a5451bde34d0f67a89c262e40ba7360c7c5c7d88f2e2da0eda1", listOf(195,223)),
        "logo:flashing" to Program("aw_cfg4_7.bin", 284, "9f5864d0c6cba08da6f2eb08ad44590049149fab78b0ee44d625b08eb46e680c", listOf(183,211,243,271)),
        "logo:rapid" to Program("aw_cfga_7.bin", 564, "6202f4725e681f68da451a4cba4de7d6ba4d7f06f3925ef3b09aa8db91ce3890", listOf(183,211,239,267,295,323,351,383,411,439,467,495,523,551))
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
    private val animatedPalettes = mapOf(
        "breathe:257" to Program("aw_fan3_101.bin", 700, "04be92740d8f25baeab2cad1e1acf10528db7f15a5fb9acd3897a418d141e4ce", listOf(255,283,311,339,371,399,427,455,487,515,543,571,603,631,659,687)),
        "breathe:258" to Program("aw_fan3_102.bin", 700, "5c4cde946409c6c88558bc9dbbaa6f22e987dd6c17842d48cc9e5db649a5b4ca", listOf(255,283,311,339,371,399,427,455,487,515,543,571,603,631,659,687)),
        "breathe:259" to Program("aw_fan3_103.bin", 700, "2db67e9c16d5b2c14fa2c176c62a7be48b158597c9da4cab8b71b3f77dc99e5c", listOf(255,283,311,339,371,399,427,455,487,515,543,571,603,631,659,687)),
        "breathe:260" to Program("aw_fan3_104.bin", 700, "4195b6b06511135a8c0f25eb43be595b632f6ff9bffd666f5b96e7e88224ffac", listOf(255,283,311,339,371,399,427,455,487,515,543,571,603,631,659,687)),
        "breathe:261" to Program("aw_fan3_105.bin", 700, "654635cf10c99dc94d5b552cc072154fc0d751d7ad80fffa54c5b9efd2eb0db4", listOf(255,283,311,339,371,399,427,455,487,515,543,571,603,631,659,687)),
        "breathe:262" to Program("aw_fan3_106.bin", 700, "84f94fb79cd9cc4d32450dead320e024a0794116f2ddec695236f4a1a0627d1e", listOf(255,283,311,339,371,399,427,455,487,515,543,571,603,631,659,687)),
        "breathe:263" to Program("aw_fan3_107.bin", 700, "62b025e1af140cf351fdf7d7575d2adab4e1b7e966480dc07e725b17b6106356", listOf(255,283,311,339,371,399,427,455,487,515,543,571,603,631,659,687)),
        "breathe:264" to Program("aw_fan3_108.bin", 700, "0779b431f97ac5180ba6f5ca46ed0360f2ae03f945de231e688646b63abb5335", listOf(255,283,311,339,371,399,427,455,487,515,543,571,603,631,659,687)),
        "flashing:257" to Program("aw_fan4_101.bin", 476, "bae4a1ec2fd1f9b77d4841c903788ef3bedf4c6b3f57ec0862951d241973adc2", listOf(255,283,315,343,375,403,435,463)),
        "flashing:258" to Program("aw_fan4_102.bin", 476, "3505f5f0f16630da14ae41070e4094676ec361f7a37a0f12e5f309ba1dc8c371", listOf(255,283,315,343,375,403,435,463)),
        "flashing:259" to Program("aw_fan4_103.bin", 476, "9a8a98fda0c28a9d6a52c16a6560fdab9aedbead3ae131074285933fe4b6ba69", listOf(255,283,315,343,375,403,435,463)),
        "flashing:260" to Program("aw_fan4_104.bin", 476, "0225801aeadc54e8f35420be4ed2488d9673db2b2a3133a9b3f024900a85bde6", listOf(255,283,315,343,375,403,435,463)),
        "flashing:261" to Program("aw_fan4_105.bin", 476, "88bf213f54d813b13bd0260820a87971698eaef23d3fa784cbc391d0eeb00cdf", listOf(255,283,315,343,375,403,435,463)),
        "flashing:262" to Program("aw_fan4_106.bin", 476, "bd7d10e63e2cd2944e6d2d8afdee5791c5d7a85be585a91f2a4d3ec4323f272c", listOf(255,283,315,343,375,403,435,463)),
        "flashing:263" to Program("aw_fan4_107.bin", 476, "8d3de3d734cf29cdc08cd69d98769700de68ec7fc61949bea3e0178715a51544", listOf(255,283,315,343,375,403,435,463)),
        "flashing:264" to Program("aw_fan4_108.bin", 476, "b4eb345622f66b50ead9c1ec5e517a4242734d971a5ff76c3448258c3083ef20", listOf(255,283,315,343,375,403,435,463)),
        "blink:257" to Program("aw_fan6_101.bin", 812, "c8ef592613f46ccbac828982bb332ee0bfb3b62d0e19c63f3173c3d556cff673", listOf(255,283,311,339,367,399,427,455,483,511,543,571,599,627,655,687,715,743,771,799)),
        "blink:258" to Program("aw_fan6_102.bin", 812, "8d5b60853645d4a96c5a3e7b41988b368e15f3e092478b8600105fff54a172cb", listOf(255,283,311,339,367,399,427,455,483,511,543,571,599,627,655,687,715,743,771,799)),
        "blink:259" to Program("aw_fan6_103.bin", 812, "12f459fc68fc2bbc1203affec7853af7a58225fb5a10250e122eeca559269f41", listOf(255,283,311,339,367,399,427,455,483,511,543,571,599,627,655,687,715,743,771,799)),
        "blink:260" to Program("aw_fan6_104.bin", 812, "c2150bff7d45f1a5a6405737c378c3a0185106660677c7d7f627ec8ff4f08112", listOf(255,283,311,339,367,399,427,455,483,511,543,571,599,627,655,687,715,743,771,799)),
        "blink:261" to Program("aw_fan6_105.bin", 812, "80298e767a4282c18279c9008510c1753fe39191b5b878cc5000a9aff24c11b8", listOf(255,283,311,339,367,399,427,455,483,511,543,571,599,627,655,687,715,743,771,799)),
        "blink:262" to Program("aw_fan6_106.bin", 812, "b59f9840ad4a17a261d4da1a0acdf1c0392ea57815581de15e47416d93557a78", listOf(255,283,311,339,367,399,427,455,483,511,543,571,599,627,655,687,715,743,771,799)),
        "blink:263" to Program("aw_fan6_107.bin", 812, "fae23d089412c3ad101c5323484850ce6319dfa36070cf5b30080449c3c4babb", listOf(255,283,311,339,367,399,427,455,483,511,543,571,599,627,655,687,715,743,771,799)),
        "blink:264" to Program("aw_fan6_108.bin", 812, "e8056ae3e4208451759a4691a508c5a38a36350ba979c5b5b77fdb20dea2a7e2", listOf(255,283,311,339,367,399,427,455,483,511,543,571,599,627,655,687,715,743,771,799)),
        "rapid:257" to Program("aw_fana_101.bin", 556, "53be2c0a3321ec7098aec7c44acd8f7d996940d38c2bc9ad82fb34c8edebb3eb", listOf(291,319,351,379,411,439,471,499)),
        "rapid:258" to Program("aw_fana_102.bin", 556, "6cd10716c322fd43e59663b1ccbd62e35abf3d65c96f1d92016c416b93bc0b85", listOf(291,319,351,379,411,439,471,499)),
        "rapid:259" to Program("aw_fana_103.bin", 556, "d254e31edb016a795ef72a7fc5b9ce23c20cc32e45509bf9f7243f3c47a3ee23", listOf(291,319,351,379,411,439,471,499)),
        "rapid:260" to Program("aw_fana_104.bin", 556, "44e17fcbb015db1d68cbbfe9762b2e9827a73e9bb2a59431929bcec32269e239", listOf(291,319,351,379,411,439,471,499)),
        "rapid:261" to Program("aw_fana_105.bin", 556, "de9d7f81947abacffe871edb0802b26964dbf84e4bb03642e5fbcfa1b0e1770f", listOf(291,319,351,379,411,439,471,499)),
        "rapid:262" to Program("aw_fana_106.bin", 556, "8aed4cbdfd79676d95cae5d4d144b592fcbc99399c9b7f3960e6c733abac2c81", listOf(291,319,351,379,411,439,471,499)),
        "rapid:263" to Program("aw_fana_107.bin", 556, "6cd10716c322fd43e59663b1ccbd62e35abf3d65c96f1d92016c416b93bc0b85", listOf(291,319,351,379,411,439,471,499)),
        "rapid:264" to Program("aw_fana_108.bin", 556, "7fdbd7c5252105e01254a72a58d047dc1476c9c3b1c9f97e87c47b69421d4238", listOf(291,319,351,379,411,439,471,499))
    )
    fun supported(zone: String, value: String, color: Int): Boolean {
        val base = effect(value)
        if (color !in listOf(1,3,4,5,6,7,8,9) && !(zone == "fan" && FanLedPalette.isPalette(color))) return false
        if (zone == "triggers") return ShoulderLedSplit.baseEffect(base) in listOf("steady", "breathe", "flashing", "rapid")
        return if (zone == "fan" && FanLedPalette.isPalette(color)) base == "steady" || "$base:$color" in animatedPalettes
        else "$zone:$base" in single
    }
    /** Build one serialized RGB cycle update; only due zones are replayed. */
    fun cycleCommand(effect: String, logoColor: Int?, triggerColor: Int?, fanColor: Int?,
        bottomColor: Int? = null, logoBrightness: Int = 255, triggerBrightness: Int = 255,
        fanBrightness: Int = 255, barColor: Int? = null, barBrightness: Int = logoBrightness,
        logoEnabled: Boolean = true, barEnabled: Boolean = true): String? {
        val commands = mutableListOf<String>()
        fun add(zone: String, color: Int?, brightness: Int, bottom: Int? = null): Boolean {
            if (color == null) return true
            if (brightness !in MIN..MAX) return false
            val base = if (zone == "logo" && barColor != null) LogoBarSelection(effect, logoEnabled, brightness,
                barEnabled, barColor, barBrightness).encode()
            else if (bottom != null) ShoulderLedSplit.encode(effect,
                ShoulderLedSplit.presetRgb(color), ShoulderLedSplit.presetRgb(bottom)) else effect
            val rendered = command(zone, if (base.startsWith("areas:")) base else encode(brightness, base), color) ?: return false
            commands.add(rendered)
            return true
        }
        if (!add("logo", logoColor, logoBrightness) ||
            !add("triggers", triggerColor, triggerBrightness, bottomColor) ||
            !add("fan", fanColor, fanBrightness)) return null
        return commands.joinToString(" &&\n")
    }

    /** Null means unsupported; callers must reject dimmed requests rather than silently use stock output. */
    fun command(zone: String, value: String, color: Int): String? {
        if (value.startsWith("dim:") && decode(value) == null) return null
        val areas = LogoBarSelection.decode(value)
        if (value.startsWith("areas:") && (zone != "logo" || areas == null)) return null
        if (!supported(zone, value, color)) return null
        val base = effect(value)
        val intensity = areas?.logoBrightness ?: level(value)
        if (zone == "triggers") {
            val split = ShoulderLedSplit.decode(base)
            val rgb = stockRgb(color)
            return ShoulderLedSplit.command(ShoulderLedSplit.encode(
                split?.effect ?: base, scale(split?.topRgb ?: rgb, intensity),
                scale(split?.bottomRgb ?: rgb, intensity)))
        }
        val palette = zone == "fan" && FanLedPalette.isPalette(color)
        val program = if (palette && base != "steady") animatedPalettes.getValue("$base:$color")
        else if (palette) Program("aw_fan2_${color.toString(16)}.bin", 364,
            paletteHashes[color - 0x101], listOf(255,287,319,351)) else single.getValue("$zone:$base")
        val rgb = if (areas?.logoEnabled == false) 0 else scale(stockRgb(color), intensity)
        val barRgb = if (areas == null) rgb else if (!areas.barEnabled) 0 else scale(stockRgb(areas.barColor), areas.barBrightness)
        val barComponents = listOf((barRgb shr 16) and 255, (barRgb shr 8) and 255, barRgb and 255)
        val components = listOf((rgb shr 16) and 255, (rgb shr 8) and 255, rgb and 255)
        val effectValue = "0x" + (if (zone == "logo") "1" else "3") +
            (when (base) { "breathe" -> "003"; "flashing" -> "004"; "blink" -> "006"; "rapid" -> "00a"; else -> "002" }) + (if (palette) color.toString(16) else "007")
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
                    ${if (palette) "for (k=0; k<=8; k+=4) b[o+k]=int((b[o+k]*$intensity+127)/255)" else "if (b[o]!=0 || b[o+4]!=84 || b[o+8]!=255) exit 1; b[o]=(j>count/2 && ${if (areas != null) 1 else 0}) ? ${barComponents[0]} : ${components[0]}; b[o+4]=(j>count/2 && ${if (areas != null) 1 else 0}) ? ${barComponents[1]} : ${components[1]}; b[o+8]=(j>count/2 && ${if (areas != null) 1 else 0}) ? ${barComponents[2]} : ${components[2]}"}
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
