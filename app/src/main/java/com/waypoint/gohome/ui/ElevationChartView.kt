package com.waypoint.gohome.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.waypoint.gohome.data.GeoUtils
import com.waypoint.gohome.data.TrackPoint
import kotlin.math.max
import kotlin.math.min

/** Draws a simple altitude-vs-distance line chart for a recorded track. */
class ElevationChartView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var distances = floatArrayOf()
    private var altitudes = floatArrayOf()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1976D2")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#331976D2")
        style = Paint.Style.FILL
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9E9E9E")
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#616161")
        textSize = 32f
    }

    fun setData(points: List<TrackPoint>) {
        val withAltitude = points.filter { it.altitude != null }
        if (withAltitude.size < 2) {
            distances = floatArrayOf()
            altitudes = floatArrayOf()
            invalidate()
            return
        }
        val distanceValues = FloatArray(withAltitude.size)
        val altitudeValues = FloatArray(withAltitude.size)
        var cumulative = 0f
        for (i in withAltitude.indices) {
            if (i > 0) {
                cumulative += GeoUtils.distanceMeters(
                    withAltitude[i - 1].latitude, withAltitude[i - 1].longitude,
                    withAltitude[i].latitude, withAltitude[i].longitude
                )
            }
            distanceValues[i] = cumulative
            altitudeValues[i] = withAltitude[i].altitude!!.toFloat()
        }
        distances = distanceValues
        altitudes = altitudeValues
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (distances.size < 2) return

        val paddingLeft = 90f
        val paddingBottom = 50f
        val paddingTop = 30f
        val chartWidth = width - paddingLeft - 20f
        val chartHeight = height - paddingTop - paddingBottom

        val minAlt = altitudes.min()
        val maxAlt = altitudes.max()
        val altRange = max(maxAlt - minAlt, 1f)
        val maxDistance = distances.max().let { if (it <= 0f) 1f else it }

        // axes
        canvas.drawLine(paddingLeft, paddingTop, paddingLeft, height - paddingBottom, axisPaint)
        canvas.drawLine(paddingLeft, height - paddingBottom, width.toFloat(), height - paddingBottom, axisPaint)

        val path = Path()
        val fillPath = Path()
        for (i in distances.indices) {
            val x = paddingLeft + (distances[i] / maxDistance) * chartWidth
            val y = paddingTop + (1f - (altitudes[i] - minAlt) / altRange) * chartHeight
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height - paddingBottom)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(paddingLeft + chartWidth, height - paddingBottom)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)

        canvas.drawText("${maxAlt.toInt()} m", 4f, paddingTop + 10f, textPaint)
        canvas.drawText("${minAlt.toInt()} m", 4f, height - paddingBottom, textPaint)
        canvas.drawText("${(maxDistance / 1000f).let { String.format("%.1f", it) }} km", width - 110f, height - 10f, textPaint)
    }
}
