package com.waypoint.gohome.data

import android.content.ContentResolver
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Bundles a GPX file (with `<link>` references to photo files) plus the actual photos into a ZIP. */
object GpxZipExporter {
    fun export(
        trip: Trip,
        waypoints: List<Waypoint>,
        track: List<TrackPoint>,
        contentResolver: ContentResolver,
        outputStream: OutputStream
    ) {
        ZipOutputStream(outputStream).use { zos ->
            val gpxBytes = ByteArrayOutputStream().apply {
                GpxExporter.export(trip, waypoints, track, this)
            }.toByteArray()
            zos.putNextEntry(ZipEntry("trasa.gpx"))
            zos.write(gpxBytes)
            zos.closeEntry()

            ZipPhotoWriter.writePhotos(zos, contentResolver, waypoints)
        }
    }
}
