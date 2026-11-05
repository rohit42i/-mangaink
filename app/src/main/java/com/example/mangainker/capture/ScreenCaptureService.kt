package com.example.mangainker.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.view.WindowManager
import com.example.mangainker.MangaInkerApp
import com.example.mangainker.core.Logger

/**
 * Captures one frame only after explicit Android consent and while ibisPaint is foreground.
 * No frame is written to disk or sent over the network.
 */
class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var seenFrames = 0
    private var completed = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultCode != RESULT_OK || resultData == null) {
            Logger.warning("Screen capture was cancelled or permission data was missing.")
            stopSelf()
            return START_NOT_STICKY
        }

        startCaptureNotification()
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, resultData)
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels.coerceAtLeast(1)
            val height = metrics.heightPixels.coerceAtLeast(1)
            val density = metrics.densityDpi.coerceAtLeast(1)

            worker = HandlerThread("MangaInkerCapture").also { it.start() }
            val handler = Handler(worker!!.looper)
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Logger.info("Android stopped the screen-capture session.")
                    stopSelf()
                }
            }, handler)

            reader?.setOnImageAvailableListener({ source ->
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    if (completed) return@setOnImageAvailableListener
                    val app = application as MangaInkerApp
                    if (!app.sessionController.state.value.ibisPaintForeground) {
                        seenFrames = 0
                        return@setOnImageAvailableListener
                    }
                    // Ignore initial transition frames and use the next stable ibisPaint frame.
                    seenFrames++
                    if (seenFrames < 3) return@setOnImageAvailableListener

                    val plane = image.planes[0]
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val paddedWidth = image.width + (rowStride - pixelStride * image.width) / pixelStride
                    val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
                    padded.copyPixelsFromBuffer(plane.buffer)
                    val frame = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
                    padded.recycle()
                    completed = true
                    app.sessionController.importCapturedBitmap(frame)
                    Logger.info("Captured ibisPaint frame locally: ${frame.width}x${frame.height}")
                    stopSelf()
                } catch (error: Exception) {
                    Logger.error("Screen capture failed: ${error.message ?: error.javaClass.simpleName}")
                    stopSelf()
                } finally {
                    image.close()
                }
            }, handler)

            display = projection?.createVirtualDisplay(
                "MangaInkerScreenCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader?.surface,
                null,
                handler
            )
            Logger.info("Recording screen. Waiting for ibisPaint to become foreground.")
        } catch (error: Exception) {
            Logger.error("Unable to start screen capture: ${error.message ?: error.javaClass.simpleName}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startCaptureNotification() {
        val channelId = "manga_inker_screen_capture"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "Screen capture", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Recording screen")
            .setContentText("Manga Inker is capturing one ibisPaint frame for local scanning.")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        display?.release()
        reader?.close()
        projection?.stop()
        worker?.quitSafely()
        display = null
        reader = null
        projection = null
        worker = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_RESULT_CODE = "capture_result_code"
        const val EXTRA_RESULT_DATA = "capture_result_data"
        private const val NOTIFICATION_ID = 42
    }
}
