package com.waypoint.gohome.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import com.waypoint.gohome.data.GeoUtils
import com.waypoint.gohome.data.GpxExporter
import com.waypoint.gohome.data.GpxImporter
import com.waypoint.gohome.data.Waypoint
import com.waypoint.gohome.databinding.ActivityMainBinding
import com.waypoint.gohome.history.TripHistoryActivity
import com.waypoint.gohome.location.CompassSensor
import com.waypoint.gohome.location.LocationTrackingService
import com.waypoint.gohome.location.RoutingClient
import com.waypoint.gohome.location.RoutingResult
import com.waypoint.gohome.sun.SunInfoActivity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

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
    private var recordingStartTime = 0L

    private lateinit var currentLocationMarker: Marker
    private lateinit var trackPolyline: Polyline
    private lateinit var routePolyline: Polyline
    private val waypointMarkers = mutableListOf<Marker>()

    private val statsHandler = Handler(Looper.getMainLooper())
    private val statsRunnable = object : Runnable {
        override fun run() {
            updateStats()
            statsHandler.postDelayed(this, 1000)
        }
    }

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
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startRecording() }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startRecording() }

    private val exportGpxLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
            if (uri != null) exportGpxTo(uri)
        }

    private val importGpxLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importGpxFrom(uri)
        }

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

        routePolyline = Polyline(binding.mapView).apply {
            outlinePaint.color = ContextCompat.getColor(this@MainActivity, R.color.marker_target)
            outlinePaint.strokeWidth = 6f
            outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 15f), 0f)
        }
        binding.mapView.overlays.add(routePolyline)

        currentLocationMarker = Marker(binding.mapView).apply {
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_current_location)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = getString(R.string.label_start)
        }
    }

    private fun setupButtons() {
        binding.btnMarkPosition.setOnClickListener {
            withCurrentLocation { location ->
                viewModel.markPosition(location.latitude, location.longitude, altitudeOf(location))
                centerMap(location)
            }
        }

        binding.btnAddWaypoint.setOnClickListener {
            withCurrentLocation { location ->
                viewModel.addWaypoint(location.latitude, location.longitude, altitudeOf(location))
                centerMap(location)
            }
        }

        binding.btnRecord.setOnClickListener {
            if (recording) stopRecording() else startRecording()
        }

        binding.btnReturn.setOnClickListener {
            when {
                viewModel.returnMode.value -> viewModel.cancelReturn()
                viewModel.waypoints.value.isEmpty() -> toast(getString(R.string.msg_no_waypoints))
                else -> showReturnModeDialog()
            }
        }

        binding.btnCancelReturn.setOnClickListener {
            viewModel.cancelReturn()
        }

        binding.checkRoadRouting.setOnCheckedChangeListener { _, checked ->
            if (checked) refreshRoadRoute() else clearRoadRoute()
        }

        binding.btnLocateMe.setOnClickListener {
            withCurrentLocation { location -> centerMap(location) }
        }
    }

    private fun altitudeOf(location: Location): Double? = if (location.hasAltitude()) location.altitude else null

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
                launch { viewModel.returnRoute.collect { updateReturnLabel() } }
                launch {
                    viewModel.returnIndex.collect {
                        updateReturnLabel()
                        if (binding.checkRoadRouting.isChecked) refreshRoadRoute() else clearRoadRoute()
                    }
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
                    showWaypointDialog(waypoint)
                    true
                }
            }
            waypointMarkers.add(marker)
            binding.mapView.overlays.add(marker)
        }
        binding.mapView.invalidate()
    }

    private fun showReturnModeDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.btn_return)
            .setItems(
                arrayOf(
                    getString(R.string.menu_return_via_waypoints),
                    getString(R.string.menu_return_direct_to_start),
                    getString(R.string.menu_return_choose_target)
                )
            ) { _, which ->
                when (which) {
                    0 -> viewModel.enterReturnMode(targetWaypointId = null, direct = false)
                    1 -> viewModel.enterReturnMode(targetWaypointId = null, direct = true)
                    2 -> showChooseTargetDialog()
                }
            }
            .show()
    }

    private fun showChooseTargetDialog() {
        val sortedWaypoints = viewModel.waypoints.value.sortedBy { it.sequence }
        val labels = sortedWaypoints.map {
            if (it.isStart) getString(R.string.label_start) else getString(R.string.label_waypoint, it.sequence)
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.title_choose_target)
            .setItems(labels) { _, which ->
                viewModel.enterReturnMode(targetWaypointId = sortedWaypoints[which].id)
            }
            .show()
    }

    private fun showWaypointDialog(waypoint: Waypoint) {
        val title = if (waypoint.isStart) getString(R.string.label_start) else getString(R.string.label_waypoint, waypoint.sequence)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(
                arrayOf(
                    getString(R.string.menu_set_as_target),
                    getString(R.string.menu_delete_waypoint),
                    getString(R.string.menu_cancel)
                )
            ) { _, which ->
                when (which) {
                    0 -> {
                        viewModel.enterReturnMode(targetWaypointId = waypoint.id)
                        toast(getString(R.string.msg_target_set))
                    }
                    1 -> viewModel.deleteWaypoint(waypoint.id)
                }
            }
            .show()
    }

    private fun updateReturnPanelVisibility(active: Boolean) {
        binding.navPanel.visibility = if (active) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnReturn.text = if (active) getString(R.string.btn_cancel_return) else getString(R.string.btn_return)
        if (!active) {
            binding.checkRoadRouting.isChecked = false
            clearRoadRoute()
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

    private fun refreshRoadRoute() {
        val route = viewModel.returnRoute.value
        val index = viewModel.returnIndex.value
        val target = route.getOrNull(index) ?: return
        val location = lastLocation ?: return
        lifecycleScope.launch {
            when (val result = RoutingClient.fetchRoute(location.latitude, location.longitude, target.latitude, target.longitude)) {
                is RoutingResult.Success -> {
                    routePolyline.setPoints(result.points.map { GeoPoint(it.latitude, it.longitude) })
                }
                is RoutingResult.Failure -> {
                    toast(
                        getString(
                            when (result.reason) {
                                "no_connection" -> R.string.msg_routing_no_connection
                                "no_route" -> R.string.msg_routing_no_route
                                else -> R.string.msg_routing_server_error
                            }
                        )
                    )
                    routePolyline.setPoints(emptyList())
                }
            }
            binding.mapView.invalidate()
        }
    }

    private fun clearRoadRoute() {
        routePolyline.setPoints(emptyList())
        binding.mapView.invalidate()
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
            when (viewModel.checkArrival(location.latitude, location.longitude)) {
                MainViewModel.ArrivalStatus.FINISHED -> {
                    vibrateArrival()
                    toast(getString(R.string.msg_arrived))
                }
                MainViewModel.ArrivalStatus.ADVANCED -> vibrateArrival()
                MainViewModel.ArrivalStatus.NONE -> Unit
            }
        }
    }

    private fun vibrateArrival() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VibratorManager::class.java))?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun updateStats() {
        val track = viewModel.trackPoints.value
        val distanceMeters = GeoUtils.totalDistanceMeters(track)
        binding.statDistance.text = if (distanceMeters >= 1000) {
            String.format(Locale.US, "%.2f km", distanceMeters / 1000f)
        } else {
            "${distanceMeters.roundToInt()} m"
        }

        val elapsedMs = System.currentTimeMillis() - recordingStartTime
        val totalSeconds = elapsedMs / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        binding.statTime.text = String.format(Locale.US, "%02d:%02d:%02d", h, m, s)

        binding.statPace.text = if (distanceMeters > 50f) {
            val paceSecPerKm = (elapsedMs / 1000.0) / (distanceMeters / 1000.0)
            val pm = (paceSecPerKm / 60).toInt()
            val ps = (paceSecPerKm % 60).toInt()
            String.format(Locale.US, "%d:%02d /km", pm, ps)
        } else {
            "--:-- /km"
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
        maybePromptBatteryOptimization { actuallyStartRecording() }
    }

    private fun maybePromptBatteryOptimization(onContinue: () -> Unit) {
        val powerManager = getSystemService(PowerManager::class.java)
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.dialog_battery_title)
                .setMessage(R.string.dialog_battery_message)
                .setPositiveButton(R.string.btn_open_settings) { _, _ ->
                    try {
                        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                    } catch (_: Exception) {
                    }
                    onContinue()
                }
                .setNegativeButton(R.string.btn_skip) { _, _ -> onContinue() }
                .show()
        } else {
            onContinue()
        }
    }

    private fun actuallyStartRecording() {
        ContextCompat.startForegroundService(this, Intent(this, LocationTrackingService::class.java))
        recording = true
        recordingStartTime = System.currentTimeMillis()
        viewModel.setRecording(true)
        binding.btnRecord.text = getString(R.string.btn_record_stop)
        binding.statsBar.visibility = android.view.View.VISIBLE
        statsHandler.removeCallbacks(statsRunnable)
        statsHandler.post(statsRunnable)
    }

    private fun stopRecording() {
        stopService(Intent(this, LocationTrackingService::class.java))
        recording = false
        viewModel.setRecording(false)
        binding.btnRecord.text = getString(R.string.btn_record_start)
        binding.statsBar.visibility = android.view.View.GONE
        statsHandler.removeCallbacks(statsRunnable)
    }

    private fun showAutoWaypointDialog() {
        val prefs = getSharedPreferences(LocationTrackingService.PREFS_NAME, Context.MODE_PRIVATE)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
        }
        val checkBox = CheckBox(this).apply {
            text = getString(R.string.menu_auto_waypoint)
            isChecked = prefs.getBoolean(LocationTrackingService.KEY_AUTO_WAYPOINT_ENABLED, false)
        }
        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.dialog_auto_waypoint_distance_hint)
            setText(prefs.getFloat(LocationTrackingService.KEY_AUTO_WAYPOINT_DISTANCE, LocationTrackingService.DEFAULT_AUTO_WAYPOINT_DISTANCE).toInt().toString())
        }
        container.addView(checkBox)
        container.addView(editText)

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_auto_waypoint_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val distance = editText.text.toString().toFloatOrNull() ?: LocationTrackingService.DEFAULT_AUTO_WAYPOINT_DISTANCE
                prefs.edit()
                    .putBoolean(LocationTrackingService.KEY_AUTO_WAYPOINT_ENABLED, checkBox.isChecked)
                    .putFloat(LocationTrackingService.KEY_AUTO_WAYPOINT_DISTANCE, distance)
                    .apply()
                toast(
                    if (checkBox.isChecked) {
                        getString(R.string.msg_auto_waypoint_enabled, distance.toInt())
                    } else {
                        getString(R.string.msg_auto_waypoint_disabled)
                    }
                )
            }
            .setNegativeButton(R.string.menu_cancel, null)
            .show()
    }

    private fun exportGpxTo(uri: Uri) {
        lifecycleScope.launch {
            val snapshot = viewModel.getCurrentTripSnapshot()
            if (snapshot == null) {
                toast(getString(R.string.msg_gpx_export_failed))
                return@launch
            }
            val (trip, waypoints, track) = snapshot
            val success = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openOutputStream(uri)?.use { GpxExporter.export(trip, waypoints, track, it) }
                    true
                } catch (_: Exception) {
                    false
                }
            }
            toast(getString(if (success) R.string.msg_gpx_exported else R.string.msg_gpx_export_failed))
        }
    }

    private fun importGpxFrom(uri: Uri) {
        lifecycleScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri)?.use { GpxImporter.parse(it) }
                } catch (_: Exception) {
                    null
                }
            }
            if (parsed == null || (parsed.waypoints.isEmpty() && parsed.track.isEmpty())) {
                toast(getString(R.string.msg_gpx_import_failed))
                return@launch
            }
            viewModel.importGpx(parsed.waypoints, parsed.track)
            toast(getString(R.string.msg_gpx_imported))
        }
    }

    private fun downloadOfflineMap() {
        // The free OSM Mapnik tile source explicitly forbids bulk downloads (TileSourcePolicy.FLAG_NO_BULK)
        // to protect the shared server. osmdroid enforces this by throwing on a background thread, which
        // would otherwise crash the app with no chance to catch it — so we check first instead of calling in.
        if (!TileSourceFactory.MAPNIK.tileSourcePolicy.acceptsBulkDownload()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.title_offline_map_unavailable)
                .setMessage(R.string.msg_offline_bulk_not_allowed)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        toast(getString(R.string.msg_offline_download_started))
        val cacheManager = CacheManager(binding.mapView)
        val boundingBox = binding.mapView.boundingBox
        val currentZoom = binding.mapView.zoomLevelDouble.toInt()
        val minZoom = currentZoom
        val maxZoom = (currentZoom + 3).coerceAtMost(19)
        cacheManager.downloadAreaAsync(this, boundingBox, minZoom, maxZoom, object : CacheManager.CacheManagerCallback {
            override fun onTaskComplete() {
                runOnUiThread { toast(getString(R.string.msg_offline_download_done)) }
            }

            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) = Unit
            override fun downloadStarted() = Unit
            override fun setPossibleTilesInArea(total: Int) = Unit

            override fun onTaskFailed(errors: Int) {
                runOnUiThread { toast(getString(R.string.msg_offline_download_failed)) }
            }
        })
    }

    private fun showCurrentPositionDialog() {
        val location = lastLocation
        if (location == null) {
            toast(getString(R.string.msg_no_location))
            return
        }
        val message = buildString {
            appendLine(String.format(Locale.US, getString(R.string.label_latitude), location.latitude))
            appendLine(String.format(Locale.US, getString(R.string.label_longitude), location.longitude))
            if (location.hasAltitude()) {
                appendLine(String.format(Locale.US, getString(R.string.label_altitude), location.altitude))
            } else {
                appendLine(getString(R.string.label_altitude_unavailable))
            }
            if (location.hasAccuracy()) {
                append(String.format(Locale.US, getString(R.string.label_accuracy), location.accuracy))
            }
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.title_current_position)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun openSunInfo() {
        val location = lastLocation
        if (location == null) {
            toast(getString(R.string.msg_no_location))
            return
        }
        val intent = Intent(this, SunInfoActivity::class.java).apply {
            putExtra(SunInfoActivity.EXTRA_LAT, location.latitude)
            putExtra(SunInfoActivity.EXTRA_LON, location.longitude)
        }
        startActivity(intent)
    }

    private fun showElevationProfile() {
        val track = viewModel.trackPoints.value
        if (track.none { it.altitude != null }) {
            toast(getString(R.string.msg_no_altitude_data))
            return
        }
        val chart = ElevationChartView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (250 * resources.displayMetrics.density).toInt()
            )
            setData(track)
        }
        val container = LinearLayout(this).apply {
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(chart)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.title_elevation_profile)
            .setView(container)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private enum class ShareTarget(val packageName: String, val labelRes: Int) {
        WHATSAPP("com.whatsapp", R.string.share_target_whatsapp),
        SIGNAL("org.thoughtcrime.securesms", R.string.share_target_signal),
        SMS("", R.string.share_target_sms)
    }

    private fun showShareLocationDialog() {
        if (lastLocation == null) {
            toast(getString(R.string.msg_no_location_to_share))
            return
        }
        val targets = ShareTarget.values()
        AlertDialog.Builder(this)
            .setTitle(R.string.title_share_location)
            .setItems(targets.map { getString(it.labelRes) }.toTypedArray()) { _, which ->
                shareLocation(targets[which])
            }
            .show()
    }

    private fun shareLocation(target: ShareTarget) {
        val location = lastLocation ?: run {
            toast(getString(R.string.msg_no_location_to_share))
            return
        }
        lifecycleScope.launch {
            val distanceMeters = GeoUtils.totalDistanceMeters(viewModel.trackPoints.value)
            val distanceText = if (distanceMeters >= 1000) {
                String.format(Locale.US, "%.2f km", distanceMeters / 1000f)
            } else {
                "${distanceMeters.roundToInt()} m"
            }
            val mapLink = "https://www.openstreetmap.org/?mlat=${location.latitude}&mlon=${location.longitude}" +
                "#map=16/${location.latitude}/${location.longitude}"
            val message = getString(R.string.share_message_template, mapLink, distanceText)

            when (target) {
                ShareTarget.SMS -> sendSms(message)
                else -> {
                    val gpxUri = writeShareGpxSnapshot()
                    sendToApp(target, message, gpxUri)
                }
            }
        }
    }

    private suspend fun writeShareGpxSnapshot(): Uri? {
        val snapshot = viewModel.getCurrentTripSnapshot() ?: return null
        val (trip, waypoints, track) = snapshot
        if (waypoints.isEmpty() && track.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            try {
                val dir = File(cacheDir, "share").apply { mkdirs() }
                val file = File(dir, "trasa.gpx")
                FileOutputStream(file).use { GpxExporter.export(trip, waypoints, track, it) }
                FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun sendToApp(target: ShareTarget, message: String, gpxUri: Uri?) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (gpxUri != null) "application/gpx+xml" else "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            if (gpxUri != null) {
                putExtra(Intent.EXTRA_STREAM, gpxUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            setPackage(target.packageName)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            toast(getString(R.string.msg_app_not_installed, getString(target.labelRes)))
        }
    }

    private fun sendSms(message: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply {
            putExtra("sms_body", message)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            toast(getString(R.string.msg_app_not_installed, getString(R.string.share_target_sms)))
        }
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
        when (item.itemId) {
            R.id.action_share_location -> {
                showShareLocationDialog()
                return true
            }
            R.id.action_new_trip -> {
                if (recording) stopRecording()
                viewModel.newTrip()
                return true
            }
            R.id.action_trip_history -> {
                if (recording) stopRecording()
                startActivity(Intent(this, TripHistoryActivity::class.java))
                return true
            }
            R.id.action_export_gpx -> {
                val fileName = "trasa_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(java.util.Date()) + ".gpx"
                exportGpxLauncher.launch(fileName)
                return true
            }
            R.id.action_import_gpx -> {
                importGpxLauncher.launch(arrayOf("*/*"))
                return true
            }
            R.id.action_offline_map -> {
                downloadOfflineMap()
                return true
            }
            R.id.action_elevation_profile -> {
                showElevationProfile()
                return true
            }
            R.id.action_auto_waypoint -> {
                showAutoWaypointDialog()
                return true
            }
            R.id.action_current_position -> {
                showCurrentPositionDialog()
                return true
            }
            R.id.action_sun_info -> {
                openSunInfo()
                return true
            }
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
        statsHandler.removeCallbacks(statsRunnable)
    }
}
