package com.elitedarkkaiser.redmagic

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import com.elitedarkkaiser.redmagic.ui.components.LedControlViewFactory
import android.provider.Settings
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.elitedarkkaiser.redmagic.ui.AppTheme

object NotificationLightingDialog {
    fun show(activity: Activity) {
        val panel = LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(32,16,32,16) }
        panel.addView(CheckBox(activity).apply {
            text="Enable screen-off notification lighting"; isChecked=NotificationLightingState.enabled(activity)
            setOnCheckedChangeListener { _, value ->
                NotificationLightingState.setEnabled(activity,value)
                activity.sendBroadcast(Intent(NotificationLightingService.ACTION_SETTINGS_CHANGED).setPackage(activity.packageName))
            }
        })
        panel.addView(Button(activity).apply { text="Grant notification access"; setOnClickListener { activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } })
        val dialog=MaterialAlertDialogBuilder(activity).setTitle("Notification lighting").setView(ScrollView(activity).apply { addView(panel) }).setNegativeButton("Close",null).create()
        panel.addView(Button(activity).apply { text="Add app"; setOnClickListener { dialog.dismiss(); showEditor(activity,null) } })
        panel.addView(TextView(activity).apply { text="Configured apps" })
        val packages=NotificationLightingState.packages(activity)
        if(packages.isEmpty()) panel.addView(TextView(activity).apply { text="No apps configured. Tap Add app to create a notification profile." })
        packages.forEach { pkg ->
            val label=runCatching { activity.packageManager.getApplicationLabel(activity.packageManager.getApplicationInfo(pkg,0)).toString() }.getOrDefault(pkg)
            panel.addView(TextView(activity).apply { text=label })
            panel.addView(Button(activity).apply { text="Edit $label"; setOnClickListener { dialog.dismiss(); showEditor(activity,pkg) } })
            panel.addView(Button(activity).apply { text="Remove $label"; setOnClickListener {
                MaterialAlertDialogBuilder(activity).setMessage("Remove notification lighting for $label?").setNegativeButton("Cancel",null).setPositiveButton("Remove") { _,_ ->
                    NotificationLightingState.remove(activity,pkg)
                    activity.sendBroadcast(Intent(NotificationLightingService.ACTION_SETTINGS_CHANGED).setPackage(activity.packageName))
                    dialog.dismiss(); show(activity)
                }.show()
            } })
        }
        dialog.show()
    }
    private fun showEditor(activity: Activity, targetPackage: String?) {
        val apps = activity.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
            .distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(activity.packageManager).toString().lowercase() }
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(32,16,32,16) }
        val enabled = CheckBox(activity).apply { text="Enable screen-off notification lighting"; isChecked=NotificationLightingState.enabled(activity) }
        content.addView(enabled)
        content.addView(TextView(activity).apply { text="Notification access is required. Only new notifications while the screen is off light up. Calls and plugged-in charging interrupt it. Logo and GAME MODE bar can use separate colors and brightness. Selecting fan/trigger LEDs may activate fan power; cooling safety then applies." })
        content.addView(Button(activity).apply { text="Grant notification access"; setOnClickListener {
            activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } })
        val appSpinner=Spinner(activity).apply { adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,apps.map { it.loadLabel(activity.packageManager).toString() }) }
        content.addView(TextView(activity).apply { text="App" }); content.addView(appSpinner)
        val effects=listOf("steady","breathe","flashing","rapid")
        val effect=Spinner(activity).apply { adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,listOf("Steady","Breathe","Flashing","Rapid")) }
        content.addView(TextView(activity).apply { text="Default effect (split zones use their editor settings)" }); content.addView(effect)
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
        val brightnessLabel=TextView(activity); val brightness=SeekBar(activity).apply { max=223 }
        brightness.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s:SeekBar?,p:Int,user:Boolean) { brightnessLabel.text="Default brightness: ${p+32} / 255" }
            override fun onStartTrackingTouch(s:SeekBar?) {}
            override fun onStopTrackingTouch(s:SeekBar?) {}
        })
        content.addView(brightnessLabel);content.addView(brightness)
        val logo=CheckBox(activity).apply { text="Logo / GAME MODE bar" }
        val triggers=CheckBox(activity).apply { text="Trigger LEDs" }
        val fan=CheckBox(activity).apply { text="Fan LEDs" }
        content.addView(logo);content.addView(triggers);content.addView(fan)
        var logoState: com.elitedarkkaiser.redmagic.state.LedState? = null
        var triggerState: com.elitedarkkaiser.redmagic.state.LedState? = null
        val logoEditor=Button(activity).apply { text="Edit logo / GAME MODE bar colors and brightness"; setOnClickListener {
            LogoBarProfileUi.show(activity,"Notification logo and GAME MODE bar",logoState ?: com.elitedarkkaiser.redmagic.state.LedState(logo.isChecked,LedBrightness.encode(brightness.progress+32,effects[effect.selectedItemPosition]),selectedColor),onSave={ logoState=it; logo.isChecked=it.enabled })
        } }
        content.addView(logoEditor)
        content.addView(Button(activity).apply { text="Edit top / bottom trigger colors and brightness"; setOnClickListener {
            val swatches=LedControlViewFactory(activity)
            var staged=triggerState ?: com.elitedarkkaiser.redmagic.state.LedState(triggers.isChecked,LedBrightness.encode(brightness.progress+32,effects[effect.selectedItemPosition]),selectedColor)
            val editor=TriggerLedProfileUi.create(activity,"Trigger LEDs","Enable trigger LEDs",staged,
                TriggerLedProfileUi.Deps(AppTheme.textPrimary,AppTheme.textSecondary,AppTheme.accentColor,AppTheme.panelPressed,AppTheme.borderColor,
                    { (it*activity.resources.displayMetrics.density).toInt() },swatches::colorDot,swatches::colorDotDrawable)) { staged=it }
            MaterialAlertDialogBuilder(activity).setTitle("Notification triggers").setView(ScrollView(activity).apply { addView(editor) })
                .setNegativeButton("Cancel",null).setPositiveButton("Use settings") { _,_ -> triggerState=staged; triggers.isChecked=staged.enabled }.show()
        } })
        fun load(index:Int) {
            val pkg=apps.getOrNull(index)?.activityInfo?.packageName ?: return
            val p=NotificationLightingState.read(activity,pkg) ?: NotificationLightingState.Profile()
            logoState=p.logoState
            triggerState=p.triggerState
            selectedColor=p.color;refreshDots();effect.setSelection(effects.indexOf(p.effect).coerceAtLeast(0))
            duration.setSelection(seconds.indexOf(p.seconds).takeIf { it>=0 } ?: 2)
            brightness.progress=p.brightness-32;brightnessLabel.text="Brightness: ${p.brightness} / 255"
            logo.isChecked=p.logo;triggers.isChecked=p.triggers;fan.isChecked=p.fan
        }
        appSpinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent:AdapterView<*>?,view:android.view.View?,position:Int,id:Long) { load(position) }
            override fun onNothingSelected(parent:AdapterView<*>?) {}
        }
        val initialIndex=apps.indexOfFirst { it.activityInfo.packageName==targetPackage }.coerceAtLeast(0)
        appSpinner.setSelection(initialIndex)
        load(initialIndex)
        fun changed() { activity.sendBroadcast(Intent(NotificationLightingService.ACTION_SETTINGS_CHANGED).setPackage(activity.packageName)) }
        MaterialAlertDialogBuilder(activity).setTitle("Notification lighting")
            .setView(ScrollView(activity).apply { addView(content) }).setNegativeButton("Cancel",null)
            .setNeutralButton("Remove app") { _,_ ->
                apps.getOrNull(appSpinner.selectedItemPosition)?.activityInfo?.packageName?.let { NotificationLightingState.remove(activity,it) };changed()
            }.setPositiveButton("Save") { _,_ ->
                NotificationLightingState.setEnabled(activity,enabled.isChecked)
                apps.getOrNull(appSpinner.selectedItemPosition)?.activityInfo?.packageName?.let {
                    NotificationLightingState.save(activity,it,NotificationLightingState.Profile(selectedColor,effects[effect.selectedItemPosition],seconds[duration.selectedItemPosition],brightness.progress+32,logo.isChecked,triggers.isChecked,fan.isChecked,logoState?.copy(enabled=logo.isChecked),triggerState?.copy(enabled=triggers.isChecked)))
                };changed()
            }.show()
    }
}
