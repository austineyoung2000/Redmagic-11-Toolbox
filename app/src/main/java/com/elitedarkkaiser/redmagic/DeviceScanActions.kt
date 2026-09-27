package com.elitedarkkaiser.redmagic

import android.content.Context
import java.util.concurrent.Executors

object DeviceScanActions {
    private val requests = SingleFlightQueue<DeviceCapabilities>()
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "RedMagicCapabilityScan").apply {
            priority = Thread.NORM_PRIORITY - 1
        }
    }

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

        if (!requests.joinOrStart(onComplete)) {
            return
        }

        val appContext = context.applicationContext
        executor.execute {
            val capabilities = runCatching {
                DeviceCapabilityScanner.scan(appContext).also { report ->
                    saveDeviceCapabilityReportStorage(appContext, report)
                }.toDeviceCapabilities()
            }.getOrElse {
                runCatching {
                    deviceCapabilitiesStorage(appContext)
                }.getOrDefault(DeviceCapabilities.unknown())
            }

            requests.complete(capabilities).forEach { callback ->
                runCatching { callback(capabilities) }
            }
        }
    }
}
