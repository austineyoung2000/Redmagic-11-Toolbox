package com.elitedarkkaiser.redmagic

import android.content.Context

object DeviceScanActions {
    fun runBackgroundScan(
        context: Context,
        force: Boolean = false,
        onComplete: ((DeviceCapabilities) -> Unit)? = null
    ) {
        if (!DeviceCompatibility.isSupportedDevice()) return

        if (
            !force &&
            hasDeviceCapabilityReportStorage(context)
        ) {
            onComplete?.invoke(
                deviceCapabilitiesStorage(context)
            )
            return
        }

        Thread {
            val report = DeviceCapabilityScanner.scan(
                context.applicationContext
            )
            saveDeviceCapabilityReportStorage(context, report)
            onComplete?.invoke(
                report.toDeviceCapabilities()
            )
        }.apply {
            name = "RedMagicCapabilityScan"
            priority = Thread.NORM_PRIORITY - 1
            start()
        }
    }
}
