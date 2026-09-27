package com.elitedarkkaiser.redmagic

import android.content.Context
import android.util.Log

object RefreshRateCoordinator {
    private const val TAG = "RedmagicRefreshRate"

    private var activePackage: String? = null
    private var activeRate: Int? = null

    @Synchronized
    fun onForegroundPackage(
        context: Context,
        packageName: String
    ): RefreshRateApplyResult? {
        val profile = RefreshRateStorage.getProfile(
            context,
            packageName
        )
        if (profile == null) {
            RefreshRateOverlay.hide()
            clearRuntimeState()
            return null
        }

        val effectiveRate = if (profile.enabled) {
            profile.refreshRateHz
        } else {
            0
        }

        if (
            activePackage == packageName &&
            activeRate == effectiveRate
        ) {
            if (profile.enabled && profile.showOverlay) {
                RefreshRateOverlay.show(context, packageName)
            } else {
                RefreshRateOverlay.hide()
            }
            return null
        }

        val result = RefreshRateBridge.apply(
            context,
            packageName,
            effectiveRate
        )
        if (result.success) {
            if (profile.pendingReset) {
                RefreshRateStorage.finishPendingReset(
                    context,
                    packageName
                )
            }
            activePackage = packageName
            activeRate = effectiveRate
            if (profile.enabled && profile.showOverlay) {
                RefreshRateOverlay.show(context, packageName)
            } else {
                RefreshRateOverlay.hide()
            }
            Log.i(
                TAG,
                "Applied $effectiveRate Hz to $packageName " +
                    "through ${result.backend}"
            )
        } else {
            RefreshRateOverlay.hide()
            clearRuntimeState()
            Log.w(TAG, result.message)
        }
        return result
    }

    @Synchronized
    fun clearRuntimeState() {
        activePackage = null
        activeRate = null
    }
}
