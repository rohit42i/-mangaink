package com.example.mangainker.core

data class SessionState(
    val serviceConnected: Boolean = false,
    val ibisPaintForeground: Boolean = false,
    val running: Boolean = false,
    val paused: Boolean = false,
    val testArmed: Boolean = false,
    val showResultDialog: Boolean = false,
    val lastGestureResult: String = "No gesture dispatched yet",
    val message: String = "Enable both permissions to prepare a test.",
    val gestureSpeedMs: Int = 500,
    val panelAutoCollapseMs: Long = 8_000L,
    val showDebugLog: Boolean = false,
    val restrictedSettingsConfirmed: Boolean = false,
    val isError: Boolean = false,
    val startedAtElapsedMs: Long = 0L
)

sealed interface SessionCommand {
    data object Start : SessionCommand
    data object Pause : SessionCommand
    data object Resume : SessionCommand
    data object Stop : SessionCommand
    data object RunTest : SessionCommand
}

enum class DispatchGateResult { ALLOWED, BLOCKED, REJECTED }
