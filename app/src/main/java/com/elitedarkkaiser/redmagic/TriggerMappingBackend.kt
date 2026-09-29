package com.elitedarkkaiser.redmagic

import android.content.Context
import android.util.Base64

/**
 * Selects the stock TGK implementation when it is available and falls back to
 * the optional root module on compatible custom ROMs. Both implementations are
 * explicitly disabled during transitions so they can never own the shoulder
 * triggers at the same time.
 */
object TriggerMappingBackend {
    const val MODULE_BACKEND = "Trigger Bridge module"
    const val MINIMUM_MODULE_VERSION = "0.3.0"
    private const val MINIMUM_MODULE_VERSION_CODE = 10

    /*
     * Root-only validation escape hatch. This exists solely to exercise the
     * custom-ROM Trigger Bridge lifecycle on stock hardware without disabling
     * or damaging the firmware TGK implementation.
     *
     * Normal users never create this exact sentinel, so production selection
     * remains native-TGK-first.
     */
    private const val FORCE_MODULE_TEST_MARKER =
        "/data/local/tmp/redmagic-force-trigger-bridge"
    private const val FORCE_MODULE_TEST_TOKEN =
        "force-module-v1"

    private fun forceModuleForTesting(): Boolean {
        return RootShell.exec(
            "[ -f '$FORCE_MODULE_TEST_MARKER' ] && " +
                "grep -qx '$FORCE_MODULE_TEST_TOKEN' " +
                "'$FORCE_MODULE_TEST_MARKER'"
        )
    }

    fun moduleInstalled(): Boolean {
        return RootShell.exec(
            "prop='${TriggerBridgeModule.MODULE_PROP_PATH}'; " +
                "[ -x '${TriggerBridgeModule.CONTROL_PATH}' ] && " +
                "[ -x '${TriggerBridgeModule.DAEMON_PATH}' ] && " +
                "grep -qx 'id=redmagic_trigger_bridge' " +
                "\"\$prop\" && " +
                "version_code=\$(sed -n " +
                "'s/^versionCode=//p' \"\$prop\" | head -n 1); " +
                "case \"\$version_code\" in " +
                "''|*[!0-9]*) exit 1 ;; esac; " +
                "[ \"\$version_code\" -ge " +
                "$MINIMUM_MODULE_VERSION_CODE ]"
        )
    }

    fun apply(
        context: Context,
        profile: NativeTgkProfile,
        mapping: NativeTgkOrientationMapping,
        displayWidth: Int,
        displayHeight: Int
    ): NativeTgkApplyResult {
        val nativeProbe = NativeTgkBridge.readState(context)
        val forceModule = forceModuleForTesting()

        if (!forceModule && nativeProbe.success) {
            TriggerBridgeModule.disableIfInstalled()

            val nativeResult = NativeTgkBridge.applyMapping(
                context = context,
                mapping = mapping,
                displayWidth = displayWidth,
                displayHeight = displayHeight,
                hapticsEnabled = profile.hapticsEnabled,
                leftBehavior = profile.effectiveLeftBehavior(),
                rightBehavior = profile.effectiveRightBehavior(),
                leftRapidFireCount =
                    profile.effectiveLeftRapidFireCount(),
                rightRapidFireCount =
                    profile.effectiveRightRapidFireCount()
            )

            if (nativeResult.success || !moduleInstalled()) {
                return nativeResult
            }

            /*
             * Some custom frameworks retain readable TGK transactions but
             * reject configuration. Release any partially armed vendor state
             * before falling back to the module.
             */
            runCatching { NativeTgkBridge.disable(context) }
        }

        if (!moduleInstalled()) {
            return NativeTgkApplyResult(
                success = false,
                backend = null,
                state = null,
                message = nativeProbe.message +
                    " | Trigger Bridge $MINIMUM_MODULE_VERSION or newer " +
                    "is not installed"
            )
        }

        /* Best effort: a partial vendor implementation must not remain armed. */
        runCatching { NativeTgkBridge.disable(context) }

        return TriggerBridgeModule.apply(profile)
    }

