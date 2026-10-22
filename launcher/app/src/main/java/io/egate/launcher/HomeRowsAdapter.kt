package io.egate.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

/** Vertical list of shelves; each shelf is a header plus a horizontal FocusRowRecyclerView. */
class HomeRowsAdapter(
    private val onClick: (Title, HomeRow) -> Unit,
    private val onLongClick: (Title, HomeRow) -> Unit,
    private val onFocus: (Title, HomeRow) -> Unit,
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo) -> Unit,
    private val onAppFocus: (AppInfo) -> Unit
) : RecyclerView.Adapter<HomeRowsAdapter.VH>() {

    private var rows: List<HomeRow> = emptyList()
    /** D-pad up from the first shelf lands here (the Home tab). */
    var upTarget: View? = null
    /** Title rows share recycled cards; app rows use a different item layout so they keep their own. */
    private val titlePool = RecyclerView.RecycledViewPool()
    private val skeletonPool = RecyclerView.RecycledViewPool()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val header: TextView = view.findViewById(R.id.row_header)
        val row: FocusRowRecyclerView = view.findViewById(R.id.row_titles)
    }

    override fun getItemViewType(position: Int): Int {
        val r = rows[position]
        return when {
            r.loading -> TYPE_SKELETON
            r.isAppRow -> TYPE_APPS
            else -> TYPE_TITLES
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_home_row, parent, false)
        val vh = VH(v)
        when (viewType) {
            TYPE_TITLES -> vh.row.setRecycledViewPool(titlePool)
            TYPE_SKELETON -> vh.row.setRecycledViewPool(skeletonPool)
        }
        vh.row.itemAnimator = null
        return vh
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = rows[position]
        holder.header.text = r.header
        holder.row.upTarget = if (position == 0) upTarget else null
        holder.row.adapter = when {
            r.loading -> SkeletonAdapter()
            r.isAppRow -> AppAdapter(r.apps, onAppClick, onAppLongClick, onAppFocus)
            else -> TitleAdapter(r, onClick, onLongClick, onFocus)
        }
    }

    override fun getItemCount() = rows.size

    /**
     * Shelves are matched by header, so a skeleton turning into its titles rebinds only that row
     * and cards already on screen (and focus) survive. A row that loaded empty is dropped.
     */
    fun submit(newRows: List<HomeRow>) {
        val old = rows
        rows = newRows
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = newRows.size
            override fun areItemsTheSame(o: Int, n: Int) = old[o].header == newRows[n].header
            override fun areContentsTheSame(o: Int, n: Int) = old[o] == newRows[n]
        }, false).dispatchUpdatesTo(this)
    }
    fun row(position: Int): HomeRow? = rows.getOrNull(position)

    companion object { const val TYPE_TITLES = 0; const val TYPE_APPS = 1; const val TYPE_SKELETON = 2 }
}
