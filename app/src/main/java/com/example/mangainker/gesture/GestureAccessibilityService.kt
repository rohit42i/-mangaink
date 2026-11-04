package com.example.mangainker.gesture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.example.mangainker.MangaInkerApp
import com.example.mangainker.core.DispatchGateResult
import com.example.mangainker.core.Logger
import com.example.mangainker.core.SessionCommand
import com.example.mangainker.core.SessionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class GestureAccessibilityService : AccessibilityService(), GestureDispatcher {
    private lateinit var controller: SessionController
    private val mainHandler = Handler(Looper.getMainLooper())
    private var volumeStopTask: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        controller = (application as MangaInkerApp).sessionController
        controller.registerDispatcher(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            controller.onForegroundPackageChanged(event.packageName?.toString().orEmpty())
        }
    }

    override fun onInterrupt() {
        if (::controller.isInitialized) {
            controller.unregisterDispatcher(this, "Accessibility service interrupted")
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (::controller.isInitialized) controller.unregisterDispatcher(this, "Accessibility service was unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        volumeStopTask?.let(mainHandler::removeCallbacks)
        if (::controller.isInitialized) controller.unregisterDispatcher(this, "Accessibility service destroyed")
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN || !::controller.isInitialized) return false

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && controller.state.value.running) {
            volumeStopTask?.let(mainHandler::removeCallbacks)
            val task = Runnable {
                if (controller.state.value.running) {
                    Logger.warning("Hardware stop triggered by volume-down long press")
                    controller.dispatch(SessionCommand.Stop)
                }
            }
            volumeStopTask = task
            mainHandler.postDelayed(task, 1_500L)
        } else if (event.action == KeyEvent.ACTION_UP) {
            volumeStopTask?.let(mainHandler::removeCallbacks)
            volumeStopTask = null
        }
        return false
    }

    override suspend fun drawPath(
        points: List<PointF>,
        durationMs: Long,
        segmentIndex: Int,
        runId: Long
    ): GestureResult {
        if (points.size < 2) return GestureResult.Failed("Gesture segment has fewer than two points.")
        val duration = durationMs.coerceIn(1L, 500L)
        val path = withContext(Dispatchers.Default) {
            Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
            .build()

        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val gate = controller.dispatchIfCurrent(runId) {
                    dispatchGesture(
                        gesture,
                        object : GestureResultCallback() {
                            override fun onCompleted(gestureDescription: GestureDescription?) {
                                Logger.info("segment=$segmentIndex duration=${duration}ms result=Completed")
                                controller.updateLastGestureResult("Segment " + segmentIndex + ": Completed")
                                if (continuation.isActive) continuation.resume(GestureResult.Completed)
                            }

                            override fun onCancelled(gestureDescription: GestureDescription?) {
                                Logger.warning("segment=$segmentIndex duration=${duration}ms result=Cancelled")
                                controller.updateLastGestureResult("Segment " + segmentIndex + ": Cancelled")
                                if (continuation.isActive) continuation.resume(GestureResult.Cancelled)
                            }
                        },
                        mainHandler
                    )
                }

                when (gate) {
                    DispatchGateResult.ALLOWED -> Unit
                    DispatchGateResult.BLOCKED -> {
                        Logger.warning("segment=$segmentIndex duration=${duration}ms result=BlockedByStopGate")
                        if (continuation.isActive) continuation.resume(GestureResult.Cancelled)
                    }
                    DispatchGateResult.REJECTED -> {
                        val reason = "Android rejected dispatchGesture for segment " + segmentIndex
                        Logger.error("segment=$segmentIndex duration=${duration}ms result=Failed reason=$reason")
                        controller.updateLastGestureResult("Segment " + segmentIndex + ": " + reason)
                        if (continuation.isActive) continuation.resume(GestureResult.Failed(reason))
                    }
                }

                continuation.invokeOnCancellation {
                    Logger.warning("segment=$segmentIndex duration=${duration}ms result=CancelledByPlayer; an accepted stroke may finish")
                }
            }
        }
    }
}
