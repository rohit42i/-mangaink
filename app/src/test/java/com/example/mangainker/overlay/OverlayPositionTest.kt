package com.example.mangainker.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPositionTest {
    @Test
    fun clampKeepsBubbleInsideTopAndLeftEdges() {
        assertEquals(
            OverlayPosition.Point(0, 0),
            OverlayPosition.clamp(-80, -20, 56, 56, 360, 800)
        )
    }

    @Test
    fun clampKeepsExpandedPanelInsideBottomAndRightEdges() {
        assertEquals(
            OverlayPosition.Point(68, 680),
            OverlayPosition.clamp(900, 900, 292, 120, 360, 800)
        )
    }

    @Test
    fun snapUsesNearestHorizontalEdgeAndPreservesBoundedVerticalPosition() {
        assertEquals(
            OverlayPosition.Point(0, 300),
            OverlayPosition.snapToNearestEdge(30, 300, 56, 56, 360, 800)
        )
        assertEquals(
            OverlayPosition.Point(304, 300),
            OverlayPosition.snapToNearestEdge(270, 300, 56, 56, 360, 800)
        )
    }

    @Test
    fun clampHandlesScreensSmallerThanThePanel() {
        assertEquals(
            OverlayPosition.Point(0, 0),
            OverlayPosition.clamp(40, 70, 400, 900, 360, 800)
        )
    }
}
