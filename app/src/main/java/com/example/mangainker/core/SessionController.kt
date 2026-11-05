package com.example.mangainker.core

import android.content.Context
import android.graphics.Point
import android.net.Uri
import android.os.SystemClock
import android.view.WindowManager
import com.example.mangainker.gesture.GestureDispatcher
import com.example.mangainker.gesture.GesturePoint
import com.example.mangainker.gesture.GestureResult
import com.example.mangainker.gesture.PathPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.floor
import kotlin.math.ceil

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
    private var scanJob: Job? = null
    private var importJob: Job? = null

    fun dispatch(command: SessionCommand) {
        when (command) {
            SessionCommand.Start -> startTrace()
            SessionCommand.RunTest -> startTest()
            SessionCommand.Pause -> pause()
            SessionCommand.Resume -> resume()
            SessionCommand.Stop -> stop()
        }
    }

    fun importPage(uri: Uri) {
        if (mutableState.value.running) stop()
        scanJob?.cancel()
        importJob?.cancel()
        mutableState.update {
            it.copy(
                scanInProgress = true,
                scanProgress = 0f,
                scanStatus = "Opening the selected page…",
                paths = emptyList(),
                calibration = null,
                calibrationCorners = emptyList(),
                calibrating = false,
                testGestureVerified = false,
                isError = false,
                message = "Importing the image locally."
            )
        }
        importJob = scope.launch(Dispatchers.IO) {
            try {
                val bitmap = BitmapTools.decodeForProcessing(appContext.contentResolver, uri)
                currentCoroutineContext().ensureActive()
                val oldBitmap = mutableState.value.sourceBitmap
                mutableState.update {
                    it.copy(
                        sourceBitmap = bitmap,
                        sourceName = "Imported page",
                        pageWidth = bitmap.width,
                        pageHeight = bitmap.height,
                        paths = emptyList(),
                        selectedLeft = 0f,
                        selectedTop = 0f,
                        selectedRight = 1f,
                        selectedBottom = 1f,
                        selectionMode = false,
                        scanLeftPx = 0,
                        scanTopPx = 0,
                        scanWidthPx = bitmap.width,
                        scanHeightPx = bitmap.height,
                        foregroundPixels = 0,
                        skeletonPixels = 0,
                        discardedComponents = 0,
                        threshold = 0,
                        calibration = null,
                        calibrationCorners = emptyList(),
                        calibrating = false,
                        testGestureVerified = false,
                        scanInProgress = false,
                        scanProgress = 0f,
                        scanStatus = "Page loaded. Scan the complete page to extract its outlines.",
                        isError = false,
                        message = "Page loaded at " + bitmap.width + " × " + bitmap.height + " pixels."
                    )
                }
                if (oldBitmap != null && oldBitmap !== bitmap && !oldBitmap.isRecycled) oldBitmap.recycle()
                Logger.info("Page imported locally size=" + bitmap.width + "x" + bitmap.height)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(
                        scanInProgress = false,
                        scanProgress = 0f,
                        scanStatus = error.message ?: "The selected image could not be loaded.",
                        isError = true,
                        message = "Image import failed."
                    )
                }
                Logger.error("Page import failed: " + (error.message ?: error.javaClass.simpleName))
            }
        }
    }

    /**
     * Receives a single frame from the explicitly approved MediaProjection session.
     * The bitmap stays in memory and is immediately downsampled before local scanning.
     */
    fun importCapturedBitmap(captured: android.graphics.Bitmap) {
        if (mutableState.value.running) stop()
        scanJob?.cancel()
        importJob?.cancel()
        importJob = scope.launch(Dispatchers.Default) {
            var accepted: android.graphics.Bitmap? = null
            try {
                val maxSide = 1400
                val scale = minOf(1f, maxSide.toFloat() / maxOf(captured.width, captured.height))
                accepted = if (scale < 1f) {
                    android.graphics.Bitmap.createScaledBitmap(
                        captured,
                        (captured.width * scale).toInt().coerceAtLeast(1),
                        (captured.height * scale).toInt().coerceAtLeast(1),
                        true
                    ).also { if (it !== captured && !captured.isRecycled) captured.recycle() }
                } else captured
                val previous = mutableState.value.sourceBitmap
                mutableState.update {
                    it.copy(
                        sourceBitmap = accepted,
                        sourceName = "ibisPaint screen capture",
                        pageWidth = accepted!!.width,
                        pageHeight = accepted!!.height,
                        paths = emptyList(),
                        selectedLeft = 0f,
                        selectedTop = 0f,
                        selectedRight = 1f,
                        selectedBottom = 1f,
                        selectionMode = false,
                        scanLeftPx = 0,
                        scanTopPx = 0,
                        scanWidthPx = accepted!!.width,
                        scanHeightPx = accepted!!.height,
                        foregroundPixels = 0,
                        skeletonPixels = 0,
                        discardedComponents = 0,
                        threshold = 0,
                        calibration = null,
                        calibrationCorners = emptyList(),
                        calibrating = false,
                        testGestureVerified = false,
                        scanInProgress = false,
                        scanProgress = 0f,
                        scanStatus = "Screen captured. Preparing the local outline scan…",
                        isError = false,
                        message = "One ibisPaint screen frame was captured locally. No screenshot was uploaded."
                    )
                }
                if (previous != null && previous !== accepted && !previous.isRecycled) previous.recycle()
                Logger.info("Screen frame imported locally size=${accepted!!.width}x${accepted!!.height}")
                scanFullPage()
            } catch (error: Exception) {
                val bitmapToRecycle = accepted ?: captured
                if (!bitmapToRecycle.isRecycled && mutableState.value.sourceBitmap !== bitmapToRecycle) {
                    bitmapToRecycle.recycle()
                }
                mutableState.update {
                    it.copy(
                        scanInProgress = false,
                        isError = true,
                        scanStatus = error.message ?: "The captured screen could not be processed.",
                        message = "Screen capture import failed."
                    )
                }
                Logger.error("Captured screen import failed: ${error.message ?: error.javaClass.simpleName}")
            }
        }
    }

    fun scanPage() {
        val snapshot = mutableState.value
        val bitmap = snapshot.sourceBitmap
        if (bitmap == null || bitmap.isRecycled) {
            mutableState.update { it.copy(isError = true, scanStatus = "Choose a page before scanning.") }
            return
        }
        if (snapshot.running) {
            mutableState.update { it.copy(isError = true, scanStatus = "Stop tracing before scanning another page.") }
            return
        }
        if (snapshot.scanInProgress || scanJob?.isActive == true) return

        val left = floor(snapshot.selectedLeft * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val top = floor(snapshot.selectedTop * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val right = ceil(snapshot.selectedRight * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = ceil(snapshot.selectedBottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
        val cropWidth = right - left
        val cropHeight = bottom - top
        val thresholdOffset = snapshot.thresholdOffset
        val minimum = snapshot.minComponentPixels
        val smoothing = snapshot.smoothing
        val detail = snapshot.detailPreservation
        mutableState.update {
            it.copy(
                scanInProgress = true,
                scanProgress = 0.05f,
                scanStatus = if (left == 0 && top == 0 && right == bitmap.width && bottom == bitmap.height)
                    "Preparing the full page scan…"
                else "Preparing selected area scan…",
                paths = emptyList(),
                calibration = null,
                testGestureVerified = false,
                isError = false
            )
        }
        scanJob = scope.launch(Dispatchers.Default) {
            val scanCoroutineJob = currentCoroutineContext()[Job]
            val cancellationCheck: () -> Unit = {
                if (scanCoroutineJob?.isActive == false) {
                    throw CancellationException("Page scan cancelled.")
                }
            }
            try {
                val fullGray = BitmapTools.grayscale(bitmap, cancellationCheck)
                currentCoroutineContext().ensureActive()
                val grayscale = if (left == 0 && top == 0 && right == bitmap.width && bottom == bitmap.height) {
                    fullGray
                } else {
                    ByteArray(cropWidth * cropHeight).also { crop ->
                        for (row in 0 until cropHeight) {
                            cancellationCheck()
                            val sourceOffset = (top + row) * bitmap.width + left
                            val targetOffset = row * cropWidth
                            fullGray.copyInto(crop, targetOffset, sourceOffset, sourceOffset + cropWidth)
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(scanProgress = 0.25f, scanStatus = "Detecting dark outline pixels in selected region…") }
                val result = OutlineProcessor.process(
                    grayscale = grayscale,
                    width = cropWidth,
                    height = cropHeight,
                    thresholdOffset = thresholdOffset,
                    minComponentPixels = minimum,
                    smoothing = smoothing,
                    detailPreservation = detail,
                    cancellationCheck = cancellationCheck,
                    onProgress = { progress, status ->
                        cancellationCheck()
                        mutableState.update { current ->
                            if (current.scanInProgress) {
                                current.copy(scanProgress = progress, scanStatus = status)
                            } else {
                                current
                            }
                        }
                    }
                )
                currentCoroutineContext().ensureActive()
                if (mutableState.value.sourceBitmap !== bitmap) return@launch
                mutableState.update {
                    it.copy(
                        paths = result.paths,
                        scanLeftPx = left,
                        scanTopPx = top,
                        scanWidthPx = cropWidth,
                        scanHeightPx = cropHeight,
                        selectionMode = false,
                        foregroundPixels = result.foregroundPixels,
                        skeletonPixels = result.skeletonPixels,
                        discardedComponents = result.discardedComponents,
                        threshold = result.threshold,
                        scanInProgress = false,
                        scanProgress = 1f,
                        scanStatus = result.paths.size.toString() + " outline paths extracted from the whole page.",
                        calibration = null,
                        testGestureVerified = false,
                        isError = false,
                        message = "Scan complete. Review the blue path preview, then calibrate the canvas."
                    )
                }
                Logger.info(
                    "Page scan completed paths=" + result.paths.size +
                        " foreground=" + result.foregroundPixels +
                        " skeleton=" + result.skeletonPixels +
                        " threshold=" + result.threshold
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(
                        scanInProgress = false,
                        scanProgress = 0f,
                        scanStatus = error.message ?: "The page could not be scanned.",
                        isError = true,
                        message = "Outline extraction failed. Adjust the scan settings and try again."
                    )
                }
                Logger.error("Page scan failed: " + (error.message ?: error.javaClass.simpleName))
            }
        }
    }


    /**
     * Stops the current import or scan operation without clearing the imported page.
     * The scan coroutine checks cancellation before publishing its result, so a
     * cancelled scan cannot replace the page preview with stale paths.
     */
    fun cancelScan() {
        val snapshot = mutableState.value
        if (!snapshot.scanInProgress) return

        scanJob?.cancel()
        scanJob = null
        importJob?.cancel()
        importJob = null

        mutableState.update {
            it.copy(
                scanInProgress = false,
                scanProgress = 0f,
                scanStatus = "Operation cancelled.",
                selectionMode = false,
                isError = false,
                message = if (it.sourceBitmap == null) {
                    "Import cancelled. Choose a page when you're ready."
                } else {
                    "Scanning stopped. Your imported page is still available."
                }
            )
        }
        Logger.info("Current import or scan cancelled by user")
    }


    fun enableSelectionMode() {
        val snapshot = mutableState.value
        if (snapshot.sourceBitmap == null || snapshot.running || snapshot.scanInProgress) return
        mutableState.update {
            it.copy(
                selectionMode = true,
                scanStatus = "Drag a rectangle over the page preview to select the area to scan.",
                message = "Selected-area mode is active. Drag over the preview to set the scan region.",
                isError = false
            )
        }
    }

    fun setScanRegion(left: Float, top: Float, right: Float, bottom: Float) {
        val l = left.coerceIn(0f, 1f)
        val t = top.coerceIn(0f, 1f)
        val r = right.coerceIn(0f, 1f)
        val b = bottom.coerceIn(0f, 1f)
        if (r - l < 0.015f || b - t < 0.015f) {
            mutableState.update { it.copy(selectionMode = false, isError = true, scanStatus = "The selected area is too small. Drag a larger rectangle.") }
            return
        }
        mutableState.update {
            it.copy(
                selectedLeft = l,
                selectedTop = t,
                selectedRight = r,
                selectedBottom = b,
                selectionMode = false,
                scanStatus = "Selected region ready. Scan selected area to extract its outlines.",
                message = "Selected scan region: " + ((r - l) * 100).toInt() + "% × " + ((b - t) * 100).toInt() + "% of the page.",
                isError = false
            )
        }
    }

    fun cancelSelectionMode() {
        mutableState.update { it.copy(selectionMode = false, scanStatus = "Selection cancelled.") }
    }

    fun scanFullPage() {
        mutableState.update {
            it.copy(
                selectedLeft = 0f,
                selectedTop = 0f,
                selectedRight = 1f,
                selectedBottom = 1f,
                selectionMode = false
            )
        }
        scanPage()
    }

    fun clearPage() {
        if (mutableState.value.running) stop()
        scanJob?.cancel()
        importJob?.cancel()
        cancelCalibration()
        val bitmap = mutableState.value.sourceBitmap
        mutableState.update {
            it.copy(
                sourceBitmap = null,
                sourceName = "",
                pageWidth = 0,
                pageHeight = 0,
                paths = emptyList(),
                foregroundPixels = 0,
                skeletonPixels = 0,
                discardedComponents = 0,
                threshold = 0,
                scanInProgress = false,
                scanProgress = 0f,
                scanStatus = "Choose a complete outlined page.",
                selectedLeft = 0f,
                selectedTop = 0f,
                selectedRight = 1f,
                selectedBottom = 1f,
                selectionMode = false,
                scanLeftPx = 0,
                scanTopPx = 0,
                scanWidthPx = 0,
                scanHeightPx = 0,
                calibration = null,
                calibrationCorners = emptyList(),
                testGestureVerified = false,
                currentPath = 0,
                completedPaths = 0,
                totalPaths = 0,
                failedPaths = 0,
                progress = 0f,
                isError = false,
                message = "Page cleared."
            )
        }
        if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
        Logger.info("Page session cleared")
    }

    fun setThresholdOffset(value: Int) {
        mutableState.update { it.copy(thresholdOffset = value.coerceIn(-30, 30)) }
    }

    fun setMinComponentPixels(value: Int) {
        mutableState.update { it.copy(minComponentPixels = value.coerceIn(1, 20)) }
    }

    fun setSmoothing(value: Float) {
        mutableState.update { it.copy(smoothing = value.coerceIn(0f, 1f)) }
    }

    fun setDetailPreservation(value: Float) {
        mutableState.update { it.copy(detailPreservation = value.coerceIn(0f, 1f)) }
    }

    fun setGestureSpeed(value: Int) {
        mutableState.update { it.copy(gestureSpeedMs = value.coerceIn(100, 500)) }
    }

    fun setDrawingSpeed(value: Float) {
        mutableState.update { it.copy(drawingSpeedPxPerSecond = value.coerceIn(250f, 3_000f)) }
    }

    fun setPanelAutoCollapse(value: Long) {
        mutableState.update { it.copy(panelAutoCollapseMs = value.coerceIn(0L, 30_000L)) }
    }

    fun setShowDebugLog(value: Boolean) {
        mutableState.update { it.copy(showDebugLog = value) }
    }

    fun confirmRestrictedSettingsUnlocked() {
        mutableState.update { it.copy(restrictedSettingsConfirmed = true) }
        Logger.info("Restricted settings manually confirmed by user")
    }

    fun beginCalibration() {
        val snapshot = mutableState.value
        if (snapshot.sourceBitmap == null || snapshot.paths.isEmpty()) {
            mutableState.update {
                it.copy(isError = true, message = "Import and scan the whole page before calibrating.")
            }
            return
        }
        if (snapshot.running) return

        val display = Point()
        val manager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        manager.defaultDisplay.getRealSize(display)
        val width = display.x.toFloat().coerceAtLeast(1f)
        val height = display.y.toFloat().coerceAtLeast(1f)
        val corners = listOf(
            InkPoint(width * 0.12f, height * 0.12f),
            InkPoint(width * 0.88f, height * 0.12f),
            InkPoint(width * 0.88f, height * 0.88f),
            InkPoint(width * 0.12f, height * 0.88f)
        )
        mutableState.update {
            it.copy(
                calibrating = true,
                calibrationCorners = corners,
                calibration = null,
                testGestureVerified = false,
                isError = false,
                message = "Drag markers 1–4 onto the four visible corners of the ibisPaint page."
            )
        }
        Logger.info("Canvas calibration started")
    }

    fun moveCalibrationCorner(index: Int, x: Float, y: Float) {
        if (index !in 0..3 || !mutableState.value.calibrating) return
        val display = Point()
        val manager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        manager.defaultDisplay.getRealSize(display)
        val point = InkPoint(
            x.coerceIn(1f, (display.x - 1).coerceAtLeast(2).toFloat()),
            y.coerceIn(1f, (display.y - 1).coerceAtLeast(2).toFloat())
        )
        mutableState.update { state ->
            val next = state.calibrationCorners.toMutableList()
            if (next.size == 4) {
                next[index] = point
                state.copy(calibrationCorners = next)
            } else state
        }
    }

    fun saveCalibration(): Boolean {
        val corners = mutableState.value.calibrationCorners
        if (corners.size != 4) return false
        val calibration = PageCalibration(corners[0], corners[1], corners[2], corners[3])
        if (ProjectiveTransform.from(calibration) == null) {
            mutableState.update {
                it.copy(
                    isError = true,
                    message = "Those corners cross or form a very small area. Move the markers to the four page corners."
                )
            }
            Logger.warning("Calibration rejected: invalid page quadrilateral")
            return false
        }
        mutableState.update {
            it.copy(
                calibration = calibration,
                calibrating = false,
                testGestureVerified = false,
                isError = false,
                message = "Canvas alignment saved. Run the test stroke before full-page tracing."
            )
        }
        Logger.info("Canvas calibration saved")
        return true
    }

    fun cancelCalibration() {
        mutableState.update {
            it.copy(
                calibrating = false,
                calibrationCorners = emptyList(),
                message = if (it.calibration == null) "Calibration cancelled." else "Previous canvas alignment retained."
            )
        }
    }

    fun registerDispatcher(value: GestureDispatcher) {
        dispatcher = value
        mutableState.update {
            it.copy(serviceConnected = true, isError = false, message = "Accessibility service connected.")
        }
        Logger.info("Accessibility service connected")
    }

    fun unregisterDispatcher(value: GestureDispatcher, reason: String) {
        if (dispatcher !== value) return
        dispatcher = null
        mutableState.update { it.copy(serviceConnected = false, ibisPaintForeground = false) }
        Logger.warning(reason)
        if (mutableState.value.running) {
            stopWithError("Accessibility disconnected during tracing. Re-enable the service before continuing.")
        } else {
            mutableState.update { it.copy(message = "Accessibility service disconnected. Re-enable it in Android settings.") }
        }
    }

    fun onForegroundPackageChanged(packageName: String) {
        val isIbis = packageName.contains("ibispaint", ignoreCase = true)
        mutableState.update { it.copy(ibisPaintForeground = isIbis) }
        if (mutableState.value.running && !isIbis) {
            stopWithError("ibisPaint left the foreground. Tracing was stopped for safety.")
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
        val verified = answer.equals("Yes", ignoreCase = true)
        Logger.info("TEST_RESULT | visible test curve=" + answer.uppercase())
        mutableState.update {
            it.copy(
                showResultDialog = false,
                testGestureVerified = verified,
                message = if (verified) {
                    "Test confirmed. The curve should already be undone in ibisPaint. Full-page tracing is unlocked."
                } else {
                    "The test was not confirmed. Recheck the canvas corners and brush, then run the test again."
                },
                lastGestureResult = if (verified) "User confirmed visible test and undo" else "Test not verified",
                isError = !verified
            )
        }
    }

    private fun startTest() {
        val snapshot = mutableState.value
        if (snapshot.calibration == null) {
            mutableState.update { it.copy(isError = true, message = "Calibrate all four page corners before testing.") }
            return
        }
        if (!canDispatch(snapshot)) return

        val transform = ProjectiveTransform.from(snapshot.calibration)
        if (transform == null) {
            mutableState.update { it.copy(isError = true, message = "Canvas calibration is invalid. Calibrate again.") }
            return
        }
        val points = (0..60).map { index ->
            val t = index / 60f
            val u = 0.35f + 0.30f * t
            val v = 0.50f + 0.10f * sin(2.0 * PI * t).toFloat()
            val mapped = transform.map(u, v)
            GesturePoint(mapped.x, mapped.y)
        }
        if (points.any { !it.x.isFinite() || !it.y.isFinite() }) {
            mutableState.update { it.copy(isError = true, message = "The test path could not be mapped to the canvas.") }
            return
        }
        startPlayback(listOf(points), isTest = true, durationOverrideMs = 1_500L)
    }

    private fun startTrace() {
        val snapshot = mutableState.value
        if (!snapshot.testGestureVerified) {
            mutableState.update { it.copy(isError = true, message = "Run the test stroke, confirm it appeared, and undo it before tracing the page.") }
            return
        }
        if (snapshot.paths.isEmpty()) {
            mutableState.update { it.copy(isError = true, message = "Scan the full page or a selected area before starting the outline trace.") }
            return
        }
        if (snapshot.calibration == null) {
            mutableState.update { it.copy(isError = true, message = "Calibrate the four page corners before starting.") }
            return
        }
        if (!canDispatch(snapshot)) return

        val transform = ProjectiveTransform.from(snapshot.calibration)
        if (transform == null) {
            mutableState.update { it.copy(isError = true, message = "Canvas calibration is invalid. Calibrate again.") }
            return
        }
        val pageMaxX = (snapshot.pageWidth - 1).coerceAtLeast(1).toFloat()
        val pageMaxY = (snapshot.pageHeight - 1).coerceAtLeast(1).toFloat()
        val mappedPaths = snapshot.paths.mapNotNull { inkPath ->
            val mapped = inkPath.points.map { point ->
                transform.map(
                    ((snapshot.scanLeftPx + point.x) / pageMaxX).coerceIn(0f, 1f),
                    ((snapshot.scanTopPx + point.y) / pageMaxY).coerceIn(0f, 1f)
                )
            }
            if (mapped.size < 2 || mapped.any { !it.x.isFinite() || !it.y.isFinite() }) null
            else mapped.map { GesturePoint(it.x, it.y) }
        }
        if (mappedPaths.isEmpty()) {
            mutableState.update { it.copy(isError = true, message = "No valid outline paths could be mapped to the calibrated canvas.") }
            return
        }
        startPlayback(mappedPaths, isTest = false)
    }

    private fun canDispatch(snapshot: SessionState): Boolean {
        if (!snapshot.serviceConnected || dispatcher == null) {
            mutableState.update {
                it.copy(isError = true, message = "Accessibility is not connected. Enable Manga Inker in Android Accessibility settings.")
            }
            return false
        }
        if (!snapshot.ibisPaintForeground) {
            mutableState.update {
                it.copy(isError = true, message = "Bring ibisPaint to the foreground, then use the floating controls.")
            }
            return false
        }
        if (snapshot.running) return false
        return true
    }

    private fun startPlayback(
        paths: List<List<GesturePoint>>,
        isTest: Boolean,
        durationOverrideMs: Long? = null
    ) {
        val activeDispatcher = dispatcher ?: return
        val runId = runIds.incrementAndGet()
        synchronized(dispatchLock) {
            activeRunId = runId
            dispatchAllowed = true
        }
        val total = paths.size
        mutableState.update {
            it.copy(
                running = true,
                paused = false,
                startedAtElapsedMs = SystemClock.elapsedRealtime(),
                showResultDialog = false,
                isError = false,
                currentPath = 1,
                completedPaths = 0,
                totalPaths = total,
                failedPaths = 0,
                progress = 0f,
                message = if (isTest) "Drawing the alignment test inside the calibrated page." else "Tracing the scanned page outline."
            )
        }
        Logger.info("run=" + runId + " started type=" + (if (isTest) "test" else "page") + " paths=" + total)

        pathPlayer.startPaths(
            paths = paths,
            speedPxPerSecond = state.value.drawingSpeedPxPerSecond,
            configuredSegmentMs = state.value.gestureSpeedMs,
            runId = runId,
            isCurrentRun = ::isCurrentRun,
            dispatch = { segment, activeId ->
                activeDispatcher.drawPath(segment.points, segment.durationMs, segment.index, activeId)
            },
            onSegmentResult = { pathNumber, segment, result ->
                val label = when (result) {
                    GestureResult.Completed -> "Completed"
                    GestureResult.Cancelled -> "Cancelled"
                    is GestureResult.Failed -> "Failed: " + result.reason
                }
                updateLastGestureResult("Path " + pathNumber + " · segment " + segment.index + ": " + label)
            },
            onPathProgress = { completed, pathTotal ->
                mutableState.update {
                    it.copy(
                        currentPath = (completed + 1).coerceAtMost(pathTotal),
                        completedPaths = if (isTest) 0 else completed,
                        progress = if (isTest) 0f else completed.toFloat() / pathTotal.coerceAtLeast(1)
                    )
                }
            },
            onComplete = { completeRun(runId, isTest) },
            onFailure = { reason -> stopWithError(reason, runId) },
            durationOverrideMs = durationOverrideMs
        )
    }

    private fun pause() {
        if (!mutableState.value.running || mutableState.value.paused) return
        pathPlayer.pause()
        mutableState.update { it.copy(paused = true, message = "Pausing after the active gesture segment finishes.") }
        Logger.info("Pause requested")
    }

    private fun resume() {
        if (!mutableState.value.running || !mutableState.value.paused) return
        pathPlayer.resume()
        mutableState.update { it.copy(paused = false, message = "Tracing resumed.") }
        Logger.info("Resume requested")
    }

    private fun stop() {
        stopInternals()
        mutableState.update {
            it.copy(
                running = false,
                paused = false,
                showResultDialog = false,
                startedAtElapsedMs = 0L,
                isError = false,
                progress = 0f,
                message = "Stopped. No queued outline paths will be dispatched."
            )
        }
        Logger.warning("Stop requested; queued path segments cleared")
    }

    private fun stopWithError(reason: String, expectedRunId: Long? = null) {
        if (expectedRunId != null && !isCurrentRun(expectedRunId)) return
        stopInternals()
        mutableState.update {
            it.copy(
                running = false,
                paused = false,
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

    private fun completeRun(runId: Long, isTest: Boolean) {
        if (!isCurrentRun(runId)) return
        synchronized(dispatchLock) { dispatchAllowed = false }
        mutableState.update {
            it.copy(
                running = false,
                paused = false,
                startedAtElapsedMs = 0L,
                showResultDialog = isTest,
                isError = false,
                progress = if (isTest) it.progress else 1f,
                message = if (isTest) {
                    "Android completed the test gesture. Check the visible result in ibisPaint."
                } else {
                    "Page trace completed. Check the canvas for missed or misplaced lines."
                }
            )
        }
        Logger.info("run=" + runId + " dispatch=Completed type=" + (if (isTest) "test" else "page"))
    }

    private fun isCurrentRun(runId: Long): Boolean =
        synchronized(dispatchLock) { dispatchAllowed && activeRunId == runId }

    fun close() {
        stopInternals()
        scanJob?.cancel()
        importJob?.cancel()
        scope.cancel()
        val bitmap = mutableState.value.sourceBitmap
        if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
    }
}
