package com.example.mangainker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProjectiveTransformTest {
    @Test
    fun mapsAllPageCornersAndCenterIntoCalibratedCanvasRectangle() {
        val calibration = PageCalibration(
            topLeft = InkPoint(120f, 180f),
            topRight = InkPoint(920f, 180f),
            bottomRight = InkPoint(920f, 1380f),
            bottomLeft = InkPoint(120f, 1380f)
        )
        val transform = ProjectiveTransform.from(calibration)
        assertNotNull(transform)

        val mapper = requireNotNull(transform)
        assertPoint(InkPoint(120f, 180f), mapper.map(0f, 0f))
        assertPoint(InkPoint(920f, 180f), mapper.map(1f, 0f))
        assertPoint(InkPoint(920f, 1380f), mapper.map(1f, 1f))
        assertPoint(InkPoint(120f, 1380f), mapper.map(0f, 1f))
        assertPoint(InkPoint(520f, 780f), mapper.map(0.5f, 0.5f))
    }

    private fun assertPoint(expected: InkPoint, actual: InkPoint) {
        assertEquals(expected.x, actual.x, 0.1f)
        assertEquals(expected.y, actual.y, 0.1f)
    }
}
