package com.waypoint.gohome.ar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Overlay drawn on top of the live camera preview: positions a marker for the target waypoint
 * horizontally according to how far it is, in degrees, from where the phone is currently
 * pointing (device heading vs. compass bearing to the target), mapped across the camera's
 * approximate field of view. When the target is outside that field of view, an edge arrow shows
 * which way to turn instead.
 */
class WaypointArView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    /** Degrees: positive = target is to the right of where the camera points, negative = left. */
    var relativeBearingDeg: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    var distanceText: String = ""
        set(value) {
            field = value
            invalidate()
        }

    var hasFix: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** Approximate horizontal field of view of a typical phone's main rear camera. */
    var horizontalFovDeg: Float = 60f

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFC107")
        style = Paint.Style.FILL
    }
    private val markerOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        setShadowLayer(6f, 0f, 0f, Color.BLACK)
    }
    private val edgeArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFC107")
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!hasFix) return

        val markerSize = min(width, height) * 0.06f
        textPaint.textSize = markerSize * 0.7f

        val halfFov = horizontalFovDeg / 2f
        if (relativeBearingDeg in -halfFov..halfFov) {
            val fraction = relativeBearingDeg / halfFov // -1..1
            val cx = width / 2f + fraction * (width / 2f)
            val cy = height * 0.42f

            canvas.drawCircle(cx, cy, markerSize, markerPaint)
            canvas.drawCircle(cx, cy, markerSize, markerOutlinePaint)
            canvas.drawText(distanceText, cx, cy + markerSize + textPaint.textSize, textPaint)
        } else {
            val onRight = relativeBearingDeg > 0
            val cy = height * 0.42f
            val cx = if (onRight) width - markerSize * 1.5f else markerSize * 1.5f
            val path = Path().apply {
                if (onRight) {
                    moveTo(cx - markerSize, cy - markerSize)
                    lineTo(cx + markerSize, cy)
                    lineTo(cx - markerSize, cy + markerSize)
                } else {
                    moveTo(cx + markerSize, cy - markerSize)
                    lineTo(cx - markerSize, cy)
                    lineTo(cx + markerSize, cy + markerSize)
                }
                close()
            }
            canvas.drawPath(path, edgeArrowPaint)
            canvas.drawText(distanceText, cx, cy + markerSize * 2.2f, textPaint)
        }
    }
}
