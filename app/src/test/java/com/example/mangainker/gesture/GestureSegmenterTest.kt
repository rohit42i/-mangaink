package com.example.mangainker.gesture

import org.junit.Assert.assertTrue
import org.junit.Test

class GestureSegmenterTest {
    @Test
    fun segmentsNeverExceedAndroidLimit() {
        val points = (0..60).map { GesturePoint(it.toFloat(), it.toFloat()) }
        val segments = GestureSegmenter().segment(points, 1_500L, 800)

        assertTrue("Segments should not be empty", segments.isNotEmpty())
        assertTrue("Each gesture must be <= 500 ms", segments.all { it.durationMs in 1L..500L })

        val first = segments.first().points.first()
        val last = segments.last().points.last()
        assertTrue("The first point must be retained", first.x == points.first().x && first.y == points.first().y)
        assertTrue("The last point must be retained", last.x == points.last().x && last.y == points.last().y)
    }

    @Test
    fun longTwoPointLineIsSplitIntoDistinctContiguousPieces() {
        val points = listOf(GesturePoint(0f, 20f), GesturePoint(900f, 20f))
        val segments = GestureSegmenter().segment(points, 1_500L, 500)

        assertTrue(segments.size == 3)
        assertTrue(segments.all { it.points.size >= 2 && it.durationMs <= 500L })
        assertTrue(segments[0].points.first().x == 0f)
        assertTrue(segments[0].points.last().x == 300f)
        assertTrue(segments[1].points.first().x == 300f)
        assertTrue(segments[1].points.last().x == 600f)
        assertTrue(segments[2].points.first().x == 600f)
        assertTrue(segments[2].points.last().x == 900f)
    }

    @Test
    fun segmentsShareBoundaryPointsAndPreserveOrder() {
        val points = (0..60).map { GesturePoint(it.toFloat(), (it * 2).toFloat()) }
        val segments = GestureSegmenter().segment(points, 1_500L, 500)

        assertTrue("A 1500 ms path at 500 ms should create three segments", segments.size == 3)

        val firstEnd = segments[0].points.last()
        val secondStart = segments[1].points.first()
        val secondEnd = segments[1].points.last()
        val thirdStart = segments[2].points.first()

        assertTrue("Adjacent segments must share their boundary", firstEnd.x == secondStart.x && firstEnd.y == secondStart.y)
        assertTrue("Adjacent segments must share their boundary", secondEnd.x == thirdStart.x && secondEnd.y == thirdStart.y)
    }
}
