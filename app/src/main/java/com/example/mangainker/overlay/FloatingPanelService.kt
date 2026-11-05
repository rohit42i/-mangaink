package com.example.mangainker.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import com.example.mangainker.ui.MainActivity
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.example.mangainker.MangaInkerApp
import com.example.mangainker.core.InkPoint
import com.example.mangainker.core.SessionController
import com.example.mangainker.ui.InkPalette
import com.example.mangainker.ui.MangaInkerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

class FloatingPanelService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var controller: SessionController
    private var panelView: ComposeView? = null
    private var borderView: ComposeView? = null
    private lateinit var panelParams: WindowManager.LayoutParams
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val calibrationHandles = ArrayList<CalibrationHandle>()
    private var calibrationToolbar: LinearLayout? = null
    private var calibrationVisible = false

    private data class CalibrationHandle(
        val view: TextView,
        val params: WindowManager.LayoutParams
    )

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow display over other apps first.", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        controller = (application as MangaInkerApp).sessionController
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startAsForeground()
        addRunningBorder()
        addPanel()

        serviceScope.launch {
            controller.state.collect { state ->
                if (state.calibrating && !calibrationVisible) showCalibration()
                if (!state.calibrating && calibrationVisible) removeCalibration()
            }
        }
    }

    private fun startAsForeground() {
        val channelId = "manga_inker_controls"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Manga Inker controls",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("Manga Inker is ready")
                .setContentText("Floating drawing controls are available.")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Manga Inker is ready")
                .setContentText("Floating drawing controls are available.")
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

    private fun addRunningBorder() {
        val view = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MangaInkerTheme {
                    val state by controller.state.collectAsState()
                    Canvas(Modifier.fillMaxSize()) {
                        if (state.running) {
                            drawRect(
                                color = InkPalette.accent,
                                topLeft = androidx.compose.ui.geometry.Offset(1f, 1f),
                                size = androidx.compose.ui.geometry.Size(size.width - 2f, size.height - 2f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                            )
                        }
                    }
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        borderView = view
        windowManager.addView(view, params)
    }

    private fun addPanel() {
        val view = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MangaInkerTheme {
                    FloatingPanelContent(
                        controller = controller,
                        onDrag = ::movePanel,
                        onSnap = ::snapPanel,
                        onClose = { stopSelf() },
                        onOpenMainScreen = {
                            startActivity(
                                Intent(this@FloatingPanelService, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        onReconnectAccessibility = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    )
                }
            }
        }
        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(14)
            y = dp(110)
        }
        panelView = view
        windowManager.addView(view, panelParams)
    }

    private fun showCalibration() {
        val corners = controller.state.value.calibrationCorners
        if (corners.size != 4) return
        calibrationVisible = true
        removeCalibrationViewsOnly()
        val colors = listOf(
            0xFF70D8E8.toInt(),
            0xFFFFD166.toInt(),
            0xFF8BE0A1.toInt(),
            0xFFFF8A80.toInt()
        )
        corners.forEachIndexed { index, point ->
            val size = dp(48)
            val handle = TextView(this).apply {
                text = (index + 1).toString()
                textSize = 16f
                setTextColor(0xFF111318.toInt())
                gravity = Gravity.CENTER
                background = rounded(colors[index], size)
                elevation = dp(8).toFloat()
                contentDescription = "Drag page corner " + (index + 1)
            }
            val params = WindowManager.LayoutParams(
                size,
                size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (point.x - size / 2f).toInt().coerceAtLeast(0)
                y = (point.y - size / 2f).toInt().coerceAtLeast(0)
            }
            attachDrag(index, handle, params)
            calibrationHandles.add(CalibrationHandle(handle, params))
            windowManager.addView(handle, params)
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = rounded(0xF21A2029.toInt(), dp(12))
        }
        val label = TextView(this).apply {
            text = "Place 1–4 on the page corners"
            textSize = 11f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, dp(7), 0)
        }
        toolbar.addView(label)
        toolbar.addView(Button(this).apply {
            text = "Save"
            isAllCaps = false
            setOnClickListener {
                if (!controller.saveCalibration()) {
                    Toast.makeText(this@FloatingPanelService, controller.state.value.message, Toast.LENGTH_LONG).show()
                }
            }
        })
        toolbar.addView(Button(this).apply {
            text = "Cancel"
            isAllCaps = false
            setOnClickListener { controller.cancelCalibration() }
        })
        calibrationToolbar = toolbar
        val toolbarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(38)
        }
        windowManager.addView(toolbar, toolbarParams)
    }

    private fun attachDrag(
        index: Int,
        view: View,
        params: WindowManager.LayoutParams
    ) {
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
                    params.x = (startX + event.rawX - downRawX).toInt().coerceAtLeast(0)
                    params.y = (startY + event.rawY - downRawY).toInt().coerceAtLeast(0)
                    runCatching { windowManager.updateViewLayout(view, params) }
                    val size = dp(48)
                    controller.moveCalibrationCorner(
                        index,
                        params.x + size / 2f,
                        params.y + size / 2f
                    )
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
    }

    private fun movePanel(deltaX: Int, deltaY: Int) {
        val maxY = (resources.displayMetrics.heightPixels - (panelView?.height ?: 0)).coerceAtLeast(0)
        panelParams.x = (panelParams.x + deltaX).coerceAtLeast(0)
        panelParams.y = (panelParams.y + deltaY).coerceIn(0, maxY)
        panelView?.let { runCatching { windowManager.updateViewLayout(it, panelParams) } }
    }

    private fun snapPanel() {
        val width = resources.displayMetrics.widthPixels
        val panelWidth = panelView?.width ?: 0
        panelParams.x = if (panelParams.x + panelWidth / 2 < width / 2) {
            0
        } else {
            (width - panelWidth).coerceAtLeast(0)
        }
        panelView?.let { runCatching { windowManager.updateViewLayout(it, panelParams) } }
    }

    private fun removeCalibrationViewsOnly() {
        calibrationHandles.forEach { runCatching { windowManager.removeView(it.view) } }
        calibrationHandles.clear()
        calibrationToolbar?.let { runCatching { windowManager.removeView(it) } }
        calibrationToolbar = null
    }

    private fun removeCalibration() {
        calibrationVisible = false
        removeCalibrationViewsOnly()
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        if (::controller.isInitialized) {
            if (controller.state.value.running) {
                controller.dispatch(com.example.mangainker.core.SessionCommand.Stop)
            }
            if (controller.state.value.scanInProgress) {
                controller.cancelScan()
            }
        }
        serviceScope.cancel()
        removeCalibration()
        panelView?.let { runCatching { windowManager.removeView(it) } }
        borderView?.let { runCatching { windowManager.removeView(it) } }
        panelView = null
        borderView = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
