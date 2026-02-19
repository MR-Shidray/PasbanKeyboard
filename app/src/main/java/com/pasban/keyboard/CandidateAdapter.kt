package com.pasban.keyboard

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CandidateAdapter(
    private val onPick: (String) -> Unit
) : RecyclerView.Adapter<CandidateAdapter.VH>() {

    private val items = mutableListOf<String>()

    fun submit(newItems: List<String>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.candidate_chip, parent, false)
        return VH(v as TextView)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.t.text = s
        holder.t.setOnClickListener { onPick(s) }
    }

    class VH(val t: TextView) : RecyclerView.ViewHolder(t)
}
