package com.example.mangainker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.mangainker.core.Logger
import com.example.mangainker.core.SessionCommand
import com.example.mangainker.core.SessionController
import com.example.mangainker.core.SessionState
import com.example.mangainker.permissions.PermissionChecks

private enum class AppTab { Home, Settings }

@Composable
fun MangaInkerRoot(
    controller: SessionController,
    permissionRefreshKey: Int,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onShowFloatingPanel: () -> Unit,
    onOpenIbisPaint: () -> Unit
) {
    val state by controller.state.collectAsState()
    val logs by Logger.lines.collectAsState()
    val context = LocalContext.current
    val overlayGranted = remember(permissionRefreshKey) { PermissionChecks.overlayGranted(context) }
    val accessibilityGranted = remember(permissionRefreshKey) { PermissionChecks.accessibilityGranted(context) }
    var selectedTab by remember { mutableStateOf(AppTab.Home) }
    var showRestrictedGuide by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = InkPalette.background,
        bottomBar = {
            NavigationBar(containerColor = InkPalette.surface, contentColor = InkPalette.primaryText) {
                NavigationBarItem(
                    selected = selectedTab == AppTab.Home,
                    onClick = { selectedTab = AppTab.Home },
                    icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.Settings,
                    onClick = { selectedTab = AppTab.Settings },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            AppTab.Home -> HomeScreen(
                modifier = Modifier.padding(padding),
                state = state,
                overlayGranted = overlayGranted,
                accessibilityGranted = accessibilityGranted,
                onOpenOverlay = onOpenOverlaySettings,
                onOpenAccessibility = onOpenAccessibilitySettings,
                onRestrictedGuide = { showRestrictedGuide = true },
                onTestStroke = {
                    controller.dispatch(SessionCommand.RunTest)
                    onShowFloatingPanel()
                },
                onRecoveryAction = {
                    if (!state.serviceConnected) onOpenAccessibilitySettings() else onOpenIbisPaint()
                }
            )
            AppTab.Settings -> SettingsScreen(
                modifier = Modifier.padding(padding),
                state = state,
                logs = logs,
                onGestureSpeed = controller::setGestureSpeed,
                onAutoCollapse = controller::setPanelAutoCollapse,
                onShowDebugLog = controller::setShowDebugLog
            )
        }
    }

    if (state.showResultDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Test stroke finished") },
            text = { Text("Did ibisPaint draw a line?") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { controller.recordTestResult("Yes") }) { Text("Yes") }
                    TextButton(onClick = { controller.recordTestResult("No") }) { Text("No") }
                    TextButton(onClick = { controller.recordTestResult("Partly") }) { Text("Partly") }
                }
            },
            containerColor = InkPalette.surface
        )
    }

    if (showRestrictedGuide) {
        AlertDialog(
            onDismissRequest = { showRestrictedGuide = false },
            title = { Text("Restricted settings") },
            text = {
                Text("On Android 13 and newer, open App info, use the three-dot menu, then choose Allow restricted settings. Return here after enabling it. Android does not provide a reliable public status check, so this row uses your manual confirmation.")
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        showRestrictedGuide = false
                        onOpenAppInfo()
                    }) { Text("Open app info") }
                    TextButton(onClick = {
                        controller.confirmRestrictedSettingsUnlocked()
                        showRestrictedGuide = false
                    }) { Text("I've enabled it") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestrictedGuide = false }) { Text("Later") }
            },
            containerColor = InkPalette.surface
        )
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    state: SessionState,
    overlayGranted: Boolean,
    accessibilityGranted: Boolean,
    onOpenOverlay: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onRestrictedGuide: () -> Unit,
    onTestStroke: () -> Unit,
    onRecoveryAction: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LineArtMark(Modifier.size(42.dp))
            Column {
                Text("Manga Inker", style = MaterialTheme.typography.titleLarge)
                Text("Local gesture test", color = InkPalette.secondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }

        Text("Setup", style = MaterialTheme.typography.titleMedium)
        Card(colors = CardDefaults.cardColors(containerColor = InkPalette.surface), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SetupRow("Display over apps", "Required for the floating control panel.", overlayGranted, "Settings", onOpenOverlay)
                SetupRow("Accessibility service", "Sends test touch gestures without reading screen content.", accessibilityGranted, "Settings", onOpenAccessibility)
                SetupRow("Restricted settings unlocked", "Manual check for sideloaded apps on Android 13+.", state.restrictedSettingsConfirmed, "Guide", onRestrictedGuide)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = InkPalette.surface), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Service status", style = MaterialTheme.typography.titleMedium)
                StatusLine("Service connected", if (state.serviceConnected) "Yes" else "No")
                StatusLine("Last gesture result", state.lastGestureResult)
                Text(state.message, color = if (state.isError) InkPalette.warning else InkPalette.secondaryText)
            }
        }

        if (state.isError) {
            Card(colors = CardDefaults.cardColors(containerColor = InkPalette.raised), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(state.message, color = InkPalette.primaryText)
                    OutlinedButton(onClick = onRecoveryAction) {
                        Text(if (state.serviceConnected) "Return to ibisPaint" else "Open Accessibility settings")
                    }
                }
            }
        }

        Button(
            onClick = onTestStroke,
            enabled = overlayGranted && accessibilityGranted && !state.running,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = InkPalette.accent),
            shape = RoundedCornerShape(12.dp)
        ) { Text("Test stroke") }

        if (!overlayGranted || !accessibilityGranted) {
            Text("Enable Display over apps and the Accessibility service to run the test.", color = InkPalette.secondaryText, style = MaterialTheme.typography.bodySmall)
        }
        Text("No network. No storage. Nothing leaves the device.", color = InkPalette.secondaryText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SetupRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, color = InkPalette.secondaryText, style = MaterialTheme.typography.bodySmall)
            Text(
                if (granted) "Granted" else "Needs action",
                color = if (granted) InkPalette.success else InkPalette.warning,
                style = MaterialTheme.typography.labelMedium
            )
        }
        OutlinedButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = InkPalette.secondaryText, modifier = Modifier.weight(0.9f))
        Text(value, color = InkPalette.primaryText, modifier = Modifier.weight(1.1f), style = TextStyle(fontFeatureSettings = "tnum"))
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    state: SessionState,
    logs: List<String>,
    onGestureSpeed: (Int) -> Unit,
    onAutoCollapse: (Long) -> Unit,
    onShowDebugLog: (Boolean) -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)

        Card(colors = CardDefaults.cardColors(containerColor = InkPalette.surface), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Gesture speed", style = MaterialTheme.typography.titleMedium)
                Text(state.gestureSpeedMs.toString() + " ms target segment", color = InkPalette.secondaryText)
                Slider(
                    value = state.gestureSpeedMs.toFloat(),
                    onValueChange = { onGestureSpeed(it.toInt()) },
                    valueRange = 200f..800f,
                    steps = 11
                )
                Text("Android dispatch segments are capped at 500 ms. Values above 500 ms use the 500 ms cap.", style = MaterialTheme.typography.bodySmall, color = InkPalette.secondaryText)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = InkPalette.surface), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Panel auto-collapse delay", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (state.panelAutoCollapseMs == 0L) "Off" else (state.panelAutoCollapseMs / 1_000L).toString() + " seconds",
                    color = InkPalette.secondaryText
                )
                Slider(
                    value = state.panelAutoCollapseMs.toFloat() / 1_000f,
                    onValueChange = { onAutoCollapse((it * 1_000f).toLong()) },
                    valueRange = 0f..30f,
                    steps = 4
                )
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = InkPalette.surface), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Show debug log", style = MaterialTheme.typography.titleMedium)
                        Text("View recent MI_GESTURE lines held in memory.", color = InkPalette.secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = state.showDebugLog, onCheckedChange = onShowDebugLog)
                }
                if (state.showDebugLog) {
                    if (logs.isEmpty()) {
                        Text("No gesture log entries yet.", color = InkPalette.secondaryText)
                    } else {
                        Column(
                            Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(8.dp))
                                .background(InkPalette.raised).verticalScroll(rememberScrollState()).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            logs.forEach { line ->
                                Text(line, color = InkPalette.primaryText, style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace))
                            }
                        }
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = InkPalette.surface), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("About", style = MaterialTheme.typography.titleMedium)
                Text("Version 1.0.1-stage1", color = InkPalette.secondaryText)
                Text("No network. No storage. Nothing leaves the device.", color = InkPalette.secondaryText)
            }
        }
    }
}

@Composable
private fun LineArtMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.18f, size.height * 0.78f)
            cubicTo(size.width * 0.25f, size.height * 0.15f, size.width * 0.75f, size.height * 0.15f, size.width * 0.82f, size.height * 0.78f)
            moveTo(size.width * 0.35f, size.height * 0.5f)
            cubicTo(size.width * 0.44f, size.height * 0.28f, size.width * 0.57f, size.height * 0.72f, size.width * 0.67f, size.height * 0.5f)
        }
        drawPath(path, color = InkPalette.accent, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
    }
}
