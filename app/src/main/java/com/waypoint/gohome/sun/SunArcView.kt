package com.waypoint.gohome.sun

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Horizon-relative "sun arc" widget: a horizon line with the sun drawn above or below it.
 * Horizontal position tracks progress through the solar day (solar noon = center, a full 24h
 * spans the view width so the marker keeps drifting through the night off past the horizon
 * ends). Vertical position tracks the sun's actual altitude relative to the horizon line.
 * Position changes animate instead of jumping, so periodic refreshes read as the sun visibly
 * creeping across the sky rather than teleporting.
 */
class SunArcView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var targetXFrac = 0.5f
    private var targetYFrac = 0.5f
    private var drawnXFrac = 0.5f
    private var drawnYFrac = 0.5f
    private var altitudeDeg = 0.0
    private var hasData = false

    private var animator: ValueAnimator? = null

    private val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8D6E63")
        strokeWidth = 4f
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40000000")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    private val sunDayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFB300") }
    private val sunNightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#78909C") }
    private val sunGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#33FFB300") }

    /**
     * [now] and [solarNoon] place the sun horizontally; [altitudeDeg] places it vertically,
     * relative to the horizon line (0 degrees = on the line, negative = below it).
     */
    fun update(now: Long, solarNoon: Long, altitudeDeg: Double) {
        val dayFraction = (now - solarNoon) / DAY_MS
        val newTargetX = (0.5f + dayFraction.toFloat()).coerceIn(0f, 1f)
        val norm = ((altitudeDeg - MIN_ALT) / (MAX_ALT - MIN_ALT)).coerceIn(-0.05, 1.05)
        val newTargetY = (1.0 - norm).toFloat()
        this.altitudeDeg = altitudeDeg

        val startX = drawnXFrac
        val startY = drawnYFrac
        val firstUpdate = !hasData
        hasData = true
        targetXFrac = newTargetX
        targetYFrac = newTargetY

        animator?.cancel()
        if (firstUpdate) {
            drawnXFrac = targetXFrac
            drawnYFrac = targetYFrac
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                drawnXFrac = startX + (targetXFrac - startX) * f
                drawnYFrac = startY + (targetYFrac - startY) * f
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val horizonY = h * 0.72f

        val skyTop = if (altitudeDeg > 0) Color.parseColor("#33FFF3CD") else Color.parseColor("#1A263238")
        val skyPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, horizonY, skyTop, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, horizonY, skyPaint)

        val arcTop = h * 0.08f
        canvas.drawArc(0f, arcTop, w, horizonY * 2f - arcTop, 180f, 180f, false, arcPaint)
        canvas.drawLine(0f, horizonY, w, horizonY, horizonPaint)

        if (!hasData) return

        val x = drawnXFrac * w
        val y = drawnYFrac * h
        val sunPaint = if (altitudeDeg > 0) sunDayPaint else sunNightPaint
        canvas.drawCircle(x, y, 26f, sunGlowPaint)
        canvas.drawCircle(x, y, 16f, sunPaint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val DAY_MS = 1000.0 * 60 * 60 * 24
        private const val MIN_ALT = -20.0
        private const val MAX_ALT = 80.0
    }
}
