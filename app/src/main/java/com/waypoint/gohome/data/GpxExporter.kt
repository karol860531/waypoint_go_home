package com.waypoint.gohome.data

import android.util.Xml
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Writes a trip's waypoints and recorded track as a standard GPX 1.1 file. */
object GpxExporter {

    fun export(trip: Trip, waypoints: List<Waypoint>, track: List<TrackPoint>, outputStream: OutputStream) {
        val serializer = Xml.newSerializer()
        serializer.setOutput(outputStream, "UTF-8")
        serializer.startDocument("UTF-8", true)
        serializer.startTag(null, "gpx")
        serializer.attribute(null, "version", "1.1")
        serializer.attribute(null, "creator", "WaypointGoHome")
        serializer.attribute(null, "xmlns", "http://www.topografix.com/GPX/1/1")

        waypoints.forEach { wp ->
            serializer.startTag(null, "wpt")
            serializer.attribute(null, "lat", wp.latitude.toString())
            serializer.attribute(null, "lon", wp.longitude.toString())
            wp.altitude?.let {
                serializer.startTag(null, "ele")
                serializer.text(it.toString())
                serializer.endTag(null, "ele")
            }
            serializer.startTag(null, "name")
            serializer.text(
                when {
                    wp.isStart -> "Start"
                    wp.isEnd -> "End"
                    !wp.label.isNullOrBlank() -> wp.label
                    else -> "Waypoint ${wp.sequence}"
                }
            )
            serializer.endTag(null, "name")
            if (wp.photoUri != null) {
                serializer.startTag(null, "link")
                serializer.attribute(null, "href", ExportPhotoNaming.fileNameFor(wp.id))
                serializer.startTag(null, "text")
                serializer.text("Zdjęcie")
                serializer.endTag(null, "text")
                serializer.startTag(null, "type")
                serializer.text("image/jpeg")
                serializer.endTag(null, "type")
                serializer.endTag(null, "link")
            }
            serializer.endTag(null, "wpt")
        }

        serializer.startTag(null, "trk")
        serializer.startTag(null, "name")
        serializer.text(trip.name)
        serializer.endTag(null, "name")
        serializer.startTag(null, "trkseg")
        track.forEach { tp ->
            serializer.startTag(null, "trkpt")
            serializer.attribute(null, "lat", tp.latitude.toString())
            serializer.attribute(null, "lon", tp.longitude.toString())
            tp.altitude?.let {
                serializer.startTag(null, "ele")
                serializer.text(it.toString())
                serializer.endTag(null, "ele")
            }
            serializer.startTag(null, "time")
            serializer.text(isoFormat(tp.timestamp))
            serializer.endTag(null, "time")
            serializer.endTag(null, "trkpt")
        }
        serializer.endTag(null, "trkseg")
        serializer.endTag(null, "trk")

        serializer.endTag(null, "gpx")
        serializer.endDocument()
        serializer.flush()
    }

    private fun isoFormat(timestamp: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(timestamp))
    }
}
