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
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: View = view.findViewById(R.id.card)
        val icon: ImageView = view.findViewById(R.id.icon)
        val label: TextView = view.findViewById(R.id.label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position]
        holder.icon.setImageDrawable(app.icon)
        holder.label.text = app.label
        holder.card.background = buildBackground(holder.card)
        holder.card.setOnClickListener { onClick(app) }
        holder.card.setOnLongClickListener { onLongClick(app); true }
        holder.card.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.14f else 1.0f
            v.animate().scaleX(scale).scaleY(scale).setDuration(130).start()
            v.elevation = if (hasFocus) dp(v, 10f) else 0f
            holder.label.isSelected = hasFocus
        }
    }

    override fun getItemCount(): Int = items.size

    fun submit(newItems: List<AppInfo>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun setAccent(color: Int) {
        accent = color
        notifyDataSetChanged()
    }

    private fun dp(v: View, value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, v.resources.displayMetrics)

    private fun buildBackground(v: View): StateListDrawable {
        val radius = dp(v, 16f)
        val normal = GradientDrawable().apply {
            cornerRadius = radius
            setColor(Color.parseColor("#1E2438"))
        }
        val focused = GradientDrawable().apply {
            cornerRadius = radius
            // translucent accent fill + solid accent border
            setColor(withAlpha(accent, 0x33))
            setStroke(dp(v, 2.5f).toInt(), accent)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
