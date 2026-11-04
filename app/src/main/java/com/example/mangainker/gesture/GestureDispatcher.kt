package com.example.mangainker.gesture

import android.graphics.PointF

sealed interface GestureResult {
    data object Completed : GestureResult
    data object Cancelled : GestureResult
    data class Failed(val reason: String) : GestureResult
}

interface GestureDispatcher {
    suspend fun drawPath(
        points: List<PointF>,
        durationMs: Long,
        segmentIndex: Int,
        runId: Long
    ): GestureResult
}
