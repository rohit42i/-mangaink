package com.example.mangainker.core

import kotlin.math.max
import kotlin.math.min

data class PageBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    fun width(): Int = right - left
    fun height(): Int = bottom - top
}

/**
 * Finds a large, connected light canvas inside a screen capture.
 * Toolbars and icons are usually too small to pass the area and aspect checks.
 * Returns null rather than guessing when the screen does not contain a clear page.
 */
internal object PageBoundsDetector {
    fun detect(
        grayscale: ByteArray,
        width: Int,
        height: Int,
        cancellationCheck: () -> Unit = {}
    ): PageBounds? {
        if (width < 40 || height < 40 || grayscale.size != width * height) return null

        var significantRegionCount = 0
        val visited = ByteArray(grayscale.size)
        val queue = IntArray(grayscale.size)
        var bestCount = 0
        var bestLeft = 0
        var bestTop = 0
        var bestRight = 0
        var bestBottom = 0
        val offsets = intArrayOf(-width - 1, -width, -width + 1, -1, 1, width - 1, width, width + 1)

        for (start in grayscale.indices) {
            if ((start and 8191) == 0) cancellationCheck()
            if (visited[start].toInt() != 0 || (grayscale[start].toInt() and 0xff) < LIGHT_THRESHOLD) continue

            var head = 0
            var tail = 0
            var left = start % width
            var right = left
            var top = start / width
            var bottom = top
            queue[tail++] = start
            visited[start] = 1

            while (head < tail) {
                if ((head and 4095) == 0) cancellationCheck()
                val index = queue[head++]
                val x = index % width
                val y = index / width
                left = min(left, x)
                right = max(right, x)
                top = min(top, y)
                bottom = max(bottom, y)

                for (offset in offsets) {
                    val next = index + offset
                    if (next < 0 || next >= grayscale.size) continue
                    val nx = next % width
                    val ny = next / width
                    if (kotlin.math.abs(nx - x) > 1 || kotlin.math.abs(ny - y) > 1) continue
                    if (visited[next].toInt() == 0 &&
                        (grayscale[next].toInt() and 0xff) >= LIGHT_THRESHOLD
                    ) {
                        visited[next] = 1
                        queue[tail++] = next
                    }
                }
            }

            val boxWidth = right - left + 1
            val boxHeight = bottom - top + 1
            val boxArea = boxWidth.toLong() * boxHeight
            val screenArea = width.toLong() * height
            val aspect = boxWidth.toFloat() / boxHeight.coerceAtLeast(1)
            val fillRatio = tail.toFloat() / boxArea.coerceAtLeast(1L).toFloat()
            val touchesAllEdges = left <= 1 && top <= 1 && right >= width - 2 && bottom >= height - 2

            // A thick panel frame can split one page into several white components.
            // Remember substantial regions so we can refuse an unsafe single-panel crop.
            if (!touchesAllEdges &&
                boxWidth >= width * MIN_REGION_WIDTH_FRACTION &&
                boxHeight >= height * MIN_REGION_HEIGHT_FRACTION &&
                boxArea >= screenArea * MIN_REGION_SCREEN_AREA &&
                aspect in MIN_ASPECT..MAX_ASPECT &&
                fillRatio >= MIN_FILL_RATIO
            ) {
                significantRegionCount++
            }

            if (!touchesAllEdges &&
                boxWidth >= width * MIN_WIDTH_FRACTION &&
                boxHeight >= height * MIN_HEIGHT_FRACTION &&
                boxArea >= screenArea * MIN_SCREEN_AREA &&
                aspect in MIN_ASPECT..MAX_ASPECT &&
                fillRatio >= MIN_FILL_RATIO &&
                tail > bestCount
            ) {
                bestCount = tail
                bestLeft = left
                bestTop = top
                bestRight = right
                bestBottom = bottom
            }
        }

        // Never auto-crop to just the largest panel when other substantial
        // page-like regions are disconnected. The import flow keeps the full
        // screenshot and asks the user to select the complete artwork area.
        if (significantRegionCount > 1) return null
        if (bestCount == 0) return null
        val padX = max(2, ((bestRight - bestLeft + 1) * EDGE_PADDING).toInt())
        val padY = max(2, ((bestBottom - bestTop + 1) * EDGE_PADDING).toInt())
        return PageBounds(
            (bestLeft - padX).coerceAtLeast(0),
            (bestTop - padY).coerceAtLeast(0),
            (bestRight + padX + 1).coerceAtMost(width),
            (bestBottom + padY + 1).coerceAtMost(height)
        )
    }

    private const val LIGHT_THRESHOLD = 222
    private const val MIN_REGION_WIDTH_FRACTION = 0.20f
    private const val MIN_REGION_HEIGHT_FRACTION = 0.10f
    private const val MIN_REGION_SCREEN_AREA = 0.015
    private const val MIN_WIDTH_FRACTION = 0.30f
    private const val MIN_HEIGHT_FRACTION = 0.30f
    private const val MIN_SCREEN_AREA = 0.16
    private const val MIN_FILL_RATIO = 0.18f
    private const val MIN_ASPECT = 0.30f
    private const val MAX_ASPECT = 2.4f
    private const val EDGE_PADDING = 0.012f
}
