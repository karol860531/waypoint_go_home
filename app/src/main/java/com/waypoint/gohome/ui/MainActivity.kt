package com.waypoint.gohome.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.waypoint.gohome.R
import com.waypoint.gohome.data.Waypoint
import com.waypoint.gohome.databinding.ActivityMainBinding
import com.waypoint.gohome.location.CompassSensor
import com.waypoint.gohome.location.LocationTrackingService
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var compass: CompassSensor

    private var lastLocation: Location? = null
    private var lastHeading: Float = 0f
    private var recording = false

    private lateinit var currentLocationMarker: Marker
    private lateinit var trackPolyline: Polyline
    private val waypointMarkers = mutableListOf<Marker>()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            onNewLocation(location)
        }
    }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                startLiveLocationUpdates()
            } else {
                toast(getString(R.string.msg_permission_required))
            }
        }

    private val backgroundPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        compass = CompassSensor(this) { heading ->
            lastHeading = heading
            updateNavArrow()
        }

        setupMap()
        setupButtons()
        observeViewModel()

        if (hasFineLocationPermission()) {
            startLiveLocationUpdates()
        } else {
            requestFineLocationPermission()
        }
    }

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.controller.setZoom(16.0)
        binding.mapView.controller.setCenter(GeoPoint(52.2297, 21.0122)) // Warsaw, until we get a fix

        trackPolyline = Polyline(binding.mapView).apply {
            outlinePaint.color = ContextCompat.getColor(this@MainActivity, R.color.track_line)
            outlinePaint.strokeWidth = 8f
        }
        binding.mapView.overlays.add(trackPolyline)

        currentLocationMarker = Marker(binding.mapView).apply {
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_current_location)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = getString(R.string.label_start)
        }
    }

    private fun setupButtons() {
        binding.btnMarkPosition.setOnClickListener {
            withCurrentLocation { location ->
                viewModel.markPosition(location.latitude, location.longitude)
                centerMap(location)
            }
        }

        binding.btnAddWaypoint.setOnClickListener {
            withCurrentLocation { location ->
                viewModel.addWaypoint(location.latitude, location.longitude)
                centerMap(location)
            }
        }

        binding.btnRecord.setOnClickListener {
            if (recording) stopRecording() else startRecording()
        }

        binding.btnReturn.setOnClickListener {
            if (viewModel.waypoints.value.isEmpty()) {
                toast(getString(R.string.msg_no_waypoints))
            } else {
                viewModel.enterReturnMode(targetWaypointId = null)
            }
        }

        binding.btnCancelReturn.setOnClickListener {
            viewModel.cancelReturn()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.waypoints.collect { redrawWaypoints(it) } }
                launch {
                    viewModel.trackPoints.collect { points ->
                        trackPolyline.setPoints(points.map { GeoPoint(it.latitude, it.longitude) })
                        binding.mapView.invalidate()
                    }
                }
                launch { viewModel.returnMode.collect { updateReturnPanelVisibility(it) } }
                launch {
                    viewModel.returnRoute.collect { updateReturnLabel() }
                }
                launch {
                    viewModel.returnIndex.collect { updateReturnLabel() }
                }
            }
        }
    }

    private fun redrawWaypoints(waypoints: List<Waypoint>) {
        waypointMarkers.forEach { binding.mapView.overlays.remove(it) }
        waypointMarkers.clear()

        waypoints.forEach { waypoint ->
            val marker = Marker(binding.mapView).apply {
                position = GeoPoint(waypoint.latitude, waypoint.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = ContextCompat.getDrawable(
                    this@MainActivity,
                    if (waypoint.isStart) R.drawable.ic_marker_start else R.drawable.ic_marker_waypoint
                )
                title = if (waypoint.isStart) {
                    getString(R.string.label_start)
                } else {
                    getString(R.string.label_waypoint, waypoint.sequence)
                }
                setOnMarkerClickListener { _, _ ->
                    showTargetSelectionDialog(waypoint)
                    true
                }
            }
            waypointMarkers.add(marker)
            binding.mapView.overlays.add(marker)
        }
        binding.mapView.invalidate()
    }

    private fun showTargetSelectionDialog(waypoint: Waypoint) {
        AlertDialog.Builder(this)
            .setTitle(if (waypoint.isStart) getString(R.string.label_start) else getString(R.string.label_waypoint, waypoint.sequence))
            .setItems(arrayOf(getString(R.string.menu_set_as_target), getString(R.string.menu_cancel))) { _, which ->
                if (which == 0) {
                    viewModel.enterReturnMode(targetWaypointId = waypoint.id)
                    toast(getString(R.string.msg_target_set))
                }
            }
            .show()
    }

    private fun updateReturnPanelVisibility(active: Boolean) {
        binding.navPanel.visibility = if (active) android.view.View.VISIBLE else android.view.View.GONE
        if (active) {
            binding.btnReturn.text = getString(R.string.btn_cancel_return)
        } else {
            binding.btnReturn.text = getString(R.string.btn_return)
        }
    }

    private fun updateReturnLabel() {
        val route = viewModel.returnRoute.value
        val index = viewModel.returnIndex.value
        val target = route.getOrNull(index) ?: return
        val label = if (target.isStart) getString(R.string.label_start) else getString(R.string.label_waypoint, target.sequence)
        binding.navTargetLabel.text = getString(R.string.label_target_prefix, label)

        val location = lastLocation ?: return
        val results = FloatArray(1)
        Location.distanceBetween(location.latitude, location.longitude, target.latitude, target.longitude, results)
        val distance = results[0]
        binding.navDistance.text = if (distance >= 1000) {
            getString(R.string.label_distance_km, distance / 1000f)
        } else {
            getString(R.string.label_distance_m, distance.roundToInt())
        }
    }

    private fun updateNavArrow() {
        if (!viewModel.returnMode.value) return
        val route = viewModel.returnRoute.value
        val index = viewModel.returnIndex.value
        val target = route.getOrNull(index) ?: return
        val location = lastLocation ?: return
        val targetLocation = Location("target").apply {
            latitude = target.latitude
            longitude = target.longitude
        }
        val bearing = location.bearingTo(targetLocation)
        binding.navArrow.rotation = ((bearing - lastHeading) + 360) % 360
    }

    private fun onNewLocation(location: Location) {
        lastLocation = location
        currentLocationMarker.position = GeoPoint(location.latitude, location.longitude)
        if (currentLocationMarker !in binding.mapView.overlays) {
            binding.mapView.overlays.add(currentLocationMarker)
        }
        binding.mapView.invalidate()
        updateReturnLabel()
        updateNavArrow()

        if (viewModel.returnMode.value) {
            val arrived = viewModel.checkArrival(location.latitude, location.longitude)
            if (arrived) toast(getString(R.string.msg_arrived))
        }
    }

    private fun withCurrentLocation(action: (Location) -> Unit) {
        if (!hasFineLocationPermission()) {
            requestFineLocationPermission()
            return
        }
        val cached = lastLocation
        if (cached != null) {
            action(cached)
            return
        }
        try {
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        onNewLocation(location)
                        action(location)
                    } else {
                        toast(getString(R.string.msg_location_unavailable))
                    }
                }
        } catch (_: SecurityException) {
            requestFineLocationPermission()
        }
    }

    private fun centerMap(location: Location) {
        binding.mapView.controller.animateTo(GeoPoint(location.latitude, location.longitude))
    }

    private fun startLiveLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateDistanceMeters(2f)
            .build()
        try {
            fusedClient.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (_: SecurityException) {
            requestFineLocationPermission()
        }
    }

    private fun startRecording() {
        if (!hasFineLocationPermission()) {
            requestFineLocationPermission()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocationPermission()) {
            toast(getString(R.string.msg_background_permission_rationale))
            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, LocationTrackingService::class.java))
        recording = true
        viewModel.setRecording(true)
        binding.btnRecord.text = getString(R.string.btn_record_stop)
    }

    private fun stopRecording() {
        stopService(Intent(this, LocationTrackingService::class.java))
        recording = false
        viewModel.setRecording(false)
        binding.btnRecord.text = getString(R.string.btn_record_start)
    }

    private fun hasFineLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasBackgroundLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestFineLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_new_trip) {
            viewModel.newTrip()
            if (recording) stopRecording()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        compass.start()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        compass.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedClient.removeLocationUpdates(locationCallback)
    }
}
