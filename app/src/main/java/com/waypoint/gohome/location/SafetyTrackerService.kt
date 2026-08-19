package com.waypoint.gohome.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.waypoint.gohome.R
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Foreground "safety check-in" service: every configured interval, it silently texts the
 * current position (as an OpenStreetMap link) to one pre-configured trusted phone number via
 * SMS — no server of any kind is involved, this device never accepts inbound connections. Always
 * visible to the device's own user via the ongoing notification, same as track recording.
 */
class SafetyTrackerService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val intervalMinutes = prefs.getInt(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
            .coerceAtLeast(MIN_INTERVAL_MINUTES)
        val phone = prefs.getString(KEY_PHONE, null)
        if (phone.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(intervalMinutes, phone))
        startLoop(intervalMinutes, phone)
        return START_STICKY
    }

    private fun startLoop(intervalMinutes: Int, phone: String) {
        serviceScope.launch {
            while (isActive) {
                sendLocationSms(phone)
                delay(intervalMinutes * 60_000L)
            }
        }
    }

    private suspend fun sendLocationSms(phone: String) {
        val location = getCurrentLocationOnce() ?: return
        val mapLink = "https://www.openstreetmap.org/?mlat=${location.latitude}&mlon=${location.longitude}" +
            "#map=16/${location.latitude}/${location.longitude}"
        val time = SimpleDateFormat("HH:mm", Locale("pl", "PL")).format(java.util.Date())
        val message = getString(R.string.safety_tracker_sms_template, time, mapLink)
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phone, null, message, null, null)
        } catch (_: Exception) {
            // Best-effort: a single failed send (no signal, permission revoked mid-run, etc.)
            // shouldn't stop the whole check-in loop — the next tick will simply try again.
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun getCurrentLocationOnce(): Location? = suspendCancellableCoroutine { cont ->
        try {
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { cont.resume(it, null) }
                .addOnFailureListener { cont.resume(null, null) }
        } catch (_: SecurityException) {
            cont.resume(null, null)
        }
    }

    private fun buildNotification(intervalMinutes: Int, phone: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.safety_tracker_notification_title))
            .setContentText(getString(R.string.safety_tracker_notification_text, intervalMinutes, phone))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.safety_tracker_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "safety_tracker"
        private const val NOTIFICATION_ID = 1002
        private const val MIN_INTERVAL_MINUTES = 5
        const val DEFAULT_INTERVAL_MINUTES = 15

        const val PREFS_NAME = "waypoint_prefs"
        const val KEY_ENABLED = "safety_tracker_enabled"
        const val KEY_PHONE = "safety_tracker_phone"
        const val KEY_INTERVAL_MINUTES = "safety_tracker_interval_minutes"
    }
}
