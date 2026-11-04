package com.example.mangainker.gesture

data class GesturePoint(val x: Float, val y: Float)

sealed interface GestureResult {
    data object Completed : GestureResult
    data object Cancelled : GestureResult
    data class Failed(val reason: String) : GestureResult
}

interface GestureDispatcher {
    suspend fun drawPath(
        points: List<GesturePoint>,
        durationMs: Long,
        segmentIndex: Int,
        runId: Long
    ): GestureResult
}
