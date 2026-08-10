package com.waypoint.gohome.data

/**
 * A user-marked point: either the trip's start point or a waypoint added along the way.
 * [sequence] defines the order in which points were recorded (0 = start).
 */
data class Waypoint(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val sequence: Int,
    val isStart: Boolean,
    val timestamp: Long
)
