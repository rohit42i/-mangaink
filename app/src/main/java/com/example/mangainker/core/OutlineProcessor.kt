package com.example.mangainker.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Local, classical line extraction. No network or model is involved. */
object OutlineProcessor {
    private val dx = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
    private val dy = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)

    fun process(
        grayscale: ByteArray,
        width: Int,
        height: Int,
        thresholdOffset: Int = 0,
        minComponentPixels: Int = 3,
        smoothing: Float = 0.28f,
        detailPreservation: Float = 0.78f,
        cancellationCheck: () -> Unit = {},
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): ProcessingResult {
        require(width > 2 && height > 2) { "Image is too small to process." }
        require(grayscale.size == width * height) { "Image data does not match its dimensions." }
        cancellationCheck()
        onProgress(0.30f, "Calculating the page threshold…")
        val threshold = (otsuThreshold(grayscale) + thresholdOffset).coerceIn(8, 248)
        cancellationCheck()
        val mask = ByteArray(grayscale.size)
        var foreground = 0
        for (i in grayscale.indices) {
            if ((i and 8191) == 0) cancellationCheck()
            if ((grayscale[i].toInt() and 0xff) <= threshold) {
                mask[i] = 1
                foreground++
            }
        }
        if (foreground == 0) throw IllegalArgumentException("No dark outline pixels were found. Try a darker outline or adjust threshold.")

        onProgress(0.43f, "Removing isolated specks…")
        val discarded = removeSmallComponents(mask, width, height, minComponentPixels.coerceAtLeast(1), cancellationCheck)
        val remaining = mask.count { it.toInt() != 0 }
        if (remaining == 0) throw IllegalArgumentException("Only tiny specks were detected. Reduce the speck-removal setting.")

        onProgress(0.55f, "Thinning outlines to centre lines…")
        thinZhangSuen(mask, width, height, cancellationCheck)
        val skeletonCount = mask.count { it.toInt() != 0 }
        onProgress(0.72f, "Tracing separate strokes and panel borders…")
        val rawPaths = trace(mask, width, height, cancellationCheck)
        onProgress(0.86f, "Simplifying and smoothing detected paths…")
        val paths = rawPaths.mapIndexedNotNull { index, raw ->
            cancellationCheck()
            onProgress(
                0.86f + 0.12f * ((index + 1).toFloat() / rawPaths.size.coerceAtLeast(1)),
                "Refining path ${index + 1} of ${rawPaths.size}…"
            )
            if (raw.size < 2) return@mapIndexedNotNull null
            val detail = detailPreservation.coerceIn(0f, 1f)
            val simplifyTolerance = 2.0f - (1.5f * detail)
            val simplified = simplify(raw, simplifyTolerance, cancellationCheck)
            val smoothed = smooth(simplified, smoothing.coerceIn(0f, 1f), cancellationCheck)
            if (smoothed.size < 2) return@mapIndexedNotNull null
            val length = pathLength(smoothed)
            if (length < 4f) return@mapIndexedNotNull null
            InkPath(smoothed, length)
        }

        if (paths.isEmpty()) throw IllegalArgumentException("No usable line paths were reconstructed from this image.")
        onProgress(0.99f, "Preparing the outline preview…")
        return ProcessingResult(paths, threshold, foreground, skeletonCount, discarded)
    }

    private fun otsuThreshold(gray: ByteArray): Int {
        val histogram = IntArray(256)
        gray.forEach { histogram[it.toInt() and 0xff]++ }
        val total = gray.size.toDouble()
        var sum = 0.0
        for (i in 0..255) sum += i * histogram[i]
        var sumBackground = 0.0
        var weightBackground = 0.0
        var bestVariance = -1.0
        var bestThreshold = 127
        for (threshold in 0..255) {
            weightBackground += histogram[threshold]
            if (weightBackground == 0.0) continue
            val weightForeground = total - weightBackground
            if (weightForeground == 0.0) break
            sumBackground += threshold * histogram[threshold]
            val meanBackground = sumBackground / weightBackground
            val meanForeground = (sum - sumBackground) / weightForeground
            val variance = weightBackground * weightForeground *
                (meanBackground - meanForeground) * (meanBackground - meanForeground)
            if (variance > bestVariance) {
                bestVariance = variance
                bestThreshold = threshold
            }
        }
        return bestThreshold
    }

    private fun removeSmallComponents(
        mask: ByteArray,
        width: Int,
        height: Int,
        minimum: Int,
        cancellationCheck: () -> Unit
    ): Int {
        if (minimum <= 1) return 0
        val visited = ByteArray(mask.size)
        val stack = IntArray(mask.size)
        val component = IntArray(mask.size)
        var discarded = 0
        for (start in mask.indices) {
            if ((start and 8191) == 0) cancellationCheck()
            if (mask[start].toInt() == 0 || visited[start].toInt() != 0) continue
            var stackSize = 0
            var componentSize = 0
            stack[stackSize++] = start
            visited[start] = 1
            while (stackSize > 0) {
                if ((componentSize and 4095) == 0) cancellationCheck()
                val current = stack[--stackSize]
                component[componentSize++] = current
                val x = current % width
                val y = current / width
                for (direction in 0..7) {
                    val nx = x + dx[direction]
                    val ny = y + dy[direction]
                    if (nx !in 0 until width || ny !in 0 until height) continue
                    val next = ny * width + nx
                    if (mask[next].toInt() != 0 && visited[next].toInt() == 0) {
                        visited[next] = 1
                        stack[stackSize++] = next
                    }
                }
            }
            if (componentSize < minimum) {
                for (i in 0 until componentSize) mask[component[i]] = 0
                discarded++
            }
        }
        return discarded
    }

    private fun thinZhangSuen(mask: ByteArray, width: Int, height: Int, cancellationCheck: () -> Unit) {
        val removals = IntArray(mask.size)
        var changed = true
        var iterations = 0
        while (changed && iterations++ < 64) {
            changed = false
            for (phase in 0..1) {
                var count = 0
                for (y in 1 until height - 1) {
                    cancellationCheck()
                    for (x in 1 until width - 1) {
                        val index = y * width + x
                        if (mask[index].toInt() == 0) continue
                        val p2 = mask[index - width].toInt()
                        val p3 = mask[index - width + 1].toInt()
                        val p4 = mask[index + 1].toInt()
                        val p5 = mask[index + width + 1].toInt()
                        val p6 = mask[index + width].toInt()
                        val p7 = mask[index + width - 1].toInt()
                        val p8 = mask[index - 1].toInt()
                        val p9 = mask[index - width - 1].toInt()
                        val neighborCount = p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9
                        if (neighborCount !in 2..6) continue
                        val transitions =
                            (if (p2 == 0 && p3 == 1) 1 else 0) +
                            (if (p3 == 0 && p4 == 1) 1 else 0) +
                            (if (p4 == 0 && p5 == 1) 1 else 0) +
                            (if (p5 == 0 && p6 == 1) 1 else 0) +
                            (if (p6 == 0 && p7 == 1) 1 else 0) +
                            (if (p7 == 0 && p8 == 1) 1 else 0) +
                            (if (p8 == 0 && p9 == 1) 1 else 0) +
                            (if (p9 == 0 && p2 == 1) 1 else 0)
                        if (transitions != 1) continue
                        val condition = if (phase == 0) {
                            p2 * p4 * p6 == 0 && p4 * p6 * p8 == 0
                        } else {
                            p2 * p4 * p8 == 0 && p2 * p6 * p8 == 0
                        }
                        if (condition) removals[count++] = index
                    }
                }
                if (count > 0) {
                    for (i in 0 until count) mask[removals[i]] = 0
                    changed = true
                }
            }
        }
    }

    private fun neighbor(index: Int, direction: Int, mask: ByteArray, width: Int, height: Int): Int {
        val x = index % width
        val y = index / width
        val nx = x + dx[direction]
        val ny = y + dy[direction]
        if (nx !in 0 until width || ny !in 0 until height) return -1
        if (dx[direction] != 0 && dy[direction] != 0) {
            val horizontal = y * width + nx
            val vertical = ny * width + x
            if (mask[horizontal].toInt() != 0 || mask[vertical].toInt() != 0) return -1
        }
        val candidate = ny * width + nx
        return if (mask[candidate].toInt() != 0) candidate else -1
    }

    private fun degree(index: Int, mask: ByteArray, width: Int, height: Int): Int {
        var count = 0
        for (d in 0..7) if (neighbor(index, d, mask, width, height) >= 0) count++
        return count
    }

    private fun trace(
        mask: ByteArray,
        width: Int,
        height: Int,
        cancellationCheck: () -> Unit
    ): List<List<InkPoint>> {
        val size = mask.size
        val degrees = ByteArray(size)
        for (index in mask.indices) {
            if ((index and 8191) == 0) cancellationCheck()
            if (mask[index].toInt() != 0) degrees[index] = degree(index, mask, width, height).toByte()
        }
        val visitedEdges = ByteArray(size * 8)
        val results = ArrayList<List<InkPoint>>()

        fun isVisited(index: Int, direction: Int): Boolean = visitedEdges[index * 8 + direction].toInt() != 0
        fun mark(index: Int, direction: Int, next: Int) {
            visitedEdges[index * 8 + direction] = 1
            visitedEdges[next * 8 + ((direction + 4) % 8)] = 1
        }
        fun walk(start: Int, firstDirection: Int): List<InkPoint> {
            val points = ArrayList<InkPoint>()
            var previous = start
            var direction = firstDirection
            var current = neighbor(previous, direction, mask, width, height)
            if (current < 0 || isVisited(previous, direction)) return points
            mark(previous, direction, current)
            points.add(InkPoint((previous % width).toFloat(), (previous / width).toFloat()))
            points.add(InkPoint((current % width).toFloat(), (current / width).toFloat()))
            var steps = 0
            while (degrees[current].toInt() == 2 && steps++ < size) {
                if ((steps and 1023) == 0) cancellationCheck()
                var nextDirection = -1
                var next = -1
                for (d in 0..7) {
                    val candidate = neighbor(current, d, mask, width, height)
                    if (candidate >= 0 && candidate != previous && !isVisited(current, d)) {
                        nextDirection = d
                        next = candidate
                        break
                    }
                }
                if (next < 0) break
                mark(current, nextDirection, next)
                previous = current
                current = next
                points.add(InkPoint((current % width).toFloat(), (current / width).toFloat()))
            }
            return points
        }

        for (index in mask.indices) {
            if ((index and 8191) == 0) cancellationCheck()
            if (mask[index].toInt() == 0 || degrees[index].toInt() == 2 || degrees[index].toInt() == 0) continue
            for (direction in 0..7) {
                if (neighbor(index, direction, mask, width, height) >= 0 && !isVisited(index, direction)) {
                    val path = walk(index, direction)
                    if (path.size >= 2) results.add(path)
                }
            }
        }
        // Closed loops have no endpoints or junctions, so trace their remaining edges.
        for (index in mask.indices) {
            if ((index and 8191) == 0) cancellationCheck()
            if (mask[index].toInt() == 0) continue
            for (direction in 0..7) {
                if (neighbor(index, direction, mask, width, height) >= 0 && !isVisited(index, direction)) {
                    val path = walk(index, direction)
                    if (path.size >= 2) results.add(path)
                }
            }
        }
        return results
    }

    private fun simplify(points: List<InkPoint>, epsilon: Float, cancellationCheck: () -> Unit): List<InkPoint> {
        if (points.size <= 2) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        val stack = mutableListOf(0 to points.lastIndex)
        val epsilonSquared = epsilon * epsilon
        while (stack.isNotEmpty()) {
            cancellationCheck()
            val (first, last) = stack.removeAt(stack.lastIndex)
            var maximum = epsilonSquared
            var selected = -1
            val a = points[first]
            val b = points[last]
            val vx = b.x - a.x
            val vy = b.y - a.y
            val lengthSquared = vx * vx + vy * vy
            for (i in first + 1 until last) {
                if ((i and 4095) == 0) cancellationCheck()
                val p = points[i]
                val distanceSquared = if (lengthSquared == 0f) {
                    val ox = p.x - a.x
                    val oy = p.y - a.y
                    ox * ox + oy * oy
                } else {
                    val t = (((p.x - a.x) * vx + (p.y - a.y) * vy) / lengthSquared).coerceIn(0f, 1f)
                    val ox = p.x - (a.x + t * vx)
                    val oy = p.y - (a.y + t * vy)
                    ox * ox + oy * oy
                }
                if (distanceSquared > maximum) {
                    maximum = distanceSquared
                    selected = i
                }
            }
            if (selected >= 0) {
                keep[selected] = true
                stack.add(first to selected)
                stack.add(selected to last)
            }
        }
        return points.filterIndexed { index, _ -> keep[index] }
    }

    private fun smooth(points: List<InkPoint>, strength: Float, cancellationCheck: () -> Unit): List<InkPoint> {
        if (strength <= 0.02f || points.size < 4) return points
        val output = ArrayList<InkPoint>(points.size * 2)
        output.add(points.first())
        for (i in 0 until points.size - 1) {
            if ((i and 2047) == 0) cancellationCheck()
            val p0 = points[max(0, i - 1)]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[min(points.lastIndex, i + 2)]
            val steps = 3
            for (step in 1..steps) {
                val t = step / (steps + 1f)
                val t2 = t * t
                val t3 = t2 * t
                val curveX = 0.5f * ((2 * p1.x) + (-p0.x + p2.x) * t +
                    (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 +
                    (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3)
                val curveY = 0.5f * ((2 * p1.y) + (-p0.y + p2.y) * t +
                    (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 +
                    (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3)
                val linearX = p1.x + (p2.x - p1.x) * t
                val linearY = p1.y + (p2.y - p1.y) * t
                output.add(InkPoint(
                    linearX + (curveX - linearX) * strength,
                    linearY + (curveY - linearY) * strength
                ))
            }
            output.add(p2)
        }
        return output
    }

    private fun pathLength(points: List<InkPoint>): Float {
        var total = 0f
        for (i in 1 until points.size) {
            val dx = points[i].x - points[i - 1].x
            val dy = points[i].y - points[i - 1].y
            total += sqrt(dx * dx + dy * dy)
        }
        return total
    }
}
