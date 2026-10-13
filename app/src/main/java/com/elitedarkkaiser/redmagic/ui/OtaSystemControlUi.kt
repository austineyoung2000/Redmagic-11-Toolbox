package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.widget.Button
import android.widget.LinearLayout
import com.elitedarkkaiser.redmagic.OtaUpdateController
import com.elitedarkkaiser.redmagic.OtaUpdatePolicy

/** Inline OTA controls; no nested dialogs, stored toggle, or background enforcement. */
internal object OtaSystemControlUi {
    fun attach(activity: Activity, card: LinearLayout, deps: ControlsTabDeps): () -> Unit {
        val title = deps.bodyText("OTA Updates")
        val description = deps.subtleLabel("Enable or disable the three REDMAGIC OTA packages for user 0.")
        val status = deps.subtleLabel("OTA: Checking…")
        val details = deps.subtleLabel("")
        lateinit var enable: Button
        lateinit var disable: Button
        var busy = false

        fun setBusy(value: Boolean) {
            busy = value
            enable.isEnabled = !value
            disable.isEnabled = !value
        }

        fun show(snapshot: OtaUpdatePolicy.Snapshot?, note: String? = null) {
            status.text = snapshot?.summary() ?: "OTA: Unable to verify status"
            details.text = listOfNotNull(snapshot?.details(), note).joinToString("\n")
        }

        fun change(enabled: Boolean) {
            if (busy) return
            setBusy(true)
            status.text = if (enabled) "OTA: Enabling…" else "OTA: Disabling…"
            details.text = ""
            val submitted = deps.runBackground {
                val result = runCatching { OtaUpdateController.setEnabled(enabled) }.getOrNull()
                status.post {
                    if (activity.isFinishing || activity.isDestroyed) return@post
                    setBusy(false)
                    show(result?.snapshot, if (result?.confirmed(enabled) == true) null else
                        "The change could not be fully confirmed. Use Refresh Status to check each package.")
                }
            }
            if (!submitted) {
                setBusy(false)
                show(null, "Unable to start the OTA change. Try again.")
            }
        }

        enable = deps.actionButton("ENABLE OTA", false) { change(true) }
        disable = deps.actionButton("DISABLE OTA", false) { change(false) }
        card.addView(deps.space(deps.dp(12)))
        card.addView(title)
        card.addView(description)
        card.addView(status)
        card.addView(details)
        card.addView(deps.row(enable, disable))

        val refresh: () -> Unit = refresh@ {
            if (busy) return@refresh
            setBusy(true)
            status.text = "OTA: Checking…"
            val submitted = deps.runBackground {
                val snapshot = runCatching { OtaUpdateController.read() }.getOrNull()
                status.post {
                    if (activity.isFinishing || activity.isDestroyed) return@post
                    setBusy(false)
                    show(snapshot, if (snapshot == null) "Use Refresh Status to try again." else null)
                }
            }
            if (!submitted) {
                setBusy(false)
                show(null, "Unable to start the status check. Try again.")
            }
        }
        refresh()
        return refresh
    }
}
