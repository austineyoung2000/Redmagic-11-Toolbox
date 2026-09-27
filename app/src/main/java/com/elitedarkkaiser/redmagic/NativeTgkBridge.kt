package com.elitedarkkaiser.redmagic

import android.content.Context
import android.util.Log
import java.util.concurrent.TimeUnit

data class NativeTgkState(
    val globalEnabled: Boolean,
    val leftEnabled: Boolean,
    val rightEnabled: Boolean,
    val hapticsEnabled: Boolean?
) {
    fun mappingEnabled(): Boolean {
        return globalEnabled &&
            leftEnabled &&
            rightEnabled
    }

    fun fullyDisabled(): Boolean {
        return !globalEnabled &&
            !leftEnabled &&
            !rightEnabled
    }
}

data class NativeTgkApplyResult(
    val success: Boolean,
    val backend: String?,
    val state: NativeTgkState?,
    val message: String
)

object NativeTgkBridge {
    private const val TAG = "RedmagicNativeTgk"

    const val LEFT_KEY_CODE = 137
    const val RIGHT_KEY_CODE = 138

    private const val STOCK_TGK_VERSION = 40
    private const val CONFIG_SETTLE_MS = 2_000L
    private const val ENABLE_SETTLE_MS = 1_000L

    @Synchronized
    fun readState(context: Context): NativeTgkApplyResult {
        val appContext = context.applicationContext
        val failures = mutableListOf<String>()
        val backends = listOf<() -> Backend>(
            { ServiceCallBackend() },
            { ReflectionBackend(appContext) }
        )

        backends.forEach { createBackend ->
            val backend = try {
                createBackend()
            } catch (error: Throwable) {
                failures += errorSummary(
                    "backend initialization",
                    error
                )
                return@forEach
            }

            try {
                val state = backend.readState()
                return NativeTgkApplyResult(
                    success = true,
                    backend = backend.name,
                    state = state,
                    message = "Native TGK state read successfully"
                )
            } catch (error: Throwable) {
                failures += errorSummary(backend.name, error)
            }
        }

        return NativeTgkApplyResult(
            success = false,
            backend = null,
            state = null,
            message = failures.joinToString(
                separator = " | ",
                prefix = "Could not read native TGK state: "
            )
        )
    }

