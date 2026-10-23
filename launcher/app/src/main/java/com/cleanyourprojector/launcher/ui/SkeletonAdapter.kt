package com.cleanyourprojector.launcher.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cleanyourprojector.launcher.R
import com.cleanyourprojector.launcher.graphics.Look

/** Placeholder cards for a shelf whose titles are still loading: card-shaped, unfocusable, pulsing. */
class SkeletonAdapter(private val count: Int = 6) : RecyclerView.Adapter<SkeletonAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: View = view.findViewById(R.id.card)
        var pulse: ObjectAnimator? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_skeleton, parent, false)
        return VH(v).also {
            it.card.clipToOutline = true
            // A single flat drawable: fading it needs no offscreen layer, so a dozen pulsing
            // cards cost the GPU a dozen quads instead of a dozen render targets per frame.
            it.card.forceHasOverlappingRendering(false)
        }
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.card.setBackgroundResource(Look.cardBg)
        holder.pulse?.cancel()
        holder.pulse = ObjectAnimator.ofFloat(holder.card, View.ALPHA, 0.35f, 0.8f).apply {
            duration = 900
            startDelay = position * 90L   // a soft wave across the shelf instead of one blink
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    override fun onViewRecycled(holder: VH) {
        holder.pulse?.cancel(); holder.pulse = null
        holder.card.alpha = 1f
    }

    override fun onViewDetachedFromWindow(holder: VH) { holder.pulse?.pause() }
    override fun onViewAttachedToWindow(holder: VH) { holder.card.setBackgroundResource(Look.cardBg); holder.pulse?.resume() }

    override fun getItemCount() = count
}
