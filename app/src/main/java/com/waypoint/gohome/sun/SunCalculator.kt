package com.waypoint.gohome.sun

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Offline sun position / sunrise-sunset-twilight calculator, based on the standard low-precision
 * solar position algorithm described at aa.quae.nl/en/reken/zonpositie.html (the same public
 * formulas underlying the widely used SunCalc.js library). Accurate to within a minute or two —
 * plenty for hiking/orientation purposes — and needs no network access.
 */
object SunCalculator {

    private const val DAY_MS = 1000.0 * 60 * 60 * 24
    private const val J1970 = 2440588.0
    private const val J2000 = 2451545.0
    private val RAD = PI / 180.0
    private val OBLIQUITY = RAD * 23.4397

    private fun toJulian(dateMillis: Long) = dateMillis / DAY_MS - 0.5 + J1970
    private fun fromJulian(j: Double) = ((j + 0.5 - J1970) * DAY_MS).toLong()
    private fun toDays(dateMillis: Long) = toJulian(dateMillis) - J2000

    private fun solarMeanAnomaly(d: Double) = RAD * (357.5291 + 0.98560028 * d)

    private fun eclipticLongitude(m: Double): Double {
        val c = RAD * (1.9148 * sin(m) + 0.02 * sin(2 * m) + 0.0003 * sin(3 * m))
        val p = RAD * 102.9372
        return m + c + p + PI
    }

    private fun declination(l: Double) = asin(sin(l) * sin(OBLIQUITY))
    private fun rightAscension(l: Double) = atan2(sin(l) * cos(OBLIQUITY), cos(l))

    private class SunCoords(val dec: Double, val ra: Double)

    private fun sunCoords(d: Double): SunCoords {
        val m = solarMeanAnomaly(d)
        val l = eclipticLongitude(m)
        return SunCoords(declination(l), rightAscension(l))
    }

    private fun siderealTime(d: Double, lw: Double) = RAD * (280.16 + 360.9856235 * d) - lw

    private fun azimuthFromHourAngle(h: Double, phi: Double, dec: Double) =
        atan2(sin(h), cos(h) * sin(phi) - tan(dec) * cos(phi))

    private fun altitudeFromHourAngle(h: Double, phi: Double, dec: Double) =
        asin(sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(h))

    data class SunPosition(
        /** Compass bearing in degrees: 0 = north, 90 = east, 180 = south, 270 = west. */
        val azimuthDeg: Double,
        /** Degrees above the horizon; negative means the sun is below the horizon. */
        val altitudeDeg: Double
    )

    fun getPosition(dateMillis: Long, lat: Double, lon: Double): SunPosition {
        val lw = RAD * -lon
        val phi = RAD * lat
        val d = toDays(dateMillis)
        val c = sunCoords(d)
        val h = siderealTime(d, lw) - c.ra
        val bearing = (Math.toDegrees(azimuthFromHourAngle(h, phi, c.dec)) + 180.0 + 360.0) % 360.0
        val altitude = Math.toDegrees(altitudeFromHourAngle(h, phi, c.dec))
        return SunPosition(bearing, altitude)
    }

    data class SunTimes(
        val dawn: Long,
        val sunrise: Long,
        val solarNoon: Long,
        val sunset: Long,
        val dusk: Long
    )

    private const val J0 = 0.0009

    private fun julianCycle(d: Double, lw: Double) = Math.round(d - J0 - lw / (2 * PI)).toDouble()
    private fun approxTransit(ht: Double, lw: Double, n: Double) = J0 + (ht + lw) / (2 * PI) + n
    private fun solarTransitJ(ds: Double, m: Double, l: Double) =
        J2000 + ds + 0.0053 * sin(m) - 0.0069 * sin(2 * l)

    private fun hourAngle(h: Double, phi: Double, dec: Double): Double {
        val cosH = (sin(h) - sin(phi) * sin(dec)) / (cos(phi) * cos(dec))
        return acos(cosH.coerceIn(-1.0, 1.0))
    }

    private fun getSetJ(h: Double, lw: Double, phi: Double, dec: Double, n: Double, m: Double, l: Double): Double {
        val w = hourAngle(h, phi, dec)
        val a = approxTransit(w, lw, n)
        return solarTransitJ(a, m, l)
    }

    /** Sunrise/sunset (standard -0.833°) and civil dawn/dusk (sun at -6°) for that calendar day. */
    fun getTimes(dateMillis: Long, lat: Double, lon: Double): SunTimes {
        val lw = RAD * -lon
        val phi = RAD * lat
        val d = toDays(dateMillis)
        val n = julianCycle(d, lw)
        val ds = approxTransit(0.0, lw, n)
        val m = solarMeanAnomaly(ds)
        val l = eclipticLongitude(m)
        val dec = declination(l)
        val jNoon = solarTransitJ(ds, m, l)

        val jSet = getSetJ(RAD * -0.833, lw, phi, dec, n, m, l)
        val jRise = jNoon - (jSet - jNoon)

        val jDusk = getSetJ(RAD * -6.0, lw, phi, dec, n, m, l)
        val jDawn = jNoon - (jDusk - jNoon)

        return SunTimes(
            dawn = fromJulian(jDawn),
            sunrise = fromJulian(jRise),
            solarNoon = fromJulian(jNoon),
            sunset = fromJulian(jSet),
            dusk = fromJulian(jDusk)
        )
    }

    private val CARDINALS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    fun bearingToCardinal(bearingDeg: Double): String {
        val index = (((bearingDeg % 360.0) + 360.0) % 360.0 / 45.0 + 0.5).toInt() % 8
        return CARDINALS[index]
    }
}
