package com.elitedarkkaiser.redmagic

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.elitedarkkaiser.redmagic.ui.AppTheme

internal object MagicKeyAppPickerDialog {
    private const val TAG = "MagicKeyAppPicker"

    data class Deps(
        val textPrimary: Int,
        val textSecondary: Int,
        val panelColor: Int,
        val borderColor: Int,
        val typeface: Typeface?,
        val dp: (Int) -> Int,
        val roundedBg: (Int, Int, Int) -> Drawable,
        val roundedFill: (Int, Int) -> Drawable,
        val space: (Int) -> View,
        val runBackground: (() -> Unit) -> Boolean
    )

    data class MagicKeyAppItem(
        val pkg: String,
        val label: String
    )

    fun show(
        activity: MainActivity,
        targetButton: Button,
        statusLabel: TextView?,
        applyLaunchAppMagicKeyMode:
            (String, String, TextView, Button) -> Unit,
        deps: Deps
    ) {
        val status = statusLabel

        if (status == null) {
            Toast.makeText(
                activity,
                "Magic Key controls are not ready",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        targetButton.isEnabled = false
        targetButton.text = "MAGIC KEY APP: Loading…"

        val submitted = deps.runBackground {
            val result = runCatching {
                loadLaunchableApps(activity)
            }

            activity.runOnUiThread {
                if (
                    activity.isFinishing ||
                    activity.isDestroyed
                ) {
                    return@runOnUiThread
                }

                restoreTargetButton(activity, targetButton)

                result.onSuccess { apps ->
                    if (apps.isEmpty()) {
                        Toast.makeText(
                            activity,
                            "No launchable apps found",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@onSuccess
                    }

                    showSelectionDialog(
                        activity = activity,
                        apps = apps,
                        selectedPackage =
                            savedMagicKeyAppPackageStorage(
                                activity
                            ),
                        titleText = "Choose Magic Key App",
                        subtitleText =
                            "Pick one launchable app for the Magic Key",
                        onSelected = { selected ->
                            applyLaunchAppMagicKeyMode(
                                selected.pkg,
                                selected.label,
                                status,
                                targetButton
                            )
                        },
                        deps = deps
                    )
                }.onFailure { error ->
                    android.util.Log.e(
                        TAG,
                        "Unable to load launchable apps",
                        error
                    )

                    Toast.makeText(
                        activity,
                        "Unable to load installed apps",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        if (!submitted) {
            restoreTargetButton(activity, targetButton)
            Toast.makeText(
                activity,
                "Unable to start installed-app scan",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun chooseApp(
        activity: MainActivity,
        title: String,
        subtitle: String,
        selectedPackage: String?,
        deps: Deps,
        onSelected: (MagicKeyAppItem) -> Unit
    ) {
        val submitted = deps.runBackground {
            val result = runCatching {
                loadLaunchableApps(activity)
            }

            activity.runOnUiThread {
                if (
                    activity.isFinishing ||
                    activity.isDestroyed
                ) {
                    return@runOnUiThread
                }

                result.onSuccess { apps ->
                    if (apps.isEmpty()) {
                        Toast.makeText(
                            activity,
                            "No launchable apps found",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        showSelectionDialog(
                            activity = activity,
                            apps = apps,
                            selectedPackage = selectedPackage,
                            titleText = title,
                            subtitleText = subtitle,
                            onSelected = onSelected,
                            deps = deps
                        )
                    }
                }.onFailure { error ->
                    android.util.Log.e(
                        TAG,
                        "Unable to load launchable apps",
                        error
                    )
                    Toast.makeText(
                        activity,
                        "Unable to load installed apps",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        if (!submitted) {
            Toast.makeText(
                activity,
                "Unable to start installed-app scan",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun restoreTargetButton(
        activity: MainActivity,
        targetButton: Button
    ) {
        targetButton.isEnabled = true
        targetButton.text =
            "MAGIC KEY APP: " +
                MagicKeyActions.resolveAppLabel(
                    activity,
                    savedMagicKeyAppPackageStorage(
                        activity
                    )
                )
    }

    private fun loadLaunchableApps(
        activity: MainActivity
    ): List<MagicKeyAppItem> {
        val packageManager = activity.packageManager
        val launcherIntent = Intent(
            Intent.ACTION_MAIN
        ).addCategory(
            Intent.CATEGORY_LAUNCHER
        )

        @Suppress("DEPRECATION")
        return packageManager.queryIntentActivities(
            launcherIntent,
            0
        ).asSequence()
            .mapNotNull { info ->
                val pkg = info.activityInfo
                    ?.packageName
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                if (
                    pkg == activity.packageName ||
                    info.activityInfo?.enabled == false
                ) {
                    return@mapNotNull null
                }

                val label = runCatching {
                    info.loadLabel(packageManager)
                        .toString()
                        .trim()
                }.getOrDefault(pkg)

                MagicKeyAppItem(
                    pkg = pkg,
                    label = label.ifBlank { pkg }
                )
            }
            .distinctBy { it.pkg }
            .sortedWith(
                compareBy<MagicKeyAppItem> {
                    it.label.lowercase()
                }.thenBy {
                    it.pkg.lowercase()
                }
            )
            .toList()
    }

    private fun showSelectionDialog(
        activity: MainActivity,
        apps: List<MagicKeyAppItem>,
        selectedPackage: String?,
        titleText: String,
        subtitleText: String,
        onSelected: (MagicKeyAppItem) -> Unit,
        deps: Deps
    ) {
        val rowNormal = AppTheme.panelColor
        val rowSelected = AppTheme.chipActiveColor
        val accent = AppTheme.accentColor
        val iconCache = LruCache<String, Drawable>(48)

        var currentSelection = selectedPackage

        val listView = ListView(activity).apply {
            divider = null
            dividerHeight = 0
            clipToPadding = false
            isVerticalScrollBarEnabled = true
            overScrollMode =
                View.OVER_SCROLL_IF_CONTENT_SCROLLS
            background = null
        }

        lateinit var saveButton: MaterialButton

        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = apps.size

            override fun getItem(
                position: Int
            ): MagicKeyAppItem = apps[position]

            override fun getItemId(
                position: Int
            ): Long = position.toLong()

            override fun getView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val app = apps[position]
                val isSelected =
                    currentSelection == app.pkg

                return LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(
                        deps.dp(14),
                        deps.dp(10),
                        deps.dp(14),
                        deps.dp(10)
                    )
                    background = deps.roundedBg(
                        if (isSelected) {
                            rowSelected
                        } else {
                            rowNormal
                        },
                        deps.borderColor,
                        16
                    )
                    layoutParams = AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )

                    val check =
                        MaterialCheckBox(activity).apply {
                            isChecked = isSelected
                            isClickable = false
                            isFocusable = false
                            buttonTintList =
                                ColorStateList.valueOf(
                                    accent
                                )
                        }

                    val icon = ImageView(activity).apply {
                        val size = deps.dp(40)
                        layoutParams =
                            LinearLayout.LayoutParams(
                                size,
                                size
                            ).apply {
                                marginStart = deps.dp(8)
                            }

                        val drawable =
                            iconCache.get(app.pkg)
                                ?: runCatching {
                                    activity.packageManager
                                        .getApplicationIcon(
                                            app.pkg
                                        )
                                }.getOrNull()?.also {
                                    iconCache.put(
                                        app.pkg,
                                        it
                                    )
                                }

                        setImageDrawable(drawable)
                    }

                    val textWrap =
                        LinearLayout(activity).apply {
                            orientation =
                                LinearLayout.VERTICAL
                            layoutParams =
                                LinearLayout.LayoutParams(
                                    0,
                                    ViewGroup.LayoutParams
                                        .WRAP_CONTENT,
                                    1f
                                ).apply {
                                    marginStart = deps.dp(12)
                                }

                            addView(TextView(activity).apply {
                                text = app.label
                                textSize = 15f
                                setTextColor(
                                    deps.textPrimary
                                )
                                setTypeface(
                                    deps.typeface,
                                    Typeface.BOLD
                                )
                                maxLines = 1
                                ellipsize =
                                    android.text.TextUtils
                                        .TruncateAt.END
                            })

                            addView(TextView(activity).apply {
                                text = app.pkg
                                textSize = 11f
                                setTextColor(
                                    deps.textSecondary
                                )
                                maxLines = 1
                                ellipsize =
                                    android.text.TextUtils
                                        .TruncateAt.END
                                setPadding(
                                    0,
                                    deps.dp(2),
                                    0,
                                    0
                                )
                            })
                        }

                    addView(check)
                    addView(icon)
                    addView(textWrap)

                    setOnClickListener {
                        currentSelection = app.pkg
                        saveButton.isEnabled = true
                        notifyDataSetChanged()
                    }
                }
            }
        }

        listView.adapter = adapter

        var dialogRef: AlertDialog? = null

        val title = TextView(activity).apply {
            text = titleText
            textSize = 18f
            setTextColor(deps.textPrimary)
            setTypeface(deps.typeface, Typeface.BOLD)
            setPadding(
                deps.dp(4),
                deps.dp(2),
                deps.dp(4),
                deps.dp(12)
            )
        }

        val subtitle = TextView(activity).apply {
            text = subtitleText
            textSize = 12f
            setTextColor(deps.textSecondary)
            setPadding(
                deps.dp(4),
                0,
                deps.dp(4),
                deps.dp(12)
            )
        }

        saveButton = MaterialButton(activity).apply {
            text = "Save"
            textSize = 13f
            isAllCaps = false
            setTextColor(deps.textPrimary)
            backgroundTintList =
                ColorStateList.valueOf(accent)
            rippleColor = ColorStateList.valueOf(
                AppTheme.rippleColor
            )
            cornerRadius = deps.dp(14)
            insetTop = 0
            insetBottom = 0
            minHeight = deps.dp(48)
            isEnabled = currentSelection != null
            setPadding(
                deps.dp(18),
                deps.dp(10),
                deps.dp(18),
                deps.dp(10)
            )

            setOnClickListener {
                val selected = apps.firstOrNull {
                    it.pkg == currentSelection
                } ?: return@setOnClickListener

                dialogRef?.dismiss()
                onSelected(selected)
            }
        }

        val cancelButton = MaterialButton(
            activity,
            null,
            com.google.android.material.R.attr
                .materialButtonOutlinedStyle
        ).apply {
            text = "Cancel"
            textSize = 13f
            isAllCaps = false
            setTextColor(deps.textPrimary)
            backgroundTintList =
                ColorStateList.valueOf(
                    Color.TRANSPARENT
                )
            strokeWidth = deps.dp(1)
            strokeColor =
                ColorStateList.valueOf(
                    deps.borderColor
                )
            rippleColor = ColorStateList.valueOf(
                AppTheme.rippleColor
            )
            cornerRadius = deps.dp(14)
            insetTop = 0
            insetBottom = 0
            minHeight = deps.dp(48)
            setPadding(
                deps.dp(18),
                deps.dp(10),
                deps.dp(18),
                deps.dp(10)
            )
            setOnClickListener {
                dialogRef?.dismiss()
            }
        }

        val buttonRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, deps.dp(14), 0, 0)
            addView(cancelButton)
            addView(deps.space(deps.dp(10)))
            addView(saveButton)
        }

        val listHolder = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = deps.roundedBg(
                rowNormal,
                deps.borderColor,
                18
            )
            setPadding(
                deps.dp(8),
                deps.dp(8),
                deps.dp(8),
                deps.dp(8)
            )
            addView(
                listView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    deps.dp(420)
                )
            )
        }

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                deps.dp(18),
                deps.dp(18),
                deps.dp(18),
                deps.dp(18)
            )
            background = deps.roundedBg(
                deps.panelColor,
                deps.borderColor,
                22
            )
            addView(title)
            addView(subtitle)
            addView(listHolder)
            addView(buttonRow)
        }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                deps.dp(12),
                deps.dp(12),
                deps.dp(12),
                deps.dp(12)
            )
            setBackgroundColor(AppTheme.bgColor)
            addView(container)
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(root)
            .setCancelable(true)
            .create()

        dialogRef = dialog
        dialog.show()
        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )

        if (currentSelection != null) {
            val selectedIndex = apps.indexOfFirst {
                it.pkg == currentSelection
            }
            if (selectedIndex >= 0) {
                listView.setSelection(selectedIndex)
            }
        }
    }
}
