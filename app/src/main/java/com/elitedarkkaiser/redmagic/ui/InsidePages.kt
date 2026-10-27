package com.elitedarkkaiser.redmagic.ui

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Presents existing control cards as focused pages without changing their handlers. */
internal object InsidePages {
    data class Page(val title: String, val subtitle: String, val cards: List<View>, val action: (() -> Unit)? = null, val icon: String = "✦", val palette: (() -> Int?)? = null, val factory: (() -> List<View>)? = null)

    private data class Navigation(val open: (Int) -> Unit, val back: () -> Boolean, val refresh: () -> Unit)
    fun open(container: LinearLayout, index: Int) {
        (container.tag as? Navigation)?.open?.invoke(index)
    }
    fun back(container: LinearLayout): Boolean = (container.tag as? Navigation)?.back?.invoke() ?: false
    fun refresh(container: LinearLayout) { (container.tag as? Navigation)?.refresh?.invoke() }

    fun install(container: LinearLayout, title: String, pages: List<Page>, prefix: (() -> List<View>)? = null) {
        val context = container.context
        val dp: (Int) -> Int = { AppTheme.dp(context, it) }
        container.removeAllViews()
        val menu = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val detail = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        fun label(value: String, size: Float, bold: Boolean = false) = TextView(context).apply {
            text = value
            textSize = size
            setTextColor(AppTheme.textPrimary)
            if (bold) setTypeface(AppTheme.appTypeface, Typeface.BOLD)
        }
        menu.addView(label(title, 26f, true).apply { setPadding(0, dp(8), 0, dp(18)) })
        val prefixViews = mutableListOf<View>()
        fun refreshPrefix() {
            prefixViews.forEach { menu.removeView(it) }
            prefixViews.clear()
            prefix?.invoke()?.forEachIndexed { index, view ->
                menu.addView(view, index + 1)
                prefixViews.add(view)
            }
        }
        refreshPrefix()
        val menuRows = mutableListOf<View>()
        pages.forEach { page ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), dp(17), dp(18), dp(17))
                background = AppTheme.roundedBg(AppTheme.panelColor, AppTheme.borderColor, dp(18).toFloat())
                isClickable = true
                isFocusable = true
                contentDescription = "Open ${page.title}"
            }
            val icon = ToolboxIcons.create(context, page.title)
            val iconParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(16) }
            row.addView(icon, iconParams)
            val text = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            text.addView(label(page.title, 16f, true))
            text.addView(label(page.subtitle, 13f).apply {
                setTextColor(AppTheme.textSecondary)
                setPadding(0, dp(4), 0, 0)
            })
            page.palette?.let { selectedColor ->
                val palette = object : LinearLayout(context) {
                    private val ids = listOf(1, 3, 4, 5, 6, 7, 8, 9)
                    private val paletteColors = listOf("#FF0000", "#FF8C00", "#FFD600", "#00E676", "#00E5FF", "#1565FF", "#A020F0", "#FF69B4")
                    init {
                        orientation = HORIZONTAL
                        setPadding(0, dp(10), 0, 0)
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        repeat(ids.size) {
                            addView(View(context), LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(5) })
                        }
                        refresh()
                    }
                    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                        super.onWindowFocusChanged(hasWindowFocus)
                        if (hasWindowFocus) refresh()
                    }
                    private fun refresh() {
                        val selected = selectedColor()
                        ids.forEachIndexed { index, id ->
                            getChildAt(index).background = android.graphics.drawable.GradientDrawable().apply {
                                shape = android.graphics.drawable.GradientDrawable.OVAL
                                setColor(android.graphics.Color.parseColor(paletteColors[index]))
                                if (selected == id) setStroke(dp(2), android.graphics.Color.WHITE)
                            }
                        }
                    }
                }
                text.addView(palette)
            }
            row.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(label("›", 26f))
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.bottomMargin = dp(10)
            menu.addView(row, params)
            menuRows.add(row)
            row.setOnClickListener {
                if (page.action != null) {
                    page.action.invoke()
                    return@setOnClickListener
                }
                detail.removeAllViews()
                detail.addView(label(page.title, 26f, true).apply {
                    setPadding(0, dp(8), 0, dp(18))
                })
                (page.factory?.invoke() ?: page.cards).forEach { card ->
                    (card.parent as? ViewGroup)?.removeView(card)
                    detail.addView(card)
                }
                menu.visibility = View.GONE
                detail.visibility = View.VISIBLE
            }
        }
        container.addView(menu)
        container.addView(detail)
        container.tag = Navigation(
            open = { index -> menuRows.getOrNull(index)?.performClick() },
            back = {
                if (detail.visibility == View.VISIBLE) {
                    detail.visibility = View.GONE
                    refreshPrefix()
                    menu.visibility = View.VISIBLE
                    true
                } else false
            },
            refresh = { if (menu.visibility == View.VISIBLE) refreshPrefix() }
        )
    }
}
