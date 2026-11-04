package com.rohit.mangainker.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Local page compositor for panel borders, screentones, lettering and sound effects. */
class MangaStudioView(context: Context) : View(context) {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(22, 27, 35) }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val tonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.FILL }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val bubbleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val effectFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
        style = Paint.Style.FILL
    }
    private val effectOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val pageRect = RectF()
    private val toneClipRect = RectF()
    private val panelRect = RectF()
    private var source: Bitmap? = null
    private var settings = StudioSettings()
    private var pendingText: String? = null
    private var pendingType = LetteringType.SPEECH
    private var pageScale = 1f
    private var pageLeft = 0f
    private var pageTop = 0f
    private var activeDragIndex = -1
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    fun setPage(bitmap: Bitmap?) {
        source = bitmap
        invalidate()
    }

    fun setSettings(next: StudioSettings) {
        settings = next
        invalidate()
    }

    fun currentSettings(): StudioSettings = settings

    fun setPanelPreset(preset: PanelPreset) {
        settings = settings.copy(panelPreset = preset)
        invalidate()
    }

    fun setGutter(value: Int) {
        settings = settings.copy(gutterFraction = value.coerceIn(0, 60) / 1000f)
        invalidate()
    }

    fun setBorderWidth(value: Int) {
        settings = settings.copy(borderWidth = value.coerceIn(1, 24).toFloat())
        invalidate()
    }

    fun setTone(pattern: TonePattern?, density: Int, opacity: Int, panelIndex: Int) {
        settings = settings.copy(
            tonePattern = pattern,
            toneDensity = density.coerceIn(6, 64),
            toneOpacity = opacity.coerceIn(0, 60),
            tonePanelIndex = panelIndex
        )
        invalidate()
    }

    fun setPendingLettering(text: String, type: LetteringType) {
        pendingText = text.trim().take(180).ifEmpty { null }
        pendingType = type
        invalidate()
    }

    fun removeLastLettering(): Boolean {
        if (settings.lettering.isEmpty()) return false
        settings = settings.copy(lettering = settings.lettering.dropLast(1))
        invalidate()
        return true
    }

    fun clearLettering() {
        settings = settings.copy(lettering = emptyList())
        invalidate()
    }

    fun exportBitmap(): Bitmap? {
        val page = source ?: return null
        if (page.isRecycled || page.width <= 0 || page.height <= 0) return null
        val output = createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        drawDocument(canvas, page.width.toFloat(), page.height.toFloat(), page)
        return output
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        val page = source ?: return
        if (page.isRecycled || width <= 0 || height <= 0) return
        val scale = min((width - dp(28)) / page.width.toFloat(), (height - dp(28)) / page.height.toFloat())
        if (scale <= 0f) return
        pageScale = scale
        val pageWidth = page.width * scale
        val pageHeight = page.height * scale
        pageLeft = (width - pageWidth) / 2f
        pageTop = (height - pageHeight) / 2f
        pageRect.set(pageLeft, pageTop, pageLeft + pageWidth, pageTop + pageHeight)
        canvas.drawBitmap(page, null, pageRect, bitmapPaint)
        canvas.withTranslation(pageLeft, pageTop) {
            scale(scale, scale)
            drawOverlays(this, page.width.toFloat(), page.height.toFloat())
        }
    }

    private fun drawDocument(canvas: Canvas, pageWidth: Float, pageHeight: Float, page: Bitmap) {
        pageRect.set(0f, 0f, pageWidth, pageHeight)
        canvas.drawBitmap(page, null, pageRect, bitmapPaint)
        drawOverlays(canvas, pageWidth, pageHeight)
    }

    private fun drawOverlays(canvas: Canvas, pageWidth: Float, pageHeight: Float) {
        drawTones(canvas, pageWidth, pageHeight)
        drawPanels(canvas, pageWidth, pageHeight)
        settings.lettering.forEach { drawLettering(canvas, pageWidth, pageHeight, it) }
    }

    private fun drawPanels(canvas: Canvas, pageWidth: Float, pageHeight: Float) {
        panelPaint.strokeWidth = settings.borderWidth
        PanelLayout.rectangles(settings.panelPreset, settings.gutterFraction).forEach { panel ->
            panelRect.set(panel.left * pageWidth, panel.top * pageHeight, panel.right * pageWidth, panel.bottom * pageHeight)
            canvas.drawRect(panelRect, panelPaint)
        }
    }

    private fun drawTones(canvas: Canvas, pageWidth: Float, pageHeight: Float) {
        val pattern = settings.tonePattern ?: return
        val regions = PanelLayout.tonePanelRectangles(settings)
        if (regions.isEmpty() || settings.toneOpacity <= 0) return
        val step = (70f - settings.toneDensity * 0.8f).coerceAtLeast(12f)
        tonePaint.alpha = (settings.toneOpacity * 255 / 100).coerceIn(0, 255)
        regions.forEach { region ->
            toneClipRect.set(region.left * pageWidth, region.top * pageHeight, region.right * pageWidth, region.bottom * pageHeight)
            canvas.withClip(toneClipRect) {
                var y = toneClipRect.top + step / 2f
                while (y < toneClipRect.bottom) {
                    var x = toneClipRect.left + step / 2f
                    while (x < toneClipRect.right) {
                        when (pattern) {
                            TonePattern.DOTS -> drawCircle(x, y, (step * 0.075f).coerceAtLeast(0.8f), tonePaint)
                            TonePattern.DIAGONAL -> drawLine(x - step / 2f, y + step / 2f, x + step / 2f, y - step / 2f, tonePaint.apply { strokeWidth = 1.1f })
                            TonePattern.CROSSHATCH -> {
                                tonePaint.strokeWidth = 0.9f
                                drawLine(x - step / 2f, y + step / 2f, x + step / 2f, y - step / 2f, tonePaint)
                                drawLine(x - step / 2f, y - step / 2f, x + step / 2f, y + step / 2f, tonePaint)
                            }
                            TonePattern.HORIZONTAL -> {
                                tonePaint.strokeWidth = 1.1f
                                drawLine(toneClipRect.left, y, toneClipRect.right, y, tonePaint)
                            }
                            TonePattern.VERTICAL -> {
                                tonePaint.strokeWidth = 1.1f
                                drawLine(x, toneClipRect.top, x, toneClipRect.bottom, tonePaint)
                            }
                            TonePattern.SPEED_LINES -> {
                                tonePaint.strokeWidth = 0.9f
                                val offset = (x - toneClipRect.centerX()) * 0.22f
                                drawLine(x - step * 0.45f, y + step * 0.45f + offset, x + step * 0.45f, y - step * 0.45f + offset, tonePaint)
                            }
                        }
                        x += step
                    }
                    y += step
                }
            }
        }
        tonePaint.alpha = 255
    }

    private fun drawLettering(canvas: Canvas, pageWidth: Float, pageHeight: Float, item: LetteringElement) {
        val centerX = item.centerX * pageWidth
        val centerY = item.centerY * pageHeight
        val maxTextWidth = pageWidth * if (item.type == LetteringType.SOUND_EFFECT) 0.58f else 0.34f
        val fontSize = (pageWidth * 0.027f * item.scale).coerceIn(18f, 58f)
        textPaint.textSize = fontSize
        effectFill.textSize = fontSize * 1.55f
        effectOutline.textSize = effectFill.textSize
        val lines = wrap(item.text, textPaint, maxTextWidth)
        val lineHeight = fontSize * 1.18f
        val contentWidth = max(50f, lines.maxOfOrNull { textPaint.measureText(it) } ?: 50f)
        val contentHeight = lineHeight * lines.size
        val paddingX = fontSize * 0.8f
        val paddingY = fontSize * 0.55f
        val bubbleWidth = contentWidth + paddingX * 2f
        val bubbleHeight = contentHeight + paddingY * 2f

        canvas.withRotation(item.rotationDegrees, centerX, centerY) {
        when (item.type) {
            LetteringType.SPEECH -> {
                panelRect.set(centerX - bubbleWidth / 2f, centerY - bubbleHeight / 2f, centerX + bubbleWidth / 2f, centerY + bubbleHeight / 2f)
                val tail = Path().apply {
                    moveTo(centerX - bubbleWidth * 0.2f, panelRect.bottom - 2f)
                    lineTo(centerX - bubbleWidth * 0.08f, panelRect.bottom + fontSize * 0.7f)
                    lineTo(centerX + bubbleWidth * 0.02f, panelRect.bottom - 2f)
                    close()
                }
                canvas.drawPath(tail, whitePaint)
                canvas.drawPath(tail, bubbleStroke)
                canvas.drawOval(panelRect, whitePaint)
                canvas.drawOval(panelRect, bubbleStroke)
            }
            LetteringType.THOUGHT -> {
                panelRect.set(centerX - bubbleWidth / 2f, centerY - bubbleHeight / 2f, centerX + bubbleWidth / 2f, centerY + bubbleHeight / 2f)
                canvas.drawOval(panelRect, whitePaint)
                canvas.drawOval(panelRect, bubbleStroke)
                canvas.drawCircle(centerX - bubbleWidth * 0.22f, panelRect.bottom + fontSize * 0.27f, fontSize * 0.13f, whitePaint)
                canvas.drawCircle(centerX - bubbleWidth * 0.22f, panelRect.bottom + fontSize * 0.27f, fontSize * 0.13f, bubbleStroke)
                canvas.drawCircle(centerX - bubbleWidth * 0.30f, panelRect.bottom + fontSize * 0.55f, fontSize * 0.07f, whitePaint)
                canvas.drawCircle(centerX - bubbleWidth * 0.30f, panelRect.bottom + fontSize * 0.55f, fontSize * 0.07f, bubbleStroke)
            }
            LetteringType.NARRATION -> {
                panelRect.set(centerX - bubbleWidth / 2f, centerY - bubbleHeight / 2f, centerX + bubbleWidth / 2f, centerY + bubbleHeight / 2f)
                canvas.drawRect(panelRect, whitePaint)
                canvas.drawRect(panelRect, bubbleStroke)
            }
            LetteringType.SOUND_EFFECT -> {
                effectOutline.strokeWidth = fontSize * 0.17f
                effectFill.color = Color.BLACK
                effectOutline.color = Color.WHITE
                val effectBaseline = centerY + contentHeight * 0.35f
                drawText(item.text.take(32), centerX, effectBaseline, effectOutline)
                drawText(item.text.take(32), centerX, effectBaseline, effectFill)
                return@withRotation
            }
        }
        val startY = centerY - contentHeight / 2f + fontSize * 0.82f
        lines.forEachIndexed { index, line ->
            drawText(line, centerX, startY + index * lineHeight, textPaint)
        }
        }
    }

    private fun wrap(value: String, paint: Paint, maxWidth: Float): List<String> {
        val words = value.replace('\n', ' ').split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return listOf("")
        val lines = ArrayList<String>()
        var line = ""
        words.forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                lines.add(line)
                line = word
            } else line = candidate
        }
        if (line.isNotEmpty()) lines.add(line)
        return lines.take(5)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val page = source ?: return false
        if (pageScale <= 0f) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                moved = false
                activeDragIndex = findLettering(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > dp(5) || abs(event.y - downY) > dp(5)) moved = true
                if (activeDragIndex >= 0 && moved) {
                    val x = ((event.x - pageLeft) / (page.width * pageScale)).coerceIn(0f, 1f)
                    val y = ((event.y - pageTop) / (page.height * pageScale)).coerceIn(0f, 1f)
                    val mutable = settings.lettering.toMutableList()
                    mutable[activeDragIndex] = mutable[activeDragIndex].copy(centerX = x, centerY = y)
                    settings = settings.copy(lettering = mutable)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!moved && activeDragIndex < 0) {
                    val isInsidePage = event.x in pageLeft..(pageLeft + page.width * pageScale) &&
                        event.y in pageTop..(pageTop + page.height * pageScale)
                    val value = pendingText
                    if (isInsidePage && !value.isNullOrBlank()) {
                        val x = ((event.x - pageLeft) / (page.width * pageScale)).coerceIn(0f, 1f)
                        val y = ((event.y - pageTop) / (page.height * pageScale)).coerceIn(0f, 1f)
                        settings = settings.copy(lettering = settings.lettering + LetteringElement(value, pendingType, x, y))
                        pendingText = null
                        invalidate()
                        performClick()
                    }
                }
                activeDragIndex = -1
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                activeDragIndex = -1
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun findLettering(x: Float, y: Float): Int {
        if (pageScale <= 0f) return -1
        val page = source ?: return -1
        val normalizedX = (x - pageLeft) / (page.width * pageScale)
        val normalizedY = (y - pageTop) / (page.height * pageScale)
        return settings.lettering.indexOfLast {
            val dx = (it.centerX - normalizedX) * page.width
            val dy = (it.centerY - normalizedY) * page.height
            sqrt(dx * dx + dy * dy) < page.width * 0.12f
        }
    }

    private fun dp(value: Int): Float = value * resources.displayMetrics.density
}
