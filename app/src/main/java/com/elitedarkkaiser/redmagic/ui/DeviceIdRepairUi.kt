package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.widget.Button
import android.widget.LinearLayout
import com.elitedarkkaiser.redmagic.DeviceIdRepairController
import com.google.android.material.materialswitch.MaterialSwitch

internal object DeviceIdRepairUi {
    fun attach(activity: Activity, card: LinearLayout, deps: ControlsTabDeps): () -> Unit {
        val status = deps.subtleLabel("Device ID Repair: Checking…")
        val boot = MaterialSwitch(activity).apply { text = "Apply repair once at boot" }
        lateinit var run: Button
        var busy = false
        var publishing = false
        fun submit(action: () -> String?) {
            if (busy) return
            busy = true; run.isEnabled = false; boot.isEnabled = false
            status.text = "Device ID Repair: Working…"
            val queued = deps.runBackground {
                val note = runCatching(action).getOrElse { "Unable to complete operation" }
                val state = runCatching { DeviceIdRepairController.read() }.getOrNull()
                status.post {
                    if (activity.isFinishing || activity.isDestroyed) return@post
                    publishing = true
                    boot.isChecked = state?.boot == true
                    publishing = false
                    busy = false
                    run.isEnabled = state != null && !state.module
                    boot.isEnabled = state != null && (!state.module || state.boot)
                    status.text = when {
                        state?.module == true -> "Handled by installed module. Toolbox will skip repair."
                        else -> note ?: state?.outcome ?: "Unable to verify status"
                    }
                }
            }
            if (!queued) { busy = false; run.isEnabled = false; boot.isEnabled = false; status.text = "Unable to start operation; use Refresh Status" }
        }
        run = deps.actionButton("RUN REPAIR", false) { submit { DeviceIdRepairController.run(activity.applicationContext) } }
        boot.setOnCheckedChangeListener { _, checked ->
            if (!publishing) submit {
                if (DeviceIdRepairController.setBoot(activity.applicationContext, checked))
                    if (checked) "Boot repair enabled" else "Boot repair disabled; existing provisioning is unchanged"
                else "Boot setting could not be confirmed; refresh status"
            }
        }
        card.addView(deps.space(deps.dp(12)))
        card.addView(deps.bodyText("Device ID Repair"))
        card.addView(deps.subtleLabel("Optional NX809J device-info provisioning repair for the reported integrity/Widevine issue. Writes secure provisioning data; does not install an attestation keybox. Disabling boot repair does not undo provisioning."))
        card.addView(status); card.addView(run); card.addView(boot)
        val refresh: () -> Unit = { submit { null } }
        refresh()
        return refresh
    }
}
