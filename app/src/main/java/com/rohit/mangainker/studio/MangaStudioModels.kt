package com.rohit.mangainker.studio


/** Normalized page-space geometry so layouts remain independent of preview size. */
data class PageRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f)
        require(right > left && bottom > top)
    }
}

enum class PanelPreset(val label: String) {
    KEEP_ORIGINAL("Keep existing panels"),
    SINGLE("Single page"),
    TWO_ROWS("Two horizontal panels"),
    TWO_COLUMNS("Two vertical panels"),
    THREE_STRIPS("Three horizontal panels"),
    FOUR_GRID("Four-panel grid"),
    SIX_GRID("Six-panel grid"),
    SEVEN_PANELS("Seven-panel page")
}

enum class TonePattern(val label: String) {
    DOTS("Dots"),
    DIAGONAL("Diagonal lines"),
    CROSSHATCH("Crosshatch"),
    HORIZONTAL("Horizontal lines"),
    VERTICAL("Vertical lines"),
    SPEED_LINES("Speed lines")
}

enum class LetteringType(val label: String) {
    SPEECH("Speech bubble"),
    THOUGHT("Thought bubble"),
    NARRATION("Narration box"),
    SOUND_EFFECT("Sound effect")
}

data class LetteringElement(
    val text: String,
    val type: LetteringType,
    val centerX: Float,
    val centerY: Float,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f
) {
    init {
        require(centerX in 0f..1f && centerY in 0f..1f)
        require(scale in 0.5f..2.5f)
        require(text.length <= 180)
    }
}

data class StudioSettings(
    val panelPreset: PanelPreset = PanelPreset.KEEP_ORIGINAL,
    val gutterFraction: Float = 0.018f,
    val borderWidth: Float = 5f,
    val tonePattern: TonePattern? = null,
    val toneDensity: Int = 24,
    val toneOpacity: Int = 18,
    val tonePanelIndex: Int = -1,
    val lettering: List<LetteringElement> = emptyList()
) {
    init {
        require(gutterFraction in 0f..0.06f)
        require(borderWidth in 1f..24f)
        require(toneDensity in 6..64)
        require(toneOpacity in 0..60)
        require(tonePanelIndex >= -1)
    }
}

object PanelLayout {
    fun rectangles(preset: PanelPreset, gutter: Float): List<PageRect> {
        val gap = gutter.coerceIn(0f, 0.06f)
        fun horizontal(rows: Int): List<PageRect> {
            val each = (1f - gap * (rows - 1)) / rows
            return (0 until rows).map { row ->
                val top = row * (each + gap)
                PageRect(0f, top, 1f, (top + each).coerceAtMost(1f))
            }
        }
        fun vertical(columns: Int): List<PageRect> {
            val each = (1f - gap * (columns - 1)) / columns
            return (0 until columns).map { column ->
                val left = column * (each + gap)
                PageRect(left, 0f, (left + each).coerceAtMost(1f), 1f)
            }
        }
        return when (preset) {
            PanelPreset.KEEP_ORIGINAL -> emptyList()
            PanelPreset.SINGLE -> listOf(PageRect(0f, 0f, 1f, 1f))
            PanelPreset.TWO_ROWS -> horizontal(2)
            PanelPreset.TWO_COLUMNS -> vertical(2)
            PanelPreset.THREE_STRIPS -> horizontal(3)
            PanelPreset.FOUR_GRID -> grid(2, 2, gap)
            PanelPreset.SIX_GRID -> grid(3, 2, gap)
            // A practical 1 + 2 + 4 page: one full-width opener, two middle beats, four closing beats.
            PanelPreset.SEVEN_PANELS -> sevenPanelLayout(gap)
        }
    }

    private fun sevenPanelLayout(gutter: Float): List<PageRect> {
        val rowHeight = (1f - gutter * 2f) / 3f
        val halfWidth = (1f - gutter) / 2f
        val quarterWidth = (1f - gutter * 3f) / 4f
        val middleTop = rowHeight + gutter
        val bottomTop = middleTop + rowHeight + gutter
        return listOf(
            PageRect(0f, 0f, 1f, rowHeight),
            PageRect(0f, middleTop, halfWidth, middleTop + rowHeight),
            PageRect(halfWidth + gutter, middleTop, 1f, middleTop + rowHeight),
            PageRect(0f, bottomTop, quarterWidth, 1f),
            PageRect(quarterWidth + gutter, bottomTop, quarterWidth * 2f + gutter, 1f),
            PageRect(quarterWidth * 2f + gutter * 2f, bottomTop, quarterWidth * 3f + gutter * 2f, 1f),
            PageRect(quarterWidth * 3f + gutter * 3f, bottomTop, 1f, 1f)
        )
    }

    private fun grid(columns: Int, rows: Int, gutter: Float): List<PageRect> {
        val width = (1f - gutter * (columns - 1)) / columns
        val height = (1f - gutter * (rows - 1)) / rows
        return buildList(columns * rows) {
            for (row in 0 until rows) {
                for (column in 0 until columns) {
                    val left = column * (width + gutter)
                    val top = row * (height + gutter)
                    add(PageRect(left, top, (left + width).coerceAtMost(1f), (top + height).coerceAtMost(1f)))
                }
            }
        }
    }

    fun tonePanelRectangles(settings: StudioSettings): List<PageRect> {
        if (settings.tonePattern == null) return emptyList()
        if (settings.panelPreset == PanelPreset.KEEP_ORIGINAL) {
            return if (settings.tonePanelIndex == -1) listOf(PageRect(0f, 0f, 1f, 1f)) else emptyList()
        }
        val panels = rectangles(settings.panelPreset, settings.gutterFraction)
        return when {
            settings.tonePanelIndex == -1 -> panels
            settings.tonePanelIndex !in panels.indices -> emptyList()
            else -> listOf(panels[settings.tonePanelIndex])
        }
    }
}
