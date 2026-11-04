package com.example.mangainker.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureSegmenterTest {
    @Test
    fun segmentsNeverExceedAndroidLimit() {
        val points = (0..60).map { GesturePoint(it.toFloat(), it.toFloat()) }
        val segments = GestureSegmenter().segment(points, 1_500L, 800)

        assertTrue(segments.isNotEmpty())
        assertTrue(segments.all { it.durationMs in 1L..500L })
        assertEquals(points.first(), segments.first().points.first())
        assertEquals(points.last(), segments.last().points.last())
    }

    @Test
    fun segmentsShareBoundaryPointsAndPreserveOrder() {
        val points = (0..60).map { GesturePoint(it.toFloat(), (it * 2).toFloat()) }
        val segments = GestureSegmenter().segment(points, 1_500L, 500)

        assertEquals(3, segments.size)
        assertEquals(segments[0].points.last(), segments[1].points.first())
        assertEquals(segments[1].points.last(), segments[2].points.first())
    }
}
