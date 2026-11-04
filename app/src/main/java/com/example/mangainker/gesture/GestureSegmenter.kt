package com.example.mangainker.gesture

import kotlin.math.ceil
import kotlin.math.hypot

data class GestureSegment(
    val index: Int,
    val points: List<GesturePoint>,
    val durationMs: Long
)

class GestureSegmenter {
    fun segment(
        points: List<GesturePoint>,
        totalDurationMs: Long,
        configuredSegmentMs: Int
    ): List<GestureSegment> {
        require(points.size >= 2) { "A gesture needs at least two points." }
        require(totalDurationMs > 0L) { "Gesture duration must be positive." }

        val maxDuration = configuredSegmentMs.coerceIn(100, 500).toLong()
        val count = ceil(totalDurationMs.toDouble() / maxDuration.toDouble()).toInt()
        val cumulative = DoubleArray(points.size)
        for (index in 1 until points.size) {
            cumulative[index] = cumulative[index - 1] + hypot(
                (points[index].x - points[index - 1].x).toDouble(),
                (points[index].y - points[index - 1].y).toDouble()
            )
        }
        val totalLength = cumulative.last()
        require(totalLength > 0.0) { "A gesture path must have non-zero length." }

        fun pointAt(distance: Double): GesturePoint {
            val target = distance.coerceIn(0.0, totalLength)
            var index = 1
            while (index < cumulative.size - 1 && cumulative[index] < target) index++
            val startDistance = cumulative[index - 1]
            val segmentLength = cumulative[index] - startDistance
            val fraction = if (segmentLength <= 1e-9) 0.0
                else ((target - startDistance) / segmentLength).coerceIn(0.0, 1.0)
            val first = points[index - 1]
            val second = points[index]
            return GesturePoint(
                (first.x + (second.x - first.x) * fraction).toFloat(),
                (first.y + (second.y - first.y) * fraction).toFloat()
            )
        }

        return (0 until count).map { segmentIndex ->
            val startTime = segmentIndex * maxDuration
            val endTime = minOf(totalDurationMs, startTime + maxDuration)
            val startDistance = totalLength * startTime.toDouble() / totalDurationMs
            val endDistance = totalLength * endTime.toDouble() / totalDurationMs
            val segmentPoints = ArrayList<GesturePoint>()
            segmentPoints.add(pointAt(startDistance))
            for (index in 1 until points.lastIndex) {
                if (cumulative[index] > startDistance && cumulative[index] < endDistance) {
                    segmentPoints.add(points[index])
                }
            }
            segmentPoints.add(pointAt(endDistance))
            GestureSegment(
                index = segmentIndex + 1,
                points = segmentPoints,
                durationMs = endTime - startTime
            )
        }
    }
}
