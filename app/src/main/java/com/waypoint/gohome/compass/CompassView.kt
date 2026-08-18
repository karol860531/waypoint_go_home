package com.waypoint.gohome.compass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A classic fixed-dial compass: N/E/S/W stay put on screen, the needle rotates to track the
 * device's live heading (red tip = north, gray tail = south) — just like a handheld compass.
 */
class CompassView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    /** Device heading in degrees, 0 = facing true/magnetic north. */
    var heading: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DADFE2")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8A96A0")
        strokeWidth = 3f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5C6670")
        textAlign = Paint.Align.CENTER
    }
    private val northLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3E6B85")
        textAlign = Paint.Align.CENTER
    }
    private val needleNorthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3E6B85")
        style = Paint.Style.FILL
    }
    private val needleSouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8A96A0")
        style = Paint.Style.FILL
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B3A4D")
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f * 0.86f
        if (radius <= 0f) return

        labelPaint.textSize = radius * 0.16f
        northLabelPaint.textSize = radius * 0.18f

        canvas.drawCircle(cx, cy, radius, ringPaint)

        for (deg in 0 until 360 step 30) {
            val rad = Math.toRadians(deg - 90.0)
            val outerX = cx + radius * cos(rad).toFloat()
            val outerY = cy + radius * sin(rad).toFloat()
            val innerRadius = if (deg % 90 == 0) radius * 0.84f else radius * 0.92f
            val innerX = cx + innerRadius * cos(rad).toFloat()
            val innerY = cy + innerRadius * sin(rad).toFloat()
            canvas.drawLine(innerX, innerY, outerX, outerY, tickPaint)
        }

        val labelRadius = radius * 0.68f
        drawCardinal(canvas, "N", 0, cx, cy, labelRadius, northLabelPaint)
        drawCardinal(canvas, "E", 90, cx, cy, labelRadius, labelPaint)
        drawCardinal(canvas, "S", 180, cx, cy, labelRadius, labelPaint)
        drawCardinal(canvas, "W", 270, cx, cy, labelRadius, labelPaint)

        canvas.save()
        canvas.rotate(-heading, cx, cy)
        val needleLength = radius * 0.72f
        val needleWidth = radius * 0.08f
        canvas.drawPath(
            Path().apply {
                moveTo(cx, cy - needleLength)
                lineTo(cx - needleWidth, cy)
                lineTo(cx + needleWidth, cy)
                close()
            },
            needleNorthPaint
        )
        canvas.drawPath(
            Path().apply {
                moveTo(cx, cy + needleLength)
                lineTo(cx - needleWidth, cy)
                lineTo(cx + needleWidth, cy)
                close()
            },
            needleSouthPaint
        )
        canvas.restore()

        canvas.drawCircle(cx, cy, radius * 0.05f, centerPaint)
    }

    private fun drawCardinal(canvas: Canvas, label: String, deg: Int, cx: Float, cy: Float, labelRadius: Float, paint: Paint) {
        val rad = Math.toRadians(deg - 90.0)
        val x = cx + labelRadius * cos(rad).toFloat()
        val y = cy + labelRadius * sin(rad).toFloat()
        canvas.drawText(label, x, y + paint.textSize / 3f, paint)
    }
}
