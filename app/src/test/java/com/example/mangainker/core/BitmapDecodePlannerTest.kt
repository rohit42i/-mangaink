package com.example.mangainker.core

import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapDecodePlannerTest {
    @Test
    fun keepsNearTargetPagesAtFullDecodeResolutionBeforePreciseResize() {
        assertEquals(1, BitmapDecodePlanner.inSampleSize(1500, 2200, 1400))
        assertEquals(1, BitmapDecodePlanner.inSampleSize(2799, 1800, 1400))
    }

    @Test
    fun usesLargestSafePowerOfTwoSampleForLargePages() {
        assertEquals(2, BitmapDecodePlanner.inSampleSize(3000, 4500, 1400))
        assertEquals(4, BitmapDecodePlanner.inSampleSize(6000, 9000, 1400))
        assertEquals(4, BitmapDecodePlanner.inSampleSize(8000, 5000, 1400))
    }

    @Test
    fun doesNotDownsampleAlreadySmallPages() {
        assertEquals(1, BitmapDecodePlanner.inSampleSize(900, 1300, 1400))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidDimensions() {
        BitmapDecodePlanner.inSampleSize(0, 1000, 1400)
    }
}
