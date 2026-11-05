package com.example.mangainker.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.mangainker.MangaInkerApp
import com.example.mangainker.capture.ScreenCaptureService
import com.example.mangainker.permissions.PermissionChecks
import com.example.mangainker.overlay.FloatingPanelService
import com.example.mangainker.update.GitHubUpdateManager

class MainActivity : ComponentActivity() {
    private val controller by lazy { (application as MangaInkerApp).sessionController }
    private val updateManager by lazy { (application as MangaInkerApp).updateManager }
    private var permissionRefreshKey by mutableIntStateOf(0)
    private var navigationRequest by mutableIntStateOf(0)
    private var navigateToSettings by androidx.compose.runtime.mutableStateOf(false)
    private var pendingExportBitmap: Bitmap? = null

    private val createPngDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val bitmap = pendingExportBitmap
        pendingExportBitmap = null
        if (uri == null || bitmap == null) {
            bitmap?.recycle()
            return@registerForActivityResult
        }
        try {
            contentResolver.openOutputStream(uri)?.use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Android could not encode the PNG."
                }
            } ?: error("Android could not open the selected export destination.")
            Toast.makeText(this, "PNG exported successfully.", Toast.LENGTH_LONG).show()
        } catch (error: Exception) {
            Toast.makeText(this, "PNG export failed: " + (error.message ?: "unknown error"), Toast.LENGTH_LONG).show()
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(controller::importPage)
    }

    private val requestScreenCapture = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val grantData = result.data
        if (result.resultCode != RESULT_OK || grantData == null) {
            Toast.makeText(this, "Screen capture was not approved. Nothing was recorded.", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        stopService(Intent(this, FloatingPanelService::class.java))
        val captureIntent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, grantData)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(captureIntent)
        else startService(captureIntent)
        openIbisPaint()
        Toast.makeText(this, "Recording screen. Manga Inker will capture when ibisPaint is visible.", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigateToSettings = intent.getBooleanExtra(EXTRA_NAVIGATE_SETTINGS, false)
        setContent {
            MangaInkerTheme {
                MangaInkerRoot(
                    controller = controller,
                    updateManager = updateManager,
                    permissionRefreshKey = permissionRefreshKey,
                    navigationRequest = navigationRequest,
                    navigateToSettings = navigateToSettings,
                    onPickPage = { openDocument.launch(arrayOf("image/*")) },
                    onCaptureScreen = { beginScreenCapture() },
                    onOpenOverlaySettings = {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
                    },
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onOpenAppInfo = {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName)))
                    },
                    onShowFloatingPanel = { showFloatingPanel() },
                    onOpenIbisPaint = { openIbisPaint() },
                    onExportImage = { bitmap -> exportPng(bitmap) }
                )
            }
        }
    }


    private fun beginScreenCapture() {
        if (!PermissionChecks.accessibilityGranted(this) || !controller.state.value.serviceConnected) {
            Toast.makeText(this, "Enable Manga Inker Accessibility first. It detects when ibisPaint is visible.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        val manager = getSystemService(MediaProjectionManager::class.java)
        requestScreenCapture.launch(manager.createScreenCaptureIntent())
    }

    private fun exportPng(bitmap: Bitmap) {
        if (bitmap.isRecycled) {
            Toast.makeText(this, "The export image is no longer available.", Toast.LENGTH_LONG).show()
            return
        }
        pendingExportBitmap?.takeIf { !it.isRecycled }?.recycle()
        pendingExportBitmap = bitmap
        createPngDocument.launch("MangaInk-Page.png")
    }

    private fun showFloatingPanel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow display over other apps first.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            return
        }
        val intent = Intent(this, FloatingPanelService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navigateToSettings = intent.getBooleanExtra(EXTRA_NAVIGATE_SETTINGS, false)
        navigationRequest++
    }

    override fun onResume() {
        super.onResume()
        permissionRefreshKey++
    }

    private fun openIbisPaint() {
        val packages = listOf("jp.ne.ibis.ibispaintx.app", "jp.ne.ibis.ibispaint.app")
        val launchIntent = packages.firstNotNullOfOrNull { packageName ->
            packageManager.getLaunchIntentForPackage(packageName)
        }
        if (launchIntent != null) startActivity(launchIntent)
        else Toast.makeText(this, "Open ibisPaint X manually, then use the floating controls.", Toast.LENGTH_LONG).show()
    }
    companion object {
        const val EXTRA_NAVIGATE_SETTINGS = "manga_inker_navigate_settings"
    }

}
