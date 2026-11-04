package com.example.mangainker.gesture

import android.graphics.PointF
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureSegmenterTest {
    @Test
    fun segmentsNeverExceedAndroidLimit() {
        val points = (0..60).map { PointF(it.toFloat(), it.toFloat()) }
        val segments = GestureSegmenter().segment(points, 1_500L, 800)

        assertTrue(segments.isNotEmpty())
        assertTrue(segments.all { it.durationMs in 1L..500L })
        assertTrue(segments.first().points.first() == points.first())
        assertTrue(segments.last().points.last() == points.last())
    }

    @Test
    fun segmentsShareBoundaryPointsAndPreserveOrder() {
        val points = (0..60).map { PointF(it.toFloat(), (it * 2).toFloat()) }
        val segments = GestureSegmenter().segment(points, 1_500L, 500)

        assertTrue(segments.size == 3)
        assertTrue(segments[0].points.last() == segments[1].points.first())
        assertTrue(segments[1].points.last() == segments[2].points.first())
    }
}
