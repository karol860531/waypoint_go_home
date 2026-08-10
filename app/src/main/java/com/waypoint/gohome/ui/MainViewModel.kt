package com.waypoint.gohome.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.waypoint.gohome.data.TrackPoint
import com.waypoint.gohome.data.Trip
import com.waypoint.gohome.data.TripRepository
import com.waypoint.gohome.data.Waypoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TripRepository.getInstance(application)

    val currentTripId: StateFlow<Long> = repository.currentTripId
    val waypoints: StateFlow<List<Waypoint>> = repository.waypoints
    val trackPoints: StateFlow<List<TrackPoint>> = repository.trackPoints

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _returnRoute = MutableStateFlow<List<Waypoint>>(emptyList())
    val returnRoute: StateFlow<List<Waypoint>> = _returnRoute

    private val _returnIndex = MutableStateFlow(0)
    val returnIndex: StateFlow<Int> = _returnIndex

    private val _returnMode = MutableStateFlow(false)
    val returnMode: StateFlow<Boolean> = _returnMode

    fun setRecording(recording: Boolean) {
        _isRecording.value = recording
    }

    fun markPosition(lat: Double, lon: Double, altitude: Double?) = viewModelScope.launch {
        if (waypoints.value.isEmpty()) {
            repository.addWaypoint(lat, lon, altitude)
        }
    }

    fun addWaypoint(lat: Double, lon: Double, altitude: Double?) = viewModelScope.launch {
        repository.addWaypoint(lat, lon, altitude)
    }

    fun deleteWaypoint(waypointId: Long) = viewModelScope.launch {
        repository.deleteWaypoint(waypointId)
    }

    /** Builds the reversed route back to [targetWaypointId], or the trip's start point if null. */
    fun enterReturnMode(targetWaypointId: Long?) {
        val all = waypoints.value.sortedBy { it.sequence }
        if (all.isEmpty()) return
        val target = all.firstOrNull { it.id == targetWaypointId }
            ?: all.firstOrNull { it.isStart }
            ?: all.first()
        val route = all.filter { it.sequence >= target.sequence }
            .sortedByDescending { it.sequence }
        _returnRoute.value = route
        _returnIndex.value = 0
        _returnMode.value = true
    }

    fun cancelReturn() {
        _returnMode.value = false
        _returnRoute.value = emptyList()
        _returnIndex.value = 0
    }

    enum class ArrivalStatus { NONE, ADVANCED, FINISHED }

    /** Call with the current fix; advances to the next leg once within [thresholdMeters] of the target. */
    fun checkArrival(currentLat: Double, currentLon: Double, thresholdMeters: Float = 20f): ArrivalStatus {
        val route = _returnRoute.value
        val index = _returnIndex.value
        if (index >= route.size) return ArrivalStatus.NONE
        val target = route[index]
        val results = FloatArray(1)
        android.location.Location.distanceBetween(currentLat, currentLon, target.latitude, target.longitude, results)
        if (results[0] <= thresholdMeters) {
            val nextIndex = index + 1
            _returnIndex.value = nextIndex
            if (nextIndex >= route.size) {
                _returnMode.value = false
                return ArrivalStatus.FINISHED
            }
            return ArrivalStatus.ADVANCED
        }
        return ArrivalStatus.NONE
    }

    fun newTrip() = viewModelScope.launch {
        cancelReturn()
        repository.startNewTrip()
    }

    suspend fun getAllTrips(): List<Trip> = repository.getAllTrips()

    fun loadTrip(tripId: Long) = viewModelScope.launch {
        cancelReturn()
        repository.loadTrip(tripId)
    }

    fun deleteTrip(tripId: Long) = viewModelScope.launch {
        repository.deleteTrip(tripId)
    }

    suspend fun getCurrentTripSnapshot(): Triple<Trip, List<Waypoint>, List<TrackPoint>>? {
        val trip = repository.getTrip(currentTripId.value) ?: return null
        return Triple(trip, waypoints.value, trackPoints.value)
    }

    fun importGpx(
        waypoints: List<Triple<Double, Double, Double?>>,
        track: List<Triple<Double, Double, Double?>>
    ) = viewModelScope.launch {
        repository.importTrip("Import ${repository.defaultTripName()}", waypoints, track)
    }
}