    @Synchronized
    fun applyMapping(
        context: Context,
        mapping: NativeTgkOrientationMapping,
        displayWidth: Int,
        displayHeight: Int,
        hapticsEnabled: Boolean,
        leftBehavior: NativeTgkTriggerBehavior =
            NativeTgkTriggerBehavior.SINGLE_TOUCH,
        rightBehavior: NativeTgkTriggerBehavior =
            NativeTgkTriggerBehavior.SINGLE_TOUCH,
        leftRapidFireCount: Int = 0,
        rightRapidFireCount: Int = 0
    ): NativeTgkApplyResult {
        if (!mapping.isComplete()) {
            return NativeTgkApplyResult(
                success = false,
                backend = null,
                state = null,
                message = "Both L and R mappings are required"
            )
        }

        if (displayWidth <= 1 || displayHeight <= 1) {
            return NativeTgkApplyResult(
                success = false,
                backend = null,
                state = null,
                message = "Display dimensions are unavailable"
            )
        }

        val left = mapping.left!!.scaledTo(
            displayWidth,
            displayHeight
        )
        val right = mapping.right!!.scaledTo(
            displayWidth,
            displayHeight
        )

        val failures = mutableListOf<String>()

        backendFactories(context).forEach { createBackend ->
            val backend = try {
                createBackend()
            } catch (error: Throwable) {
                failures += errorSummary(
                    "backend initialization",
                    error
                )
                return@forEach
            }

            try {
                backend.disable()
                backend.setDriveEnabled(true)
                backend.setVersion(STOCK_TGK_VERSION)

                backend.setPoint(
                    LEFT_KEY_CODE,
                    left
                )
                backend.setMode(
                    modeForBehavior(
                        leftBehavior,
                        leftRapidFireCount
                    ),
                    LEFT_KEY_CODE
                )
                if (
                    leftBehavior ==
                    NativeTgkTriggerBehavior.RAPID_FIRE
                ) {
                    backend.setRapidFireCount(
                        leftRapidFireCount,
                        LEFT_KEY_CODE
                    )
                }

                backend.setPoint(
                    RIGHT_KEY_CODE,
                    right
                )
                backend.setMode(
                    modeForBehavior(
                        rightBehavior,
                        rightRapidFireCount
                    ),
                    RIGHT_KEY_CODE
                )
                if (
                    rightBehavior ==
                    NativeTgkTriggerBehavior.RAPID_FIRE
                ) {
                    backend.setRapidFireCount(
                        rightRapidFireCount,
                        RIGHT_KEY_CODE
                    )
                }

                /*
                 * NX809J processes setTgkPoint/setTgkMode
                 * asynchronously. Enabling immediately afterward
                 * is overwritten by the delayed vendor reset.
                 */
                Thread.sleep(CONFIG_SETTLE_MS)

                /*
                 * Match the stock REDMAGIC TGK presentation. The
                 * framework owns this top-edge press highlight and
                 * automatically adds/removes it with native trigger
                 * down/up events. Keep this optional so an older or
                 * custom framework can reject the visual API without
                 * preventing the proven touch-mapping path from
                 * enabling.
                 */
                runCatching {
                    backend.setTopEffectEnabled(true)
                }.onFailure { error ->
                    Log.w(
                        TAG,
                        "${backend.name} stock TGK top effect unavailable",
                        error
                    )
                }

                backend.setConsumeKeys(true)
                backend.setHaptics(hapticsEnabled)
                backend.setLeftEnabled(true)
                backend.setRightEnabled(true)
                backend.setGlobalEnabled(true)

                Thread.sleep(ENABLE_SETTLE_MS)

                val state = backend.readState()
                if (state.mappingEnabled()) {
                    Log.i(
                        TAG,
                        "Native TGK enabled through ${backend.name}"
                    )
                    return NativeTgkApplyResult(
                        success = true,
                        backend = backend.name,
                        state = state,
                        message = "Native TGK mapping enabled"
                    )
                }

                runCatching {
                    backend.disable()
                }

                failures += "${backend.name}: " +
                    "TGK state verification remained disabled"
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()

                runCatching {
                    backend.disable()
                }

                return NativeTgkApplyResult(
                    success = false,
                    backend = backend.name,
                    state = null,
                    message = "TGK configuration interrupted"
                )
            } catch (error: Throwable) {
                runCatching {
                    backend.disable()
                }

                Log.w(
                    TAG,
                    "${backend.name} TGK apply failed",
                    error
                )
                failures += errorSummary(
                    backend.name,
                    error
                )
            }
        }

        return NativeTgkApplyResult(
            success = false,
            backend = null,
            state = null,
            message = failures.joinToString(
                separator = " | ",
                prefix = "Native TGK unavailable: "
            )
        )
    }

    @Synchronized
    fun disable(
        context: Context
    ): NativeTgkApplyResult {
        val failures = mutableListOf<String>()

        backendFactories(context).forEach { createBackend ->
            val backend = try {
                createBackend()
            } catch (error: Throwable) {
                failures += errorSummary(
                    "backend initialization",
                    error
                )
                return@forEach
            }

            try {
                backend.disable()
                val state = backend.readState()

                if (state.fullyDisabled()) {
                    Log.i(
                        TAG,
                        "Native TGK disabled through ${backend.name}"
                    )
                    return NativeTgkApplyResult(
                        success = true,
                        backend = backend.name,
                        state = state,
                        message = "Native TGK mapping disabled"
                    )
                }

                failures += "${backend.name}: " +
                    "TGK state verification remained enabled"
            } catch (error: Throwable) {
                Log.w(
                    TAG,
                    "${backend.name} TGK disable failed",
                    error
                )
                failures += errorSummary(
                    backend.name,
                    error
                )
            }
        }

        return NativeTgkApplyResult(
            success = false,
            backend = null,
            state = null,
            message = failures.joinToString(
                separator = " | ",
                prefix = "Could not disable native TGK: "
            )
        )
    }

    private fun backendFactories(
        context: Context
    ): List<() -> Backend> {
        val appContext = context.applicationContext

        return listOf(
            {
                ServiceCallBackend()
            },
            {
                ReflectionBackend(appContext)
            }
        )
    }

    private fun errorSummary(
        source: String,
        error: Throwable
    ): String {
        val detail = error.cause?.message
            ?: error.message
            ?: error.javaClass.simpleName

        return "$source: $detail"
    }

