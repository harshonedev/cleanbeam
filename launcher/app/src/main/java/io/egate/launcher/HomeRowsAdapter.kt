package io.egate.launcher

import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
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
    private val titlePool = RecyclerView.RecycledViewPool().apply { setMaxRecycledViews(0, POOL_MAX) }
    private val skeletonPool = RecyclerView.RecycledViewPool()
    private var warming = false

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val header: TextView = view.findViewById(R.id.row_header)
        val row: FocusRowRecyclerView = view.findViewById(R.id.row_titles)
        /** Header of the shelf this holder last showed; a different one means a fresh shelf. */
        var boundHeader: String? = null
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
        // Every card is the same size, so a shelf never needs to re-measure the whole screen when its
        // titles change; and let the outer list pre-build the first cards of the shelf scrolling in.
        vh.row.setHasFixedSize(true)
        (vh.row.layoutManager as? LinearLayoutManager)?.initialPrefetchItemCount = 5
        return vh
    }

    /**
     * A holder keeps its inner adapter and swaps the data into it: replacing the adapter would
     * throw away every card view and its loaded art, then rebuild them, on each vertical scroll.
     */
    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = rows[position]
        holder.header.text = r.header
        holder.row.upTarget = if (position == 0) upTarget else null
        val fresh = holder.boundHeader != r.header
        holder.boundHeader = r.header
        val current = holder.row.adapter
        when {
            r.loading -> if (current !is SkeletonAdapter) holder.row.adapter = SkeletonAdapter()
            r.isAppRow -> if (current is AppAdapter) current.submit(r.apps)
                          else holder.row.adapter = AppAdapter(r.apps, onAppClick, onAppLongClick, onAppFocus)
            else -> if (current is TitleAdapter) current.submit(r)
                    else holder.row.adapter = TitleAdapter(r, onClick, onLongClick, onFocus)
        }
        // A recycled holder showing a different shelf starts at that shelf's first card.
        if (fresh) holder.row.scrollToPosition(0)
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

    /**
     * Inflating a title card costs this CPU 7-12 ms, and a shelf scrolling in for the first time
     * needs six of them inside one frame. Build them ahead while the main thread is idle, one per
     * idle slot so input is never held up, until the shared pool holds [WARM_CARDS].
     */
    fun warmUp(host: RecyclerView) {
        if (warming) return
        warming = true
        val proto = TitleAdapter(HomeRow("", emptyList(), null, null), onClick, onLongClick, onFocus)
        Looper.myQueue().addIdleHandler {
            if (!host.isAttachedToWindow) { warming = false; return@addIdleHandler false }
            if (titlePool.getRecycledViewCount(0) >= WARM_CARDS) { warming = false; return@addIdleHandler false }
            titlePool.putRecycledView(proto.createViewHolder(host, 0))
            true
        }
    }

    companion object {
        const val TYPE_TITLES = 0; const val TYPE_APPS = 1; const val TYPE_SKELETON = 2
        private const val POOL_MAX = 40
        private const val WARM_CARDS = 24
    }
}
