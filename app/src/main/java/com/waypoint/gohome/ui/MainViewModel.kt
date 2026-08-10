package com.waypoint.gohome.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.waypoint.gohome.data.TrackPoint
import com.waypoint.gohome.data.TripRepository
import com.waypoint.gohome.data.Waypoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TripRepository.getInstance(application)

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

    fun markPosition(lat: Double, lon: Double) = viewModelScope.launch {
        if (waypoints.value.isEmpty()) {
            repository.addWaypoint(lat, lon, isStart = true)
        }
    }

    fun addWaypoint(lat: Double, lon: Double) = viewModelScope.launch {
        val isFirstPoint = waypoints.value.isEmpty()
        repository.addWaypoint(lat, lon, isStart = isFirstPoint)
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

    /** Call with the current fix; advances to the next leg once within [thresholdMeters] of the target. */
    fun checkArrival(currentLat: Double, currentLon: Double, thresholdMeters: Float = 20f): Boolean {
        val route = _returnRoute.value
        val index = _returnIndex.value
        if (index >= route.size) return false
        val target = route[index]
        val results = FloatArray(1)
        android.location.Location.distanceBetween(currentLat, currentLon, target.latitude, target.longitude, results)
        if (results[0] <= thresholdMeters) {
            val nextIndex = index + 1
            _returnIndex.value = nextIndex
            if (nextIndex >= route.size) {
                _returnMode.value = false
                return true
            }
        }
        return false
    }

    fun newTrip() = viewModelScope.launch {
        cancelReturn()
        repository.clearTrip()
    }
}
