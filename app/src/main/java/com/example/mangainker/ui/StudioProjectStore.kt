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
    val text: String = "",
    val rotationDegrees: Float = 0f,
    val textScale: Float = 1f,
    val tailOffsetX: Float = 0f,
    val effectDensity: Float = 1f
)

data class StudioPage(
    val id: String,
    val title: String,
    val pageFile: String,
    val pageWidth: Int,
    val pageHeight: Int,
    val elements: List<StudioElement> = emptyList()
)

data class StudioProject(
    val id: String,
    val title: String,
    val pageFile: String,
    val pageWidth: Int,
    val pageHeight: Int,
    val updatedAt: Long,
    val elements: List<StudioElement> = emptyList(),
    val pages: List<StudioPage> = emptyList(),
    val activePageId: String? = null
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
        val pageId = UUID.randomUUID().toString()
        val image = File(directory, "page-$pageId.png")
        writePageBitmap(page, image)
        val firstPage = StudioPage(
            id = pageId,
            title = "Page 1",
            pageFile = image.name,
            pageWidth = page.width,
            pageHeight = page.height
        )
        val project = projectForPage(
            StudioProject(
                id = id,
                title = safeTitle,
                pageFile = firstPage.pageFile,
                pageWidth = firstPage.pageWidth,
                pageHeight = firstPage.pageHeight,
                updatedAt = System.currentTimeMillis(),
                pages = listOf(firstPage),
                activePageId = pageId
            ),
            firstPage
        )
        saveProject(project)
        return project
    }

    fun loadProject(id: String): LoadedStudioProject? {
        val stored = readProject(File(root, id)) ?: return null
        return loadPage(stored, stored.activePageId ?: stored.pages.firstOrNull()?.id.orEmpty())
    }

    fun loadPage(project: StudioProject, pageId: String): LoadedStudioProject? {
        val page = pagesFor(project).firstOrNull { it.id == pageId } ?: return null
        val image = File(File(root, project.id), page.pageFile)
        if (!image.isFile) return null
        val bitmap = BitmapFactory.decodeFile(image.absolutePath) ?: return null
        return LoadedStudioProject(projectForPage(project, page), bitmap)
    }

    fun addPage(project: StudioProject, bitmap: Bitmap, title: String): StudioProject {
        require(!bitmap.isRecycled) { "The imported page is no longer available. Import it again." }
        val directory = File(root, project.id)
        require(directory.isDirectory) { "This project folder no longer exists." }
        val pages = pagesFor(project)
        val id = UUID.randomUUID().toString()
        val image = File(directory, "page-$id.png")
        writePageBitmap(bitmap, image)
        val page = StudioPage(
            id = id,
            title = title.trim().ifBlank { "Page ${pages.size + 1}" }.take(60),
            pageFile = image.name,
            pageWidth = bitmap.width,
            pageHeight = bitmap.height
        )
        val updated = projectForPage(project.copy(pages = pages + page), page)
        saveProject(updated)
        return updated
    }

    fun duplicateActivePage(project: StudioProject): StudioProject {
        val pages = pagesFor(project)
        val active = pages.firstOrNull { it.id == project.activePageId }
            ?: throw IllegalStateException("Choose a page to duplicate.")
        val directory = File(root, project.id)
        val source = File(directory, active.pageFile)
        require(source.isFile) { "The current page image is missing." }
        val duplicateId = UUID.randomUUID().toString()
        val target = File(directory, "page-$duplicateId.png")
        source.copyTo(target, overwrite = false)
        val duplicate = active.copy(
            id = duplicateId,
            title = active.title + " copy",
            pageFile = target.name,
            elements = active.elements.map { it.copy(id = UUID.randomUUID().toString()) }
        )
        val updated = projectForPage(project.copy(pages = pages + duplicate), duplicate)
        saveProject(updated)
        return updated
    }

    fun renameProject(project: StudioProject, title: String): StudioProject {
        val safeTitle = title.trim().ifBlank { "Untitled comic project" }.take(60)
        val updated = project.copy(title = safeTitle, updatedAt = System.currentTimeMillis())
        saveProject(updated)
        return updated
    }

    fun renamePage(project: StudioProject, pageId: String, title: String): StudioProject {
        val safeTitle = title.trim().ifBlank { "Untitled page" }.take(60)
        val pages = pagesFor(project).map { page ->
            if (page.id == pageId) page.copy(title = safeTitle) else page
        }
        require(pages.any { it.id == pageId }) { "The selected page no longer exists." }
        val updated = project.copy(pages = pages)
        saveProject(updated)
        return updated
    }

    fun movePage(project: StudioProject, pageId: String, offset: Int): StudioProject {
        val pages = pagesFor(project).toMutableList()
        val index = pages.indexOfFirst { it.id == pageId }
        if (index < 0) return project
        val target = (index + offset).coerceIn(0, pages.lastIndex)
        if (target == index) return project
        val moved = pages.removeAt(index)
        pages.add(target, moved)
        val updated = project.copy(pages = pages)
        saveProject(updated)
        return updated
    }

    fun removePage(project: StudioProject, pageId: String): StudioProject {
        val pages = pagesFor(project)
        require(pages.size > 1) { "A project must keep at least one page." }
        val index = pages.indexOfFirst { it.id == pageId }
        require(index >= 0) { "The selected page no longer exists." }
        val remaining = pages.toMutableList().apply { removeAt(index) }
        val nextActive = if (project.activePageId == pageId) {
            remaining[(index - 1).coerceAtLeast(0)]
        } else {
            remaining.first { it.id == project.activePageId }
        }
        val updated = projectForPage(project.copy(pages = remaining), nextActive)
        saveProject(updated)
        val deleted = File(File(root, project.id), pages[index].pageFile)
        if (deleted.exists()) deleted.delete()
        return updated
    }

    fun saveProject(project: StudioProject) {
        val directory = File(root, project.id)
        require(directory.isDirectory) { "This project folder no longer exists." }
        val normalizedPages = pagesFor(project).toMutableList()
        val activeIndex = normalizedPages.indexOfFirst { it.id == project.activePageId }
        val activePage = if (activeIndex >= 0) {
            normalizedPages[activeIndex].copy(
                pageFile = project.pageFile,
                pageWidth = project.pageWidth,
                pageHeight = project.pageHeight,
                elements = project.elements
            ).also { normalizedPages[activeIndex] = it }
        } else {
            val newPage = StudioPage(
                id = UUID.randomUUID().toString(),
                title = "Page 1",
                pageFile = project.pageFile,
                pageWidth = project.pageWidth,
                pageHeight = project.pageHeight,
                elements = project.elements
            )
            normalizedPages.add(newPage)
            newPage
        }

        val json = JSONObject().apply {
            put("id", project.id)
            put("title", project.title)
            put("pageFile", activePage.pageFile)
            put("pageWidth", activePage.pageWidth)
            put("pageHeight", activePage.pageHeight)
            put("updatedAt", System.currentTimeMillis())
            put("activePageId", activePage.id)
            put("elements", elementsToJson(activePage.elements))
            put("pages", JSONArray().apply {
                normalizedPages.forEach { page ->
                    put(JSONObject().apply {
                        put("id", page.id)
                        put("title", page.title)
                        put("pageFile", page.pageFile)
                        put("pageWidth", page.pageWidth)
                        put("pageHeight", page.pageHeight)
                        put("elements", elementsToJson(page.elements))
                    })
                }
            })
        }
        val temporary = File(directory, "project.json.tmp")
        temporary.writeText(json.toString(2))
        val target = File(directory, "project.json")
        if (target.exists() && !target.delete()) {
            temporary.delete()
            throw IllegalStateException("Could not replace the saved project file.")
        }
        check(temporary.renameTo(target)) {
            temporary.delete()
            "Could not save the project changes."
        }
    }

    fun deleteProject(id: String) {
        val directory = File(root, id)
        if (directory.exists() && !directory.deleteRecursively()) {
            throw IllegalStateException("Could not delete this project.")
        }
    }

    /** Writes one rendered PNG per project page into a streaming CBZ archive. */
    fun writeProjectCbz(project: StudioProject, output: java.io.OutputStream) {
        val pages = pagesFor(project)
        require(pages.isNotEmpty()) { "This project does not contain any pages." }
        java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(output)).use { archive ->
            pages.forEachIndexed { index, metadata ->
                val loaded = loadPage(project, metadata.id)
                    ?: throw IllegalStateException("Could not open Page ${index + 1} for export.")
                val rendered = try {
                    renderPage(loaded.page, loaded.project)
                } finally {
                    if (!loaded.page.isRecycled) loaded.page.recycle()
                }
                try {
                    val name = "Page-" + (index + 1).toString().padStart(3, '0') + ".png"
                    archive.putNextEntry(java.util.zip.ZipEntry(name))
                    check(rendered.compress(Bitmap.CompressFormat.PNG, 100, archive)) {
                        "Could not encode Page ${index + 1}."
                    }
                    archive.closeEntry()
                } finally {
                    if (!rendered.isRecycled) rendered.recycle()
                }
            }
            archive.finish()
        }
    }

    private fun pagesFor(project: StudioProject): List<StudioPage> {
        if (project.pages.isNotEmpty()) {
            // The root fields are a compatibility view of the active page.
            // Merge them back into the page collection before any mutation so
            // edits made by older UI call sites are never overwritten by stale
            // copies in the pages array.
            return project.pages.map { page ->
                if (page.id == project.activePageId) {
                    page.copy(
                        pageFile = project.pageFile,
                        pageWidth = project.pageWidth,
                        pageHeight = project.pageHeight,
                        elements = project.elements
                    )
                } else page
            }
        }
        val id = project.activePageId ?: "legacy-page-1"
        return listOf(
            StudioPage(
                id = id,
                title = "Page 1",
                pageFile = project.pageFile,
                pageWidth = project.pageWidth,
                pageHeight = project.pageHeight,
                elements = project.elements
            )
        )
    }

    private fun projectForPage(project: StudioProject, page: StudioPage): StudioProject =
        project.copy(
            pageFile = page.pageFile,
            pageWidth = page.pageWidth,
            pageHeight = page.pageHeight,
            elements = page.elements,
            pages = pagesFor(project),
            activePageId = page.id
        )

    private fun writePageBitmap(bitmap: Bitmap, file: File) {
        file.outputStream().buffered().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "The source page could not be saved."
            }
        }
    }

    private fun elementsToJson(elements: List<StudioElement>): JSONArray =
        JSONArray().apply {
            elements.forEach { element ->
                put(JSONObject().apply {
                    put("id", element.id)
                    put("type", element.type)
                    put("left", element.left.toDouble())
                    put("top", element.top.toDouble())
                    put("right", element.right.toDouble())
                    put("bottom", element.bottom.toDouble())
                    put("text", element.text)
                    put("rotationDegrees", element.rotationDegrees.toDouble())
                    put("textScale", element.textScale.toDouble())
                    put("tailOffsetX", element.tailOffsetX.toDouble())
                    put("effectDensity", element.effectDensity.toDouble())
                })
            }
        }

    private fun elementsFromJson(array: JSONArray): List<StudioElement> =
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            StudioElement(
                id = item.optString("id", UUID.randomUUID().toString()),
                type = item.optString("type"),
                left = item.optDouble("left").toFloat(),
                top = item.optDouble("top").toFloat(),
                right = item.optDouble("right").toFloat(),
                bottom = item.optDouble("bottom").toFloat(),
                text = item.optString("text"),
                rotationDegrees = item.optDouble("rotationDegrees", 0.0).toFloat(),
                textScale = item.optDouble("textScale", 1.0).toFloat().coerceIn(0.5f, 2f),
                tailOffsetX = item.optDouble("tailOffsetX", 0.0).toFloat().coerceIn(-0.45f, 0.45f),
                effectDensity = item.optDouble("effectDensity", 1.0).toFloat().coerceIn(0.35f, 2.5f)
            )
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
            val layerSave = canvas.save()
            if (element.rotationDegrees != 0f) {
                canvas.rotate(element.rotationDegrees, rect.centerX(), rect.centerY())
            }
            when (element.type) {
                "panel" -> drawPanel(canvas, rect)
                "tone" -> drawTone(canvas, rect, element.effectDensity)
                "gradient" -> drawGradient(canvas, rect)
                "speed" -> drawSpeedLines(canvas, rect, element.effectDensity)
                "bubble" -> drawBubble(canvas, rect, element.text, element.textScale, element.tailOffsetX)
                "sfx" -> drawSfx(canvas, rect, element.text, element.textScale)
            }
            canvas.restoreToCount(layerSave)
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

    private fun drawTone(canvas: Canvas, rect: RectF, density: Float) {
        val spacing = max(3f, min(rect.width(), rect.height()) / (22f * density.coerceIn(0.35f, 2.5f)))
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

    private fun drawSpeedLines(canvas: Canvas, rect: RectF, density: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = max(1.2f, min(rect.width(), rect.height()) * 0.006f)
        }
        val save = canvas.save()
        canvas.clipRect(rect)
        val spacing = max(3f, rect.height() / (18f * density.coerceIn(0.35f, 2.5f)))
        var y = rect.top - rect.width() * 0.35f
        while (y < rect.bottom) {
            canvas.drawLine(rect.left, y, rect.right, y + rect.width() * 0.35f, paint)
            y += spacing
        }
        canvas.restoreToCount(save)
    }

    private fun drawBubble(canvas: Canvas, rect: RectF, text: String, textScale: Float, tailOffsetX: Float) {
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
        val canvasBottom = (canvas.height.toFloat() - 2f).coerceAtLeast(rect.bottom)
        val tailTipY = (rect.bottom + min(rect.height() * 0.16f, 24f))
            .coerceAtMost(canvasBottom)
        val tail = Path().apply {
            moveTo(rect.centerX() - radius * 0.42f, rect.bottom - stroke.strokeWidth)
            lineTo((rect.centerX() + rect.width() * tailOffsetX.coerceIn(-0.45f, 0.45f)).coerceIn(0f, canvas.width.toFloat()), tailTipY)
            lineTo(rect.centerX() + radius * 0.42f, rect.bottom - stroke.strokeWidth)
            close()
        }
        canvas.drawPath(tail, fill)
        canvas.drawPath(tail, stroke)
        canvas.drawRoundRect(rect, radius, radius, fill)
        canvas.drawRoundRect(rect, radius, radius, stroke)

        val label = text.ifBlank { "Dialogue" }
        val innerWidth = (rect.width() * 0.82f).coerceAtLeast(4f)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = (min(rect.height() * 0.22f, rect.width() * 0.12f) * textScale.coerceIn(0.5f, 2f)).coerceAtLeast(7f)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        fun wrapAtCurrentSize(): List<String> {
            val output = ArrayList<String>()
            label.split('\n').forEach { paragraph ->
                var remaining = paragraph.trim()
                if (remaining.isEmpty()) {
                    output.add("")
                } else {
                    while (remaining.isNotEmpty()) {
                        val fit = textPaint.breakText(remaining, true, innerWidth, null)
                            .coerceIn(1, remaining.length)
                        var end = fit
                        if (fit < remaining.length) {
                            val space = remaining.lastIndexOf(' ', fit - 1)
                            if (space > 0) end = space
                        }
                        output.add(remaining.substring(0, end).trim())
                        remaining = remaining.substring(end).trimStart()
                    }
                }
            }
            return output.ifEmpty { listOf("Dialogue") }
        }

        var lines = wrapAtCurrentSize()
        val maxTextHeight = rect.height() * 0.70f
        while (lines.size > 1 &&
            (textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent) * lines.size > maxTextHeight &&
            textPaint.textSize > 7f
        ) {
            textPaint.textSize = (textPaint.textSize - 1f).coerceAtLeast(7f)
            lines = wrapAtCurrentSize()
        }

        val lineHeight = (textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent).coerceAtLeast(1f)
        val maxLines = (maxTextHeight / lineHeight).toInt().coerceAtLeast(1)
        if (lines.size > maxLines) {
            lines = lines.take(maxLines).toMutableList().also { visible ->
                var last = visible.last()
                while (last.isNotEmpty() && textPaint.measureText("$last…") > innerWidth) {
                    last = last.dropLast(1)
                }
                visible[visible.lastIndex] = last + "…"
            }
        }
        val blockHeight = lineHeight * lines.size
        val firstBaseline = rect.centerY() - blockHeight / 2f - textPaint.fontMetrics.ascent
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, rect.centerX(), firstBaseline + lineHeight * index, textPaint)
        }
    }

    private fun drawSfx(canvas: Canvas, rect: RectF, text: String, textScale: Float) {
        val label = text.ifBlank { "SFX!" }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
            textSize = (min(rect.height() * 0.72f, rect.width() / max(3f, label.length * 0.64f)) * textScale.coerceIn(0.5f, 2f)).coerceAtLeast(7f)
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
        val legacyElements = elementsFromJson(json.optJSONArray("elements") ?: JSONArray())
        val pagesJson = json.optJSONArray("pages") ?: JSONArray()
        val pages = (0 until pagesJson.length()).mapNotNull { index ->
            val item = pagesJson.optJSONObject(index) ?: return@mapNotNull null
            val pageFile = item.optString("pageFile").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            StudioPage(
                id = item.optString("id").ifBlank { "page-${index + 1}" },
                title = item.optString("title", "Page ${index + 1}"),
                pageFile = pageFile,
                pageWidth = item.optInt("pageWidth"),
                pageHeight = item.optInt("pageHeight"),
                elements = elementsFromJson(item.optJSONArray("elements") ?: JSONArray())
            )
        }
        val rootPageFile = json.optString("pageFile", "page.png")
        val normalizedPages = if (pages.isEmpty()) {
            listOf(
                StudioPage(
                    id = "legacy-page-1",
                    title = "Page 1",
                    pageFile = rootPageFile,
                    pageWidth = json.optInt("pageWidth"),
                    pageHeight = json.optInt("pageHeight"),
                    elements = legacyElements
                )
            )
        } else pages
        val activeId = json.optString("activePageId").takeIf { id ->
            id.isNotBlank() && normalizedPages.any { it.id == id }
        } ?: normalizedPages.first().id
        val selectedPage = normalizedPages.first { it.id == activeId }
        StudioProject(
            id = json.getString("id"),
            title = json.optString("title", "Untitled project"),
            pageFile = selectedPage.pageFile,
            pageWidth = selectedPage.pageWidth,
            pageHeight = selectedPage.pageHeight,
            updatedAt = json.optLong("updatedAt"),
            elements = selectedPage.elements,
            pages = normalizedPages,
            activePageId = selectedPage.id
        )
    }.getOrNull()
}
