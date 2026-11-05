package com.example.mangainker.core

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageBoundsDetectorTest {
    @Test
    fun findsLargeArtworkCanvasAndIgnoresShortToolbar() {
        val width = 400
        val height = 700
        val gray = ByteArray(width * height) { 62 }
        for (y in 0 until 48) {
            for (x in 24 until 376) gray[y * width + x] = 245.toByte()
        }
        for (y in 100 until 600) {
            for (x in 56 until 344) gray[y * width + x] = 245.toByte()
        }
        // Artwork ink creates holes, but the page background remains connected around it.
        for (y in 160 until 520) {
            for (x in 190..192) gray[y * width + x] = 12
        }

        val bounds = PageBoundsDetector.detect(gray, width, height)

        assertNotNull(bounds)
        assertTrue(bounds!!.left in 53..56)
        assertTrue(bounds.top in 93..96)
        assertTrue(bounds.right in 344..347)
        assertTrue(bounds.bottom in 604..607)
    }

    @Test
    fun returnsNullWhenNoUnambiguousCanvasExists() {
        val width = 360
        val height = 640
        val gray = ByteArray(width * height) { 72 }
        for (y in 8 until 38) {
            for (x in 10 until 350) gray[y * width + x] = 245.toByte()
        }

        assertNull(PageBoundsDetector.detect(gray, width, height))
    }
}
