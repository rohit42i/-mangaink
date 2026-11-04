package com.example.mangainker.gesture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.mangainker.core.AppState
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class GestureAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { activePackage = event?.packageName?.toString().orEmpty() }

    val isIbisPaintForeground: Boolean
        get() = activePackage in setOf("jp.ne.ibis.ibispaintx.app", "jp.ne.ibis.ibispaint.app")
    override fun onInterrupt() { Log.w(TAG, "accessibility interrupted"); AppState.stop() }

    override fun onDestroy() {
        AppState.stop()
        if (instance === this) instance = null
        super.onDestroy()
    }

    suspend fun drawPath(points: List<PointF>, durationMs: Long): Boolean {
        if (points.size < 2) return false
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val duration = durationMs.coerceIn(1L, 500L)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
            .build()

        return suspendCancellableCoroutine { continuation ->
            val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i(TAG, "completed duration=${duration}ms points=${points.size}")
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "cancelled duration=${duration}ms points=${points.size}")
                    if (continuation.isActive) continuation.resume(false)
                }
            }, null)
            Log.i(TAG, "dispatch accepted=$accepted duration=${duration}ms")
            if (!accepted && continuation.isActive) {
                Log.w(TAG, "cancelled reason=dispatch rejected duration=${duration}ms")
                continuation.resume(false)
            }
            continuation.invokeOnCancellation {
                Log.w(TAG, "player cancelled; active Android gesture may finish")
            }
        }
    }

    companion object {
        const val TAG = "MI_GESTURE"
        @Volatile private var activePackage: String = ""

        @Volatile
        var instance: GestureAccessibilityService? = null
            private set
    }
}
