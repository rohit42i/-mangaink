package com.example.mangainker.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

data class StudioElement(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val text: String = ""
)

data class StudioProject(
    val id: String,
    val title: String,
    val pageFile: String,
    val pageWidth: Int,
    val pageHeight: Int,
    val updatedAt: Long,
    val elements: List<StudioElement> = emptyList()
)

data class LoadedStudioProject(
    val project: StudioProject,
    val page: Bitmap
)

/**
 * Local project storage and page compositor. Project JSON and source pages remain
 * in this app's private files directory; no cloud upload is used.
 */
class StudioProjectStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, "manga-ink-projects")

    fun listProjects(): List<StudioProject> =
        root.listFiles()?.mapNotNull { directory ->
            readProject(File(directory, "project.json"))
        }?.sortedByDescending { it.updatedAt }.orEmpty()

    fun createProject(page: Bitmap, title: String): StudioProject {
        require(!page.isRecycled) { "The imported page is no longer available. Import it again." }
        val safeTitle = title.trim().ifBlank { "Untitled manga page" }.take(60)
        val id = UUID.randomUUID().toString()
        val directory = File(root, id)
        check(directory.mkdirs() || directory.isDirectory) { "Could not create the project folder." }
        val image = File(directory, "page.png")
        image.outputStream().buffered().use { output ->
            check(page.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "The source page could not be saved."
            }
        }
        val project = StudioProject(
            id = id,
            title = safeTitle,
            pageFile = "page.png",
            pageWidth = page.width,
            pageHeight = page.height,
            updatedAt = System.currentTimeMillis()
        )
        saveProject(project)
        return project
    }

    fun loadProject(id: String): LoadedStudioProject? {
        val directory = File(root, id)
        val project = readProject(File(directory, "project.json")) ?: return null
        val image = File(directory, project.pageFile)
        if (!image.isFile) return null
        val bitmap = BitmapFactory.decodeFile(image.absolutePath) ?: return null
        return LoadedStudioProject(project, bitmap)
    }

    fun saveProject(project: StudioProject) {
        val directory = File(root, project.id)
        require(directory.isDirectory) { "This project folder no longer exists." }
        val json = JSONObject().apply {
            put("id", project.id)
            put("title", project.title)
            put("pageFile", project.pageFile)
            put("pageWidth", project.pageWidth)
            put("pageHeight", project.pageHeight)
            put("updatedAt", System.currentTimeMillis())
            put("elements", JSONArray().apply {
                project.elements.forEach { element ->
                    put(JSONObject().apply {
                        put("id", element.id)
                        put("type", element.type)
                        put("left", element.left.toDouble())
                        put("top", element.top.toDouble())
                        put("right", element.right.toDouble())
                        put("bottom", element.bottom.toDouble())
                        put("text", element.text)
                    })
                }
            })
        }
        val temporary = File(directory, "project.json.tmp")
        temporary.writeText(json.toString(2))
        val target = File(directory, "project.json")
        if (target.exists()) target.delete()
        check(temporary.renameTo(target)) { "Could not save the project changes." }
    }

    fun deleteProject(id: String) {
        val directory = File(root, id)
        if (directory.exists() && !directory.deleteRecursively()) {
            throw IllegalStateException("Could not delete this project.")
        }
    }

    /** Returns a rendered copy. The source bitmap and saved project remain unchanged. */
    fun renderPage(source: Bitmap, project: StudioProject): Bitmap {
        require(!source.isRecycled) { "The project page is no longer available." }
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
            ?: throw IllegalStateException("Could not create the export image.")
        val canvas = Canvas(output)
        val width = output.width.toFloat()
        val height = output.height.toFloat()
        project.elements.forEach { element ->
            val left = element.left.coerceIn(0f, 1f) * width
            val top = element.top.coerceIn(0f, 1f) * height
            val right = element.right.coerceIn(0f, 1f) * width
            val bottom = element.bottom.coerceIn(0f, 1f) * height
            if (right - left < 2f || bottom - top < 2f) return@forEach
            val rect = RectF(left, top, right, bottom)
            when (element.type) {
                "panel" -> drawPanel(canvas, rect)
                "tone" -> drawTone(canvas, rect)
                "gradient" -> drawGradient(canvas, rect)
                "speed" -> drawSpeedLines(canvas, rect)
                "bubble" -> drawBubble(canvas, rect, element.text)
                "sfx" -> drawSfx(canvas, rect, element.text)
            }
        }
        return output
    }

    private fun drawPanel(canvas: Canvas, rect: RectF) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = max(2f, min(rect.width(), rect.height()) * 0.012f)
        }
        canvas.drawRect(rect, paint)
    }

    private fun drawTone(canvas: Canvas, rect: RectF) {
        val spacing = max(7f, min(rect.width(), rect.height()) / 22f)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.FILL
        }
        val save = canvas.save()
        canvas.clipRect(rect)
        var y = rect.top + spacing / 2f
        while (y < rect.bottom) {
            var x = rect.left + spacing / 2f
            while (x < rect.right) {
                canvas.drawCircle(x, y, spacing * 0.11f, dot)
                x += spacing
            }
            y += spacing
        }
        canvas.restoreToCount(save)
    }

    private fun drawGradient(canvas: Canvas, rect: RectF) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                intArrayOf(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.argb(150, 0, 0, 0)
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(rect, paint)
    }

    private fun drawSpeedLines(canvas: Canvas, rect: RectF) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = max(1.2f, min(rect.width(), rect.height()) * 0.006f)
        }
        val save = canvas.save()
        canvas.clipRect(rect)
        val spacing = max(7f, rect.height() / 18f)
        var y = rect.top - rect.width() * 0.35f
        while (y < rect.bottom) {
            canvas.drawLine(rect.left, y, rect.right, y + rect.width() * 0.35f, paint)
            y += spacing
        }
        canvas.restoreToCount(save)
    }

    private fun drawBubble(canvas: Canvas, rect: RectF, text: String) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = max(2f, min(rect.width(), rect.height()) * 0.018f)
        }
        val radius = min(rect.width(), rect.height()) * 0.22f
        val tail = Path().apply {
            moveTo(rect.centerX() - radius * 0.42f, rect.bottom - stroke.strokeWidth)
            lineTo(rect.centerX(), rect.bottom + min(rect.height() * 0.16f, 24f))
            lineTo(rect.centerX() + radius * 0.42f, rect.bottom - stroke.strokeWidth)
            close()
        }
        canvas.drawPath(tail, fill)
        canvas.drawPath(tail, stroke)
        canvas.drawRoundRect(rect, radius, radius, fill)
        canvas.drawRoundRect(rect, radius, radius, stroke)

        val label = text.ifBlank { "Dialogue" }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = min(rect.height() * 0.25f, rect.width() / max(4f, label.length * 0.58f))
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val baseline = rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(label, rect.centerX(), baseline, textPaint)
    }

    private fun drawSfx(canvas: Canvas, rect: RectF, text: String) {
        val label = text.ifBlank { "SFX!" }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
            textSize = min(rect.height() * 0.72f, rect.width() / max(3f, label.length * 0.64f))
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeWidth = max(2f, textSize * 0.10f)
            color = android.graphics.Color.WHITE
        }
        val baseline = rect.centerY() - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(label, rect.centerX(), baseline, paint)
        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.BLACK
        canvas.drawText(label, rect.centerX(), baseline, paint)
    }

    private fun readProject(file: File): StudioProject? = runCatching {
        if (!file.isFile) return@runCatching null
        val json = JSONObject(file.readText())
        val elementsJson = json.optJSONArray("elements") ?: JSONArray()
        val elements = (0 until elementsJson.length()).mapNotNull { index ->
            val item = elementsJson.optJSONObject(index) ?: return@mapNotNull null
            StudioElement(
                id = item.optString("id"),
                type = item.optString("type"),
                left = item.optDouble("left").toFloat(),
                top = item.optDouble("top").toFloat(),
                right = item.optDouble("right").toFloat(),
                bottom = item.optDouble("bottom").toFloat(),
                text = item.optString("text")
            )
        }
        StudioProject(
            id = json.getString("id"),
            title = json.optString("title", "Untitled project"),
            pageFile = json.optString("pageFile", "page.png"),
            pageWidth = json.optInt("pageWidth"),
            pageHeight = json.optInt("pageHeight"),
            updatedAt = json.optLong("updatedAt"),
            elements = elements
        )
    }.getOrNull()
}
