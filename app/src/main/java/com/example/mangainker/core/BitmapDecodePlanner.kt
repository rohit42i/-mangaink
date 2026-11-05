package com.example.mangainker.core

/**
 * Chooses a power-of-two BitmapFactory sample that does not decode below the
 * requested longest-side target. The decoded image is resized precisely after
 * decode, avoiding the large quality loss caused by rounding the sample up.
 */
internal object BitmapDecodePlanner {
    fun inSampleSize(width: Int, height: Int, maxSide: Int): Int {
        require(width > 0 && height > 0) { "Source image dimensions must be positive." }
        require(maxSide > 0) { "Maximum image side must be positive." }

        val longestSide = maxOf(width, height)
        var sample = 1
        while (longestSide.toLong() / (sample.toLong() * 2L) >= maxSide.toLong()) {
            sample *= 2
        }
        return sample
    }
}
