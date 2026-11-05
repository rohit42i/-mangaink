package com.example.mangainker.core

import android.graphics.Bitmap

data class SessionState(
    val serviceConnected: Boolean = false,
    val ibisPaintForeground: Boolean = false,
    val running: Boolean = false,
    val paused: Boolean = false,
    val showResultDialog: Boolean = false,
    val lastGestureResult: String = "No gesture dispatched yet",
    val message: String = "Import a page to begin.",
    val gestureSpeedMs: Int = 500,
    val drawingSpeedPxPerSecond: Float = 950f,
    val panelAutoCollapseMs: Long = 8_000L,
    val showDebugLog: Boolean = false,
    val restrictedSettingsConfirmed: Boolean = false,
    val isError: Boolean = false,
    val startedAtElapsedMs: Long = 0L,

    val sourceBitmap: Bitmap? = null,
    val sourceName: String = "",
    val pageWidth: Int = 0,
    val pageHeight: Int = 0,
    val captureInProgress: Boolean = false,
    val scanInProgress: Boolean = false,
    val scanProgress: Float = 0f,
    val scanStatus: String = "Choose a complete outlined page.",
    val paths: List<InkPath> = emptyList(),
    val foregroundPixels: Int = 0,
    val skeletonPixels: Int = 0,
    val discardedComponents: Int = 0,
    val threshold: Int = 0,
    val thresholdOffset: Int = 0,
    val minComponentPixels: Int = 3,
    val smoothing: Float = 0.28f,
    val detailPreservation: Float = 0.78f,
    val selectionMode: Boolean = false,
    val selectedLeft: Float = 0f,
    val selectedTop: Float = 0f,
    val selectedRight: Float = 1f,
    val selectedBottom: Float = 1f,
    val scanLeftPx: Int = 0,
    val scanTopPx: Int = 0,
    val scanWidthPx: Int = 0,
    val scanHeightPx: Int = 0,

    val calibrationCorners: List<InkPoint> = emptyList(),
    val calibration: PageCalibration? = null,
    val calibrating: Boolean = false,
    val testGestureVerified: Boolean = false,
    val currentPath: Int = 0,
    val completedPaths: Int = 0,
    val totalPaths: Int = 0,
    val failedPaths: Int = 0,
    val resumeAvailable: Boolean = false,
    val progress: Float = 0f
)

sealed interface SessionCommand {
    data object Start : SessionCommand
    data object RunTest : SessionCommand
    data object Pause : SessionCommand
    data object Resume : SessionCommand
    data object Stop : SessionCommand
}

enum class DispatchGateResult { ALLOWED, BLOCKED, REJECTED }
