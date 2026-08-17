package com.waypoint.gohome.data

/**
 * A user-marked point: the trip's start point, a waypoint added along the way, or the end point
 * dropped automatically when recording stops. [sequence] defines the order in which points were
 * recorded (0 = start).
 */
data class Waypoint(
    val id: Long,
    val tripId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val sequence: Int,
    val isStart: Boolean,
    val isEnd: Boolean,
    val label: String?,
    val photoUri: String?,
    val timestamp: Long
)
