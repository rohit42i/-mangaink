package com.example.mangainker.overlay

/** Pure screen-coordinate helpers for keeping the floating control reachable. */
internal object OverlayPosition {
    data class Point(val x: Int, val y: Int)

    fun clamp(
        x: Int,
        y: Int,
        panelWidth: Int,
        panelHeight: Int,
        screenWidth: Int,
        screenHeight: Int
    ): Point {
        val maxX = (screenWidth - panelWidth).coerceAtLeast(0)
        val maxY = (screenHeight - panelHeight).coerceAtLeast(0)
        return Point(x.coerceIn(0, maxX), y.coerceIn(0, maxY))
    }

    fun snapToNearestEdge(
        x: Int,
        y: Int,
        panelWidth: Int,
        panelHeight: Int,
        screenWidth: Int,
        screenHeight: Int
    ): Point {
        val bounded = clamp(x, y, panelWidth, panelHeight, screenWidth, screenHeight)
        val rightEdge = (screenWidth - panelWidth).coerceAtLeast(0)
        val snappedX = if (bounded.x + panelWidth / 2 < screenWidth / 2) 0 else rightEdge
        return Point(
            snappedX,
            bounded.y
        )
    }
}
