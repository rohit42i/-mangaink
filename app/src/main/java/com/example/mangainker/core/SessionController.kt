package com.example.mangainker.core

import android.content.Context
import android.os.SystemClock
import com.example.mangainker.gesture.GestureDispatcher
import com.example.mangainker.gesture.GestureResult
import com.example.mangainker.gesture.PathPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

class SessionController(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pathPlayer = PathPlayer(scope)
    private val runIds = AtomicLong(0L)
    private val dispatchLock = Any()
    private val mutableState = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    @Volatile private var dispatcher: GestureDispatcher? = null
    private var activeRunId = 0L
    private var dispatchAllowed = false

    fun dispatch(command: SessionCommand) {
        when (command) {
            SessionCommand.RunTest -> armTest()
            SessionCommand.Start -> startTest()
            SessionCommand.Pause -> pause()
            SessionCommand.Resume -> resume()
            SessionCommand.Stop -> stop()
        }
    }

    fun registerDispatcher(value: GestureDispatcher) {
        dispatcher = value
        mutableState.update { it.copy(serviceConnected = true, isError = false, message = "Accessibility service connected.") }
        Logger.info("Accessibility service connected")
    }

    fun unregisterDispatcher(value: GestureDispatcher, reason: String) {
        if (dispatcher !== value) return
        dispatcher = null
        mutableState.update { it.copy(serviceConnected = false, ibisPaintForeground = false) }
        Logger.warning(reason)
        if (mutableState.value.running) {
            stopWithError("Accessibility disconnected during the run. Re-enable the service, then try again.")
        } else {
            mutableState.update { it.copy(message = "Accessibility service disconnected. Re-enable it in Settings.") }
        }
    }

    fun onForegroundPackageChanged(packageName: String) {
        val isIbis = packageName.contains("ibispaint", ignoreCase = true)
        mutableState.update { it.copy(ibisPaintForeground = isIbis) }
        if (mutableState.value.running && !isIbis) {
            stopWithError("ibisPaint left the foreground. The run was stopped for safety.")
        }
    }

    fun dispatchIfCurrent(runId: Long, action: () -> Boolean): DispatchGateResult =
        synchronized(dispatchLock) {
            if (!dispatchAllowed || activeRunId != runId) DispatchGateResult.BLOCKED
            else if (action()) DispatchGateResult.ALLOWED
            else DispatchGateResult.REJECTED
        }

    fun updateLastGestureResult(result: String) {
        mutableState.update { it.copy(lastGestureResult = result) }
    }

    fun shouldConfirmStop(): Boolean {
        val snapshot = mutableState.value
        return snapshot.running && snapshot.startedAtElapsedMs > 0L &&
            SystemClock.elapsedRealtime() - snapshot.startedAtElapsedMs > 30_000L
    }

    fun recordTestResult(answer: String) {
        val normalized = answer.uppercase()
        Logger.info("TEST_RESULT | Did ibisPaint draw a line? " + normalized)
        mutableState.update {
            it.copy(
                showResultDialog = false,
                message = "Test answer recorded in the MI_GESTURE app log.",
                lastGestureResult = "User confirmed: " + normalized
            )
        }
    }

    fun confirmRestrictedSettingsUnlocked() {
        mutableState.update { it.copy(restrictedSettingsConfirmed = true) }
        Logger.info("Restricted settings manually confirmed by user")
    }

    fun setGestureSpeed(value: Int) {
        mutableState.update { it.copy(gestureSpeedMs = value.coerceIn(200, 800)) }
    }

    fun setPanelAutoCollapse(value: Long) {
        mutableState.update { it.copy(panelAutoCollapseMs = value.coerceIn(0L, 30_000L)) }
    }

    fun setShowDebugLog(value: Boolean) {
        mutableState.update { it.copy(showDebugLog = value) }
    }

    private fun armTest() {
        mutableState.update {
            it.copy(
                testArmed = true,
                isError = false,
                message = "Test ready. Switch to ibisPaint X, choose a brush, then tap Start on the floating panel."
            )
        }
        Logger.info("Test stroke armed")
    }

    private fun startTest() {
        val snapshot = mutableState.value
        val activeDispatcher = dispatcher
        if (!snapshot.serviceConnected || activeDispatcher == null) {
            mutableState.update { it.copy(isError = true, message = "Accessibility is not connected. Open Accessibility settings and enable Manga Inker.") }
            return
        }
        if (!snapshot.ibisPaintForeground) {
            mutableState.update { it.copy(isError = false, message = "Open ibisPaint X and tap Start while ibisPaint is in the foreground.") }
            return
        }
        if (snapshot.running) return

        val runId = runIds.incrementAndGet()
        synchronized(dispatchLock) {
            activeRunId = runId
            dispatchAllowed = true
        }
        val startedAt = SystemClock.elapsedRealtime()
        mutableState.update {
            it.copy(
                running = true,
                paused = false,
                testArmed = false,
                isError = false,
                startedAtElapsedMs = startedAt,
                showResultDialog = false,
                message = "Running the 400 px test stroke."
            )
        }
        Logger.info("run=" + runId + " started totalDuration=1500ms")

        val metrics = appContext.resources.displayMetrics
        pathPlayer.start(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            totalDurationMs = 1_500L,
            configuredSegmentMs = snapshot.gestureSpeedMs,
            runId = runId,
            isCurrentRun = ::isCurrentRun,
            dispatch = { segment, activeId ->
                activeDispatcher.drawPath(segment.points, segment.durationMs, segment.index, activeId)
            },
            onSegmentResult = { segment, result ->
                val label = when (result) {
                    GestureResult.Completed -> "Completed"
                    GestureResult.Cancelled -> "Cancelled"
                    is GestureResult.Failed -> "Failed: " + result.reason
                }
                updateLastGestureResult("Segment " + segment.index + ": " + label)
            },
            onComplete = { completeRun(runId) },
            onFailure = { reason -> stopWithError(reason, runId) }
        )
    }

    private fun pause() {
        if (!mutableState.value.running || mutableState.value.paused) return
        pathPlayer.pause()
        mutableState.update { it.copy(paused = true, message = "Pausing after the active segment finishes.") }
        Logger.info("Pause requested")
    }

    private fun resume() {
        if (!mutableState.value.running || !mutableState.value.paused) return
        pathPlayer.resume()
        mutableState.update { it.copy(paused = false, message = "Resumed.") }
        Logger.info("Resume requested")
    }

    private fun stop() {
        stopInternals()
        mutableState.update {
            it.copy(
                running = false,
                paused = false,
                testArmed = false,
                showResultDialog = false,
                startedAtElapsedMs = 0L,
                isError = false,
                message = "Stopped. No further gesture segments will be dispatched."
            )
        }
        Logger.warning("Stop requested; queued segments cleared")
    }

    private fun stopWithError(reason: String, expectedRunId: Long? = null) {
        if (expectedRunId != null && !isCurrentRun(expectedRunId)) return
        stopInternals()
        mutableState.update {
            it.copy(
                running = false,
                paused = false,
                testArmed = false,
                showResultDialog = false,
                startedAtElapsedMs = 0L,
                isError = true,
                message = reason
            )
        }
        Logger.error(reason)
    }

    private fun stopInternals() {
        synchronized(dispatchLock) {
            dispatchAllowed = false
            activeRunId = runIds.incrementAndGet()
        }
        pathPlayer.stop()
    }

    private fun completeRun(runId: Long) {
        if (!isCurrentRun(runId)) return
        synchronized(dispatchLock) { dispatchAllowed = false }
        mutableState.update {
            it.copy(
                running = false,
                paused = false,
                startedAtElapsedMs = 0L,
                showResultDialog = true,
                isError = false,
                message = "Android reported that the gesture sequence completed. Check ibisPaint for a visible line."
            )
        }
        Logger.info("run=" + runId + " result=DispatchCompleted; visual result must be confirmed by the user")
    }

    private fun isCurrentRun(runId: Long): Boolean =
        synchronized(dispatchLock) { dispatchAllowed && activeRunId == runId }
}
