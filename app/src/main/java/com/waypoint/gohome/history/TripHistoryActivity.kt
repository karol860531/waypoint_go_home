package com.waypoint.gohome.history

import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.waypoint.gohome.R
import com.waypoint.gohome.data.GeoUtils
import com.waypoint.gohome.data.Trip
import com.waypoint.gohome.data.TripRepository
import com.waypoint.gohome.databinding.ActivityTripHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class TripHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripHistoryBinding
    private lateinit var repository: TripRepository
    private lateinit var adapter: TripAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repository = TripRepository.getInstance(this)
        adapter = TripAdapter(onOpen = ::openTrip, onDelete = ::confirmDeleteTrip)
        binding.tripList.layoutManager = LinearLayoutManager(this)
        binding.tripList.adapter = adapter

        loadTrips()
    }

    private fun loadTrips() {
        lifecycleScope.launch {
            val trips = repository.getAllTrips()
            val items = trips.map { trip -> trip to summarize(trip) }
                .map { (trip, summary) -> toListItem(trip, summary) }
            adapter.submitList(items)
            binding.emptyLabel.visibility = if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private suspend fun summarize(trip: Trip): Pair<Int, Float> {
        val waypointCount = repository.getWaypointsForTrip(trip.id).size
        val track = repository.getTrackPointsForTrip(trip.id)
        return waypointCount to GeoUtils.totalDistanceMeters(track)
    }

    private fun toListItem(trip: Trip, summary: Pair<Int, Float>): TripListItem {
        val dateText = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("pl", "PL")).format(Date(trip.createdAt))
        val distanceKm = summary.second / 1000f
        val summaryText = getString(R.string.trip_item_points, summary.first, String.format(Locale.US, "%.1f km", distanceKm))
        return TripListItem(trip.id, dateText, summaryText, isCurrent = trip.id == repository.currentTripId.value)
    }

    private fun openTrip(item: TripListItem) {
        lifecycleScope.launch {
            repository.loadTrip(item.id)
            finish()
        }
    }

    private fun confirmDeleteTrip(item: TripListItem) {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.confirm_delete_trip)
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                lifecycleScope.launch {
                    repository.deleteTrip(item.id)
                    loadTrips()
                }
            }
            .setNegativeButton(R.string.menu_cancel, null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
