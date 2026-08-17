package com.waypoint.gohome.data

/** Shared relative path convention for a waypoint's photo inside an export archive (ZIP/KMZ). */
object ExportPhotoNaming {
    fun fileNameFor(waypointId: Long): String = "photos/waypoint_$waypointId.jpg"
}
