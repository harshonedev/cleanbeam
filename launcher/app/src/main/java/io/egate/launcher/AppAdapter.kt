package io.egate.launcher

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private var items: List<AppInfo>,
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo) -> Unit,
    private val onFocus: (AppInfo) -> Unit
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
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        val vh = VH(v)
        vh.card.clipToOutline = true
        vh.card.foreground = focusRing(vh.card)
        return vh
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position]
        val card = holder.card
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
        card.outlineSpotShadowColor = app.color
        card.outlineAmbientShadowColor = app.color
        card.contentDescription = app.label

        card.setOnClickListener { onClick(app) }
        card.setOnLongClickListener { onLongClick(app); true }
        card.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.12f else 1.0f
            v.animate().scaleX(scale).scaleY(scale)
                .setDuration(if (hasFocus) 320 else 200)
                .setInterpolator(if (hasFocus) overshoot else settle)
                .start()
            v.elevation = if (hasFocus) dp(v, 14f) else dp(v, 2f)
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

    override fun onViewRecycled(holder: VH) {
        holder.card.animate().cancel()
        holder.itemView.animate().cancel()
        holder.card.scaleX = 1f; holder.card.scaleY = 1f
        holder.itemView.alpha = 1f; holder.itemView.translationX = 0f
    }

    override fun getItemCount(): Int = items.size

    fun item(position: Int): AppInfo? = items.getOrNull(position)

    fun submit(newItems: List<AppInfo>) { items = newItems; notifyDataSetChanged() }

    private fun dp(v: View, value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, v.resources.displayMetrics)

    private fun focusRing(v: View): StateListDrawable {
        val radius = dp(v, 14f)
        val focused = GradientDrawable().apply {
            cornerRadius = radius
            setColor(Color.TRANSPARENT)
            setStroke(dp(v, 3f).toInt(), Color.WHITE)
        }
        val normal = GradientDrawable().apply {
            cornerRadius = radius
            setColor(Color.TRANSPARENT)
            setStroke(dp(v, 1f).toInt(), 0x22FFFFFF)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }

    /** Apps without a banner get a diagonal wash of their own color, dark enough for the label. */
    private fun fallbackArt(v: View, color: Int): GradientDrawable {
        val hsv = FloatArray(3); Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * 0.55f).coerceAtLeast(0.18f); hsv[1] = (hsv[1] * 0.9f)
        val deep = Color.HSVToColor(hsv)
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(deep, 0xFF131727.toInt())
        ).apply { cornerRadius = dp(v, 14f) }
    }
}
