package io.egate.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TitleAdapter(
    row: HomeRow,
    private val onClick: (Title, HomeRow) -> Unit,
    private val onLongClick: (Title, HomeRow) -> Unit,
    private val onFocus: (Title, HomeRow) -> Unit
) : RecyclerView.Adapter<TitleAdapter.VH>() {

    var row: HomeRow = row; private set

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: View = view.findViewById(R.id.card)
        val art: ImageView = view.findViewById(R.id.art)
        val name: TextView = view.findViewById(R.id.name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_title, parent, false)
        return VH(v).also {
            Cards.prepare(it.card)
            val scrim = v.findViewById<View>(R.id.card_scrim)
            scrim.background = Layers.cardScrim(v.resources, Cards.dp(v, 64f).toInt())
        }
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = row.titles[position]
        val row = row
        holder.name.text = t.name
        holder.card.contentDescription = t.name
        // w300 is the smallest TMDB backdrop that still covers a focused card (297px wide at 1.12x).
        ImageLoader.load(holder.art.context, Tmdb.imageUrl(t.backdrop, "w300"), holder.art, 320)
        Cards.bindFocus(holder.card) { onFocus(t, row) }
        holder.card.setOnClickListener { onClick(t, row) }
        holder.card.setOnLongClickListener { onLongClick(t, row); true }
    }

    override fun onViewRecycled(holder: VH) { Cards.reset(holder.card) }

    override fun getItemCount() = row.titles.size

    /** Swap the shelf's titles in place; the shelf keeps its views and recycled pool. */
    fun submit(newRow: HomeRow) {
        if (newRow === row) return
        row = newRow
        notifyDataSetChanged()
    }
}
