package com.example.mangainker.ui

import android.content.Intent
import android.net.Uri
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
import com.example.mangainker.overlay.FloatingPanelService
import com.example.mangainker.update.GitHubUpdateManager

class MainActivity : ComponentActivity() {
    private val controller by lazy { (application as MangaInkerApp).sessionController }
    private val updateManager by lazy { (application as MangaInkerApp).updateManager }
    private var permissionRefreshKey by mutableIntStateOf(0)

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(controller::importPage)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MangaInkerTheme {
                MangaInkerRoot(
                    controller = controller,
                    updateManager = updateManager,
                    permissionRefreshKey = permissionRefreshKey,
                    onPickPage = { openDocument.launch(arrayOf("image/*")) },
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
                    onOpenIbisPaint = { openIbisPaint() }
                )
            }
        }
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
}
