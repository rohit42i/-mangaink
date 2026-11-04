package com.rohit.mangainker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.rohit.mangainker.core.InkPoint
import com.rohit.mangainker.core.PageCalibration
import com.rohit.mangainker.core.ProjectiveTransform
import com.rohit.mangainker.core.SessionStore
import kotlin.math.hypot

class InkingAccessibilityService : AccessibilityService() {
    private var planIndex = 0
    private var consecutiveFailures = 0
    private var gestureInFlight = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Only the event package is checked. Window content is never requested or read.
        val packageName = event?.packageName?.toString().orEmpty()
        if (packageName.isNotBlank()) {
            SessionStore.activePackage = packageName
            SessionStore.isTargetAppForeground = packageName in TARGET_PACKAGES
            if (SessionStore.isRunning && !SessionStore.isTargetAppForeground) {
                SessionStore.isPaused = true
                SessionStore.status = "Target drawing app lost focus. Pausing after the active stroke."
                SessionStore.notifyChanged()
            }
            if (!SessionStore.isTargetAppForeground && !SessionStore.isRunning) {
                SessionStore.status = "Open ibisPaint X before testing or inking."
                SessionStore.notifyChanged()
            }
        }
    }

    override fun onInterrupt() {
        requestStop("Android interrupted the drawing service")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        requestStop("Drawing service disconnected")
        super.onDestroy()
    }

    fun drawTestStroke(): Boolean {
        if (SessionStore.isRunning || !SessionStore.isTargetAppForeground) return false
        SessionStore.testGestureCompleted = false
        SessionStore.testGestureAcknowledged = false
        val transform = ProjectiveTransform.from(SessionStore.calibration ?: return false) ?: return false
        val start = transform.map(0.35f, 0.50f)
        val control = transform.map(0.50f, 0.46f)
        val end = transform.map(0.65f, 0.50f)
        val path = Path().apply {
            moveTo(start.x, start.y)
            quadTo(control.x, control.y, end.x, end.y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 550L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                SessionStore.testGestureCompleted = true
                SessionStore.status = "Android completed the test gesture. Check ibisPaint, undo the curve, then confirm."
                SessionStore.notifyChanged()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                SessionStore.testGestureCompleted = false
                SessionStore.testGestureAcknowledged = false
                SessionStore.status = "Test gesture was cancelled. Recalibrate and try again."
                SessionStore.notifyChanged()
            }
        }, null)
    }

    fun startInking(): Boolean {
        if (!SessionStore.isTargetAppForeground) {
            SessionStore.status = "Open ibisPaint X and keep its canvas active before starting."
            SessionStore.notifyChanged()
            return false
        }
        if (!SessionStore.testGestureAcknowledged) {
            SessionStore.status = "Run the test curve, check that it appears in ibisPaint, undo it, then confirm."
            SessionStore.notifyChanged()
            return false
        }
        if (SessionStore.paths.isEmpty()) {
            SessionStore.status = "Import and process an outline page first"
            SessionStore.notifyChanged()
            return false
        }
        val calibration = SessionStore.calibration
        if (calibration == null || ProjectiveTransform.from(calibration) == null) {
            SessionStore.status = "Calibrate the four page corners first"
            SessionStore.notifyChanged()
            return false
        }
        if (SessionStore.isRunning && !SessionStore.isPaused) return false
        if (SessionStore.isPaused && gestureInFlight) {
            SessionStore.status = "The active stroke is still finishing. Resume after it completes."
            SessionStore.notifyChanged()
            return false
        }

        if (SessionStore.isPaused) {
            SessionStore.isPaused = false
            SessionStore.stopRequested = false
            SessionStore.status = "Resuming after the current stroke"
        } else {
            planIndex = 0
            consecutiveFailures = 0
            SessionStore.currentPath = 0
            SessionStore.successfulPaths = 0
            SessionStore.failedPaths = 0
            SessionStore.stopRequested = false
            SessionStore.isRunning = true
            SessionStore.isPaused = false
            SessionStore.status = "Inking started"
        }
        SessionStore.notifyChanged()
        dispatchNext()
        return true
    }

    fun pauseInking() {
        if (!SessionStore.isRunning) return
        SessionStore.isPaused = true
        SessionStore.status = "Pausing after the active stroke"
        SessionStore.notifyChanged()
    }

    fun resumeInking() {
        if (SessionStore.isPaused) startInking()
    }

    fun stopInking() {
        if (!SessionStore.isRunning && !SessionStore.isPaused) return
        SessionStore.stopRequested = true
        if (SessionStore.isPaused && !gestureInFlight) {
            finish("Stopped by user")
        } else {
            SessionStore.status = "Stopping after the active stroke"
            SessionStore.notifyChanged()
        }
    }

    private fun dispatchNext() {
        if (!SessionStore.isTargetAppForeground && !SessionStore.stopRequested) {
            SessionStore.isPaused = true
            SessionStore.status = "ibisPaint is no longer foreground. Session paused safely."
            SessionStore.notifyChanged()
            return
        }
        if (SessionStore.stopRequested) {
            finish("Stopped by user")
            return
        }
        if (SessionStore.isPaused) {
            SessionStore.status = "Paused"
            SessionStore.notifyChanged()
            return
        }
        val paths = SessionStore.paths
        if (planIndex >= paths.size) {
            finish("Inking completed")
            return
        }
        val transform = ProjectiveTransform.from(SessionStore.calibration ?: run {
            finish("Calibration missing")
            return
        }) ?: run {
            finish("Calibration is invalid. Set the corners again.")
            return
        }
        val sourcePath = paths[planIndex]
        if (sourcePath.points.size < 2) {
            planIndex++
            dispatchNext()
            return
        }
        val displayPath = Path()
        var previous: InkPoint? = null
        var distance = 0.0
        sourcePath.points.forEachIndexed { index, point ->
            val u = (point.x / (SessionStore.imageWidth - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
            val v = (point.y / (SessionStore.imageHeight - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
            val mapped = transform.map(u, v)
            if (!mapped.x.isFinite() || !mapped.y.isFinite()) return@forEachIndexed
            if (index == 0 || previous == null) displayPath.moveTo(mapped.x, mapped.y)
            else {
                distance += hypot((mapped.x - previous!!.x).toDouble(), (mapped.y - previous!!.y).toDouble())
                displayPath.lineTo(mapped.x, mapped.y)
            }
            previous = mapped
        }
        if (distance < 1.0 || previous == null) {
            planIndex++
            dispatchNext()
            return
        }
        val speed = SessionStore.drawingSpeedPxPerSecond.coerceIn(250f, 2_500f)
        val duration = ((distance / speed) * 1_000.0).toLong()
            .coerceIn(80L, SessionStore.maxStrokeDurationMs.coerceIn(250L, 1_500L))
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(displayPath, 0L, duration))
            .build()

        SessionStore.status = "Drawing path ${planIndex + 1} of ${paths.size}"
        SessionStore.currentPath = planIndex
        SessionStore.notifyChanged()
        gestureInFlight = true
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                gestureInFlight = false
                consecutiveFailures = 0
                SessionStore.successfulPaths++
                planIndex++
                dispatchNext()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                gestureInFlight = false
                SessionStore.failedPaths++
                consecutiveFailures++
                if (SessionStore.stopRequested) {
                    finish("Stopped by user")
                } else {
                    SessionStore.isPaused = true
                    SessionStore.status = "Gesture cancelled. Session paused; check ibisPaint before resuming."
                    SessionStore.notifyChanged()
                    if (consecutiveFailures >= 3) finish("Stopped after three cancelled strokes")
                }
            }
        }, null)
        if (!accepted) {
            gestureInFlight = false
            SessionStore.failedPaths++
            consecutiveFailures++
            SessionStore.isPaused = true
            SessionStore.status = "Android rejected the gesture. Session paused."
            SessionStore.notifyChanged()
            if (consecutiveFailures >= 3) finish("Stopped after repeated rejected gestures")
        }
    }

    private fun requestStop(message: String) {
        if (SessionStore.isRunning || SessionStore.isPaused) finish(message)
    }

    private fun finish(message: String) {
        SessionStore.isRunning = false
        SessionStore.isPaused = false
        SessionStore.stopRequested = false
        SessionStore.status = "$message • ${SessionStore.successfulPaths} drawn, ${SessionStore.failedPaths} failed"
        SessionStore.notifyChanged()
    }

    companion object {
        private val TARGET_PACKAGES = setOf(
            "jp.ne.ibis.ibispaintx.app",
            "jp.ne.ibis.ibispaint.app"
        )

        @Volatile var instance: InkingAccessibilityService? = null
            private set
    }
}
