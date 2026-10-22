package io.egate.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TitleAdapter(
    private val row: HomeRow,
    private val onClick: (Title, HomeRow) -> Unit,
    private val onLongClick: (Title, HomeRow) -> Unit,
    private val onFocus: (Title, HomeRow) -> Unit
) : RecyclerView.Adapter<TitleAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: View = view.findViewById(R.id.card)
        val art: ImageView = view.findViewById(R.id.art)
        val name: TextView = view.findViewById(R.id.name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_title, parent, false)
        return VH(v).also { Cards.prepare(it.card) }
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = row.titles[position]
        holder.name.text = t.name
        holder.card.contentDescription = t.name
        ImageLoader.load(holder.art.context, Tmdb.imageUrl(t.backdrop, "w500"), holder.art, 520)
        Cards.bindFocus(holder.card, 0xFF5B8CFF.toInt()) { onFocus(t, row) }
        holder.card.setOnClickListener { onClick(t, row) }
        holder.card.setOnLongClickListener { onLongClick(t, row); true }
    }

    override fun onViewRecycled(holder: VH) { Cards.reset(holder.card) }

    override fun getItemCount() = row.titles.size
}
