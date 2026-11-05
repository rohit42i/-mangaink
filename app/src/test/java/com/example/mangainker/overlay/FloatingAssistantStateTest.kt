package com.example.mangainker.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingAssistantStateTest {
    @Test
    fun enableRequestDoesNotPretendTheServiceIsAlreadyRunning() {
        val state = FloatingAssistantState().requestEnable()

        assertTrue(state.requested)
        assertFalse(state.running)
        assertNull(state.error)
    }

    @Test
    fun serviceStartAndExplicitDisableKeepRequestedAndActualStateSeparate() {
        val running = FloatingAssistantState()
            .requestEnable()
            .serviceStarted()

        assertTrue(running.requested)
        assertTrue(running.running)

        val stopping = running.requestDisable()
        assertFalse(stopping.requested)
        assertTrue(stopping.running)

        val stopped = stopping.serviceStopped()
        assertFalse(stopped.requested)
        assertFalse(stopped.running)
        assertNull(stopped.error)
    }

    @Test
    fun unexpectedServiceStopIsReportedWhileAssistantIsStillRequested() {
        val stopped = FloatingAssistantState()
            .requestEnable()
            .serviceStarted()
            .serviceStopped()

        assertTrue(stopped.requested)
        assertFalse(stopped.running)
        assertNotNull(stopped.error)
    }

    @Test
    fun missingOverlayPermissionLeavesAssistantRequestedButNotRunning() {
        val waiting = FloatingAssistantState()
            .requestEnable()
            .awaitingPermission()

        assertTrue(waiting.requested)
        assertFalse(waiting.running)
        assertNotNull(waiting.error)
    }
}
