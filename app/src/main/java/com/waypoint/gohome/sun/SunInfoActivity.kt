package com.waypoint.gohome.sun

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.waypoint.gohome.R
import com.waypoint.gohome.databinding.ActivitySunInfoBinding
import com.waypoint.gohome.location.CompassSensor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class SunInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySunInfoBinding
    private lateinit var compass: CompassSensor
    private lateinit var fusedClient: FusedLocationProviderClient

    /** Seeded from the map's last known fix; kept fresh by our own location updates while open. */
    private var lat = 0.0
    private var lon = 0.0

    private val timeFormat = SimpleDateFormat("HH:mm", Locale("pl", "PL"))

    private val tickHandler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            refresh()
            tickHandler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            lat = location.latitude
            lon = location.longitude
            refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySunInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
        lon = intent.getDoubleExtra(EXTRA_LON, Double.NaN)
        if (lat.isNaN() || lon.isNaN()) {
            finish()
            return
        }

        compass = CompassSensor(this) { heading ->
            binding.sunCompass.deviceHeading = heading
        }
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
    }

    private fun refresh() {
        val now = System.currentTimeMillis()
        val times = SunCalculator.getTimes(now, lat, lon)
        val position = SunCalculator.getPosition(now, lat, lon)

        binding.sunriseTime.text = timeFormat.format(Date(times.sunrise))
        binding.sunsetTime.text = timeFormat.format(Date(times.sunset))
        binding.countdownText.text = countdownMessage(now, times)

        binding.sunAltitudeText.text = getString(R.string.label_sun_altitude, position.altitudeDeg)
        binding.sunDirectionText.text = getString(
            R.string.label_sun_direction,
            position.azimuthDeg,
            SunCalculator.bearingToCardinal(position.azimuthDeg)
        )

        val sunriseAz = SunCalculator.getPosition(times.sunrise, lat, lon).azimuthDeg
        val sunsetAz = SunCalculator.getPosition(times.sunset, lat, lon).azimuthDeg
        binding.sunCompass.update(sunriseAz, sunsetAz, position.azimuthDeg, position.altitudeDeg)
    }

    private fun countdownMessage(now: Long, times: SunCalculator.SunTimes): String {
        return when {
            now < times.dawn -> getString(R.string.countdown_to_dawn, formatDuration(times.dawn - now))
            now < times.sunrise -> getString(R.string.countdown_to_sunrise, formatDuration(times.sunrise - now))
            now < times.dusk -> getString(R.string.countdown_to_dusk, formatDuration(times.dusk - now))
            else -> {
                val tomorrow = SunCalculator.getTimes(now + TimeUnit.DAYS.toMillis(1), lat, lon)
                getString(R.string.night_next_sunrise, timeFormat.format(Date(tomorrow.sunrise)))
            }
        }
    }

    private fun formatDuration(millis: Long): String {
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis).coerceAtLeast(0)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) getString(R.string.duration_hm, hours, minutes) else getString(R.string.duration_m, minutes)
    }

    private fun startLiveLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000L)
            .setMinUpdateDistanceMeters(20f)
            .build()
        try {
            fusedClient.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (_: SecurityException) {
            // No permission after all — the screen still works with the last known fix.
        }
    }

    override fun onResume() {
        super.onResume()
        compass.start()
        tickHandler.post(tickRunnable)
        startLiveLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        compass.stop()
        tickHandler.removeCallbacks(tickRunnable)
        fusedClient.removeLocationUpdates(locationCallback)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LON = "extra_lon"
        private const val TICK_INTERVAL_MS = 30_000L
    }
}
