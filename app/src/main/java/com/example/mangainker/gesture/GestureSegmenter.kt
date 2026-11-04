package com.example.mangainker.gesture

import kotlin.math.ceil
import kotlin.math.floor

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

        val maxDuration = configuredSegmentMs.coerceIn(200, 800).coerceAtMost(500).toLong()
        val count = ceil(totalDurationMs.toDouble() / maxDuration.toDouble()).toInt()
        val lastPointIndex = points.lastIndex

        return (0 until count).map { segmentIndex ->
            val startTime = segmentIndex * maxDuration
            val endTime = minOf(totalDurationMs, startTime + maxDuration)
            val firstPoint = floor(startTime.toDouble() / totalDurationMs * lastPointIndex)
                .toInt()
                .coerceIn(0, lastPointIndex - 1)
            val finalPoint = ceil(endTime.toDouble() / totalDurationMs * lastPointIndex)
                .toInt()
                .coerceIn(firstPoint + 1, lastPointIndex)

            GestureSegment(
                index = segmentIndex + 1,
                points = points.subList(firstPoint, finalPoint + 1).toList(),
                durationMs = endTime - startTime
            )
        }
    }
}
