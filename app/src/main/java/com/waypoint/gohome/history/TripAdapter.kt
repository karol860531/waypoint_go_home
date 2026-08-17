package com.waypoint.gohome.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.waypoint.gohome.databinding.ItemTripBinding

data class TripListItem(
    val id: Long,
    val dateText: String,
    val summaryText: String,
    val isCurrent: Boolean
)

class TripAdapter(
    private val onOpen: (TripListItem) -> Unit,
    private val onDelete: (TripListItem) -> Unit
) : RecyclerView.Adapter<TripAdapter.ViewHolder>() {

    private val items = mutableListOf<TripListItem>()

    fun submitList(newItems: List<TripListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTripBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tripName.text = item.dateText
        holder.binding.tripSummary.text = item.summaryText
        holder.binding.currentBadge.visibility = if (item.isCurrent) View.VISIBLE else View.GONE
        holder.binding.root.strokeWidth = if (item.isCurrent) 4 else 0
        holder.binding.root.setOnClickListener { onOpen(item) }
        holder.binding.btnDeleteTrip.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(val binding: ItemTripBinding) : RecyclerView.ViewHolder(binding.root)
}
