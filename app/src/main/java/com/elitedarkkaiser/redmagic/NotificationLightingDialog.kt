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
        val apps = activity.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
            .distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(activity.packageManager).toString().lowercase() }
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(32,16,32,16) }
        val enabled = CheckBox(activity).apply { text="Enable screen-off notification lighting"; isChecked=NotificationLightingState.enabled(activity) }
        content.addView(enabled)
        content.addView(TextView(activity).apply { text="Notification access is required. Only new notifications while the screen is off light up. Calls and plugged-in charging interrupt it. Logo also lights the GAME MODE bar. Selecting fan/trigger LEDs may activate fan power; cooling safety then applies." })
        content.addView(Button(activity).apply { text="Grant notification access"; setOnClickListener {
            activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } })
        val appSpinner=Spinner(activity).apply { adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,apps.map { it.loadLabel(activity.packageManager).toString() }) }
        content.addView(TextView(activity).apply { text="App" }); content.addView(appSpinner)
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
        val brightnessLabel=TextView(activity); val brightness=SeekBar(activity).apply { max=223 }
        brightness.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s:SeekBar?,p:Int,user:Boolean) { brightnessLabel.text="Brightness: ${p+32} / 255" }
            override fun onStartTrackingTouch(s:SeekBar?) {}
            override fun onStopTrackingTouch(s:SeekBar?) {}
        })
        content.addView(brightnessLabel);content.addView(brightness)
        val logo=CheckBox(activity).apply { text="Logo and GAME MODE bar" }
        val triggers=CheckBox(activity).apply { text="Trigger LEDs" }
        val fan=CheckBox(activity).apply { text="Fan LEDs" }
        content.addView(logo);content.addView(triggers);content.addView(fan)
        fun load(index:Int) {
            val pkg=apps.getOrNull(index)?.activityInfo?.packageName ?: return
            val p=NotificationLightingState.read(activity,pkg) ?: NotificationLightingState.Profile()
            selectedColor=p.color;refreshDots();effect.setSelection(effects.indexOf(p.effect).coerceAtLeast(0))
            duration.setSelection(seconds.indexOf(p.seconds).takeIf { it>=0 } ?: 2)
            brightness.progress=p.brightness-32;brightnessLabel.text="Brightness: ${p.brightness} / 255"
            logo.isChecked=p.logo;triggers.isChecked=p.triggers;fan.isChecked=p.fan
        }
        appSpinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent:AdapterView<*>?,view:android.view.View?,position:Int,id:Long) { load(position) }
            override fun onNothingSelected(parent:AdapterView<*>?) {}
        }
        load(0)
        fun changed() { activity.sendBroadcast(Intent(NotificationLightingService.ACTION_SETTINGS_CHANGED).setPackage(activity.packageName)) }
        MaterialAlertDialogBuilder(activity).setTitle("Notification lighting")
            .setView(ScrollView(activity).apply { addView(content) }).setNegativeButton("Cancel",null)
            .setNeutralButton("Remove app") { _,_ ->
                apps.getOrNull(appSpinner.selectedItemPosition)?.activityInfo?.packageName?.let { NotificationLightingState.remove(activity,it) };changed()
            }.setPositiveButton("Save") { _,_ ->
                NotificationLightingState.setEnabled(activity,enabled.isChecked)
                apps.getOrNull(appSpinner.selectedItemPosition)?.activityInfo?.packageName?.let {
                    NotificationLightingState.save(activity,it,NotificationLightingState.Profile(selectedColor,effects[effect.selectedItemPosition],seconds[duration.selectedItemPosition],brightness.progress+32,logo.isChecked,triggers.isChecked,fan.isChecked))
                };changed()
            }.show()
    }
}
