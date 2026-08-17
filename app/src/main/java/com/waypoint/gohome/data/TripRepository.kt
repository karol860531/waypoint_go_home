package com.waypoint.gohome.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Persists trips/waypoints/track points in a local SQLite database and mirrors the *current*
 * trip's data in [StateFlow]s so the UI can observe changes reactively. Only one trip is
 * "current" (recording/marking always applies to it); other trips are reachable read/write via
 * the explicit per-trip query methods (used by trip history, GPX export, etc).
 */
class TripRepository private constructor(context: Context) {
    private val dbHelper = DbHelper(context.applicationContext)
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _currentTripId = MutableStateFlow(-1L)
    val currentTripId: StateFlow<Long> = _currentTripId.asStateFlow()

    private val _waypoints = MutableStateFlow<List<Waypoint>>(emptyList())
    val waypoints: StateFlow<List<Waypoint>> = _waypoints.asStateFlow()

    private val _trackPoints = MutableStateFlow<List<TrackPoint>>(emptyList())
    val trackPoints: StateFlow<List<TrackPoint>> = _trackPoints.asStateFlow()

    init {
        repoScope.launch {
            val savedId = prefs.getLong(KEY_CURRENT_TRIP, -1L)
            val tripId = if (savedId != -1L && dbHelper.tripExists(savedId)) savedId else createTripInternal()
            switchToTrip(tripId)
        }
    }

    private fun createTripInternal(): Long {
        val id = dbHelper.insertTrip(defaultTripName(), System.currentTimeMillis())
        prefs.edit().putLong(KEY_CURRENT_TRIP, id).apply()
        return id
    }

    private fun switchToTrip(tripId: Long) {
        _currentTripId.value = tripId
        _waypoints.value = dbHelper.queryWaypoints(tripId)
        _trackPoints.value = dbHelper.queryTrackPoints(tripId)
    }

    fun defaultTripName(): String =
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("pl", "PL")).format(Date())

    /** The very first point of a trip is automatically flagged as its start. */
    suspend fun addWaypoint(lat: Double, lon: Double, altitude: Double?, label: String? = null): Waypoint =
        withContext(Dispatchers.IO) {
            val tripId = _currentTripId.value
            val isStart = _waypoints.value.isEmpty()
            val nextSequence = (_waypoints.value.maxOfOrNull { it.sequence } ?: -1) + 1
            val timestamp = System.currentTimeMillis()
            val id = dbHelper.insertWaypoint(tripId, lat, lon, altitude, nextSequence, isStart, false, label, timestamp)
            val waypoint = Waypoint(id, tripId, lat, lon, altitude, nextSequence, isStart, false, label, null, timestamp)
            _waypoints.value = _waypoints.value + waypoint
            waypoint
        }

    /** Dropped automatically where recording stops — marks the trip's end point. */
    suspend fun addEndWaypoint(lat: Double, lon: Double, altitude: Double?): Waypoint =
        withContext(Dispatchers.IO) {
            val tripId = _currentTripId.value
            val isStart = _waypoints.value.isEmpty()
            val nextSequence = (_waypoints.value.maxOfOrNull { it.sequence } ?: -1) + 1
            val timestamp = System.currentTimeMillis()
            val id = dbHelper.insertWaypoint(tripId, lat, lon, altitude, nextSequence, isStart, true, null, timestamp)
            val waypoint = Waypoint(id, tripId, lat, lon, altitude, nextSequence, isStart, true, null, null, timestamp)
            _waypoints.value = _waypoints.value + waypoint
            waypoint
        }

    suspend fun deleteWaypoint(waypointId: Long) = withContext(Dispatchers.IO) {
        dbHelper.deleteWaypoint(waypointId)
        _waypoints.value = _waypoints.value.filterNot { it.id == waypointId }
    }

    suspend fun setWaypointPhoto(waypointId: Long, photoUri: String?) = withContext(Dispatchers.IO) {
        dbHelper.updateWaypointPhoto(waypointId, photoUri)
        _waypoints.value = _waypoints.value.map { if (it.id == waypointId) it.copy(photoUri = photoUri) else it }
    }

    suspend fun addTrackPoint(lat: Double, lon: Double, altitude: Double?) = withContext(Dispatchers.IO) {
        val tripId = _currentTripId.value
        val timestamp = System.currentTimeMillis()
        val id = dbHelper.insertTrackPoint(tripId, lat, lon, altitude, timestamp)
        _trackPoints.value = _trackPoints.value + TrackPoint(id, tripId, lat, lon, altitude, timestamp)
    }

    suspend fun startNewTrip(name: String? = null): Long = withContext(Dispatchers.IO) {
        val id = dbHelper.insertTrip(name ?: defaultTripName(), System.currentTimeMillis())
        prefs.edit().putLong(KEY_CURRENT_TRIP, id).apply()
        switchToTrip(id)
        id
    }

    suspend fun loadTrip(tripId: Long) = withContext(Dispatchers.IO) {
        prefs.edit().putLong(KEY_CURRENT_TRIP, tripId).apply()
        switchToTrip(tripId)
    }

    suspend fun deleteTrip(tripId: Long) = withContext(Dispatchers.IO) {
        dbHelper.deleteTrip(tripId)
        if (_currentTripId.value == tripId) {
            val fallback = dbHelper.queryTrips().firstOrNull()?.id ?: createTripInternal()
            switchToTrip(fallback)
        }
    }

    suspend fun getAllTrips(): List<Trip> = withContext(Dispatchers.IO) { dbHelper.queryTrips() }

    suspend fun getTrip(tripId: Long): Trip? = withContext(Dispatchers.IO) { dbHelper.getTrip(tripId) }

    suspend fun getWaypointsForTrip(tripId: Long): List<Waypoint> =
        withContext(Dispatchers.IO) { dbHelper.queryWaypoints(tripId) }

    suspend fun getTrackPointsForTrip(tripId: Long): List<TrackPoint> =
        withContext(Dispatchers.IO) { dbHelper.queryTrackPoints(tripId) }

    suspend fun countTrackPoints(tripId: Long): Int = withContext(Dispatchers.IO) { dbHelper.countTrackPoints(tripId) }

    /** Inserts a full track (used by GPX import) as a brand-new trip, switches to it, and returns its id. */
    suspend fun importTrip(name: String, waypoints: List<Triple<Double, Double, Double?>>, track: List<Triple<Double, Double, Double?>>): Long =
        withContext(Dispatchers.IO) {
            val tripId = dbHelper.insertTrip(name, System.currentTimeMillis())
            waypoints.forEachIndexed { index, (lat, lon, alt) ->
                dbHelper.insertWaypoint(tripId, lat, lon, alt, index, index == 0, false, null, System.currentTimeMillis())
            }
            track.forEach { (lat, lon, alt) ->
                dbHelper.insertTrackPoint(tripId, lat, lon, alt, System.currentTimeMillis())
            }
            prefs.edit().putLong(KEY_CURRENT_TRIP, tripId).apply()
            switchToTrip(tripId)
            tripId
        }

    companion object {
        private const val PREFS_NAME = "waypoint_prefs"
        private const val KEY_CURRENT_TRIP = "current_trip_id"

        @Volatile
        private var INSTANCE: TripRepository? = null

        fun getInstance(context: Context): TripRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: TripRepository(context).also { INSTANCE = it }
            }
    }
}
