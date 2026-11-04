package com.example.mangainker.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.mangainker.MangaInkerApp
import com.example.mangainker.overlay.FloatingPanelService

class MainActivity : ComponentActivity() {
    private var permissionRefreshKey by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = (application as MangaInkerApp).sessionController

        setContent {
            MangaInkerTheme {
                MangaInkerRoot(
                    controller = controller,
                    permissionRefreshKey = permissionRefreshKey,
                    onOpenOverlaySettings = {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
                    },
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onOpenAppInfo = {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName)))
                    },
                    onShowFloatingPanel = {
                        startService(Intent(this, FloatingPanelService::class.java))
                    },
                    onOpenIbisPaint = { openIbisPaint() }
                )
            }
        }
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
        else Toast.makeText(this, "Open ibisPaint X manually, then return to the floating panel.", Toast.LENGTH_LONG).show()
    }
}
