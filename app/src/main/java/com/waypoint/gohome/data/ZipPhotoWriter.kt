package com.waypoint.gohome.data

import android.content.ContentResolver
import android.net.Uri
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Copies each waypoint's photo (if any) into an already-open ZIP under [ExportPhotoNaming]'s convention. */
object ZipPhotoWriter {
    fun writePhotos(zos: ZipOutputStream, contentResolver: ContentResolver, waypoints: List<Waypoint>) {
        waypoints.forEach { wp ->
            val photoUri = wp.photoUri ?: return@forEach
            try {
                contentResolver.openInputStream(Uri.parse(photoUri))?.use { input ->
                    zos.putNextEntry(ZipEntry(ExportPhotoNaming.fileNameFor(wp.id)))
                    input.copyTo(zos)
                    zos.closeEntry()
                }
            } catch (_: Exception) {
                // Skip this one photo rather than failing the whole export.
            }
        }
    }
}
