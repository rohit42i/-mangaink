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
import com.example.mangainker.overlay.AssistantEnableResult
import com.example.mangainker.overlay.OverlayActivation
import com.example.mangainker.update.GitHubUpdateManager
import com.example.mangainker.ui.StudioProject
import com.example.mangainker.ui.StudioProjectStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val controller by lazy { (application as MangaInkerApp).sessionController }
    private val updateManager by lazy { (application as MangaInkerApp).updateManager }
    private var permissionRefreshKey by mutableIntStateOf(0)
    private var navigationRequest by mutableIntStateOf(0)
    private var navigateToSettings by androidx.compose.runtime.mutableStateOf(false)
    private var pendingExportBitmap: Bitmap? = null
    private var pendingCbzExport: Pair<StudioProjectStore, StudioProject>? = null
    private val exportScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val createPngDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val bitmap = pendingExportBitmap
        pendingExportBitmap = null
        if (uri == null || bitmap == null) {
            bitmap?.takeIf { !it.isRecycled }?.recycle()
            return@registerForActivityResult
        }
        exportScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { output ->
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                            "Android could not encode the PNG."
                        }
                    } ?: error("Android could not open the selected export destination.")
                }
                Toast.makeText(this@MainActivity, "PNG exported successfully.", Toast.LENGTH_LONG).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Toast.makeText(this@MainActivity, "PNG export failed: " + (error.message ?: "unknown error"), Toast.LENGTH_LONG).show()
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    private val createCbzDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.comicbook+zip")
    ) { uri ->
        val pending = pendingCbzExport
        pendingCbzExport = null
        if (uri == null || pending == null) return@registerForActivityResult
        exportScope.launch {
            val temporary = File(cacheDir, "mangainker-${UUID.randomUUID()}.cbz")
            try {
                withContext(Dispatchers.IO) {
                    val exportJob = currentCoroutineContext()[Job]
                    temporary.outputStream().buffered().use { output ->
                        pending.first.writeProjectCbz(pending.second, output) {
                            if (exportJob?.isActive == false) {
                                throw CancellationException("CBZ export cancelled.")
                            }
                        }
                    }
                    temporary.inputStream().buffered().use { input ->
                        contentResolver.openOutputStream(uri)?.buffered()?.use { output ->
                            input.copyTo(output)
                        } ?: error("Android could not open the selected CBZ destination.")
                    }
                }
                Toast.makeText(this@MainActivity, "Comic project exported as CBZ.", Toast.LENGTH_LONG).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Toast.makeText(this@MainActivity, "CBZ export failed: " + (error.message ?: "unknown error"), Toast.LENGTH_LONG).show()
            } finally {
                temporary.delete()
            }
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
        val captureIntent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, grantData)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(captureIntent)
        else startService(captureIntent)
        // Restore controls only when the user has left the assistant enabled.
        OverlayActivation.restoreIfRequested(this)
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
                    onToggleAssistant = { enabled -> setAssistantEnabled(enabled) },
                    onOpenIbisPaint = { openIbisPaint() },
                    onExportImage = { bitmap -> exportPng(bitmap) },
                    onExportCbz = { store, project -> exportCbz(store, project) }
                )
            }
        }
    }


    private fun beginScreenCapture() {
        val session = controller.state.value
        if (session.running || session.scanInProgress || session.captureInProgress || session.calibrating) {
            Toast.makeText(this, "Stop scanning, tracing, or calibration before starting a screen capture.", Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow Manga Inker to display over other apps so its floating scan control can stay above ibisPaint.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            return
        }
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

    private fun exportCbz(store: StudioProjectStore, project: StudioProject) {
        pendingCbzExport = store to project
        val safeName = project.title.trim()
            .replace(Regex("[^A-Za-z0-9 _-]"), "")
            .replace(Regex("\\s+"), "-")
            .take(48)
            .ifBlank { "Manga-Project" }
        createCbzDocument.launch("$safeName.cbz")
    }

    private fun showFloatingPanel() {
        setAssistantEnabled(true)
    }

    private fun setAssistantEnabled(enabled: Boolean) {
        if (!enabled) {
            OverlayActivation.disable(this)
            permissionRefreshKey++
            return
        }

        when (OverlayActivation.enable(this)) {
            AssistantEnableResult.NEEDS_OVERLAY_PERMISSION -> {
                Toast.makeText(
                    this,
                    "Allow Manga Inker to display over other apps, then return here.",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + packageName)
                    )
                )
            }
            AssistantEnableResult.START_FAILED -> {
                Toast.makeText(
                    this,
                    OverlayActivation.state.value.error ?: "Manga Inker could not start its floating controls.",
                    Toast.LENGTH_LONG
                ).show()
            }
            AssistantEnableResult.START_REQUESTED,
            AssistantEnableResult.ALREADY_RUNNING -> Unit
        }
        permissionRefreshKey++
    }

    override fun onDestroy() {
        exportScope.cancel()
        pendingExportBitmap?.takeIf { !it.isRecycled }?.recycle()
        pendingExportBitmap = null
        pendingCbzExport = null
        super.onDestroy()
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
        OverlayActivation.restoreIfRequested(this)
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
