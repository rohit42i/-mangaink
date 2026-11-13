package com.rohit.mangainker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.rohit.mangainker.core.InkPoint
import com.rohit.mangainker.core.PageCalibration
import com.rohit.mangainker.core.SessionStore

class FloatingPanelService : Service() {
    private lateinit var windowManager: WindowManager
    private var panel: LinearLayout? = null
    private var bubble: TextView? = null
    private var calibrationToolbar: LinearLayout? = null
    private val handles = ArrayList<Handle>()
    private val handler = Handler(Looper.getMainLooper())
    private var statusView: TextView? = null
    private val refresh = object : Runnable {
        override fun run() {
            statusView?.text = panelStatus()
            handler.postDelayed(this, 700L)
        }
    }

    private data class Handle(val view: TextView, val params: WindowManager.LayoutParams)

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow display over other apps first.", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startAsForeground()
        showBubble()
        handler.post(refresh)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    private fun startAsForeground() {
        val channelId = "manga_inker_controls"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Manga Inker session", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("Manga Inker floating controls active")
                .setContentText("Tap the MI bubble over any app to open drawing controls.")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Manga Inker floating controls active")
                .setContentText("Tap the MI bubble over any app to open drawing controls.")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setOngoing(true)
                .build()
        }
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(11, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(11, notification)
        }
    }

    /** A small system overlay remains available while the user works in another app. */
    private fun showBubble() {
        if (bubble != null) return
        val display = Point()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealSize(display)
        val size = dp(54)
        val icon = TextView(this).apply {
            text = "MI"
            textSize = 13f
            setTextColor(0xFF081218.toInt())
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = rounded(0xFF70D8E8.toInt(), size / 2)
            elevation = dp(8).toFloat()
            contentDescription = "Manga Inker floating controls. Tap to open, drag to move."
            isClickable = true
        }
        val params = WindowManager.LayoutParams(
            size, size, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (display.x - size - dp(16)).coerceAtLeast(0)
            y = dp(150)
        }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        icon.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(7)) moved = true
                    if (moved) {
                        params.x = (startX + dx.toInt()).coerceIn(0, (display.x - size).coerceAtLeast(0))
                        params.y = (startY + dy.toInt()).coerceIn(0, (display.y - size).coerceAtLeast(0))
                        runCatching { windowManager.updateViewLayout(icon, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) togglePanel()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
        bubble = icon
        windowManager.addView(icon, params)
    }

    private fun togglePanel() {
        if (panel == null) {
            showPanel()
        } else {
            removePanel()
            showBubble()
        }
    }

    private fun showPanel() {
        removeBubble()
        removePanel()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(0xF21A2029.toInt(), dp(16))
            elevation = dp(10).toFloat()
        }
        val heading = TextView(this).apply {
            text = "MANGA INKER  /  LIVE"
            textSize = 12f
            letterSpacing = 0.08f
            setTextColor(0xFF70D8E8.toInt())
        }
        root.addView(heading)
        statusView = TextView(this).apply {
            text = panelStatus()
            textSize = 11f
            setTextColor(0xFFE6EDF5.toInt())
            setPadding(0, dp(5), 0, dp(8))
        }
        root.addView(statusView)
        root.addView(makeButton("Calibrate page corners") { showCalibration() })
        root.addView(makeButton("Test one curve") {
            val service = InkingAccessibilityService.instance
            if (SessionStore.calibration == null) {
                toast("Calibrate the four page corners first.")
            } else if (service == null) {
                toast("Enable Manga Inker drawing access in Android settings.")
            } else if (!service.drawTestStroke()) {
                toast("Android did not accept the test gesture.")
            }
        })
        root.addView(makeButton("I can see it and undid it") {
            if (!SessionStore.confirmTestCurve()) {
                toast("Run the test curve first. Confirm only after seeing it in ibisPaint and undoing it.")
            } else {
                toast("Test verified. Full-page inking is unlocked.")
            }
        })
        root.addView(makeButton("Start / Resume inking") {
            val service = InkingAccessibilityService.instance
            if (service == null) {
                toast("Enable Manga Inker drawing access first.")
            } else if (service.startInking()) {
                // Clear the drawing area before injecting strokes; the small bubble remains available.
                removePanel()
                showBubble()
            } else {
                toast(SessionStore.status)
            }
        })
        root.addView(makeButton("Pause after current stroke") { InkingAccessibilityService.instance?.pauseInking() })
        root.addView(makeButton("Stop after current stroke") { InkingAccessibilityService.instance?.stopInking() })
        root.addView(makeButton("Minimize to floating icon") {
            removePanel()
            showBubble()
        })
        root.addView(makeButton("End floating controls") { stopSelf() })

        val params = WindowManager.LayoutParams(
            dp(238), WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(10)
            y = dp(110)
        }
        panel = root
        windowManager.addView(root, params)
    }

    private fun showCalibration() {
        removePanel()
        removeBubble()
        removeCalibration()
        val display = Point()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealSize(display)
        val defaults = listOf(
            InkPoint(display.x * 0.18f, display.y * 0.18f),
            InkPoint(display.x * 0.82f, display.y * 0.18f),
            InkPoint(display.x * 0.82f, display.y * 0.82f),
            InkPoint(display.x * 0.18f, display.y * 0.82f)
        )
        defaults.forEachIndexed { index, point ->
            val size = dp(46)
            val handleView = TextView(this).apply {
                text = (index + 1).toString()
                textSize = 16f
                setTextColor(0xFF081218.toInt())
                gravity = Gravity.CENTER
                background = rounded(listOf(0xFF70D8E8.toInt(), 0xFFFFD166.toInt(), 0xFF8BE0A1.toInt(), 0xFFFF8A80.toInt())[index], size)
                elevation = dp(8).toFloat()
                contentDescription = "Drag marker ${index + 1} to a page corner"
            }
            val params = WindowManager.LayoutParams(
                size, size, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (point.x - size / 2f).toInt().coerceAtLeast(0)
                y = (point.y - size / 2f).toInt().coerceAtLeast(0)
            }
            attachDrag(handleView, params)
            handles.add(Handle(handleView, params))
            windowManager.addView(handleView, params)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = rounded(0xF21A2029.toInt(), dp(12))
        }
        val info = TextView(this).apply {
            text = "Drag 1–4 to the page corners"
            textSize = 11f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, dp(8), 0)
        }
        bar.addView(info)
        val save = Button(this).apply {
            text = "Save"
            isAllCaps = false
            setOnClickListener { saveCalibration() }
        }
        bar.addView(save)
        val cancel = Button(this).apply {
            text = "Cancel"
            isAllCaps = false
            setOnClickListener { removeCalibration(); showPanel() }
        }
        bar.addView(cancel)
        calibrationToolbar = bar
        val barParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(42)
        }
        windowManager.addView(bar, barParams)
    }

    private fun attachDrag(view: View, params: WindowManager.LayoutParams) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (startX + event.rawX - downRawX).toInt()
                    params.y = (startY + event.rawY - downRawY).toInt()
                    runCatching { windowManager.updateViewLayout(view, params) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
    }

    private fun saveCalibration() {
        if (handles.size != 4) return
        val size = dp(46)
        val points = handles.map { handle ->
            InkPoint(handle.params.x + size / 2f, handle.params.y + size / 2f)
        }
        SessionStore.calibration = PageCalibration(points[0], points[1], points[2], points[3])
        SessionStore.testGestureCompleted = false
        SessionStore.testGestureAcknowledged = false
        SessionStore.status = "Page corners saved. Test a curve before full inking."
        SessionStore.notifyChanged()
        removeCalibration()
        showPanel()
        toast("Page alignment saved. Check all four markers before drawing.")
    }

    private fun panelStatus(): String {
        val total = SessionStore.paths.size
        val complete = SessionStore.successfulPaths
        return when {
            total == 0 -> "No page loaded"
            SessionStore.isRunning && SessionStore.isPaused -> "PAUSED  •  $complete / $total strokes\n${SessionStore.status}"
            !SessionStore.testGestureAcknowledged -> "TEST REQUIRED  •  $complete / $total strokes\n${SessionStore.status}"
            SessionStore.isPaused -> "PAUSED  •  $complete / $total strokes\n${SessionStore.status}"
            SessionStore.isRunning -> "DRAWING  •  $complete / $total strokes"
            else -> SessionStore.status
        }
    }

    private fun makeButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 11f
        minHeight = dp(36)
        setPadding(dp(6), 0, dp(6), 0)
        setOnClickListener { action() }
        val layout = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38))
        layout.bottomMargin = dp(4)
        this.layoutParams = layout
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun overlayType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun removeBubble() {
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
    }

    private fun removePanel() {
        panel?.let { runCatching { windowManager.removeView(it) } }
        panel = null
        statusView = null
    }

    private fun removeCalibration() {
        handles.forEach { runCatching { windowManager.removeView(it.view) } }
        handles.clear()
        calibrationToolbar?.let { runCatching { windowManager.removeView(it) } }
        calibrationToolbar = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        removeCalibration()
        removePanel()
        removeBubble()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
