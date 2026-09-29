package com.elitedarkkaiser.redmagic

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object NativeTgkDiagnosticsDialog {
    fun show(activity: Activity) {
        AppTheme.configure(activity)

        val reportView = TextView(activity).apply {
            text = "Reading trigger backend state…"
            textSize = 12f
            setTextColor(AppTheme.textPrimary)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(
                dp(activity, 20),
                dp(activity, 8),
                dp(activity, 20),
                dp(activity, 20)
            )
        }
        val scroll = ScrollView(activity).apply {
            addView(
                reportView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle("Trigger Backend Diagnostics")
            .setView(scroll)
            .setNegativeButton("Close", null)
            .setNeutralButton("Refresh", null)
            .setPositiveButton("Copy report", null)
            .create()

        var currentReport = ""

        fun refresh() {
            reportView.text = "Reading trigger backend state…"
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)
                ?.isEnabled = false

            Thread(
                {
                    val liveResult =
                        NativeTgkCoordinator.readActiveState(activity)
                    val report = runCatching {
                        NativeTgkDiagnostics.buildReport(
                            activity,
                            liveResult
                        )
                    }.getOrElse {
                        "Unable to create trigger diagnostic report: " +
                            (it.message ?: it.javaClass.simpleName)
                    }

                    activity.runOnUiThread {
                        if (!dialog.isShowing) return@runOnUiThread

                        currentReport = report
                        reportView.text = report
                        dialog.getButton(
                            android.app.AlertDialog.BUTTON_NEUTRAL
                        )?.isEnabled = true
                    }
                },
                "RedMagicTgkDiagnostics"
            ).start()
        }

        dialog.setOnShowListener {
            dialog.getButton(
                android.app.AlertDialog.BUTTON_NEUTRAL
            ).setOnClickListener {
                refresh()
            }
            dialog.getButton(
                android.app.AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {
                if (currentReport.isBlank()) {
                    Toast.makeText(
                        activity,
                        "Wait for the diagnostic read to finish",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                val clipboard = activity.getSystemService(
                    Context.CLIPBOARD_SERVICE
                ) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                        "REDMAGIC trigger backend diagnostics",
                        currentReport
                    )
                )
                Toast.makeText(
                    activity,
                    "Trigger diagnostic report copied",
                    Toast.LENGTH_SHORT
                ).show()
            }

            refresh()
        }

        dialog.show()
    }

    private fun dp(context: Context, value: Int): Int {
        return (
            value * context.resources.displayMetrics.density
            ).toInt()
    }
}
