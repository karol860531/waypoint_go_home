package com.waypoint.gohome.data

import android.content.ContentResolver
import android.util.Xml
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports a trip as KMZ (Google Earth format): a ZIP containing `doc.kml` plus any waypoint
 * photos. Unlike GPX, KML placemarks can show a photo directly in their info balloon, so this is
 * the richer of the two photo-friendly export options.
 */
object KmzExporter {

    fun export(
        trip: Trip,
        waypoints: List<Waypoint>,
        track: List<TrackPoint>,
        contentResolver: ContentResolver,
        outputStream: OutputStream
    ) {
        ZipOutputStream(outputStream).use { zos ->
            zos.putNextEntry(ZipEntry("doc.kml"))
            zos.write(buildKml(trip, waypoints, track))
            zos.closeEntry()

            ZipPhotoWriter.writePhotos(zos, contentResolver, waypoints)
        }
    }

    private fun buildKml(trip: Trip, waypoints: List<Waypoint>, track: List<TrackPoint>): ByteArray {
        val buffer = ByteArrayOutputStream()
        val serializer = Xml.newSerializer()
        serializer.setOutput(buffer, "UTF-8")
        serializer.startDocument("UTF-8", true)
        serializer.startTag(null, "kml")
        serializer.attribute(null, "xmlns", "http://www.opengis.net/kml/2.2")
        serializer.startTag(null, "Document")

        serializer.startTag(null, "name")
        serializer.text(trip.name)
        serializer.endTag(null, "name")

        waypoints.forEach { wp ->
            serializer.startTag(null, "Placemark")

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
                serializer.startTag(null, "description")
                serializer.cdsect("<img src=\"${ExportPhotoNaming.fileNameFor(wp.id)}\" width=\"400\"/>")
                serializer.endTag(null, "description")
            }

            serializer.startTag(null, "Point")
            serializer.startTag(null, "coordinates")
            serializer.text(coordinateString(wp.longitude, wp.latitude, wp.altitude))
            serializer.endTag(null, "coordinates")
            serializer.endTag(null, "Point")

            serializer.endTag(null, "Placemark")
        }

        if (track.size >= 2) {
            serializer.startTag(null, "Placemark")
            serializer.startTag(null, "name")
            serializer.text(trip.name)
            serializer.endTag(null, "name")
            serializer.startTag(null, "LineString")
            serializer.startTag(null, "tessellate")
            serializer.text("1")
            serializer.endTag(null, "tessellate")
            serializer.startTag(null, "coordinates")
            serializer.text(track.joinToString(" ") { coordinateString(it.longitude, it.latitude, it.altitude) })
            serializer.endTag(null, "coordinates")
            serializer.endTag(null, "LineString")
            serializer.endTag(null, "Placemark")
        }

        serializer.endTag(null, "Document")
        serializer.endTag(null, "kml")
        serializer.endDocument()
        serializer.flush()
        return buffer.toByteArray()
    }

    private fun coordinateString(lon: Double, lat: Double, altitude: Double?): String =
        if (altitude != null) "$lon,$lat,$altitude" else "$lon,$lat"
}
