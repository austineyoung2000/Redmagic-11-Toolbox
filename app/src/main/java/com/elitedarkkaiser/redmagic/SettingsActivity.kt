package com.elitedarkkaiser.redmagic

import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.content.Intent
import java.util.concurrent.Executors
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsActivity : AppCompatActivity() {
    private val backupExecutor = Executors.newSingleThreadExecutor()
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        MasterProfileDocumentTransfer.handleActivityResult(this,requestCode,resultCode,data) { task ->
            runCatching { backupExecutor.execute(task); true }.getOrDefault(false)
        }
    }
    override fun onDestroy() {
        backupExecutor.shutdown()
        super.onDestroy()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTheme.configure(this)

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        fun text(
            value: String,
            size: Float,
            secondary: Boolean = false,
            bold: Boolean = false
        ) = TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(
                if (secondary) AppTheme.textSecondary
                else AppTheme.textPrimary
            )
            if (bold) {
                setTypeface(AppTheme.appTypeface, Typeface.BOLD)
            }
        }

        fun panel() = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = AppTheme.roundedBg(
                AppTheme.panelColor,
                AppTheme.borderColor,
                dp(22).toFloat()
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(18))
        }

        val backButton = MaterialButton(this).apply {
            text = "‹"
            textSize = 28f
            contentDescription = "Back"
            minWidth = dp(48)
            minHeight = dp(48)
            insetTop = 0
            insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(
                AppTheme.chipOnColor
            )
            setTextColor(AppTheme.textPrimary)
            cornerRadius = dp(16)
            setOnClickListener { finish() }
        }

        header.addView(backButton)
        header.addView(text("Settings", 23f, bold = true).apply {
            setPadding(dp(14), 0, 0, 0)
        })

        val temperatureSummary = text("", 12f, secondary = true)
        val temperatureSwitch = MaterialSwitch(this).apply {
            text = "Use Fahrenheit"
            textSize = 14f
            setTextColor(AppTheme.textPrimary)
            isChecked = isUseFahrenheitStorage(this@SettingsActivity)
        }

        fun updateTemperatureSummary(useFahrenheit: Boolean) {
            temperatureSummary.text = if (useFahrenheit) {
                "Temperatures are displayed in Fahrenheit (°F)."
            } else {
                "Temperatures are displayed in Celsius (°C)."
            }
        }

        updateTemperatureSummary(temperatureSwitch.isChecked)
        temperatureSwitch.setOnCheckedChangeListener { _, checked ->
            saveUseFahrenheitStorage(this, checked)
            updateTemperatureSummary(checked)
        }

        val temperaturePanel = panel().apply {
            addView(text("TEMPERATURE", 12f, secondary = true, bold = true))
            addView(temperatureSwitch)
            addView(temperatureSummary.apply {
                setPadding(0, dp(4), 0, 0)
            })
        }

        val nightMode = resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK
        val appearanceName = if (
            nightMode == Configuration.UI_MODE_NIGHT_YES
        ) "Dark" else "Light"

        val appearancePanel = panel().apply {
            addView(text("APPEARANCE", 12f, secondary = true, bold = true))
            addView(text("Follow system theme", 14f, bold = true).apply {
                setPadding(0, dp(10), 0, 0)
            })
            addView(text(
                "Currently using $appearanceName mode. The app changes " +
                    "automatically with Android's light/dark setting.",
                12f,
                secondary = true
            ).apply { setPadding(0, dp(4), 0, 0) })
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(28))
            addView(header)
            addView(temperaturePanel)
            addView(appearancePanel)
            addView(panel().apply {
                addView(text("BACKUP & RESTORE", 12f, secondary = true, bold = true))
                addView(text("Save current settings, Master Profiles, notification lighting and Edit-button positions to a JSON file. Android permissions and runtime state are not included.",12f,secondary=true))
                addView(MaterialButton(this@SettingsActivity).apply {
                    text="Export backup"
                    setOnClickListener { MasterProfileDocumentTransfer.requestExport(this@SettingsActivity) }
                })
                addView(MaterialButton(this@SettingsActivity).apply {
                    text="Restore backup"
                    setOnClickListener { MasterProfileDocumentTransfer.requestImport(this@SettingsActivity) }
                })
            })
            addView(panel().apply {
                addView(text("DIAGNOSTICS", 12f, secondary = true, bold = true))
                addView(MaterialButton(this@SettingsActivity).apply {
                    text = "Boot diagnostics"
                    setOnClickListener { BootDiagnosticsDialog.show(this@SettingsActivity) }
                })
            })
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(AppTheme.bgColor)
            addView(content)
        }

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        setContentView(scroll)
    }
}
