package com.waypoint.gohome.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.waypoint.gohome.R

/**
 * A flat, square-cornered container with small corner brackets drawn at each corner — the app's
 * "blueprint/technical schematic" motif. Drop-in replacement for a plain card container; give it
 * a background color and padding like any FrameLayout.
 */
class BlueprintCard(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    private val density = resources.displayMetrics.density
    private val bracketLength = 10f * density
    private val inset = 1.5f * density

    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = ContextCompat.getColor(context, R.color.outline)
    }

    /** Corner bracket color; defaults to the outline color, override e.g. to highlight a selected card. */
    var bracketColor: Int
        get() = bracketPaint.color
        set(value) {
            bracketPaint.color = value
            invalidate()
        }

    init {
        setWillNotDraw(false)
        context.theme.obtainStyledAttributes(attrs, R.styleable.BlueprintCard, 0, 0).apply {
            try {
                bracketPaint.color = getColor(R.styleable.BlueprintCard_bracketColor, bracketPaint.color)
            } finally {
                recycle()
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // top-left
        canvas.drawLine(inset, inset, inset + bracketLength, inset, bracketPaint)
        canvas.drawLine(inset, inset, inset, inset + bracketLength, bracketPaint)
        // top-right
        canvas.drawLine(w - inset, inset, w - inset - bracketLength, inset, bracketPaint)
        canvas.drawLine(w - inset, inset, w - inset, inset + bracketLength, bracketPaint)
        // bottom-left
        canvas.drawLine(inset, h - inset, inset + bracketLength, h - inset, bracketPaint)
        canvas.drawLine(inset, h - inset, inset, h - inset - bracketLength, bracketPaint)
        // bottom-right
        canvas.drawLine(w - inset, h - inset, w - inset - bracketLength, h - inset, bracketPaint)
        canvas.drawLine(w - inset, h - inset, w - inset, h - inset - bracketLength, bracketPaint)
    }
}
