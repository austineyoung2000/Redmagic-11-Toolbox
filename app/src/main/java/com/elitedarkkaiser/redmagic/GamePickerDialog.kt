package com.elitedarkkaiser.redmagic

import android.content.Context

fun showGamePickerDialogUI(context: Context, onSave: (Set<String>) -> Unit) {
    val manager = context.packageManager
    val apps = manager.getInstalledApplications(0).filter {
        it.enabled && it.packageName != context.packageName &&
            manager.getLaunchIntentForPackage(it.packageName) != null
    }.map { MaterialAppPicker.App(it.packageName, it.loadLabel(manager).toString()) }
        .distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    MaterialAppPicker.showMultiple(context, "Choose Games / Apps", apps,
        getSavedGamePackagesStorage(context)) { selected ->
        setSavedGamePackagesStorage(context, selected)
        onSave(selected)
    }
}
