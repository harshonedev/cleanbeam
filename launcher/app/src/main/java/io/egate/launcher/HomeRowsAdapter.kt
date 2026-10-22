package io.egate.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Vertical list of shelves; each shelf is a header plus a horizontal FocusRowRecyclerView. */
class HomeRowsAdapter(
    private val onClick: (Title, HomeRow) -> Unit,
    private val onLongClick: (Title, HomeRow) -> Unit,
    private val onFocus: (Title, HomeRow) -> Unit
) : RecyclerView.Adapter<HomeRowsAdapter.VH>() {

    private var rows: List<HomeRow> = emptyList()
    /** D-pad up from the first shelf lands here (the Home tab). */
    var upTarget: View? = null
    private val pool = RecyclerView.RecycledViewPool()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val header: TextView = view.findViewById(R.id.row_header)
        val row: FocusRowRecyclerView = view.findViewById(R.id.row_titles)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_home_row, parent, false)
        val vh = VH(v)
        vh.row.setRecycledViewPool(pool)
        vh.row.itemAnimator = null
        return vh
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = rows[position]
        holder.header.text = r.header
        holder.row.upTarget = if (position == 0) upTarget else null
        holder.row.adapter = TitleAdapter(r, onClick, onLongClick, onFocus)
    }

    override fun getItemCount() = rows.size

    fun submit(newRows: List<HomeRow>) { rows = newRows; notifyDataSetChanged() }
    fun row(position: Int): HomeRow? = rows.getOrNull(position)
}
