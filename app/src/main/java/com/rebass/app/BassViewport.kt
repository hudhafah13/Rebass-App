package com.rebass.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Simple before / after bass frequency viewport.
 * Shows original detected peaks vs the new subharmonic frequencies.
 */
class BassViewport @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var originalPeaks: List<BassPeak> = emptyList()
    private var newHz: List<Float> = emptyList()
    private var targetHz: Float = 25f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#121212")
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A2A2A")
        strokeWidth = 1f
    }
    private val beforePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5252")
        style = Paint.Style.FILL
    }
    private val afterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAAAAA")
        textSize = 28f
        textAlign = Paint.Align.LEFT
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#666666")
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 26f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    fun update(peaks: List<BassPeak>, newFrequencies: List<Float>, target: Float) {
        originalPeaks = peaks
        newHz = newFrequencies
        targetHz = target
        invalidate()
    }

    fun clear() {
        originalPeaks = emptyList()
        newHz = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w < 10 || h < 10) return

        // background
        canvas.drawRoundRect(0f, 0f, w, h, 24f, 24f, bgPaint)

        val pad = 28f
        val midY = h / 2f

        // titles
        canvas.drawText("BEFORE (original bass)", pad, pad + 22f, titlePaint.apply { color = Color.parseColor("#FF8A80") })
        canvas.drawText("AFTER (new sub)", pad, midY + 22f, titlePaint.apply { color = Color.parseColor("#A5D6A7") })

        // divider
        canvas.drawLine(pad, midY, w - pad, midY, gridPaint)

        val maxHz = 80f
        val barAreaTop1 = pad + 40f
        val barAreaBot1 = midY - 16f
        val barAreaTop2 = midY + 40f
        val barAreaBot2 = h - pad - 8f

        // frequency labels
        for (hz in listOf(20, 30, 40, 50, 60, 75)) {
            val x = pad + (hz / maxHz) * (w - 2 * pad)
            canvas.drawLine(x, barAreaTop1, x, barAreaBot1, gridPaint)
            canvas.drawLine(x, barAreaTop2, x, barAreaBot2, gridPaint)
            canvas.drawText("${hz}", x, h - 6f, labelPaint)
        }

        // BEFORE bars (original peaks)
        if (originalPeaks.isEmpty()) {
            canvas.drawText("No peaks detected yet", pad, (barAreaTop1 + barAreaBot1) / 2f, textPaint)
        } else {
            val maxMag = originalPeaks.maxOf { it.magnitude }.coerceAtLeast(0.01f)
            for (p in originalPeaks.take(8)) {
                val x = pad + (p.hz / maxHz).coerceIn(0f, 1f) * (w - 2 * pad)
                val barH = ((p.magnitude / maxMag) * (barAreaBot1 - barAreaTop1 - 8f)).coerceAtLeast(6f)
                canvas.drawRoundRect(
                    x - 8f, barAreaBot1 - barH,
                    x + 8f, barAreaBot1,
                    6f, 6f, beforePaint
                )
            }
        }

        // AFTER bars (new subs)
        val displayHz = if (newHz.isNotEmpty()) newHz else {
            if (targetHz > 0f) listOf(targetHz) else emptyList()
        }
        if (displayHz.isEmpty()) {
            canvas.drawText("Process a track to see new sub", pad, (barAreaTop2 + barAreaBot2) / 2f, textPaint)
        } else {
            for (hz in displayHz) {
                val x = pad + (hz / maxHz).coerceIn(0f, 1f) * (w - 2 * pad)
                val barH = (barAreaBot2 - barAreaTop2 - 8f) * 0.85f
                canvas.drawRoundRect(
                    x - 10f, barAreaBot2 - barH,
                    x + 10f, barAreaBot2,
                    8f, 8f, afterPaint
                )
                canvas.drawText("${hz.toInt()}Hz", x, barAreaTop2 + 18f, labelPaint)
            }
        }
    }
}
