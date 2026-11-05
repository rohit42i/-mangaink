package com.example.mangainker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.concurrent.CancellationException
import org.junit.Test

class OutlineProcessorTest {
    @Test
    fun reconstructsAThickHorizontalLineIntoAPath() {
        val width = 64
        val height = 64
        val pixels = ByteArray(width * height) { 0xff.toByte() }
        for (x in 10..52) for (y in 30..33) pixels[y * width + x] = 0

        val result = OutlineProcessor.process(pixels, width, height, minComponentPixels = 1, smoothing = 0f)

        assertTrue(result.paths.isNotEmpty())
        assertTrue(result.skeletonPixels in 35..50)
        assertTrue(result.paths.first().length > 30f)
    }

    @Test
    fun filtersIsolatedDotsWhenSpeckRemovalIsEnabled() {
        val width = 64
        val height = 64
        val pixels = ByteArray(width * height) { 0xff.toByte() }
        for (x in 8..54) for (y in 31..33) pixels[y * width + x] = 0
        pixels[4 * width + 4] = 0
        pixels[7 * width + 50] = 0

        val result = OutlineProcessor.process(pixels, width, height, minComponentPixels = 3, smoothing = 0f)

        assertEquals(2, result.discardedComponents)
        assertTrue(result.paths.isNotEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankWhitePage() {
        OutlineProcessor.process(ByteArray(32 * 32) { 0xff.toByte() }, 32, 32)
    }

    @Test
    fun mapsPageCornersAndCenterToCalibratedRectangle() {
        val calibration = PageCalibration(
            InkPoint(100f, 200f), InkPoint(500f, 200f),
            InkPoint(500f, 800f), InkPoint(100f, 800f)
        )
        val transform = ProjectiveTransform.from(calibration)!!

        val topLeft = transform.map(0f, 0f)
        val center = transform.map(0.5f, 0.5f)
        val bottomRight = transform.map(1f, 1f)

        assertEquals(100f, topLeft.x, 0.01f)
        assertEquals(200f, topLeft.y, 0.01f)
        assertEquals(300f, center.x, 0.01f)
        assertEquals(500f, center.y, 0.01f)
        assertEquals(500f, bottomRight.x, 0.01f)
        assertEquals(800f, bottomRight.y, 0.01f)
    }

    @Test
    fun keepsClosedPanelBorderAsAConnectedLoop() {
        val width = 80
        val height = 80
        val pixels = ByteArray(width * height) { 0xff.toByte() }
        for (x in 15..64) {
            for (y in 15..18) pixels[y * width + x] = 0
            for (y in 61..64) pixels[y * width + x] = 0
        }
        for (y in 15..64) {
            for (x in 15..18) pixels[y * width + x] = 0
            for (x in 61..64) pixels[y * width + x] = 0
        }

        val result = OutlineProcessor.process(pixels, width, height, minComponentPixels = 1, smoothing = 0f)

        assertEquals(1, result.paths.size)
        assertTrue(result.paths.single().length > 150f)
    }

    @Test
    fun splitsBranchingLinesWithoutDroppingTheBranches() {
        val width = 80
        val height = 80
        val pixels = ByteArray(width * height) { 0xff.toByte() }
        for (x in 10..65) for (y in 38..41) pixels[y * width + x] = 0
        for (y in 10..41) for (x in 38..41) pixels[y * width + x] = 0

        val result = OutlineProcessor.process(pixels, width, height, minComponentPixels = 1, smoothing = 0f)

        assertEquals(3, result.paths.size)
        assertTrue(result.paths.sumOf { it.length.toDouble() } > 65.0)
    }

    @Test
    fun rejectsCrossedCalibrationCorners() {
        val crossed = PageCalibration(
            InkPoint(100f, 100f), InkPoint(500f, 800f),
            InkPoint(500f, 100f), InkPoint(100f, 800f)
        )

        assertEquals(null, ProjectiveTransform.from(crossed))
    }

    @Test
    fun smoothPathsStayInsideTheSourcePageBounds() {
        val width = 64
        val height = 64
        val pixels = ByteArray(width * height) { 0xff.toByte() }
        for (x in 4..59) for (y in 30..33) pixels[y * width + x] = 0

        val result = OutlineProcessor.process(pixels, width, height, minComponentPixels = 1, smoothing = 0.8f)

        assertTrue(result.paths.isNotEmpty())
        result.paths.flatMap { it.points }.forEach { point ->
            assertTrue(point.x in 0f..(width - 1).toFloat())
            assertTrue(point.y in 0f..(height - 1).toFloat())
        }
    }

    @Test
    fun scansSevenSeparatedPanelFramesOnOneFullPage() {
        val width = 420
        val height = 620
        val pixels = ByteArray(width * height) { 0xff.toByte() }
        val panels = listOf(
            20 to 20, 220 to 20,
            20 to 170, 220 to 170,
            20 to 320, 220 to 320,
            20 to 470
        )

        panels.forEach { (left, top) ->
            val right = left + 160
            val bottom = top + 110
            for (x in left..right) {
                for (thickness in 0..2) {
                    pixels[(top + thickness) * width + x] = 0
                    pixels[(bottom - thickness) * width + x] = 0
                }
            }
            for (y in top..bottom) {
                for (thickness in 0..2) {
                    pixels[y * width + left + thickness] = 0
                    pixels[y * width + right - thickness] = 0
                }
            }
        }

        val result = OutlineProcessor.process(
            pixels, width, height, minComponentPixels = 1, smoothing = 0f, detailPreservation = 1f
        )

        assertEquals("Every separated panel frame must produce a path", 7, result.paths.size)
        assertTrue("All detected paths must stay inside the full page", result.paths.flatMap { it.points }.all {
            it.x in 0f..(width - 1).toFloat() && it.y in 0f..(height - 1).toFloat()
        })
    }
    @Test
    fun cancellationStopsOutlineProcessingPromptly() {
        val width = 256
        val height = 256
        val pixels = ByteArray(width * height) { 0xff.toByte() }
        for (x in 8..247) for (y in 120..135) pixels[y * width + x] = 0

        var checkpoints = 0
        val failure = runCatching {
            OutlineProcessor.process(
                grayscale = pixels,
                width = width,
                height = height,
                minComponentPixels = 1,
                cancellationCheck = {
                    checkpoints++
                    if (checkpoints >= 3) throw CancellationException("cancelled by test")
                }
            )
        }.exceptionOrNull()

        assertTrue("The cancellation signal must escape the processing pipeline", failure is CancellationException)
        assertTrue("Cancellation checkpoints must execute", checkpoints >= 3)
    }

}
