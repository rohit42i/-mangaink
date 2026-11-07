package com.example.mangainker.overlay

import androidx.lifecycle.Lifecycle
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
}
