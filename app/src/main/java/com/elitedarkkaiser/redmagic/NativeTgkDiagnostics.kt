package com.elitedarkkaiser.redmagic

import android.content.Context
import android.os.Build
import java.text.DateFormat
import java.util.Date

data class NativeTgkDiagnosticSnapshot(
    val foregroundPackage: String?,
    val foregroundObservedAt: Long,
    val action: String?,
    val reason: String?,
    val packageName: String?,
    val orientation: NativeTgkOrientation?,
    val success: Boolean?,
    val backend: String?,
    val message: String?,
    val transitionAt: Long
)

object NativeTgkDiagnostics {
    private const val PREFS = "native_tgk_diagnostics"

    private const val KEY_FOREGROUND_PACKAGE =
        "foreground_package"
    private const val KEY_FOREGROUND_AT = "foreground_at"
    private const val KEY_ACTION = "action"
    private const val KEY_REASON = "reason"
    private const val KEY_PACKAGE = "package"
    private const val KEY_ORIENTATION = "orientation"
    private const val KEY_SUCCESS = "success"
    private const val KEY_BACKEND = "backend"
    private const val KEY_MESSAGE = "message"
    private const val KEY_TRANSITION_AT = "transition_at"

    fun recordForeground(
        context: Context,
        packageName: String
    ) {
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).edit()
            .putString(KEY_FOREGROUND_PACKAGE, packageName)
            .putLong(KEY_FOREGROUND_AT, System.currentTimeMillis())
            .apply()
    }

    fun recordApply(
        context: Context,
        packageName: String,
        orientation: NativeTgkOrientation,
        result: NativeTgkApplyResult
    ) {
        recordTransition(
            context = context,
            action = "APPLY",
            reason = null,
            packageName = packageName,
            orientation = orientation,
            result = result
        )
    }

    fun recordDisable(
        context: Context,
        reason: String,
        result: NativeTgkApplyResult
    ) {
        val previous = snapshot(context)

        recordTransition(
            context = context,
            action = "DISABLE",
            reason = reason,
            packageName = NativeTgkRuntimeState.activePackage()
                ?: previous.packageName,
            orientation = NativeTgkRuntimeState.activeOrientation()
                ?: previous.orientation,
            result = result
        )
    }

    fun snapshot(context: Context): NativeTgkDiagnosticSnapshot {
        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        val orientation = prefs.getString(
            KEY_ORIENTATION,
            null
        )?.let {
            runCatching {
                NativeTgkOrientation.valueOf(it)
            }.getOrNull()
        }

        return NativeTgkDiagnosticSnapshot(
            foregroundPackage = prefs.getString(
                KEY_FOREGROUND_PACKAGE,
                null
            ),
            foregroundObservedAt = prefs.getLong(
                KEY_FOREGROUND_AT,
                0L
            ),
            action = prefs.getString(KEY_ACTION, null),
            reason = prefs.getString(KEY_REASON, null),
            packageName = prefs.getString(KEY_PACKAGE, null),
            orientation = orientation,
            success = if (prefs.contains(KEY_SUCCESS)) {
                prefs.getBoolean(KEY_SUCCESS, false)
            } else {
                null
            },
            backend = prefs.getString(KEY_BACKEND, null),
            message = prefs.getString(KEY_MESSAGE, null),
            transitionAt = prefs.getLong(
                KEY_TRANSITION_AT,
                0L
            )
        )
    }

    fun buildReport(
        context: Context,
        liveResult: NativeTgkApplyResult
    ): String {
        val snapshot = snapshot(context)
        val runtimePackage = NativeTgkRuntimeState.activePackage()
        val runtimeOrientation =
            NativeTgkRuntimeState.activeOrientation()
        val expectedPackage = runtimePackage
            ?: snapshot.packageName
            ?: snapshot.foregroundPackage
        val expectedOrientation = runtimeOrientation
            ?: snapshot.orientation
            ?: NativeTgkCoordinator.currentOrientation(context)
        val profile = expectedPackage?.let {
            NativeTgkStorage.getProfile(context, it)
        }
        val layout = profile?.activeLayout()
        val mapping = profile?.mappingFor(expectedOrientation)
        val expectedNativeEnabled = runtimePackage != null
        val liveNativeEnabled = liveResult.state?.mappingEnabled()

        return buildString {
            appendLine("REDMAGIC 11 TOOLBOX — TGK DIAGNOSTICS")
            appendLine("Generated: ${formatTime(System.currentTimeMillis())}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}")
            appendLine()

            appendLine("FOREGROUND LIFECYCLE")
            appendLine(
                "Last observed package: " +
                    (snapshot.foregroundPackage ?: "Unavailable")
            )
            appendLine(
                "Observed at: ${formatTime(snapshot.foregroundObservedAt)}"
            )
            appendLine(
                "Runtime active package: ${runtimePackage ?: "None"}"
            )
            appendLine(
                "Runtime orientation: ${runtimeOrientation ?: "None"}"
            )
            appendLine()

            appendLine("EXPECTED MAPPING")
            appendLine("Package: ${expectedPackage ?: "None"}")
            appendLine("Orientation: $expectedOrientation")
            appendLine("Profile enabled: ${profile?.enabled ?: false}")
            appendLine("Layout: ${layout?.name ?: "None"}")
            appendLine(
                "Complete mapping: ${mapping?.isComplete() == true}"
            )
            appendLine("L target: ${rectText(mapping?.left)}")
            appendLine("R target: ${rectText(mapping?.right)}")
            appendLine(
                "L behavior: ${profile?.effectiveLeftBehavior() ?: "None"}"
            )
            appendLine(
                "R behavior: ${profile?.effectiveRightBehavior() ?: "None"}"
            )
            appendLine(
                "L rapid fire: ${profile?.effectiveLeftRapidFireCount() ?: 0}"
            )
            appendLine(
                "R rapid fire: ${profile?.effectiveRightRapidFireCount() ?: 0}"
            )
            appendLine(
                "Expected haptics: ${profile?.hapticsEnabled ?: false}"
            )
            appendLine()

            appendLine("LIVE NATIVE STATE")
            appendLine("Read succeeded: ${liveResult.success}")
            appendLine("Backend: ${liveResult.backend ?: "Unavailable"}")
            appendLine(
                "Runtime/native agreement: " +
                    when (liveNativeEnabled) {
                        null -> "UNKNOWN"
                        expectedNativeEnabled -> "MATCH"
                        else -> "MISMATCH"
                    }
            )
            liveResult.state?.let {
                appendLine("Global: ${onOff(it.globalEnabled)}")
                appendLine("Left: ${onOff(it.leftEnabled)}")
                appendLine("Right: ${onOff(it.rightEnabled)}")
                appendLine(
                    "Haptics: " +
                        (it.hapticsEnabled?.let(::onOff) ?: "Unknown")
                )
            }
            appendLine("Result: ${liveResult.message}")
            appendLine()

            appendLine("LAST TGK TRANSITION")
            appendLine("Action: ${snapshot.action ?: "None recorded"}")
            appendLine("Time: ${formatTime(snapshot.transitionAt)}")
            appendLine("Package: ${snapshot.packageName ?: "None"}")
            appendLine("Orientation: ${snapshot.orientation ?: "None"}")
            appendLine("Reason: ${snapshot.reason ?: "None"}")
            appendLine(
                "Succeeded: ${snapshot.success?.toString() ?: "Unknown"}"
            )
            appendLine("Backend: ${snapshot.backend ?: "Unavailable"}")
            append("Message: ${snapshot.message ?: "None"}")
        }
    }

    private fun recordTransition(
        context: Context,
        action: String,
        reason: String?,
        packageName: String?,
        orientation: NativeTgkOrientation?,
        result: NativeTgkApplyResult
    ) {
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).edit()
            .putString(KEY_ACTION, action)
            .putString(KEY_REASON, reason)
            .putString(KEY_PACKAGE, packageName)
            .putString(KEY_ORIENTATION, orientation?.name)
            .putBoolean(KEY_SUCCESS, result.success)
            .putString(KEY_BACKEND, result.backend)
            .putString(KEY_MESSAGE, result.message)
            .putLong(KEY_TRANSITION_AT, System.currentTimeMillis())
            .apply()
    }

    private fun rectText(rect: NativeTgkRect?): String {
        if (rect == null) return "Not configured"

        return "${rect.left},${rect.top}–${rect.right},${rect.bottom} " +
            "on ${rect.captureWidth}x${rect.captureHeight}"
    }

    private fun onOff(value: Boolean): String {
        return if (value) "ON" else "OFF"
    }

    private fun formatTime(timestamp: Long): String {
        if (timestamp <= 0L) return "Unavailable"

        return DateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.MEDIUM
        ).format(Date(timestamp))
    }
}