    private fun modeForBehavior(
        behavior: NativeTgkTriggerBehavior,
        rapidFireCount: Int
    ): Int {
        if (behavior == NativeTgkTriggerBehavior.RAPID_FIRE) {
            require(
                rapidFireCount in
                    NativeTgkStorage.supportedRapidFireCounts &&
                    rapidFireCount > 0
            ) {
                "Rapid fire requires a supported non-zero count"
            }
        }

        return behavior.vendorMode
    }

    private interface Backend {
        val name: String

        fun setPoint(
            keyCode: Int,
            rect: IntArray
        )

        fun setMode(
            mode: Int,
            keyCode: Int
        )

        fun setRapidFireCount(
            count: Int,
            keyCode: Int
        )

        fun setDriveEnabled(enabled: Boolean)
        fun setVersion(version: Int)
        fun setTopEffectEnabled(enabled: Boolean)
        fun setConsumeKeys(enabled: Boolean)
        fun setHaptics(enabled: Boolean)
        fun setLeftEnabled(enabled: Boolean)
        fun setRightEnabled(enabled: Boolean)
        fun setGlobalEnabled(enabled: Boolean)
        fun readState(): NativeTgkState

        fun disable() {
            setDriveEnabled(false)
            setGlobalEnabled(false)
            setLeftEnabled(false)
            setRightEnabled(false)
            setHaptics(false)
            setConsumeKeys(false)
        }
    }

    private class ReflectionBackend(
        private val context: Context
    ) : Backend {
        override val name = "InputManager reflection"

        private val manager = context.getSystemService(
            Context.INPUT_SERVICE
        ) ?: error("InputManager service unavailable")

        override fun setPoint(
            keyCode: Int,
            rect: IntArray
        ) {
            call(
                "setTgkPoint",
                arrayOf(
                    IntArray::class.java,
                    IntArray::class.java,
                    Integer.TYPE
                ),
                rect,
                rect.copyOf(),
                keyCode
            )
        }

        override fun setMode(
            mode: Int,
            keyCode: Int
        ) {
            call(
                "setTgkMode",
                arrayOf(
                    Integer.TYPE,
                    Integer.TYPE
                ),
                mode,
                keyCode
            )
        }

        override fun setRapidFireCount(
            count: Int,
            keyCode: Int
        ) {
            call(
                "setTgkRapidFireCount",
                arrayOf(
                    Integer.TYPE,
                    Integer.TYPE
                ),
                count,
                keyCode
            )
        }

        override fun setDriveEnabled(enabled: Boolean) {
            call(
                "enableTgkDrive",
                arrayOf(java.lang.Boolean.TYPE),
                enabled
            )
        }

        override fun setVersion(version: Int) {
            call(
                "setTgkVersion",
                arrayOf(Integer.TYPE),
                version
            )
        }

        override fun setTopEffectEnabled(enabled: Boolean) {
            callBooleanSetter(
                "setTgkTopEffectEnable",
                enabled
            )
        }

        override fun setConsumeKeys(enabled: Boolean) {
            callBooleanSetter(
                "setConsumeTgkKey",
                enabled
            )
        }

        override fun setHaptics(enabled: Boolean) {
            callBooleanSetter(
                "setTouchHapticFeedbackEnable",
                enabled
            )
        }

        override fun setLeftEnabled(enabled: Boolean) {
            callBooleanSetter(
                "setLeftTgkEnable",
                enabled
            )
        }

        override fun setRightEnabled(enabled: Boolean) {
            callBooleanSetter(
                "setRightTgkEnable",
                enabled
            )
        }

        override fun setGlobalEnabled(enabled: Boolean) {
            call(
                "setGameKeyEnable",
                arrayOf(
                    java.lang.Boolean.TYPE,
                    Context::class.java
                ),
                enabled,
                context
            )
        }

        override fun readState(): NativeTgkState {
            return NativeTgkState(
                globalEnabled = readBoolean(
                    "isGameKeyEnable"
                ),
                leftEnabled = readBoolean(
                    "isLeftGameKeyEnable"
                ),
                rightEnabled = readBoolean(
                    "isRightGameKeyEnable"
                ),
                hapticsEnabled = null
            )
        }

        private fun callBooleanSetter(
            name: String,
            enabled: Boolean
        ) {
            call(
                name,
                arrayOf(java.lang.Boolean.TYPE),
                enabled
            )
        }

        private fun readBoolean(name: String): Boolean {
            return call(
                name,
                emptyArray()
            ) as? Boolean
                ?: error("$name returned no Boolean state")
        }

        private fun call(
            name: String,
            parameterTypes: Array<Class<*>>,
            vararg arguments: Any?
        ): Any? {
            val method = manager.javaClass.getMethod(
                name,
                *parameterTypes
            )

            return method.invoke(
                manager,
                *arguments
            )
        }
    }

