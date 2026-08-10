package com.waypoint.gohome.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.waypoint.gohome.R
import com.waypoint.gohome.data.TripRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps requesting location updates and persists each fix as a
 * [com.waypoint.gohome.data.TrackPoint] so the travelled route can be drawn live on the map,
 * even while the app is backgrounded or the screen is locked. When "auto waypoint" is enabled in
 * preferences, it also drops a regular waypoint every N meters of travel.
 */
class LocationTrackingService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var repository: TripRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var lastAutoWaypointLocation: Location? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val altitude = if (location.hasAltitude()) location.altitude else null
            serviceScope.launch {
                repository.addTrackPoint(location.latitude, location.longitude, altitude)
                maybeAddAutoWaypoint(location, altitude)
            }
        }
    }

    private suspend fun maybeAddAutoWaypoint(location: Location, altitude: Double?) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_AUTO_WAYPOINT_ENABLED, false)) {
            lastAutoWaypointLocation = null
            return
        }
        val thresholdMeters = prefs.getFloat(KEY_AUTO_WAYPOINT_DISTANCE, DEFAULT_AUTO_WAYPOINT_DISTANCE)
        val last = lastAutoWaypointLocation
        if (last == null) {
            lastAutoWaypointLocation = location
            return
        }
        if (last.distanceTo(location) >= thresholdMeters) {
            repository.addWaypoint(location.latitude, location.longitude, altitude)
            lastAutoWaypointLocation = location
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        repository = TripRepository.getInstance(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_M)
            .build()
        try {
            fusedClient.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        fusedClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "location_tracking"
        private const val NOTIFICATION_ID = 1001
        private const val UPDATE_INTERVAL_MS = 5000L
        private const val MIN_UPDATE_INTERVAL_MS = 3000L
        private const val MIN_UPDATE_DISTANCE_M = 5f

        const val PREFS_NAME = "waypoint_prefs"
        const val KEY_AUTO_WAYPOINT_ENABLED = "auto_waypoint_enabled"
        const val KEY_AUTO_WAYPOINT_DISTANCE = "auto_waypoint_distance_m"
        const val DEFAULT_AUTO_WAYPOINT_DISTANCE = 500f
    }
}
