package com.elitedarkkaiser.redmagic

import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.google.android.material.button.MaterialButton
import java.util.concurrent.atomic.AtomicBoolean

object FirstInstallPermissionsDialog {
    fun show(
        activity: MainActivity,
        onSetupComplete: () -> Unit
    ) {
        val dp = { value: Int -> (value * activity.resources.displayMetrics.density).toInt() }

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(18))
            background = AppTheme.roundedBg(AppTheme.panelColor, AppTheme.borderColor, 24f)
        }

        val title = TextView(activity).apply {
            text = "Root permission setup"
            textSize = 20f
            setTextColor(AppTheme.textPrimary)
            setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val body = TextView(activity).apply {
            text =
                "Redmagic 11 Toolbox is built for rooted RedMagic 11 Pro hardware control.\n\n" +
                    "Tap Grant with root to allow:\n" +
                    "• Usage Access for Game Mode detection\n" +
                    "• Display over other apps for trigger setup\n" +
                    "• Notifications for foreground services\n" +
                    "• Phone state for Call Lighting\n\n" +
                    "Your root manager should ask for superuser access before continuing."
            textSize = 14f
            setTextColor(AppTheme.textSecondary)
            setPadding(0, dp(12), 0, dp(18))
        }

        val grantButton = MaterialButton(activity).apply {
            text = "Grant with root"
            textSize = 13f
            isAllCaps = false
            setTextColor(AppTheme.textPrimary)

            backgroundTintList =
                ColorStateList.valueOf(AppTheme.panelPressed)
            rippleColor =
                ColorStateList.valueOf(com.elitedarkkaiser.redmagic.ui.AppTheme.rippleColor)
            cornerRadius = dp(16)

            insetTop = 0
            insetBottom = 0
            minHeight = dp(48)
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }

        container.addView(title)
        container.addView(body)
        container.addView(grantButton)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(container)
            .setCancelable(false)
            .create()
        val setupRunning = AtomicBoolean(false)

        grantButton.setOnClickListener {
            if (!setupRunning.compareAndSet(false, true)) {
                return@setOnClickListener
            }

            grantButton.isEnabled = false
            grantButton.text = "Applying…"

            Thread({
                android.os.Process.setThreadPriority(
                    android.os.Process.THREAD_PRIORITY_BACKGROUND
                )

                val result = PermissionBootstrap.apply(activity)

                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) {
                        return@runOnUiThread
                    }

                    setupRunning.set(false)

                    if (result.success) {
                        setFirstInstallPermissionsPromptedStorage(
                            activity,
                            true
                        )
                        Toast.makeText(
                            activity,
                            "Root permissions applied",
                            Toast.LENGTH_SHORT
                        ).show()
                        dialog.dismiss()
                        onSetupComplete()
                    } else {
                        grantButton.isEnabled = true
                        grantButton.text = "Grant with root"

                        Toast.makeText(
                            activity,
                            buildString {
                                append("Permission setup incomplete")
                                if (!result.rootCommandSucceeded) {
                                    append(": root command failed")
                                }
                                if (result.missingPermissions.isNotEmpty()) {
                                    append(". Missing: ")
                                    append(
                                        result.missingPermissions
                                            .joinToString(", ")
                                    )
                                }
                            },
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }, "RedMagicPermissionSetup").start()
        }

        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.65f)
        }
    }
}