    private class ServiceCallBackend : Backend {
        override val name = "InputManager service call"

        override fun setPoint(
            keyCode: Int,
            rect: IntArray
        ) {
            require(rect.size == 4) {
                "TGK rectangle must contain four values"
            }

            call(
                145,
                "i32", "4",
                "i32", rect[0].toString(),
                "i32", rect[1].toString(),
                "i32", rect[2].toString(),
                "i32", rect[3].toString(),
                "i32", "4",
                "i32", rect[0].toString(),
                "i32", rect[1].toString(),
                "i32", rect[2].toString(),
                "i32", rect[3].toString(),
                "i32", keyCode.toString()
            )
        }

        override fun setMode(
            mode: Int,
            keyCode: Int
        ) {
            call(
                143,
                "i32", mode.toString(),
                "i32", keyCode.toString()
            )
        }

        override fun setRapidFireCount(
            count: Int,
            keyCode: Int
        ) {
            call(
                144,
                "i32", count.toString(),
                "i32", keyCode.toString()
            )
        }

        override fun setDriveEnabled(enabled: Boolean) {
            callBooleanSetter(146, enabled)
        }

        override fun setVersion(version: Int) {
            call(
                142,
                "i32",
                version.toString()
            )
        }

        override fun setTopEffectEnabled(enabled: Boolean) {
            val output = callBooleanSetter(
                151,
                enabled
            )
            if (
                output.contains("Exception", ignoreCase = true) ||
                output.contains("Permission Denial", ignoreCase = true)
            ) {
                throw IllegalStateException(output)
            }
        }

        override fun setConsumeKeys(enabled: Boolean) {
            callBooleanSetter(101, enabled)
        }

        override fun setHaptics(enabled: Boolean) {
            callBooleanSetter(105, enabled)
        }

        override fun setLeftEnabled(enabled: Boolean) {
            callBooleanSetter(102, enabled)
        }

        override fun setRightEnabled(enabled: Boolean) {
            callBooleanSetter(103, enabled)
        }

        override fun setGlobalEnabled(enabled: Boolean) {
            callBooleanSetter(100, enabled)
        }

        override fun readState(): NativeTgkState {
            return NativeTgkState(
                globalEnabled = readBoolean(112),
                leftEnabled = readBoolean(113),
                rightEnabled = readBoolean(116),
                hapticsEnabled = readBoolean(118)
            )
        }

        private fun callBooleanSetter(
            transaction: Int,
            enabled: Boolean
        ): String {
            return call(
                transaction,
                "i32",
                if (enabled) "1" else "0"
            )
        }

        private fun readBoolean(
            transaction: Int
        ): Boolean {
            val output = call(transaction)

            return BOOLEAN_TRUE_REGEX.containsMatchIn(
                output
            )
        }

        private fun call(
            transaction: Int,
            vararg arguments: String
        ): String {
            val command = buildList {
                add(SERVICE_BINARY)
                add("call")
                add("input")
                add(transaction.toString())
                addAll(arguments)
            }

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            if (
                !process.waitFor(
                    SERVICE_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                )
            ) {
                process.destroyForcibly()
                throw IllegalStateException(
                    "service call $transaction timed out"
                )
            }

            val output = process.inputStream
                .bufferedReader()
                .use {
                    it.readText()
                }
                .trim()

            if (process.exitValue() != 0) {
                throw IllegalStateException(
                    "service call $transaction failed: $output"
                )
            }

            return output
        }

        companion object {
            private const val SERVICE_BINARY =
                "/system/bin/service"
            private const val SERVICE_TIMEOUT_SECONDS = 5L

            private val BOOLEAN_TRUE_REGEX = Regex(
                """Parcel\(\s*00000000\s+00000001\b"""
            )
        }
    }
}
