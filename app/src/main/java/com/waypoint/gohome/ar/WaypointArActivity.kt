package com.waypoint.gohome.ar

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.waypoint.gohome.R
import com.waypoint.gohome.databinding.ActivityWaypointArBinding
import com.waypoint.gohome.location.CompassSensor
import kotlin.math.roundToInt

/**
 * Live camera preview with the target waypoint's direction overlaid as a marker, positioned
 * according to the compass bearing to the target relative to where the phone is currently
 * pointing — a lightweight "AR compass" that doesn't require ARCore/device AR support.
 */
class WaypointArActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWaypointArBinding
    private lateinit var compass: CompassSensor
    private lateinit var fusedClient: FusedLocationProviderClient

    private var targetLat = 0.0
    private var targetLon = 0.0
    private var lastLocation: Location? = null
    private var lastHeading = 0f

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            lastLocation = location
            updateOverlay()
        }
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else showPermissionMessage()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWaypointArBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        targetLat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
        targetLon = intent.getDoubleExtra(EXTRA_LON, Double.NaN)
        if (targetLat.isNaN() || targetLon.isNaN()) {
            finish()
            return
        }
        binding.toolbar.title = intent.getStringExtra(EXTRA_LABEL) ?: getString(R.string.title_waypoint_ar)

        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        compass = CompassSensor(this) { heading ->
            lastHeading = heading
            updateOverlay()
        }

        if (hasCameraPermission()) startCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun showPermissionMessage() {
        binding.permissionMessage.visibility = android.view.View.VISIBLE
        binding.permissionMessage.text = getString(R.string.msg_camera_permission_required)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener(
            {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                } catch (_: Exception) {
                    showPermissionMessage()
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun startLocationUpdates() {
        if (!hasFineLocationPermission()) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateDistanceMeters(1f)
            .build()
        try {
            fusedClient.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (_: SecurityException) {
            // No location permission — the AR view just won't have a fix to show.
        }
    }

    private fun hasFineLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun updateOverlay() {
        val location = lastLocation ?: return
        val target = Location("target").apply {
            latitude = targetLat
            longitude = targetLon
        }
        val bearingToTarget = location.bearingTo(target)
        val relative = (((bearingToTarget - lastHeading) + 540f) % 360f) - 180f

        val distanceMeters = location.distanceTo(target)
        binding.arOverlay.distanceText = if (distanceMeters >= 1000) {
            getString(R.string.label_distance_km, distanceMeters / 1000f)
        } else {
            getString(R.string.label_distance_m, distanceMeters.roundToInt())
        }
        binding.arOverlay.relativeBearingDeg = relative
        binding.arOverlay.hasFix = true
    }

    override fun onResume() {
        super.onResume()
        compass.start()
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        compass.stop()
        fusedClient.removeLocationUpdates(locationCallback)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LON = "extra_lon"
        const val EXTRA_LABEL = "extra_label"
    }
}
