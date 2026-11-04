package com.rohit.mangainker.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import androidx.core.graphics.withClip
import com.rohit.mangainker.core.InkPath

class PagePreviewView(context: Context) : View(context) {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(24, 29, 37) }
    private val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 190, 220)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        alpha = 205
    }
    private var bitmap: Bitmap? = null
    private val destination = RectF()
    private val path = Path()
    private var paths: List<InkPath> = emptyList()

    fun setPreview(source: Bitmap?, inkPaths: List<InkPath>) {
        bitmap = source
        paths = inkPaths
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 18f, 18f, backgroundPaint)
        val source = bitmap ?: return
        val maxWidth = width - 28f
        val maxHeight = height - 28f
        val scale = minOf(maxWidth / source.width, maxHeight / source.height)
        if (scale <= 0f) return
        val targetWidth = source.width * scale
        val targetHeight = source.height * scale
        val left = (width - targetWidth) / 2f
        val top = (height - targetHeight) / 2f
        destination.set(left, top, left + targetWidth, top + targetHeight)
        canvas.drawRoundRect(destination, 3f, 3f, paperPaint)
        canvas.drawBitmap(source, null, destination, imagePaint)
        if (paths.isNotEmpty()) {
            canvas.withClip(destination) {
                paths.forEach { inkPath ->
                    if (inkPath.points.size < 2) return@forEach
                    path.reset()
                    val first = inkPath.points.first()
                    path.moveTo(left + first.x * scale, top + first.y * scale)
                    for (pointIndex in 1 until inkPath.points.size) {
                        val point = inkPath.points[pointIndex]
                        path.lineTo(left + point.x * scale, top + point.y * scale)
                    }
                    drawPath(path, linePaint)
                }
            }
        }
    }
}
