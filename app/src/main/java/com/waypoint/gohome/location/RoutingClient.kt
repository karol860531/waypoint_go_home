package com.waypoint.gohome.location

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class RoutePoint(val latitude: Double, val longitude: Double)

sealed class RoutingResult {
    data class Success(val points: List<RoutePoint>) : RoutingResult()
    /** [reason] is one of "no_connection", "no_route", "server_error". */
    data class Failure(val reason: String) : RoutingResult()
}

/**
 * Fetches a road-following route from the public OSRM demo server (no API key). The demo
 * instance only hosts the "driving" profile, so the result is an approximate road route rather
 * than a true footpath — it is offered as an optional overlay, the straight-line compass/distance
 * guidance in return mode remains the primary, fully-offline navigation method. Since it's a
 * driving-only profile, points far from any road (e.g. deep in a forest/trail) will legitimately
 * come back as "no route" rather than a connectivity problem.
 */
object RoutingClient {

    suspend fun fetchRoute(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): RoutingResult =
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
                if (connection.responseCode != 200) return@withContext RoutingResult.Failure("server_error")

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                if (json.optString("code") != "Ok") return@withContext RoutingResult.Failure("no_route")

                val routes = json.getJSONArray("routes")
                if (routes.length() == 0) return@withContext RoutingResult.Failure("no_route")
                val coordinates = routes.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")

                RoutingResult.Success(
                    (0 until coordinates.length()).map { i ->
                        val pair = coordinates.getJSONArray(i)
                        RoutePoint(latitude = pair.getDouble(1), longitude = pair.getDouble(0))
                    }
                )
            } catch (_: SocketTimeoutException) {
                RoutingResult.Failure("no_connection")
            } catch (_: IOException) {
                RoutingResult.Failure("no_connection")
            } catch (_: Exception) {
                RoutingResult.Failure("server_error")
            }
        }
}
