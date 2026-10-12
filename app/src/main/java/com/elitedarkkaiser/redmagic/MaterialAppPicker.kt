package com.elitedarkkaiser.redmagic

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** Shared presentation; the feature still owns its selection and saved profile. */
internal object MaterialAppPicker {
    data class App(val packageName: String, val label: String)
    fun show(context: Context, title: String, apps: List<App>, selectedPackage: String? = null,
        subtitle: String = "Choose an app", actionLabel: String? = null,
        onManage: (() -> Unit)? = null, onDefaults: (() -> Unit)? = null,
        confirmSelection: Boolean = false, onSelected: (App) -> Unit) {
        showList(context, title, subtitle, apps, selectedPackage?.let { setOf(it) }.orEmpty(), false, { result ->
            apps.firstOrNull { it.packageName in result }?.let(onSelected)
        }, actionLabel, onManage, onDefaults, confirmSelection)
    }
    fun showMultiple(context: Context, title: String, apps: List<App>, selected: Set<String>, onSave: (Set<String>) -> Unit) {
        showList(context, title, "Select apps. Edit each app's profile separately.", apps, selected, true, onSave)
    }
    private fun showList(context: Context, title: String, subtitle: String, apps: List<App>,
        initial: Set<String>, multiple: Boolean, onSave: (Set<String>) -> Unit,
        actionLabel: String? = null, onManage: (() -> Unit)? = null, onDefaults: (() -> Unit)? = null,
        confirmSelection: Boolean = false) {
        val selected = initial.toMutableSet()
        var visible = apps
        val icons = LruCache<String, Drawable>(48)
        fun dp(value: Int) = AppTheme.dp(context, value)
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), 0)
        }
        panel.addView(TextView(context).apply {
            text = subtitle; textSize = 13f; setTextColor(AppTheme.textSecondary); setPadding(0, 0, 0, dp(12))
        })
        val search = TextInputEditText(context).apply { isSingleLine = true }
        panel.addView(TextInputLayout(context).apply {
            hint = "Search apps or packages"; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE; addView(search)
        })
        val empty = TextView(context).apply { text = "No matching apps"; setPadding(dp(8), dp(16), dp(8), dp(16)) }
        panel.addView(empty)
        val list = ListView(context).apply {
            divider = null; dividerHeight = 0; setPadding(0, dp(8), 0, 0); clipToPadding = false; emptyView = empty
        }
        panel.addView(list, LinearLayout.LayoutParams(-1, (context.resources.displayMetrics.heightPixels * .48f).toInt()))
        var dialog: androidx.appcompat.app.AlertDialog? = null
        val adapter = object : BaseAdapter() {
            override fun getCount() = visible.size
            override fun getItem(position: Int) = visible[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val app = getItem(position)
                val checked = app.packageName in selected
                val card = MaterialCardView(context).apply {
                    radius = dp(16).toFloat(); cardElevation = 0f; strokeWidth = dp(if (checked) 2 else 1)
                    strokeColor = context.getColor(if (checked) R.color.redmagic_primary else R.color.redmagic_border)
                    setCardBackgroundColor(context.getColor(if (checked) R.color.redmagic_primary_container else R.color.redmagic_surface))
                }
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), dp(12), dp(12), dp(12))
                }
                if (actionLabel == null) row.addView(MaterialCheckBox(context).apply {
                    isChecked = checked; isClickable = false; isFocusable = false
                    buttonTintList = ColorStateList.valueOf(context.getColor(R.color.redmagic_primary))
                })
                row.addView(ImageView(context).apply {
                    val icon = icons.get(app.packageName) ?: runCatching { context.packageManager.getApplicationIcon(app.packageName) }
                        .getOrNull()?.also { icons.put(app.packageName, it) }
                    setImageDrawable(icon); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginStart = dp(4) })
                row.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply {
                        text = app.label; textSize = 15f; setTypeface(null, Typeface.BOLD)
                        setTextColor(context.getColor(if (checked) R.color.redmagic_on_primary_container else R.color.redmagic_on_surface))
                        maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                    addView(TextView(context).apply {
                        text = app.packageName; textSize = 11f
                        setTextColor(context.getColor(if (checked) R.color.redmagic_on_primary_container else R.color.redmagic_on_surface_variant))
                        maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
                card.addView(row)
                card.contentDescription = "${app.label}, ${app.packageName}, ${if (checked) "selected" else "not selected"}"
                card.setOnClickListener {
                    if (multiple) {
                        if (!selected.add(app.packageName)) selected.remove(app.packageName)
                        notifyDataSetChanged()
                    } else if (confirmSelection) {
                        selected.clear(); selected.add(app.packageName); notifyDataSetChanged()
                        dialog?.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                    } else { dialog?.dismiss(); onSave(setOf(app.packageName)) }
                }
                if (actionLabel != null) row.addView(com.google.android.material.button.MaterialButton(context).apply {
                    text = actionLabel; isAllCaps = false; minWidth = 0; minimumWidth = 0
                    setOnClickListener { card.performClick() }
                })
                return FrameLayout(context).apply { setPadding(0, 0, 0, dp(8)); addView(card, FrameLayout.LayoutParams(-1, -2)) }
            }
        }
        list.adapter = adapter
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                visible = apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
                adapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        val builder = MaterialAlertDialogBuilder(context).setTitle(title).setView(panel)
            .setNegativeButton(if (actionLabel == null) "Cancel" else "Close", null)
        if (multiple || confirmSelection) builder.setPositiveButton("Save") { _, _ -> onSave(selected.toSet()) }
        if (onManage != null) builder.setNeutralButton("Add/remove apps") { _, _ -> onManage() }
        if (onDefaults != null) builder.setPositiveButton("Shared defaults") { _, _ -> onDefaults() }
        dialog = builder.create(); dialog.show()
        if (confirmSelection) dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            .isEnabled = apps.any { it.packageName in selected }
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }
}
