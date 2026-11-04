package com.example.mangainker.gesture

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

class PathPlayer(private val scope: CoroutineScope) {
    private val segmenter = GestureSegmenter()
    private var playbackJob: Job? = null
    @Volatile private var pauseRequested = false

    fun startPaths(
        paths: List<List<GesturePoint>>,
        speedPxPerSecond: Float,
        configuredSegmentMs: Int,
        runId: Long,
        isCurrentRun: (Long) -> Boolean,
        dispatch: suspend (GestureSegment, Long) -> GestureResult,
        onSegmentResult: (Int, GestureSegment, GestureResult) -> Unit,
        onPathProgress: (Int, Int) -> Unit,
        onComplete: () -> Unit,
        onFailure: (String) -> Unit,
        durationOverrideMs: Long? = null
    ) {
        stop()
        pauseRequested = false
        playbackJob = scope.launch(Dispatchers.Default) {
            try {
                val validPaths = paths.filter { it.size >= 2 }
                if (validPaths.isEmpty()) {
                    onFailure("No drawable outline paths were available.")
                    return@launch
                }
                var globalSegment = 0
                validPaths.forEachIndexed { pathIndex, points ->
                    if (!isCurrentRun(runId)) return@launch
                    val length = pathLength(points)
                    if (!length.isFinite() || length < 1f) {
                        onFailure("Outline path " + (pathIndex + 1) + " has invalid coordinates.")
                        return@launch
                    }
                    val duration = durationOverrideMs ?: (
                        (length / speedPxPerSecond.coerceIn(100f, 3_000f)) * 1_000f
                    ).toLong().coerceIn(80L, 60_000L)
                    val segments = segmenter.segment(points, duration, configuredSegmentMs)

                    for (segment in segments) {
                        while (pauseRequested && isCurrentRun(runId)) delay(16L)
                        if (!isCurrentRun(runId)) return@launch
                        val result = dispatch(segment, runId)
                        globalSegment++
                        onSegmentResult(pathIndex + 1, segment.copy(index = globalSegment), result)
                        when (result) {
                            GestureResult.Completed -> Unit
                            GestureResult.Cancelled -> {
                                if (isCurrentRun(runId)) onFailure("Android cancelled path " + (pathIndex + 1) + ".")
                                return@launch
                            }
                            is GestureResult.Failed -> {
                                if (isCurrentRun(runId)) onFailure(result.reason)
                                return@launch
                            }
                        }
                        if (isCurrentRun(runId)) delay(16L)
                    }
                    onPathProgress(pathIndex + 1, validPaths.size)
                    if (pathIndex < validPaths.lastIndex && isCurrentRun(runId)) delay(45L)
                }
                if (isCurrentRun(runId)) onComplete()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (isCurrentRun(runId)) onFailure(error.message ?: "Unknown path playback error.")
            }
        }
    }

    fun pause() {
        pauseRequested = true
    }

    fun resume() {
        pauseRequested = false
    }

    fun stop() {
        pauseRequested = false
        playbackJob?.cancel()
        playbackJob = null
    }

    private fun pathLength(points: List<GesturePoint>): Float {
        var length = 0f
        for (index in 1 until points.size) {
            length += hypot(
                (points[index].x - points[index - 1].x).toDouble(),
                (points[index].y - points[index - 1].y).toDouble()
            ).toFloat()
        }
        return length
    }
}
