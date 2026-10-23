package com.cleanyourprojector.launcher.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cleanyourprojector.launcher.R
import com.cleanyourprojector.launcher.apps.AppInfo
import com.cleanyourprojector.launcher.graphics.Look

class AppAdapter(
    private var items: List<AppInfo>,
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo) -> Unit,
    private val onFocus: (AppInfo) -> Unit,
    private val layout: Int = R.layout.item_app
) : RecyclerView.Adapter<AppAdapter.VH>() {

    /** Set before the first submit so the cards slide in once; cleared after the first layout. */
    var entrance = false
    private val overshoot = OvershootInterpolator(1.6f)
    private val settle = PathInterpolator(0.2f, 0f, 0f, 1f)

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: View = view.findViewById(R.id.card)
        val banner: ImageView = view.findViewById(R.id.banner)
        val fallback: View = view.findViewById(R.id.fallback)
        val icon: ImageView = view.findViewById(R.id.icon)
        val label: TextView = view.findViewById(R.id.label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        val vh = VH(v)
        vh.card.clipToOutline = true
        return vh
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position]
        val card = holder.card
        card.setBackgroundResource(Look.cardBg)
        if (app.banner != null) {
            holder.banner.setImageDrawable(app.banner)
            holder.banner.visibility = View.VISIBLE
            holder.fallback.visibility = View.GONE
        } else {
            holder.banner.setImageDrawable(null)
            holder.banner.visibility = View.GONE
            holder.fallback.visibility = View.VISIBLE
            holder.fallback.background = fallbackArt(card, app.color)
            holder.icon.setImageDrawable(app.icon)
            holder.label.text = app.label
        }
        card.contentDescription = app.label

        card.setOnClickListener { onClick(app) }
        card.setOnLongClickListener { onLongClick(app); true }
        card.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.12f else 1.0f
            v.animate().scaleX(scale).scaleY(scale)
                .setDuration(if (hasFocus) 320 else 200)
                .setInterpolator(if (hasFocus) overshoot else settle)
                .start()
            if (hasFocus) onFocus(app)
        }

        val slot = holder.itemView
        slot.animate().cancel()
        if (entrance && position < 7) {
            slot.alpha = 0f
            slot.translationX = dp(slot, 56f)
            slot.animate().alpha(1f).translationX(0f)
                .setStartDelay(60L + position * 55L).setDuration(420)
                .setInterpolator(settle).start()
        } else {
            slot.alpha = 1f
            slot.translationX = 0f
        }
    }

    /** Cached holders come back without a bind; the card style follows the theme on every attach. */
    override fun onViewAttachedToWindow(holder: VH) { holder.card.setBackgroundResource(Look.cardBg) }

    override fun onViewRecycled(holder: VH) {
        holder.card.animate().cancel()
        holder.itemView.animate().cancel()
        holder.card.scaleX = 1f; holder.card.scaleY = 1f
        holder.itemView.alpha = 1f; holder.itemView.translationX = 0f
    }

    override fun getItemCount(): Int = items.size

    fun item(position: Int): AppInfo? = items.getOrNull(position)

    /** Same-size refreshes rebind in place (no item animator), so a focused card keeps focus. */
    fun submit(newItems: List<AppInfo>) {
        val same = newItems.size == items.size
        items = newItems
        if (same) notifyItemRangeChanged(0, items.size) else notifyDataSetChanged()
    }

    private fun dp(v: View, value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, v.resources.displayMetrics)

    /**
     * Apps without a banner get a flat wash of their own colour, dark enough for the label.
     * One colour, no gradient: a gradient is a shader per card on this GPU.
     */
    private fun fallbackArt(v: View, color: Int): GradientDrawable {
        val hsv = FloatArray(3); Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * 0.5f).coerceAtLeast(0.16f); hsv[1] = (hsv[1] * 0.85f)
        return GradientDrawable().apply { setColor(Color.HSVToColor(hsv)); cornerRadius = dp(v, 14f) }
    }
}
