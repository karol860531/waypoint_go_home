package com.waypoint.gohome.data

import android.util.Xml
import java.io.InputStream
import org.xmlpull.v1.XmlPullParser

/** Reads waypoints (`<wpt>`) and track points (`<trkpt>`) out of a GPX file. */
object GpxImporter {

    data class ParsedGpx(
        val waypoints: List<Triple<Double, Double, Double?>>,
        val track: List<Triple<Double, Double, Double?>>
    )

    fun parse(inputStream: InputStream): ParsedGpx {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        val waypoints = mutableListOf<Triple<Double, Double, Double?>>()
        val track = mutableListOf<Triple<Double, Double, Double?>>()

        var currentTag: String? = null
        var lat: Double? = null
        var lon: Double? = null
        var elevation: Double? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag == "wpt" || currentTag == "trkpt") {
                        lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                        lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                        elevation = null
                    }
                }
                XmlPullParser.TEXT -> {
                    if (currentTag == "ele") {
                        parser.text?.trim()?.toDoubleOrNull()?.let { elevation = it }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val closedTag = parser.name
                    if (closedTag == "wpt" && lat != null && lon != null) {
                        waypoints.add(Triple(lat, lon, elevation))
                    } else if (closedTag == "trkpt" && lat != null && lon != null) {
                        track.add(Triple(lat, lon, elevation))
                    }
                    currentTag = null
                }
            }
            eventType = parser.next()
        }
        return ParsedGpx(waypoints, track)
    }
}
