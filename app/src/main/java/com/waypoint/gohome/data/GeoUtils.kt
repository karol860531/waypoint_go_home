package com.waypoint.gohome.data

import android.location.Location

/** Small geometry helpers shared by the stats bar, trip history summaries and elevation chart. */
object GeoUtils {

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    /** Sums the great-circle distance between consecutive points. */
    fun totalDistanceMeters(points: List<TrackPoint>): Float {
        if (points.size < 2) return 0f
        var total = 0f
        for (i in 1 until points.size) {
            total += distanceMeters(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude
            )
        }
        return total
    }
}
