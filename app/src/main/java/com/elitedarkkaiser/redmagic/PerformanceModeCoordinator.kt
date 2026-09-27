package com.elitedarkkaiser.redmagic

import android.content.Context
import android.util.Log

object PerformanceModeCoordinator {
    private const val TAG = "RedmagicPerformance"

    private var activePackage: String? = null
    private var activeMode: RedmagicPerformanceMode? = null

    @Synchronized
    fun needsReconcile(
        context: Context,
        packageName: String
    ): Boolean {
        val profile = PerformanceModeStorage.getProfile(
            context,
            packageName
        )?.takeIf { it.enabled }

        if (profile == null) {
            return activePackage != null
        }

        return activePackage != packageName ||
            activeMode != profile.mode ||
            PerformanceModeController.readActivePackage(context) !=
                packageName ||
            PerformanceModeController.readActiveMode(context) !=
                profile.mode
    }

    @Synchronized
    fun onForegroundPackage(
        context: Context,
        packageName: String
    ): PerformanceModeResult? {
        val profile = PerformanceModeStorage.getProfile(
            context,
            packageName
        )?.takeIf { it.enabled }

        if (profile == null) {
            return resetIfOwned(context, "left profiled app")
        }

        if (!needsReconcile(context, packageName)) {
            return null
        }

        val result = PerformanceModeController.apply(
            context,
            packageName,
            profile.mode
        )
        if (result.success) {
            activePackage = packageName
            activeMode = profile.mode
            Log.i(
                TAG,
                "${profile.mode.label} active for $packageName " +
                    "through ${result.backend}"
            )
        } else {
            clearRuntimeState()
            Log.w(TAG, result.message)
        }
        return result
    }

    @Synchronized
    fun resetIfOwned(
        context: Context,
        reason: String
    ): PerformanceModeResult? {
        if (activePackage == null) return null

        val result = PerformanceModeController.reset(context)
        if (result.success) {
            Log.i(TAG, "Performance mode reset: $reason")
            clearRuntimeState()
        } else {
            Log.w(TAG, result.message)
        }
        return result
    }

    @Synchronized
    fun recoverAndResetIfManaged(
        context: Context,
        reason: String
    ): PerformanceModeResult? {
        if (activePackage == null) {
            val livePackage =
                PerformanceModeController.readActivePackage(context)
                    ?: return null
            val profile = PerformanceModeStorage.getProfile(
                context,
                livePackage
            )?.takeIf { it.enabled } ?: return null

            activePackage = livePackage
            activeMode = profile.mode
        }

        return resetIfOwned(context, reason)
    }

    @Synchronized
    fun isActive(): Boolean {
        return activePackage != null
    }

    @Synchronized
    fun activeLabel(packageName: String): String? {
        return if (activePackage == packageName) {
            activeMode?.label
        } else {
            null
        }
    }

    @Synchronized
    fun clearRuntimeState() {
        activePackage = null
        activeMode = null
    }
}
