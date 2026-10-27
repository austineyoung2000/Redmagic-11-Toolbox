package com.elitedarkkaiser.redmagic

import android.app.Application

class RedmagicApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        )
        if (DeviceCompatibility.isSupportedDevice()) RootShell.initialize(this)
    }
}
