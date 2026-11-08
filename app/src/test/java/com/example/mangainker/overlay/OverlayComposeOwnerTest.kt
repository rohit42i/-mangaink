package com.example.mangainker.overlay

import androidx.lifecycle.Lifecycle
import com.example.mangainker.core.InkPath
import com.example.mangainker.core.InkPoint
import com.example.mangainker.core.PageCalibration
import com.example.mangainker.core.SessionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OverlayComposeOwnerTest {
    @Test
    fun overlayOwnerProvidesComposeLifecycleAndSavedStateForItsWindow() {
        val owner = OverlayComposeOwner()

        owner.attach()
        assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)
        assertNotNull(owner.savedStateRegistry)

        owner.start()
        assertEquals(Lifecycle.State.STARTED, owner.lifecycle.currentState)

        owner.destroy()
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
    }

    @Test
    fun lifecycleTransitionsAreSafeWhenStartOrDestroyIsRepeated() {
        val owner = OverlayComposeOwner()

        owner.attach()
        owner.attach()
        owner.start()
        owner.start()
        owner.destroy()
        owner.destroy()

        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
    }
    @Test
    fun overlayTraceActionRequiresAccessibilityForegroundAndCalibration() {
        val calibrated = PageCalibration(
            InkPoint(10f, 10f),
            InkPoint(500f, 10f),
            InkPoint(500f, 800f),
            InkPoint(10f, 800f)
        )
        val base = SessionState(
            serviceConnected = true,
            ibisPaintForeground = true,
            calibration = calibrated
        )

        assertTrue(canRunOverlayPrimaryAction(base))
        assertFalse(canRunOverlayPrimaryAction(base.copy(serviceConnected = false)))
        assertFalse(canRunOverlayPrimaryAction(base.copy(ibisPaintForeground = false)))
        assertFalse(canRunOverlayPrimaryAction(base.copy(calibration = null)))
        assertFalse(canRunOverlayPrimaryAction(base.copy(scanInProgress = true)))
        assertFalse(canRunOverlayPrimaryAction(base.copy(captureInProgress = true)))
        assertFalse(canRunOverlayPrimaryAction(base.copy(calibrating = true)))
        assertFalse(canRunOverlayPrimaryAction(base.copy(showResultDialog = true)))
    }

    @Test
    fun verifiedTraceActionRequiresScannedPaths() {
        val calibrated = PageCalibration(
            InkPoint(10f, 10f),
            InkPoint(500f, 10f),
            InkPoint(500f, 800f),
            InkPoint(10f, 800f)
        )
        val path = InkPath(listOf(InkPoint(1f, 1f), InkPoint(2f, 2f)), 1.4f)
        val base = SessionState(
            serviceConnected = true,
            ibisPaintForeground = true,
            calibration = calibrated,
            testGestureVerified = true,
            paths = listOf(path)
        )

        assertTrue(canRunOverlayPrimaryAction(base))
        assertFalse(canRunOverlayPrimaryAction(base.copy(paths = emptyList())))
        assertFalse(canRunOverlayPrimaryAction(base.copy(running = true)))
        assertFalse(canRunOverlayPrimaryAction(base.copy(paused = true)))
    }

}
