package com.waypoint.gohome.data

/** A recorded outing: a named container for a set of waypoints and a track. */
data class Trip(
    val id: Long,
    val name: String,
    val createdAt: Long
)
