package com.elitedarkkaiser.redmagic

import android.app.Application

class RedmagicApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (DeviceCompatibility.isSupportedDevice()) RootShell.initialize(this)
    }
}
