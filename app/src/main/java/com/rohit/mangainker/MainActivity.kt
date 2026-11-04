package com.rohit.mangainker

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rohit.mangainker.core.BitmapTools
import com.rohit.mangainker.core.OutlineProcessor
import com.rohit.mangainker.core.SessionStore
import com.rohit.mangainker.ui.PagePreviewView
import com.rohit.mangainker.studio.MangaStudioActivity
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@SuppressLint("SetTextI18n")
class MainActivity : Activity() {
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var preview: PagePreviewView
    private lateinit var imageStatus: TextView
    private lateinit var processingStatus: TextView
    private lateinit var accessStatus: TextView
    private lateinit var processButton: Button
    private lateinit var openControlsButton: Button
    private lateinit var studioButton: Button
    private lateinit var thresholdSeek: SeekBar
    private lateinit var speckSeek: SeekBar
    private lateinit var smoothSeek: SeekBar
    private lateinit var detailSeek: SeekBar
    private lateinit var speedSeek: SeekBar
    private var selectedBitmap: Bitmap? = null
    private var busy = false
    private var thresholdOffset = 0
    private var speckMinimum = 3
    private var smoothing = 0.28f
    private var detailPreservation = 0.78f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        renderSession()
    }

    private fun buildScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(28))
            setBackgroundColor(Color.rgb(14, 18, 24))
        }

        val brand = text("MANGA INKER  /  STUDIO", 12f, Color.rgb(112, 216, 232), true).apply {
            letterSpacing = 0.12f
        }
        page.addView(brand)
        page.addView(text("Clean outlines.\nDrawn inside ibisPaint.", 27f, Color.WHITE, true).apply {
            setPadding(0, dp(12), 0, dp(5))
        })
        page.addView(text("Prepare one complete manga page, review its reconstructed paths, then ink it with your selected ibisPaint brush.", 14f, Color.rgb(167, 180, 194), false).apply {
            setPadding(0, 0, 0, dp(18))
        })

        page.addView(section("01  /  SOURCE PAGE"))
        page.addView(actionButton("Choose outlined page  ·  PNG / JPG / WEBP") { chooseImage() })
        imageStatus = text("No page selected yet", 12f, Color.rgb(172, 185, 198), false).apply {
            setPadding(0, dp(7), 0, dp(8))
        }
        page.addView(imageStatus)
        preview = PagePreviewView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(340)).apply {
                bottomMargin = dp(10)
            }
        }
        page.addView(preview)

        page.addView(section("02  /  LINE RECONSTRUCTION"))
        page.addView(help("Manga Inker extracts the centreline of dark outlines, removes tiny specks, simplifies the geometry and smooths curves. Everything runs on this phone."))

        page.addView(sliderLabel("Threshold adjustment", "Changes how light or dark a pixel must be to count as ink."))
        thresholdSeek = seekBar(0, 60, 30)
        thresholdSeek.setOnSeekBarChangeListener(simpleSeek { value -> thresholdOffset = value - 30 })
        page.addView(thresholdSeek)

        page.addView(sliderLabel("Remove tiny specks", "Filters isolated marks smaller than the selected connected-pixel count."))
        speckSeek = seekBar(1, 20, 2)
        speckSeek.setOnSeekBarChangeListener(simpleSeek { value -> speckMinimum = value + 1 })
        page.addView(speckSeek)

        page.addView(sliderLabel("Curve smoothing", "Higher values smooth pixel stair-steps more, but can soften sharp corners."))
        smoothSeek = seekBar(0, 100, 28)
        smoothSeek.setOnSeekBarChangeListener(simpleSeek { value -> smoothing = value / 100f })
        page.addView(smoothSeek)

        page.addView(sliderLabel("Detail preservation", "Keeps tight corners and small features. Higher values retain more points."))
        detailSeek = seekBar(0, 100, 78)
        detailSeek.setOnSeekBarChangeListener(simpleSeek { value -> detailPreservation = value / 100f })
        page.addView(detailSeek)

        processButton = actionButton("Build clean drawing paths") { processPage() }
        page.addView(processButton)
        processingStatus = text("No paths built yet", 12f, Color.rgb(172, 185, 198), false).apply {
            setPadding(0, dp(10), 0, dp(12))
        }
        page.addView(processingStatus)

        page.addView(section("03  /  INKING CONTROLS"))
        page.addView(help("Before starting, open ibisPaint, choose the correct canvas, layer and brush. Calibrate the four page corners, test one curve, then start the full page."))
        page.addView(sliderLabel("Drawing speed", "Controls how quickly each simulated stroke is drawn."))
        speedSeek = seekBar(250, 2_500, 950)
        speedSeek.setOnSeekBarChangeListener(simpleSeek { value -> SessionStore.drawingSpeedPxPerSecond = value.toFloat() })
        page.addView(speedSeek)

        accessStatus = text("Checking Android permissions…", 12f, Color.rgb(172, 185, 198), false).apply {
            setPadding(0, dp(10), 0, dp(8))
        }
        page.addView(accessStatus)
        page.addView(help("Android may block the drawing service because this APK was installed outside an app store. This is a system safety restriction, not a Manga Inker crash. First allow restricted settings for this app, then enable its drawing service."))
        page.addView(actionButton("1. Open Manga Inker app settings") {
            openAppSettingsForRestrictedAccess()
        })
        page.addView(actionButton("2. Enable Manga Inker drawing gestures") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        page.addView(actionButton("3. Allow floating controls") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
            } else toast("Floating controls permission is already enabled.")
        })
        openControlsButton = actionButton("Open ibisPaint inking controls") { startFloatingPanel() }
        page.addView(openControlsButton)
        studioButton = actionButton("Open Manga Studio · panels, tones, lettering") {
            if (SessionStore.sourceBitmap == null || SessionStore.sourceBitmap?.isRecycled != false) {
                toast("Import a page first.")
            } else startActivity(Intent(this, MangaStudioActivity::class.java))
        }
        page.addView(studioButton)
        page.addView(actionButton("Clear current page session") { clearSession() })

        page.addView(section("HOW THE WORKFLOW WORKS"))
        page.addView(help("Manga Inker does not read ibisPaint pixels or control its layer panel. You import the exact outlined page, calibrate its four corners over the open ibisPaint canvas, verify a test curve, then let the app redraw the reconstructed paths with your currently selected brush."))
        page.addView(help("Manga Studio adds optional panel frames, screentones, speech and thought bubbles, narration boxes and sound-effect lettering. Those additions export as a new PNG; the imported source and your ibisPaint project are never overwritten."))
        page.addView(help("All processing stays on this phone. Accessibility gestures do not reproduce pen pressure or tilt, and a successful Android gesture callback cannot prove that ibisPaint rendered the stroke."))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setBackgroundColor(Color.rgb(14, 18, 24))
            addView(page)
        }
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
    }

    private fun chooseImage() {
        if (SessionStore.isRunning) { toast("Pause or stop inking before changing the page."); return }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_IMAGE)
    }

    @Deprecated("Using the platform document picker keeps the app independent of additional libraries.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_IMAGE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        busy = true
        processButton.isEnabled = false
        processingStatus.text = "Loading image locally…"
        worker.execute {
            try {
                val bitmap = BitmapTools.decodeForProcessing(contentResolver, uri)
                runOnUiThread {
                    preview.setPreview(null, emptyList())
                    SessionStore.clearPlan()
                    selectedBitmap?.let { old -> if (old !== bitmap && !old.isRecycled) old.recycle() }
                    selectedBitmap = bitmap
                    SessionStore.sourceBitmap = bitmap
                    SessionStore.imageWidth = bitmap.width
                    SessionStore.imageHeight = bitmap.height
                    SessionStore.paths = emptyList()
                    SessionStore.calibration = null
                    SessionStore.testGestureCompleted = false
                    SessionStore.testGestureAcknowledged = false
                    SessionStore.currentPath = 0
                    SessionStore.successfulPaths = 0
                    SessionStore.failedPaths = 0
                    SessionStore.status = "Page loaded. Build paths to continue."
                    preview.setPreview(bitmap, emptyList())
                    imageStatus.text = "${bitmap.width} × ${bitmap.height} px  ·  local session only"
                    processingStatus.text = "Ready to reconstruct the page outline."
                    processButton.isEnabled = true
                    busy = false
                    renderSession()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    busy = false
                    processButton.isEnabled = true
                    processingStatus.text = error.message ?: "Could not load the image."
                }
            }
        }
    }

    private fun processPage() {
        if (SessionStore.isRunning) { toast("Pause or stop inking before rebuilding paths."); return }
        val bitmap = selectedBitmap ?: run {
            toast("Choose an outlined page first.")
            return
        }
        if (busy) return
        busy = true
        processButton.isEnabled = false
        processingStatus.text = "Extracting and smoothing line paths…"
        val offset = thresholdOffset
        val minimum = speckMinimum
        val curve = smoothing
        val detail = detailPreservation
        worker.execute {
            try {
                val gray = BitmapTools.grayscale(bitmap)
                val result = OutlineProcessor.process(gray, bitmap.width, bitmap.height, offset, minimum, curve, detail)
                runOnUiThread {
                    SessionStore.setPlan(bitmap, result.paths)
                    preview.setPreview(bitmap, result.paths)
                    processingStatus.text = "${result.paths.size} paths  ·  ${result.foregroundPixels} outline pixels  ·  ${result.skeletonPixels} centreline pixels  ·  threshold ${result.threshold}"
                    processButton.isEnabled = true
                    busy = false
                    renderSession()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    processingStatus.text = error.message ?: "Line reconstruction failed."
                    processButton.isEnabled = true
                    busy = false
                }
            }
        }
    }

    private fun openAppSettingsForRestrictedAccess() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
        startActivity(intent)
        toast("In App info, open the three-dot menu and choose Allow restricted settings if it appears. Then enable Manga Inker drawing gestures in Accessibility settings.")
    }

    private fun startFloatingPanel() {
        if (SessionStore.paths.isEmpty()) {
            toast("Import a page and build its paths first.")
            return
        }
        if (!isAccessibilityEnabled()) {
            toast("Enable Manga Inker drawing accessibility first.")
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("Allow display over other apps first.")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
            return
        }
        val intent = Intent(this, FloatingPanelService::class.java)
        startForegroundService(intent)
        toast("Controls opened. Switch to ibisPaint and calibrate the page.")
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(this, InkingAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return TextUtils.SimpleStringSplitter(':').run {
            setString(enabled)
            var found = false
            while (hasNext()) if (next().equals(expected, ignoreCase = true)) found = true
            found
        }
    }

    private fun clearSession() {
        if (SessionStore.isRunning) {
            toast("Stop the inking session before clearing it.")
            return
        }
        val old = selectedBitmap
        selectedBitmap = null
        preview.setPreview(null, emptyList())
        SessionStore.clearPlan()
        if (old != null && !old.isRecycled) old.recycle()
        imageStatus.text = "No page selected yet"
        processingStatus.text = "No paths built yet"
        renderSession()
    }

    private fun renderSession() {
        if (!::accessStatus.isInitialized) return
        val accessibility = if (isAccessibilityEnabled()) "Drawing access: enabled" else "Drawing access: not enabled"
        val overlay = if (Settings.canDrawOverlays(this)) "Floating window: allowed" else "Floating window: not allowed"
        val target = if (SessionStore.isTargetAppForeground) "ibisPaint: active" else "ibisPaint: open canvas first"
        accessStatus.text = "$accessibility  ·  $overlay  ·  $target"
        openControlsButton.isEnabled = SessionStore.paths.isNotEmpty() && !busy
        if (::studioButton.isInitialized) studioButton.isEnabled = SessionStore.sourceBitmap?.isRecycled == false
        if (SessionStore.paths.isNotEmpty() && processingStatus.text.toString().contains("No paths")) {
            processingStatus.text = "${SessionStore.paths.size} paths ready. Review the preview before inking."
        }
    }

    private fun section(label: String): TextView = text(label, 11f, Color.rgb(112, 216, 232), true).apply {
        letterSpacing = 0.12f
        setPadding(0, dp(22), 0, dp(9))
    }

    private fun help(message: String): TextView = text(message, 12f, Color.rgb(157, 171, 187), false).apply {
        setPadding(0, dp(4), 0, dp(12))
    }

    private fun sliderLabel(title: String, detail: String): View {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, 0) }
        column.addView(text(title, 13f, Color.WHITE, true))
        column.addView(text(detail, 11f, Color.rgb(135, 149, 166), false).apply { setPadding(0, dp(3), 0, 0) })
        return column
    }

    private fun seekBar(minimum: Int, maximum: Int, initial: Int): SeekBar = SeekBar(this).apply {
        this.max = maximum - minimum
        progress = initial - minimum
        tag = minimum
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36))
        params.bottomMargin = dp(5)
        layoutParams = params
    }

    private fun simpleSeek(onValue: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser && seekBar != null) onValue(progress + (seekBar.tag as? Int ?: 0))
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    private fun actionButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setOnClickListener { action() }
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50))
        params.bottomMargin = dp(6)
        layoutParams = params
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        gravity = Gravity.START
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onResume() {
        super.onResume()
        if (selectedBitmap == null) {
            val restored = SessionStore.sourceBitmap
            if (restored != null && !restored.isRecycled && ::preview.isInitialized) {
                selectedBitmap = restored
                preview.setPreview(restored, SessionStore.paths)
                imageStatus.text = "${restored.width} × ${restored.height} px  ·  restored from this session"
            }
        }
        renderSession()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        if (isFinishing && !SessionStore.isRunning && !SessionStore.isPaused) {
            selectedBitmap = null
            SessionStore.clearPlan()
        }
        super.onDestroy()
    }

    companion object { private const val REQUEST_IMAGE = 41 }
}