    fun readState(
        context: Context,
        activeBackend: String? = NativeTgkRuntimeState.activeBackend()
    ): NativeTgkApplyResult {
        if (activeBackend == MODULE_BACKEND) {
            return TriggerBridgeModule.readState()
        }

        val native = NativeTgkBridge.readState(context)
        if (native.success || !moduleInstalled()) {
            return native
        }

        return TriggerBridgeModule.readState()
    }

    fun disable(context: Context): NativeTgkApplyResult {
        val native = NativeTgkBridge.disable(context)
        val module = TriggerBridgeModule.disableIfInstalled()

        if (native.success) {
            return native.copy(
                message = native.message +
                    if (module?.success == true) {
                        "; Trigger Bridge inactive"
                    } else {
                        ""
                    }
            )
        }

        if (module?.success == true) {
            return module
        }

        return NativeTgkApplyResult(
            success = false,
            backend = null,
            state = null,
            message = listOfNotNull(
                native.message,
                module?.message
            ).joinToString(" | ")
        )
    }
}

private object TriggerBridgeModule {
    const val MODULE_DIR =
        "/data/adb/modules/redmagic_trigger_bridge"
    const val CONTROL_PATH = "$MODULE_DIR/bridge-control.sh"
    const val DAEMON_PATH = "$MODULE_DIR/bin/redmagic-trigger-bridge"
    const val MODULE_PROP_PATH = "$MODULE_DIR/module.prop"

    private const val STATE_DIR =
        "/data/adb/redmagic_trigger_bridge"
    private const val CONFIG_PATH = "$STATE_DIR/config.conf"
    private const val PID_PATH = "$STATE_DIR/bridge.pid"

    private fun present(): Boolean {
        return RootShell.exec(
            "[ -x '$CONTROL_PATH' ] && " +
                "grep -qx 'id=redmagic_trigger_bridge' " +
                "'$MODULE_PROP_PATH'"
        )
    }

    fun apply(profile: NativeTgkProfile): NativeTgkApplyResult {
        val encoded = Base64.encodeToString(
            buildConfig(profile).toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )

        val command = """
            redmagic_apply_trigger_bridge() {
                state='$STATE_DIR'
                control='$CONTROL_PATH'
                temporary='${CONFIG_PATH}.new'
                mkdir -p "${'$'}state" || return 1
                chmod 0700 "${'$'}state" || return 1
                printf '%s' '$encoded' |
                    base64 -d > "${'$'}temporary" || return 1
                chmod 0600 "${'$'}temporary" || return 1
                mv -f "${'$'}temporary" '$CONFIG_PATH' || return 1
                "${'$'}control" reload >/dev/null || return 1
                "${'$'}control" on >/dev/null || return 1
                sleep 1
                pid="${'$'}(cat '$PID_PATH' 2>/dev/null)"
                [ -n "${'$'}pid" ] &&
                    kill -0 "${'$'}pid" 2>/dev/null &&
                    [ "${'$'}("${'$'}control" status)" = active ]
            }
            redmagic_apply_trigger_bridge
            redmagic_status="${'$'}?"
            unset -f redmagic_apply_trigger_bridge
            (exit "${'$'}redmagic_status")
        """.trimIndent()

        if (!RootShell.exec(command)) {
            disableIfInstalled()
            return NativeTgkApplyResult(
                success = false,
                backend = TriggerMappingBackend.MODULE_BACKEND,
                state = null,
                message = "Trigger Bridge activation failed"
            )
        }

        return enabledResult("Trigger Bridge mapping enabled")
    }

    fun readState(): NativeTgkApplyResult {
        if (!TriggerMappingBackend.moduleInstalled()) {
            return NativeTgkApplyResult(
                success = false,
                backend = TriggerMappingBackend.MODULE_BACKEND,
                state = null,
                message = "Trigger Bridge " +
                    TriggerMappingBackend.MINIMUM_MODULE_VERSION +
                    " or newer is not installed"
            )
        }

        val output = RootShell.execForOutput(
            "control='$CONTROL_PATH'; " +
                "pid=\$(cat '$PID_PATH' 2>/dev/null); " +
                "state=\$(\"\$control\" status 2>/dev/null); " +
                "if [ -n \"\$pid\" ] && " +
                "kill -0 \"\$pid\" 2>/dev/null; then " +
                "printf '%s' \"\$state\"; else exit 1; fi"
        )?.trim()

        if (output == null) {
            return NativeTgkApplyResult(
                success = false,
                backend = TriggerMappingBackend.MODULE_BACKEND,
                state = null,
                message = "Trigger Bridge daemon is not running"
            )
        }

        val active = output == "active"
        return NativeTgkApplyResult(
            success = true,
            backend = TriggerMappingBackend.MODULE_BACKEND,
            state = NativeTgkState(
                globalEnabled = active,
                leftEnabled = active,
                rightEnabled = active,
                hapticsEnabled = null
            ),
            message = if (active) {
                "Trigger Bridge mapping active"
            } else {
                "Trigger Bridge inactive"
            }
        )
    }

