package io.egate.launcher

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private var items: List<AppInfo>,
    private var accent: Int,
    private var appGlow: Boolean,
    private var namesFocusedOnly: Boolean,
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: View = view.findViewById(R.id.card)
        val icon: ImageView = view.findViewById(R.id.icon)
        val label: TextView = view.findViewById(R.id.label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position]
        val glow = if (appGlow) app.color else accent
        holder.icon.setImageDrawable(app.icon)
        holder.label.text = app.label
        holder.card.background = buildBackground(holder.card, glow)
        holder.label.alpha = if (namesFocusedOnly) 0f else 1f
        holder.card.setOnClickListener { onClick(app) }
        holder.card.setOnLongClickListener { onLongClick(app); true }
        holder.card.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.14f else 1.0f
            v.animate().scaleX(scale).scaleY(scale).setDuration(130).start()
            v.elevation = if (hasFocus) dp(v, 10f) else 0f
            holder.label.isSelected = hasFocus
            if (namesFocusedOnly) holder.label.animate().alpha(if (hasFocus) 1f else 0f).setDuration(130).start()
        }
    }

    override fun getItemCount(): Int = items.size

    fun submit(newItems: List<AppInfo>) { items = newItems; notifyDataSetChanged() }

    fun setStyle(accent: Int, appGlow: Boolean, namesFocusedOnly: Boolean) {
        this.accent = accent; this.appGlow = appGlow; this.namesFocusedOnly = namesFocusedOnly
        notifyDataSetChanged()
    }

    private fun dp(v: View, value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, v.resources.displayMetrics)

    private fun buildBackground(v: View, glow: Int): StateListDrawable {
        val radius = dp(v, 16f)
        val normal = GradientDrawable().apply { cornerRadius = radius; setColor(Color.parseColor("#1E2438")) }
        val focused = GradientDrawable().apply {
            cornerRadius = radius
            setColor(Color.argb(0x3A, Color.red(glow), Color.green(glow), Color.blue(glow)))
            setStroke(dp(v, 2.5f).toInt(), glow)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }
}
