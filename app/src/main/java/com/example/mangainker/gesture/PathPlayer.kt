package com.example.mangainker.gesture

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.tanh

class PathPlayer(private val scope: CoroutineScope) {
    private val segmenter = GestureSegmenter()
    private var playbackJob: Job? = null
    @Volatile private var pauseRequested = false

    fun start(
        widthPx: Int,
        heightPx: Int,
        totalDurationMs: Long,
        configuredSegmentMs: Int,
        runId: Long,
        isCurrentRun: (Long) -> Boolean,
        dispatch: suspend (GestureSegment, Long) -> GestureResult,
        onSegmentResult: (GestureSegment, GestureResult) -> Unit,
        onComplete: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        stop()
        pauseRequested = false
        playbackJob = scope.launch(Dispatchers.Default) {
            try {
                val points = makeTestCurve(widthPx, heightPx)
                val segments = segmenter.segment(points, totalDurationMs, configuredSegmentMs)
                for (segment in segments) {
                    while (pauseRequested && isCurrentRun(runId)) delay(16L)
                    if (!isCurrentRun(runId)) return@launch

                    val result = dispatch(segment, runId)
                    onSegmentResult(segment, result)
                    when (result) {
                        GestureResult.Completed -> Unit
                        GestureResult.Cancelled -> {
                            if (isCurrentRun(runId)) onFailure("Android cancelled segment " + segment.index)
                            return@launch
                        }
                        is GestureResult.Failed -> {
                            if (isCurrentRun(runId)) onFailure(result.reason)
                            return@launch
                        }
                    }
                    if (segment.index < segments.size && isCurrentRun(runId)) delay(16L)
                }
                if (isCurrentRun(runId)) onComplete()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (isCurrentRun(runId)) onFailure(error.message ?: "Unknown path playback error")
            }
        }
    }

    fun pause() { pauseRequested = true }
    fun resume() { pauseRequested = false }

    fun stop() {
        pauseRequested = false
        playbackJob?.cancel()
        playbackJob = null
    }

    companion object {
        fun makeTestCurve(widthPx: Int, heightPx: Int): List<GesturePoint> {
            val centerX = widthPx / 2f
            val centerY = heightPx / 2f
            val amplitude = minOf(widthPx * 0.18f, 150f)
            val scale = tanh(2.5)
            return (0..60).map { index ->
                val t = index / 60.0
                val normalizedX = tanh(5.0 * (t - 0.5)) / scale
                GesturePoint(
                    centerX + (amplitude * normalizedX).toFloat(),
                    centerY - 200f + (400f * t).toFloat()
                )
            }
        }
    }
}
