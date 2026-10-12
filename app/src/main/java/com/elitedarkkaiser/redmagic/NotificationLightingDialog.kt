package com.elitedarkkaiser.redmagic

import com.elitedarkkaiser.redmagic.ui.components.SelectionControlStyle
import android.app.Activity
import android.content.Intent
import android.app.NotificationManager
import android.content.ComponentName
import com.elitedarkkaiser.redmagic.ui.components.LedControlViewFactory
import android.provider.Settings
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.elitedarkkaiser.redmagic.ui.AppTheme

object NotificationLightingDialog {
    private val saveExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    fun show(activity: Activity) {
        val host=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
        val dialog=MaterialAlertDialogBuilder(activity).setTitle("Notification lighting")
            .setView(ScrollView(activity).apply { addView(host) }).setNegativeButton("Close",null).create()
        showManager(activity,dialog,host)
        dialog.show()
    }
    private fun showManager(activity: Activity, dialog: androidx.appcompat.app.AlertDialog, host: LinearLayout) {
        dialog.setTitle("Notification lighting")
        host.removeAllViews()
        val panel = LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(AppTheme.dp(activity,16),AppTheme.dp(activity,8),AppTheme.dp(activity,16),AppTheme.dp(activity,12)) }
        panel.addView(com.google.android.material.materialswitch.MaterialSwitch(activity).apply {
            text="Enable screen-off notification lighting"; isChecked=NotificationLightingState.enabled(activity)
            var restoring = false
            setOnCheckedChangeListener { _, value ->
                if (!restoring) {
                    isEnabled = false
                    val previous = !value
                    saveExecutor.execute {
                        val saved = runCatching { NotificationLightingState.setEnabled(activity,value) }.getOrDefault(false)
                        if (!saved) runCatching { NotificationLightingState.setEnabled(activity,previous) }
                        activity.runOnUiThread {
                            if (!activity.isDestroyed) {
                                isEnabled = true
                                if (saved) {
                                    activity.sendBroadcast(Intent(NotificationLightingService.ACTION_SETTINGS_CHANGED).setPackage(activity.packageName))
                                    NotificationLightingService.ensureConnected(activity)
                                }
                                else {
                                    restoring = true; isChecked = previous; restoring = false
                                    Toast.makeText(activity,"Notification setting could not be saved. Try again.",Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
            }
        })
        panel.addView(notificationAccessButton(activity))
        host.addView(panel)
        panel.addView(com.google.android.material.button.MaterialButton(activity).apply { isAllCaps=false; text="Add app"; setOnClickListener { showEditor(activity,null,dialog,host) } })
        panel.addView(TextView(activity).apply { text="Configured apps" })
        val packages=NotificationLightingState.packages(activity)
        if(packages.isEmpty()) panel.addView(TextView(activity).apply { text="No apps configured. Tap Add app to create a notification profile." })
        val configuredApps = packages.map { pkg ->
            val label = runCatching { activity.packageManager.getApplicationLabel(
                activity.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            MaterialAppPicker.App(pkg, label)
        }.sortedBy { it.label.lowercase() }
        val cards = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        fun renderApps(query: String = "") {
            cards.removeAllViews()
            val matching = configuredApps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
            matching.forEach { app ->
                cards.addView(MaterialAppPicker.editCard(activity, app) {
                    showEditor(activity, app.packageName, dialog, host)
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = AppTheme.dp(activity, 8) })
            }
            if (matching.isEmpty() && configuredApps.isNotEmpty()) cards.addView(TextView(activity).apply {
                text = "No matching configured apps"; setTextColor(AppTheme.textSecondary)
            })
        }
        if (configuredApps.isNotEmpty()) {
            val search = com.google.android.material.textfield.TextInputEditText(activity).apply { isSingleLine = true }
            panel.addView(com.google.android.material.textfield.TextInputLayout(activity).apply {
                hint = "Search configured apps"
                boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
                addView(search)
            })
            search.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderApps(s?.toString()?.trim().orEmpty()) }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }
        panel.addView(cards)
        renderApps()

    }
    private fun showEditor(activity: Activity, targetPackage: String?, dialog: androidx.appcompat.app.AlertDialog, host: LinearLayout) {
        val apps = activity.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
            .distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(activity.packageManager).toString().lowercase() }
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(AppTheme.dp(activity,16),AppTheme.dp(activity,8),AppTheme.dp(activity,16),AppTheme.dp(activity,12)) }
        content.addView(TextView(activity).apply { text="New notifications light up while locked and unplugged. Calls take priority. Fan/trigger LEDs may activate fan power; cooling safety still applies." })
        var selectedAppIndex = -1
        val appButton = com.google.android.material.button.MaterialButton(activity).apply {
            text = "Choose app"; isAllCaps = false
        }
        if (targetPackage == null) content.addView(TextView(activity).apply { text="App" })
        content.addView(appButton)
        val effects=listOf("steady","breathe","flashing","rapid")
        var selectedEffect = "steady"
        val effectButtons = linkedMapOf<String, android.widget.Button>()
        fun refreshEffects() {
            effectButtons.forEach { (value, button) -> SelectionControlStyle.apply(button, value == selectedEffect) }
        }
        val effectRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val effectFactory = LedControlViewFactory(activity)
        effects.zip(listOf("Steady", "Breathe", "Flashing", "Rapid")).forEach { (value, title) ->
            val button = effectFactory.filterChip(title, value == selectedEffect) {
                selectedEffect = value
                refreshEffects()
            }
            effectButtons[value] = button
            effectRow.addView(button, LinearLayout.LayoutParams(0,
                AppTheme.dp(activity, 48), 1f).apply {
                if (effectRow.childCount > 0) marginStart = AppTheme.dp(activity, 6)
            })
        }
        content.addView(TextView(activity).apply { text="Effect" })
        content.addView(effectRow)
        val colors=listOf(1 to 0xffff0000.toInt(),3 to 0xffff8800.toInt(),4 to 0xffffd700.toInt(),5 to 0xff00dd77.toInt(),6 to 0xff00ddee.toInt(),7 to 0xff1765ff.toInt(),8 to 0xffaa22ee.toInt(),9 to 0xffff66bb.toInt())
        val names=listOf("Red","Orange","Yellow","Green","Cyan","Blue","Purple","Pink")
        var selectedColor=7
        val palette = LedControlViewFactory(activity)
        val dots=mutableListOf<android.view.View>()
        fun refreshDots() { dots.forEachIndexed { index, dot ->
            dot.background=palette.colorDotDrawable(String.format("#%06X", colors[index].second and 0xffffff), selectedColor==colors[index].first)
        } }
        colors.chunked(4).forEachIndexed { rowIndex,rowColors -> content.addView(LinearLayout(activity).apply {
            rowColors.forEachIndexed { col,pair -> addView(palette.colorDot(String.format("#%06X", pair.second and 0xffffff), selectedColor==pair.first) {
                selectedColor=pair.first; refreshDots()
            }.apply {
                contentDescription=names[rowIndex*4+col]
                dots.add(this)
            },LinearLayout.LayoutParams((44*activity.resources.displayMetrics.density).toInt(),(44*activity.resources.displayMetrics.density).toInt()).apply { setMargins(6,6,6,6) }) }
        }) }
        val duration=Spinner(activity).apply { adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,listOf("3 seconds","5 seconds","10 seconds","15 seconds","30 seconds")) }
        val seconds=listOf(3,5,10,15,30)
        content.addView(TextView(activity).apply { text="Lighting window" }); content.addView(duration)
        content.addView(TextView(activity).apply { text="Uses REDMAGIC stock brightness. New distinct notifications restart the selected lighting window." })
        val logo=com.google.android.material.checkbox.MaterialCheckBox(activity).apply { text="Logo / GAME MODE bar" }
        val triggers=com.google.android.material.checkbox.MaterialCheckBox(activity).apply { text="Trigger LEDs" }
        val fan=com.google.android.material.checkbox.MaterialCheckBox(activity).apply { text="Fan LEDs" }
        content.addView(logo);content.addView(triggers);content.addView(fan)
        fun load(index:Int) {
            val pkg=apps.getOrNull(index)?.activityInfo?.packageName ?: return
            val p=NotificationLightingState.read(activity,pkg) ?: NotificationLightingState.Profile()
            selectedColor=p.color;refreshDots();selectedEffect=p.effect.takeIf { it in effects } ?: "steady";refreshEffects()
            duration.setSelection(seconds.indexOf(p.seconds).takeIf { it>=0 } ?: 2)
            logo.isChecked=p.logo;triggers.isChecked=p.triggers;fan.isChecked=p.fan
        }
        appButton.setOnClickListener {
            MaterialAppPicker.show(activity, "Choose notification app",
                apps.map { MaterialAppPicker.App(it.activityInfo.packageName, it.loadLabel(activity.packageManager).toString()) },
                apps.getOrNull(selectedAppIndex)?.activityInfo?.packageName) { app ->
                selectedAppIndex = apps.indexOfFirst { it.activityInfo.packageName == app.packageName }
                appButton.text = app.label
                load(selectedAppIndex)
            }
        }
        val targetIndex=apps.indexOfFirst { it.activityInfo.packageName==targetPackage }
        if (targetPackage != null && targetIndex < 0) {
            host.removeAllViews()
            dialog.setTitle("App unavailable")
            host.addView(TextView(activity).apply { text="$targetPackage is no longer available. Its saved profile is retained." })
            host.addView(com.google.android.material.button.MaterialButton(activity).apply { isAllCaps=false; text="Back"; setOnClickListener { showManager(activity,dialog,host) } })
            host.addView(com.google.android.material.button.MaterialButton(activity).apply { isAllCaps=false; text="Remove profile"; setOnClickListener {
                confirmRemoval(activity,targetPackage) { showManager(activity,dialog,host) }
            } })
            return
        }
        appButton.isEnabled=targetPackage == null

        val initialIndex=targetIndex.coerceAtLeast(0)
        selectedAppIndex = initialIndex
        appButton.text = apps.getOrNull(initialIndex)?.loadLabel(activity.packageManager) ?: "Choose app"
        load(initialIndex)
        fun changed() {
            activity.sendBroadcast(Intent(NotificationLightingService.ACTION_SETTINGS_CHANGED).setPackage(activity.packageName))
            NotificationLightingService.ensureConnected(activity)
        }
        content.addView(com.google.android.material.button.MaterialButton(activity).apply { isAllCaps=false; text="Save app profile"; isEnabled=apps.isNotEmpty(); setOnClickListener {
            val pkg = apps.getOrNull(selectedAppIndex)?.activityInfo?.packageName
            if (pkg == null) {
                Toast.makeText(activity,"Select an app before saving.",Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val profile = NotificationLightingState.Profile(selectedColor,selectedEffect,seconds[duration.selectedItemPosition],255,logo.isChecked,triggers.isChecked,fan.isChecked)
            isEnabled = false
            saveExecutor.execute {
                val saved = runCatching { NotificationLightingState.save(activity,pkg,profile) }.getOrDefault(false)
                activity.runOnUiThread {
                    if (!activity.isDestroyed) {
                        isEnabled = true
                        if (saved) {
                            changed(); showManager(activity,dialog,host)
                            Toast.makeText(activity,"Notification profile saved",Toast.LENGTH_SHORT).show()
                        } else Toast.makeText(activity,"Profile could not be saved. Your editor remains open; try again.",Toast.LENGTH_LONG).show()
                    }
                }
            }
        } })
        content.addView(com.google.android.material.button.MaterialButton(activity).apply { isAllCaps=false; text="Back to configured apps"; setOnClickListener { showManager(activity,dialog,host) } })
        if (targetPackage != null) content.addView(com.google.android.material.button.MaterialButton(activity).apply { isAllCaps=false; text="Remove app"; setOnClickListener {
            confirmRemoval(activity,targetPackage) { showManager(activity,dialog,host) }
        } })
        dialog.setTitle(if (targetPackage == null) "Add notification app" else runCatching {
            "Edit " + activity.packageManager.getApplicationLabel(activity.packageManager.getApplicationInfo(targetPackage,0)).toString()
        }.getOrDefault("Edit notification app"))
        host.removeAllViews();host.addView(content)
        (host.parent as? ScrollView)?.post { (host.parent as? ScrollView)?.scrollTo(0,0) }
    }

    private fun notificationAccessButton(activity: Activity): Button = object : com.google.android.material.button.MaterialButton(activity) {
        private fun refreshStatus() {
            val granted=activity.getSystemService(NotificationManager::class.java)
                .isNotificationListenerAccessGranted(ComponentName(activity,NotificationLightingService::class.java))
            text=if (granted) "Notification access granted · Manage" else "Grant notification access"
            if (granted) NotificationLightingService.ensureConnected(activity)
        }
        init {
            isAllCaps = false
            refreshStatus()
            setOnClickListener { activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            super.onWindowFocusChanged(hasWindowFocus)
            if (hasWindowFocus) refreshStatus()
        }
    }
    private fun confirmRemoval(activity: Activity, pkg: String, onRemoved: () -> Unit) {
        val label=runCatching { activity.packageManager.getApplicationLabel(activity.packageManager.getApplicationInfo(pkg,0)).toString() }.getOrDefault(pkg)
        MaterialAlertDialogBuilder(activity).setMessage("Remove notification lighting for $label?")
            .setNegativeButton("Cancel",null).setPositiveButton("Remove") { _,_ ->
                NotificationLightingState.remove(activity,pkg)
                activity.sendBroadcast(Intent(NotificationLightingService.ACTION_SETTINGS_CHANGED).setPackage(activity.packageName))
                onRemoved()
            }.show()
    }
}
