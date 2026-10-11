package com.elitedarkkaiser.redmagic

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
        val panel = LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(32,16,32,16) }
        panel.addView(CheckBox(activity).apply {
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
                                if (saved) activity.sendBroadcast(Intent(NotificationLightingService.ACTION_SETTINGS_CHANGED).setPackage(activity.packageName))
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
        panel.addView(Button(activity).apply { text="Add app"; setOnClickListener { showEditor(activity,null,dialog,host) } })
        panel.addView(TextView(activity).apply { text="Configured apps" })
        val packages=NotificationLightingState.packages(activity)
        if(packages.isEmpty()) panel.addView(TextView(activity).apply { text="No apps configured. Tap Add app to create a notification profile." })
        packages.forEach { pkg ->
            val label=runCatching { activity.packageManager.getApplicationLabel(activity.packageManager.getApplicationInfo(pkg,0)).toString() }.getOrDefault(pkg)
            panel.addView(Button(activity).apply { text="Edit $label"; setOnClickListener { showEditor(activity,pkg,dialog,host) } })

        }
    }
    private fun showEditor(activity: Activity, targetPackage: String?, dialog: androidx.appcompat.app.AlertDialog, host: LinearLayout) {
        val apps = activity.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
            .distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(activity.packageManager).toString().lowercase() }
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(32,16,32,16) }
        content.addView(TextView(activity).apply { text="New notifications light up while locked and unplugged. Calls take priority. Fan/trigger LEDs may activate fan power; cooling safety still applies." })
        val appSpinner=Spinner(activity).apply { adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,apps.map { it.loadLabel(activity.packageManager).toString() }) }
        if (targetPackage == null) content.addView(TextView(activity).apply { text="App" })
        content.addView(appSpinner)
        val effects=listOf("steady","breathe","flashing","rapid")
        val effect=Spinner(activity).apply { adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,listOf("Steady","Breathe","Flashing","Rapid")) }
        content.addView(TextView(activity).apply { text="Effect" }); content.addView(effect)
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
        val logo=CheckBox(activity).apply { text="Logo / GAME MODE bar" }
        val triggers=CheckBox(activity).apply { text="Trigger LEDs" }
        val fan=CheckBox(activity).apply { text="Fan LEDs" }
        content.addView(logo);content.addView(triggers);content.addView(fan)
        fun load(index:Int) {
            val pkg=apps.getOrNull(index)?.activityInfo?.packageName ?: return
            val p=NotificationLightingState.read(activity,pkg) ?: NotificationLightingState.Profile()
            selectedColor=p.color;refreshDots();effect.setSelection(effects.indexOf(p.effect).coerceAtLeast(0))
            duration.setSelection(seconds.indexOf(p.seconds).takeIf { it>=0 } ?: 2)
            logo.isChecked=p.logo;triggers.isChecked=p.triggers;fan.isChecked=p.fan
        }
        appSpinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent:AdapterView<*>?,view:android.view.View?,position:Int,id:Long) { load(position) }
            override fun onNothingSelected(parent:AdapterView<*>?) {}
        }
        val targetIndex=apps.indexOfFirst { it.activityInfo.packageName==targetPackage }
        if (targetPackage != null && targetIndex < 0) {
            host.removeAllViews()
            dialog.setTitle("App unavailable")
            host.addView(TextView(activity).apply { text="$targetPackage is no longer available. Its saved profile is retained." })
            host.addView(Button(activity).apply { text="Back"; setOnClickListener { showManager(activity,dialog,host) } })
            host.addView(Button(activity).apply { text="Remove profile"; setOnClickListener {
                confirmRemoval(activity,targetPackage) { showManager(activity,dialog,host) }
            } })
            return
        }
        appSpinner.isEnabled=targetPackage == null
        if (targetPackage != null) appSpinner.visibility=android.view.View.GONE
        val initialIndex=targetIndex.coerceAtLeast(0)
        appSpinner.setSelection(initialIndex)
        load(initialIndex)
        fun changed() { activity.sendBroadcast(Intent(NotificationLightingService.ACTION_SETTINGS_CHANGED).setPackage(activity.packageName)) }
        content.addView(Button(activity).apply { text="Save app profile"; isEnabled=apps.isNotEmpty(); setOnClickListener {
            val pkg = apps.getOrNull(appSpinner.selectedItemPosition)?.activityInfo?.packageName
            if (pkg == null) {
                Toast.makeText(activity,"Select an app before saving.",Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val profile = NotificationLightingState.Profile(selectedColor,effects[effect.selectedItemPosition],seconds[duration.selectedItemPosition],255,logo.isChecked,triggers.isChecked,fan.isChecked)
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
        content.addView(Button(activity).apply { text="Back to configured apps"; setOnClickListener { showManager(activity,dialog,host) } })
        if (targetPackage != null) content.addView(Button(activity).apply { text="Remove app"; setOnClickListener {
            confirmRemoval(activity,targetPackage) { showManager(activity,dialog,host) }
        } })
        dialog.setTitle(if (targetPackage == null) "Add notification app" else runCatching {
            "Edit " + activity.packageManager.getApplicationLabel(activity.packageManager.getApplicationInfo(targetPackage,0)).toString()
        }.getOrDefault("Edit notification app"))
        host.removeAllViews();host.addView(content)
        (host.parent as? ScrollView)?.post { (host.parent as? ScrollView)?.scrollTo(0,0) }
    }

    private fun notificationAccessButton(activity: Activity): Button = object : Button(activity) {
        private fun refreshStatus() {
            val granted=activity.getSystemService(NotificationManager::class.java)
                .isNotificationListenerAccessGranted(ComponentName(activity,NotificationLightingService::class.java))
            text=if (granted) "Notification access granted · Manage" else "Grant notification access"
        }
        init {
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