    fun disableIfInstalled(): NativeTgkApplyResult? {
        /*
         * Disable even an outdated installation. It must never retain input
         * ownership merely because it is too old to qualify for activation.
         */
        if (!present()) {
            return null
        }

        val disabled = RootShell.exec(
            "'$CONTROL_PATH' off >/dev/null && " +
                "[ \"\$('$CONTROL_PATH' status)\" = inactive ]"
        )

        return NativeTgkApplyResult(
            success = disabled,
            backend = TriggerMappingBackend.MODULE_BACKEND,
            state = if (disabled) {
                NativeTgkState(
                    globalEnabled = false,
                    leftEnabled = false,
                    rightEnabled = false,
                    hapticsEnabled = null
                )
            } else {
                null
            },
            message = if (disabled) {
                "Trigger Bridge mapping disabled"
            } else {
                "Could not disable Trigger Bridge"
            }
        )
    }

    private fun enabledResult(message: String): NativeTgkApplyResult {
        return NativeTgkApplyResult(
            success = true,
            backend = TriggerMappingBackend.MODULE_BACKEND,
            state = NativeTgkState(
                globalEnabled = true,
                leftEnabled = true,
                rightEnabled = true,
                hapticsEnabled = null
            ),
            message = message
        )
    }

    private fun buildConfig(profile: NativeTgkProfile): String {
        val portrait = profile.mappingFor(NativeTgkOrientation.PORTRAIT)
        val landscape = profile.mappingFor(NativeTgkOrientation.LANDSCAPE)

        val portraitLeft = portrait?.left.normalizedOr(2500, 5000)
        val portraitRight = portrait?.right.normalizedOr(7500, 5000)
        val landscapeLeft = landscape?.left.normalizedOr(2500, 5000)
        val landscapeRight = landscape?.right.normalizedOr(7500, 5000)

        return buildString {
            appendLine("enabled=1")
            appendLine("grab_devices=1")
            appendLine("swap_triggers=0")
            appendLine("left_behavior=${profile.effectiveLeftBehavior().vendorMode}")
            appendLine("right_behavior=${profile.effectiveRightBehavior().vendorMode}")
            appendLine("left_rapid_fire=${profile.effectiveLeftRapidFireCount()}")
            appendLine("right_rapid_fire=${profile.effectiveRightRapidFireCount()}")
            appendLine("haptics_enabled=${if (profile.hapticsEnabled) 1 else 0}")

            for (rotation in 0..3) {
                val left = if (rotation % 2 == 0) {
                    portraitLeft
                } else {
                    landscapeLeft
                }
                val right = if (rotation % 2 == 0) {
                    portraitRight
                } else {
                    landscapeRight
                }
                appendLine("rot${rotation}_left_x=${left.first}")
                appendLine("rot${rotation}_left_y=${left.second}")
                appendLine("rot${rotation}_right_x=${right.first}")
                appendLine("rot${rotation}_right_y=${right.second}")
            }
        }
    }

    private fun NativeTgkRect?.normalizedOr(
        fallbackX: Int,
        fallbackY: Int
    ): Pair<Int, Int> {
        if (this == null || !isValid()) {
            return fallbackX to fallbackY
        }

        val centerX = (left.toLong() + right.toLong()) * 5_000L /
            captureWidth.toLong()
        val centerY = (top.toLong() + bottom.toLong()) * 5_000L /
            captureHeight.toLong()

        return centerX.toInt().coerceIn(0, 10_000) to
            centerY.toInt().coerceIn(0, 10_000)
    }
}
