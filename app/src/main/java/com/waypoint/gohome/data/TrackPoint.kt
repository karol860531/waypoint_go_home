package com.waypoint.gohome.data

/** A single GPS fix recorded while the route was being drawn/recorded. */
data class TrackPoint(
    val id: Long,
    val tripId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val timestamp: Long
)
