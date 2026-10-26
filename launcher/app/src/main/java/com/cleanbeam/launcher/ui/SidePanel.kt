package com.cleanbeam.launcher.ui

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.cleanbeam.launcher.R
import com.cleanbeam.launcher.graphics.Cards
import com.cleanbeam.launcher.graphics.Look

/**
 * Every menu of the launcher: a sheet that slides in from the right edge over a scrim.
 *
 * Levels stack: [open] pushes one, Back pops one, Left (or the scrim) collapses them all.
 * A level is a builder, not a snapshot, so [refresh] can redraw it after a toggle while the
 * focused row stays put. Focus never leaves the sheet while it is open.
 */
class SidePanel @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    /** One line of a level. [checked] draws a tick (true) or leaves room for one (false); null draws nothing. */
    class Row(
        val label: String,
        val value: String? = null,
        val checked: Boolean? = null,
        val onSelect: (() -> Unit)? = null
    )

    /**
     * What a level shows. [input] is placed above the rows (a text field with its own Save/Cancel
     * rows); [onKey] sees every key before the panel does, for the shortcut capture.
     */
    class Content(
        val title: String,
        val rows: List<Row>,
        val message: String? = null,
        val input: EditText? = null,
        val onKey: ((KeyEvent) -> Boolean)? = null
    )

    private val scrim: View
    private val sheet: View
    private val content: View
    private val title: TextView
    private val message: TextView
    private val scroll: ScrollView
    private val host: LinearLayout
    /** The rows of the level on screen; a render builds a new one and drops the old afterwards. */
    private var rows: LinearLayout? = null
    private val stack = ArrayList<() -> Content>()
    private var current: Content? = null
    private val ease = PathInterpolator(0.2f, 0f, 0f, 1f)
    private var restoreFocus: View? = null
    private var gen = 0
    private var accent = 0

    val isOpen: Boolean get() = visibility == VISIBLE && stack.isNotEmpty()

    init {
        LayoutInflater.from(context).inflate(R.layout.side_panel, this, true)
        scrim = findViewById(R.id.panel_scrim)
        sheet = findViewById(R.id.panel_sheet)
        content = findViewById(R.id.panel_content)
        title = findViewById(R.id.panel_title)
        message = findViewById(R.id.panel_message)
        scroll = findViewById(R.id.panel_scroll)
        host = findViewById(R.id.panel_rows)
        scrim.setOnClickListener { close() }
        restyle(0)
    }

    /** Solid or glass sheet after the theme, and the tick colour after the accent. Call [refresh] to redraw. */
    fun restyle(accentColor: Int) {
        sheet.setBackgroundResource(Look.panelBg)
        accent = accentColor
    }

    /** Push a level. The first one slides the sheet in; later ones slide the content across. */
    fun open(builder: () -> Content) {
        val wasOpen = isOpen
        stack += builder
        if (!wasOpen) {
            restoreFocus = (context as? Activity)?.currentFocus
            gen++
            visibility = VISIBLE
            render(builder(), focusIndex = -1)
            scrim.alpha = 0f
            scrim.animate().cancel()
            scrim.animate().alpha(1f).setDuration(240).start()
            content.alpha = 1f; content.translationX = 0f
            // The sheet has a fixed width in the layout, so it is known before the first layout pass.
            sheet.animate().cancel()
            sheet.translationX = sheet.layoutParams.width.toFloat()
            sheet.animate().translationX(0f).setDuration(360).setInterpolator(ease).start()
        } else {
            swap(builder(), forward = true)
        }
    }

    /** Redraw the top level in place (after a toggle), keeping the focused row. */
    fun refresh() {
        val b = stack.lastOrNull() ?: return
        render(b(), focusIndex = focusedRow())
    }

    /** Back: one level up; the sheet goes when the last one is popped. */
    fun back(): Boolean {
        if (!isOpen) return false
        stack.removeAt(stack.size - 1)
        if (stack.isEmpty()) { close(); return true }
        swap(stack.last()(), forward = false)
        return true
    }

    /** Collapse the whole sheet, whatever is stacked. */
    fun close() {
        if (visibility != VISIBLE) return
        stack.clear()
        current = null
        hideKeyboard()
        val g = ++gen
        scrim.animate().cancel()
        scrim.animate().alpha(0f).setDuration(220).start()
        sheet.animate().cancel()
        sheet.animate().translationX(sheet.width.toFloat()).setDuration(260).setInterpolator(ease)
            .withEndAction { if (g == gen) { visibility = GONE; host.removeAllViews(); rows = null } }
            .start()
        restoreFocus?.takeIf { it.isAttachedToWindow }?.requestFocus()
        restoreFocus = null
    }

    /** Level change: the content slips out one way and the new one in from the other. */
    private fun swap(c: Content, forward: Boolean) {
        val g = ++gen
        val dx = Cards.dp(this, 16f)
        hideKeyboard()
        content.animate().cancel()
        content.animate().alpha(0f).translationX(if (forward) -dx else dx).setDuration(120).withEndAction {
            if (g != gen) return@withEndAction
            render(c, focusIndex = -1)
            content.translationX = if (forward) dx * 1.5f else -dx * 1.5f
            content.animate().alpha(1f).translationX(0f).setDuration(240).setInterpolator(ease).start()
        }.start()
    }

    /**
     * Build the level's rows next to the old ones, hand focus straight across, then drop the old
     * ones: removing a focused row first would send focus to the window's first view (a tab).
     */
    private fun render(c: Content, focusIndex: Int) {
        current = c
        title.text = c.title
        message.text = c.message ?: ""
        message.visibility = if (c.message.isNullOrBlank()) GONE else VISIBLE
        val old = rows
        val fresh = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        c.input?.let { input ->
            (input.parent as? ViewGroup)?.removeView(input)
            input.setBackgroundResource(R.drawable.panel_input_bg)
            input.setTextColor(resources.getColor(R.color.text_primary, null))
            input.setHintTextColor(resources.getColor(R.color.text_muted, null))
            val pad = Cards.dp(this, 14f).toInt()
            input.setPadding(pad, pad, pad, pad)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = Cards.dp(this, 10f).toInt()
            fresh.addView(input, lp)
        }
        val inflater = LayoutInflater.from(context)
        val anyChecked = c.rows.any { it.checked != null }
        for (r in c.rows) {
            val v = inflater.inflate(R.layout.item_panel_row, fresh, false)
            val label = v.findViewById<TextView>(R.id.row_label)
            val value = v.findViewById<TextView>(R.id.row_value)
            val check = v.findViewById<TextView>(R.id.row_check)
            label.text = r.label
            value.text = r.value ?: ""
            value.visibility = if (r.value.isNullOrEmpty()) GONE else VISIBLE
            check.visibility = if (!anyChecked) GONE else if (r.checked == true) VISIBLE else INVISIBLE
            if (accent != 0) check.setTextColor(accent)
            v.setOnFocusChangeListener { row, has -> paintRow(row, has) }
            v.setOnClickListener { r.onSelect?.invoke() }
            fresh.addView(v)
        }
        host.addView(fresh, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rows = fresh
        // Land on the asked-for row, else the ticked one, else the first; a text field takes focus itself.
        val n = c.rows.size
        val target = when {
            c.input != null && focusIndex < 0 -> null
            focusIndex in 0 until n -> rowAt(focusIndex)
            else -> c.rows.indexOfFirst { it.checked == true }.takeIf { it >= 0 }?.let { rowAt(it) } ?: rowAt(0)
        }
        if (target != null) target.requestFocus() else c.input?.requestFocus()
        if (old != null) host.removeView(old)
        scroll.scrollTo(0, 0)
    }

    /** The focused row goes light with dark text; the tick keeps the accent. */
    private fun paintRow(row: View, focused: Boolean) {
        val label = row.findViewById<TextView>(R.id.row_label)
        val value = row.findViewById<TextView>(R.id.row_value)
        label.setTextColor(resources.getColor(if (focused) R.color.text_inverse else R.color.text_primary, null))
        value.setTextColor(resources.getColor(if (focused) R.color.text_inverse else R.color.text_secondary, null))
        value.alpha = if (focused) 0.7f else 1f
    }

    private fun rowAt(i: Int): View? {
        val r = rows ?: return null
        val offset = if (current?.input != null) 1 else 0
        return r.getChildAt(i + offset)
    }

    private fun focusedRow(): Int {
        val r = rows ?: return -1
        val f = findFocus() ?: return -1
        val offset = if (current?.input != null) 1 else 0
        for (i in offset until r.childCount) if (r.getChildAt(i) === f || isInside(f, r.getChildAt(i))) return i - offset
        return -1
    }

    private fun hideKeyboard() {
        val f = findFocus() as? EditText ?: return
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(f.windowToken, 0)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        current?.onKey?.let { if (it(event)) return true }
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) back()
            return true
        }
        if (code == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN && findFocus() !is EditText) {
            close()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** D-pad never leaves the sheet: an edge press stays on the current row. */
    override fun focusSearch(focused: View?, direction: Int): View? {
        val next = super.focusSearch(focused, direction)
        return if (next != null && isInside(next, this)) next else focused
    }

    private fun isInside(v: View, root: View): Boolean {
        if (v === root) return true
        var p: ViewParent? = v.parent
        while (p != null) { if (p === root) return true; p = p.parent }
        return false
    }
}
