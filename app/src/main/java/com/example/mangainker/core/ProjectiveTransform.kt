package com.example.mangainker.core

import kotlin.math.abs

/** Maps normalized full-page coordinates to the four calibrated screen corners. */
class ProjectiveTransform private constructor(private val h: DoubleArray) {
    fun map(u: Float, v: Float): InkPoint {
        val x = u.toDouble()
        val y = v.toDouble()
        val denominator = h[6] * x + h[7] * y + 1.0
        if (abs(denominator) < 1e-9) return InkPoint(Float.NaN, Float.NaN)
        val mappedX = (h[0] * x + h[1] * y + h[2]) / denominator
        val mappedY = (h[3] * x + h[4] * y + h[5]) / denominator
        return InkPoint(mappedX.toFloat(), mappedY.toFloat())
    }

    companion object {
        fun from(calibration: PageCalibration): ProjectiveTransform? {
            val destination = listOf(
                calibration.topLeft,
                calibration.topRight,
                calibration.bottomRight,
                calibration.bottomLeft
            )
            if (destination.any { !it.x.isFinite() || !it.y.isFinite() }) return null

            val crosses = destination.indices.map { index ->
                val a = destination[index]
                val b = destination[(index + 1) % 4]
                val c = destination[(index + 2) % 4]
                (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            }
            val allPositive = crosses.all { it > 1f }
            val allNegative = crosses.all { it < -1f }
            if (!allPositive && !allNegative) return null

            val area = abs(destination.indices.sumOf { index ->
                val a = destination[index]
                val b = destination[(index + 1) % 4]
                (a.x * b.y - b.x * a.y).toDouble()
            }) * 0.5
            if (area < 10_000.0) return null

            val source = listOf(
                InkPoint(0f, 0f),
                InkPoint(1f, 0f),
                InkPoint(1f, 1f),
                InkPoint(0f, 1f)
            )
            val matrix = Array(8) { DoubleArray(9) }
            for (i in 0 until 4) {
                val u = source[i].x.toDouble()
                val v = source[i].y.toDouble()
                val x = destination[i].x.toDouble()
                val y = destination[i].y.toDouble()
                val rowX = i * 2
                val rowY = rowX + 1

                matrix[rowX][0] = u
                matrix[rowX][1] = v
                matrix[rowX][2] = 1.0
                matrix[rowX][6] = -u * x
                matrix[rowX][7] = -v * x
                matrix[rowX][8] = x

                matrix[rowY][3] = u
                matrix[rowY][4] = v
                matrix[rowY][5] = 1.0
                matrix[rowY][6] = -u * y
                matrix[rowY][7] = -v * y
                matrix[rowY][8] = y
            }
            val solution = solve(matrix) ?: return null
            return ProjectiveTransform(solution)
        }

        private fun solve(input: Array<DoubleArray>): DoubleArray? {
            val a = Array(input.size) { input[it].clone() }
            val n = a.size
            for (column in 0 until n) {
                var pivot = column
                for (row in column + 1 until n) {
                    if (abs(a[row][column]) > abs(a[pivot][column])) pivot = row
                }
                if (abs(a[pivot][column]) < 1e-10) return null

                val swap = a[column]
                a[column] = a[pivot]
                a[pivot] = swap

                val divisor = a[column][column]
                for (j in column until n + 1) a[column][j] /= divisor
                for (row in 0 until n) {
                    if (row == column) continue
                    val factor = a[row][column]
                    for (j in column until n + 1) a[row][j] -= factor * a[column][j]
                }
            }
            return DoubleArray(n) { a[it][n] }
        }
    }
}
