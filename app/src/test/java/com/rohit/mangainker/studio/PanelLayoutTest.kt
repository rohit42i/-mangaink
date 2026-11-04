package com.rohit.mangainker.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelLayoutTest {
    @Test fun panelPresetsCreateExpectedPanelCounts() {
        assertEquals(0, PanelLayout.rectangles(PanelPreset.KEEP_ORIGINAL, 0.02f).size)
        assertEquals(1, PanelLayout.rectangles(PanelPreset.SINGLE, 0.02f).size)
        assertEquals(2, PanelLayout.rectangles(PanelPreset.TWO_ROWS, 0.02f).size)
        assertEquals(2, PanelLayout.rectangles(PanelPreset.TWO_COLUMNS, 0.02f).size)
        assertEquals(3, PanelLayout.rectangles(PanelPreset.THREE_STRIPS, 0.02f).size)
        assertEquals(4, PanelLayout.rectangles(PanelPreset.FOUR_GRID, 0.02f).size)
        assertEquals(6, PanelLayout.rectangles(PanelPreset.SIX_GRID, 0.02f).size)
        assertEquals(7, PanelLayout.rectangles(PanelPreset.SEVEN_PANELS, 0.02f).size)
    }

    @Test fun panelRectanglesStayInsidePageAndDoNotOverlapAcrossGutters() {
        for (preset in PanelPreset.values()) {
            val panels = PanelLayout.rectangles(preset, 0.025f)
            panels.forEach {
                assertTrue(it.left >= 0f && it.top >= 0f)
                assertTrue(it.right <= 1f && it.bottom <= 1f)
            }
            for (i in panels.indices) for (j in i + 1 until panels.size) {
                val a = panels[i]
                val b = panels[j]
                val overlapX = minOf(a.right, b.right) - maxOf(a.left, b.left)
                val overlapY = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
                assertTrue(overlapX <= 0f || overlapY <= 0f)
            }
        }
    }

    @Test fun toneCanTargetOnePanelOrAllPanels() {
        val all = StudioSettings(panelPreset = PanelPreset.FOUR_GRID, tonePattern = TonePattern.DOTS)
        val one = all.copy(tonePanelIndex = 2)
        assertEquals(4, PanelLayout.tonePanelRectangles(all).size)
        assertEquals(1, PanelLayout.tonePanelRectangles(one).size)
    }

    @Test fun keepExistingPanelsDoesNotAddBordersAndAllowsWholePageTone() {
        val settings = StudioSettings(panelPreset = PanelPreset.KEEP_ORIGINAL, tonePattern = TonePattern.DOTS)
        assertTrue(PanelLayout.rectangles(PanelPreset.KEEP_ORIGINAL, 0.02f).isEmpty())
        assertEquals(listOf(PageRect(0f, 0f, 1f, 1f)), PanelLayout.tonePanelRectangles(settings))
        assertTrue(PanelLayout.tonePanelRectangles(settings.copy(tonePanelIndex = 0)).isEmpty())
    }
}
