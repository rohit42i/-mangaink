package com.example.mangainker.core

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlin.math.max

object BitmapTools {
    private const val MAX_SIDE = 1400

    fun decodeForProcessing(resolver: ContentResolver, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IllegalArgumentException("Android could not open that image.")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("The selected file is not a supported image.")
        }

        val sample = BitmapDecodePlanner.inSampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IllegalArgumentException("The image could not be decoded.")

        val longestSide = max(decoded.width, decoded.height)
        if (longestSide <= MAX_SIDE) return decoded

        val scale = MAX_SIDE.toFloat() / longestSide.toFloat()
        val targetWidth = (decoded.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (decoded.height * scale).toInt().coerceAtLeast(1)
        val resized = Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
        if (resized !== decoded && !decoded.isRecycled) decoded.recycle()
        return resized
    }

    fun grayscale(bitmap: Bitmap, cancellationCheck: () -> Unit = {}): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val output = ByteArray(pixels.size)

        for (i in pixels.indices) {
            if ((i and 8191) == 0) cancellationCheck()
            val pixel = pixels[i]
            val alpha = (pixel ushr 24) and 0xff
            val red = (pixel shr 16) and 0xff
            val green = (pixel shr 8) and 0xff
            val blue = pixel and 0xff
            val r = (red * alpha + 255 * (255 - alpha)) / 255
            val g = (green * alpha + 255 * (255 - alpha)) / 255
            val b = (blue * alpha + 255 * (255 - alpha)) / 255
            output[i] = ((299 * r + 587 * g + 114 * b) / 1000).toByte()
        }
        return output
    }
}
