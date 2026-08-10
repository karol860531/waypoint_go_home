package com.waypoint.gohome.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Persists waypoints/track points in a local SQLite database and mirrors them in [StateFlow]s
 * so the UI can observe changes reactively without a separate query per update.
 */
class TripRepository private constructor(context: Context) {
    private val dbHelper = DbHelper(context.applicationContext)
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _waypoints = MutableStateFlow<List<Waypoint>>(emptyList())
    val waypoints: StateFlow<List<Waypoint>> = _waypoints.asStateFlow()

    private val _trackPoints = MutableStateFlow<List<TrackPoint>>(emptyList())
    val trackPoints: StateFlow<List<TrackPoint>> = _trackPoints.asStateFlow()

    init {
        repoScope.launch {
            _waypoints.value = dbHelper.queryWaypoints()
            _trackPoints.value = dbHelper.queryTrackPoints()
        }
    }

    suspend fun addWaypoint(lat: Double, lon: Double, isStart: Boolean): Waypoint = withContext(Dispatchers.IO) {
        val nextSequence = (_waypoints.value.maxOfOrNull { it.sequence } ?: -1) + 1
        val timestamp = System.currentTimeMillis()
        val id = dbHelper.insertWaypoint(lat, lon, nextSequence, isStart, timestamp)
        val waypoint = Waypoint(id, lat, lon, nextSequence, isStart, timestamp)
        _waypoints.value = _waypoints.value + waypoint
        waypoint
    }

    suspend fun addTrackPoint(lat: Double, lon: Double) = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val id = dbHelper.insertTrackPoint(lat, lon, timestamp)
        _trackPoints.value = _trackPoints.value + TrackPoint(id, lat, lon, timestamp)
    }

    suspend fun clearTrip() = withContext(Dispatchers.IO) {
        dbHelper.clearAll()
        _waypoints.value = emptyList()
        _trackPoints.value = emptyList()
    }

    companion object {
        @Volatile
        private var INSTANCE: TripRepository? = null

        fun getInstance(context: Context): TripRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: TripRepository(context).also { INSTANCE = it }
            }
    }
}
