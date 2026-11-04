package com.rohit.mangainker.core

import android.graphics.Bitmap
import java.util.concurrent.CopyOnWriteArrayList

/** In-memory session only. Imported artwork and path geometry are never persisted. */
object SessionStore {
    @Volatile var sourceBitmap: Bitmap? = null
    @Volatile var imageWidth: Int = 0
    @Volatile var imageHeight: Int = 0
    @Volatile var paths: List<InkPath> = emptyList()
    @Volatile var calibration: PageCalibration? = null
    @Volatile var testGestureCompleted: Boolean = false
    @Volatile var testGestureAcknowledged: Boolean = false
    @Volatile var isRunning: Boolean = false
    @Volatile var isPaused: Boolean = false
    @Volatile var stopRequested: Boolean = false
    @Volatile var currentPath: Int = 0
    @Volatile var successfulPaths: Int = 0
    @Volatile var failedPaths: Int = 0
    @Volatile var status: String = "Import an outlined page to begin"
    @Volatile var activePackage: String = ""
    @Volatile var isTargetAppForeground: Boolean = false
    @Volatile var drawingSpeedPxPerSecond: Float = 950f
    @Volatile var maxStrokeDurationMs: Long = 1_000L
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun setPlan(bitmap: Bitmap, plan: List<InkPath>) {
        stopRequested = true
        isRunning = false
        isPaused = false
        sourceBitmap = bitmap
        imageWidth = bitmap.width
        imageHeight = bitmap.height
        paths = plan.toList()
        calibration = null
        testGestureCompleted = false
        testGestureAcknowledged = false
        currentPath = 0
        successfulPaths = 0
        failedPaths = 0
        testGestureCompleted = false
        testGestureAcknowledged = false
        stopRequested = false
        status = "${plan.size} paths ready for preview"
        notifyChanged()
    }

    fun clearPlan() {
        stopRequested = true
        sourceBitmap?.let { if (!it.isRecycled) it.recycle() }
        sourceBitmap = null
        paths = emptyList()
        imageWidth = 0
        imageHeight = 0
        calibration = null
        testGestureCompleted = false
        testGestureAcknowledged = false
        isRunning = false
        isPaused = false
        currentPath = 0
        successfulPaths = 0
        failedPaths = 0
        activePackage = ""
        isTargetAppForeground = false
        status = "Import an outlined page to begin"
        notifyChanged()
    }


    /** Confirm only after the user has visually checked and manually undone the test mark. */
    fun confirmTestCurve(): Boolean {
        if (!testGestureCompleted || paths.isEmpty() || calibration == null || isRunning) return false
        testGestureAcknowledged = true
        status = "Test curve verified and cleared. Ready to ink."
        notifyChanged()
        return true
    }

    fun observe(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeObserver(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyChanged() {
        listeners.forEach { listener -> runCatching { listener() } }
    }
}
