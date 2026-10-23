package com.cleanyourprojector.launcher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.cleanyourprojector.launcher.R
import com.cleanyourprojector.launcher.apps.AppInfo
import com.cleanyourprojector.launcher.home.LiteRow
import com.cleanyourprojector.launcher.settings.Tile

/**
 * Lite's Home shelves: a header plus a horizontal row of app cards or settings tiles. Focus on a
 * card reports nothing, because the hero slot shows the clock while Home is up.
 */
class LiteHomeAdapter(
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo) -> Unit,
    private val onTileClick: (Tile) -> Unit
) : RecyclerView.Adapter<LiteHomeAdapter.VH>() {

    private var rows: List<LiteRow> = emptyList()
    /** D-pad up from the first shelf lands here (the Home tab). */
    var upTarget: View? = null

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val header: TextView = view.findViewById(R.id.row_header)
        val row: FocusRowRecyclerView = view.findViewById(R.id.row_titles)
        /** Header of the shelf this holder last showed; a different one means a fresh shelf. */
        var boundHeader: String? = null
    }

    override fun getItemViewType(position: Int): Int = if (rows[position].tiles.isNotEmpty() || rows[position].apps.isEmpty()) TYPE_TILES else TYPE_APPS

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_home_row, parent, false)
        val vh = VH(v)
        vh.row.itemAnimator = null
        vh.row.setHasFixedSize(true)
        return vh
    }

    /** A holder keeps its inner adapter and swaps the data into it, so cards on screen survive a refresh. */
    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = rows[position]
        holder.header.text = r.header
        holder.row.upTarget = if (position == 0) upTarget else null
        val fresh = holder.boundHeader != r.header
        holder.boundHeader = r.header
        val current = holder.row.adapter
        if (getItemViewType(position) == TYPE_APPS) {
            if (current is AppAdapter) current.submit(r.apps)
            else holder.row.adapter = AppAdapter(r.apps, onAppClick, onAppLongClick, onFocus = {})
        } else {
            val tiles = current as? TileAdapter ?: TileAdapter(onClick = onTileClick, onFocus = {}).also { holder.row.adapter = it }
            tiles.submit(r.tiles)
        }
        if (fresh) holder.row.scrollToPosition(0)
    }

    override fun getItemCount() = rows.size

    /** Shelves are matched by header, so a changed Favorites shelf rebinds in place and keeps focus. */
    fun submit(newRows: List<LiteRow>) {
        val old = rows
        rows = newRows
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = newRows.size
            override fun areItemsTheSame(o: Int, n: Int) = old[o].header == newRows[n].header
            override fun areContentsTheSame(o: Int, n: Int) = old[o] == newRows[n]
        }, false).dispatchUpdatesTo(this)
    }

    fun row(position: Int): LiteRow? = rows.getOrNull(position)

    companion object {
        const val TYPE_APPS = 0; const val TYPE_TILES = 1
    }
}
