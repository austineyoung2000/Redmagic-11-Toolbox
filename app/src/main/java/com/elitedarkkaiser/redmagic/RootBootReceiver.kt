package com.elitedarkkaiser.redmagic

import android.content.Context
import android.content.Intent
import android.os.UserManager

/** Root module fallback; uses the same per-boot startup claim as Android boot. */
class RootBootReceiver : BootReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != BootReceiver.ROOT_BOOT_ACTION) return
        if (!DeviceCompatibility.isSupportedDevice()) return
        if (context.getSystemService(UserManager::class.java)?.isUserUnlocked != true) return
        super.onReceive(context, intent)
    }
}
