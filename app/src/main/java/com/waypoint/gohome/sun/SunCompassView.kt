package com.waypoint.gohome.sun

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Rotating compass rose showing true N/E/S/W (relative to the device's live heading), the
 * sunrise/sunset directions, and the sun's current position: angle = azimuth, distance from
 * center = altitude (an overhead sun sits near the middle, a sun near the horizon sits near the
 * ring, a sun below the horizon is drawn dimmed at the ring).
 */
class SunCompassView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var deviceHeading: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private var sunriseAzimuth = 0f
    private var sunsetAzimuth = 0f
    private var sunAzimuth = 0f
    private var sunAltitude = 0f
    private var hasData = false

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BDBDBD")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#616161")
        textSize = 36f
        textAlign = Paint.Align.CENTER
    }
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D32")
        style = Paint.Style.FILL
    }
    private val sunrisePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFB300")
        style = Paint.Style.FILL
    }
    private val sunsetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E64A19")
        style = Paint.Style.FILL
    }
    private val sunPaintDay = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FDD835")
        style = Paint.Style.FILL
    }
    private val sunPaintNight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#90A4AE")
        style = Paint.Style.FILL
    }

    fun update(sunriseAz: Double, sunsetAz: Double, sunAz: Double, sunAlt: Double) {
        sunriseAzimuth = sunriseAz.toFloat()
        sunsetAzimuth = sunsetAz.toFloat()
        sunAzimuth = sunAz.toFloat()
        sunAltitude = sunAlt.toFloat()
        hasData = true
        invalidate()
    }

    private fun pointOnCircle(bearing: Float, radius: Float, cx: Float, cy: Float): FloatArray {
        val screenAngle = Math.toRadians((bearing - deviceHeading).toDouble())
        val x = cx + radius * sin(screenAngle).toFloat()
        val y = cy - radius * cos(screenAngle).toFloat()
        return floatArrayOf(x, y)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f * 0.72f

        canvas.drawCircle(cx, cy, radius, ringPaint)

        val cardinals = listOf(0f to "N", 90f to "E", 180f to "S", 270f to "W")
        for ((bearing, label) in cardinals) {
            val p = pointOnCircle(bearing, radius + 36f, cx, cy)
            canvas.drawText(label, p[0], p[1] + 12f, labelPaint)
        }

        // fixed pointer at the top: the direction the phone is currently facing
        val pointerY = cy - radius - 56f
        canvas.drawCircle(cx, pointerY, 9f, pointerPaint)

        if (!hasData) return

        val sunriseP = pointOnCircle(sunriseAzimuth, radius, cx, cy)
        canvas.drawCircle(sunriseP[0], sunriseP[1], 15f, sunrisePaint)

        val sunsetP = pointOnCircle(sunsetAzimuth, radius, cx, cy)
        canvas.drawCircle(sunsetP[0], sunsetP[1], 15f, sunsetPaint)

        val altClamped = sunAltitude.coerceIn(0f, 90f)
        val sunRadius = radius * (1f - altClamped / 90f)
        val sunP = pointOnCircle(sunAzimuth, sunRadius, cx, cy)
        canvas.drawCircle(sunP[0], sunP[1], 22f, if (sunAltitude > 0f) sunPaintDay else sunPaintNight)
    }
}
