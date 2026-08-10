package com.waypoint.gohome.location

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class RoutePoint(val latitude: Double, val longitude: Double)

/**
 * Fetches a road-following route from the public OSRM demo server (no API key). The demo
 * instance only hosts the "driving" profile, so the result is an approximate road route rather
 * than a true footpath — it is offered as an optional overlay, the straight-line compass/distance
 * guidance in return mode remains the primary, fully-offline navigation method.
 */
object RoutingClient {

    suspend fun fetchRoute(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): List<RoutePoint>? =
        withContext(Dispatchers.IO) {
            try {
                val url = URL(
                    "https://router.project-osrm.org/route/v1/driving/" +
                        "$fromLon,$fromLat;$toLon,$toLat?overview=full&geometries=geojson"
                )
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.requestMethod = "GET"
                if (connection.responseCode != 200) return@withContext null

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                if (json.optString("code") != "Ok") return@withContext null

                val routes = json.getJSONArray("routes")
                if (routes.length() == 0) return@withContext null
                val coordinates = routes.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")

                (0 until coordinates.length()).map { i ->
                    val pair = coordinates.getJSONArray(i)
                    RoutePoint(latitude = pair.getDouble(1), longitude = pair.getDouble(0))
                }
            } catch (_: Exception) {
                null
            }
        }
}
