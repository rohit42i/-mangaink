package com.example.mangainker.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.example.mangainker.MangaInkerApp
import com.example.mangainker.core.SessionController
import com.example.mangainker.ui.InkPalette
import com.example.mangainker.ui.MangaInkerTheme

class FloatingPanelService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var controller: SessionController
    private var panelView: ComposeView? = null
    private var borderView: ComposeView? = null
    private lateinit var panelParams: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        controller = (application as MangaInkerApp).sessionController
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addRunningBorder()
        addPanel()
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
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
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
                        onSnap = ::snapPanel
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
            x = 16
            y = 100
        }
        panelView = view
        windowManager.addView(view, panelParams)
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
        panelParams.x = if (panelParams.x + panelWidth / 2 < width / 2) 0 else (width - panelWidth).coerceAtLeast(0)
        panelView?.let { runCatching { windowManager.updateViewLayout(it, panelParams) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        panelView?.let { runCatching { windowManager.removeView(it) } }
        borderView?.let { runCatching { windowManager.removeView(it) } }
        panelView = null
        borderView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
