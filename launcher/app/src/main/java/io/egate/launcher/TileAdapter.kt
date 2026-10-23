package io.egate.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TileAdapter(
    private val onClick: (Tile) -> Unit,
    private val onFocus: (Tile) -> Unit
) : RecyclerView.Adapter<TileAdapter.VH>() {

    private var items: List<Tile> = emptyList()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: View = view.findViewById(R.id.card)
        val icon: ImageView = view.findViewById(R.id.tile_icon)
        val title: TextView = view.findViewById(R.id.tile_title)
        val subtitle: TextView = view.findViewById(R.id.tile_subtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_tile, parent, false)
        return VH(v).also { Cards.prepare(it.card) }
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = items[position]
        holder.icon.setImageResource(t.icon)
        holder.title.text = t.title
        holder.subtitle.text = t.subtitle
        holder.card.contentDescription = t.title
        Cards.bindFocus(holder.card) { onFocus(t) }
        holder.card.setOnClickListener { onClick(t) }
    }

    override fun onViewRecycled(holder: VH) { Cards.reset(holder.card) }
    override fun getItemCount() = items.size
    fun item(position: Int): Tile? = items.getOrNull(position)
    /** Same-size refreshes rebind in place (no item animator), so a focused tile keeps focus. */
    fun submit(newItems: List<Tile>) {
        val same = newItems.size == items.size
        items = newItems
        if (same) notifyItemRangeChanged(0, items.size) else notifyDataSetChanged()
    }
}
