package com.example.mangainker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.graphics.Bitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mangainker.core.Logger
import com.example.mangainker.core.SessionCommand
import com.example.mangainker.core.SessionController
import com.example.mangainker.core.SessionState
import com.example.mangainker.permissions.PermissionChecks
import com.example.mangainker.overlay.FloatingAssistantState
import com.example.mangainker.overlay.OverlayActivation
import com.example.mangainker.update.GitHubUpdateManager
import com.example.mangainker.update.UpdateUiState

private enum class AppTab { Home, Studio, TraceLog, Settings }

@Composable
fun MangaInkerRoot(
    controller: SessionController,
    updateManager: GitHubUpdateManager,
    permissionRefreshKey: Int,
    navigationRequest: Int,
    navigateToSettings: Boolean,
    onPickPage: () -> Unit,
    onCaptureScreen: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onShowFloatingPanel: () -> Unit,
    onToggleAssistant: (Boolean) -> Unit,
    onOpenIbisPaint: () -> Unit,
    onExportImage: (Bitmap) -> Unit,
    onExportCbz: (StudioProjectStore, StudioProject) -> Unit
) {
    val state by controller.state.collectAsState()
    val updateState by updateManager.state.collectAsState()
    val logs by Logger.lines.collectAsState()
    val assistantState by OverlayActivation.state.collectAsState()
    val context = LocalContext.current
    val studioStore = remember(context) { StudioProjectStore(context) }
    val overlayGranted = remember(permissionRefreshKey) { PermissionChecks.overlayGranted(context) }
    val batteryExempt = remember(permissionRefreshKey) { PermissionChecks.batteryOptimizationExempt(context) }
    val accessibilityGranted = remember(permissionRefreshKey) { PermissionChecks.accessibilityGranted(context) }
    var selectedTab by remember(navigationRequest) { mutableStateOf(if (navigateToSettings) AppTab.Settings else AppTab.Home) }
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
                    selected = selectedTab == AppTab.Studio,
                    onClick = { selectedTab = AppTab.Studio },
                    icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    label = { Text("Studio") }
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.TraceLog,
                    onClick = { selectedTab = AppTab.TraceLog },
                    icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    label = { Text("Trace log") }
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
                batteryExempt = batteryExempt,
                controller = controller,
                updateState = updateState,
                onInstallUpdate = updateManager::installUpdate,
                onPickPage = onPickPage,
                onCaptureScreen = onCaptureScreen,
                onOpenOverlay = onOpenOverlaySettings,
                onOpenAccessibility = onOpenAccessibilitySettings,
                onOpenAppInfo = onOpenAppInfo,
                onRestrictedGuide = { showRestrictedGuide = true },
                onShowFloatingPanel = onShowFloatingPanel,
                assistantState = assistantState,
                onToggleAssistant = onToggleAssistant,
                onOpenIbisPaint = onOpenIbisPaint
            )
            AppTab.Studio -> StudioWorkspaceScreen(
                modifier = Modifier.padding(padding),
                importedPage = state.sourceBitmap,
                store = studioStore,
                onExportImage = onExportImage,
                onExportCbz = { currentProject -> onExportCbz(studioStore, currentProject) }
            )
            AppTab.TraceLog -> TraceLogScreen(
                modifier = Modifier.padding(padding),
                state = state,
                logs = logs,
                controller = controller
            )
            AppTab.Settings -> SettingsScreen(
                modifier = Modifier.padding(padding),
                state = state,
                logs = logs,
                controller = controller,
                updateManager = updateManager,
                onShowDebugLog = controller::setShowDebugLog
            )
        }
    }

    if (state.showResultDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Test stroke finished") },
            text = { Text("Did you see the line appear on the ibisPaint canvas, and have you undone it? Only confirm Yes after checking both.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { controller.recordTestResult("Yes") }) { Text("Yes, visible and undone") }
                    TextButton(onClick = { controller.recordTestResult("No") }) { Text("No") }
                }
            },
            dismissButton = {
                TextButton(onClick = { controller.recordTestResult("Partly") }) { Text("Partly") }
            },
            containerColor = InkPalette.surface
        )
    }

    if (showRestrictedGuide) {
        AlertDialog(
            onDismissRequest = { showRestrictedGuide = false },
            title = { Text("Restricted settings") },
            text = {
                Text("On Android 13 and newer, open App info, use the three-dot menu, then choose Allow restricted settings. Return here after enabling it. Android does not expose a reliable public status check, so this row uses your manual confirmation.")
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
    batteryExempt: Boolean,
    controller: SessionController,
    updateState: UpdateUiState,
    onInstallUpdate: () -> Unit,
    onPickPage: () -> Unit,
    onCaptureScreen: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onRestrictedGuide: () -> Unit,
    onShowFloatingPanel: () -> Unit,
    assistantState: FloatingAssistantState,
    onToggleAssistant: (Boolean) -> Unit,
    onOpenIbisPaint: () -> Unit
) {
    val setupComplete = overlayGranted && accessibilityGranted && batteryExempt
    Column(
        modifier = modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineArtMark(Modifier.size(44.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("MANGA INKER", style = MaterialTheme.typography.titleLarge, letterSpacing = 0.8.sp)
                Text("FULL PAGE OUTLINE TRACER", color = InkPalette.secondaryText, style = MaterialTheme.typography.labelSmall, letterSpacing = 0.8.sp)
            }
            SurfaceTag(when { state.captureInProgress -> "RECORDING"; state.running -> "TRACING"; state.paths.isNotEmpty() -> "PAGE READY"; else -> "LOCAL" })
        }

        if (updateState.updateAvailable) {
            Card(
                colors = CardDefaults.cardColors(containerColor = InkPalette.raised),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, InkPalette.success)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Update ready · " + updateState.latestVersionName, color = InkPalette.success, style = MaterialTheme.typography.titleMedium)
                    Text("Downloaded and SHA-256 verified. Android will ask before replacing the installed app.", color = InkPalette.secondaryText, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onInstallUpdate, modifier = Modifier.fillMaxWidth()) {
                        Text("Install update")
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = InkPalette.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, InkPalette.outline)
        ) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Device setup", style = MaterialTheme.typography.titleMedium)
                        Text("Required for floating controls and drawing gestures", color = InkPalette.secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    SurfaceTag(if (setupComplete) "READY" else "SETUP")
                }
                SetupRow("Display over apps", "Floating panel above ibisPaint", overlayGranted, "Open", onOpenOverlay)
                androidx.compose.material3.HorizontalDivider(color = InkPalette.outline)
                SetupRow("Accessibility service", "Sends the outline paths as touch gestures", accessibilityGranted, "Open", onOpenAccessibility)
                androidx.compose.material3.HorizontalDivider(color = InkPalette.outline)
                SetupRow(
                    "Screen capture",
                    "Android asks for approval each capture session",
                    state.captureInProgress,
                    if (state.captureInProgress) "Recording" else "Start",
                    onCaptureScreen
                )
                androidx.compose.material3.HorizontalDivider(color = InkPalette.outline)
                SetupRow("Restricted settings", "Manual confirmation for sideloaded builds", state.restrictedSettingsConfirmed, "Guide", onRestrictedGuide)
                androidx.compose.material3.HorizontalDivider(color = InkPalette.outline)
                SetupRow("Battery optimization", "Check whether Android allows background work", batteryExempt, "Fix", onOpenAppInfo)
                Text(
                    "ColorOS: open App info for Manga Inker, enable Auto-launch and Background activity, then set Battery usage to Unrestricted. Menu names vary by ColorOS version. Screen capture still needs Android approval every session.",
                    color = InkPalette.secondaryText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = InkPalette.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, if (assistantState.running) InkPalette.success else InkPalette.outline)
        ) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Floating assistant", style = MaterialTheme.typography.titleMedium)
                        Text("Master control for the on-screen Manga Inker bubble", color = InkPalette.secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = assistantState.requested,
                        onCheckedChange = onToggleAssistant
                    )
                }
                Text(
                    when {
                        assistantState.running && !overlayGranted -> "Service running, but Android overlay permission is currently unavailable."
                        assistantState.running && !accessibilityGranted -> "Service running. Accessibility is disconnected, so drawing gestures are unavailable."
                        assistantState.running -> "Service running. Floating controls are active."
                        assistantState.requested && !overlayGranted -> "Waiting for Display over other apps permission. Grant it, then return to Manga Inker."
                        assistantState.requested && assistantState.error != null -> "Could not start: " + assistantState.error
                        assistantState.requested -> "Starting floating controls…"
                        else -> "Off. The floating assistant service is not running."
                    },
                    color = when {
                        assistantState.error != null -> InkPalette.error
                        assistantState.running -> InkPalette.success
                        else -> InkPalette.secondaryText
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                if (!accessibilityGranted) {
                    Text(
                        "Enable Manga Inker under Android Accessibility settings before using Test or Start to draw in ibisPaint.",
                        color = InkPalette.secondaryText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    "Screen capture permission is separate and Android will ask for approval each time you capture.",
                    color = InkPalette.secondaryText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = InkPalette.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, InkPalette.outline)
        ) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("1. Source page", style = MaterialTheme.typography.titleMedium)
                        Text("Import one complete manga page", color = InkPalette.secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        if (state.sourceBitmap != null) state.pageWidth.toString() + " × " + state.pageHeight else "No page",
                        color = InkPalette.secondaryText,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                OutlinedButton(onClick = onPickPage, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(9.dp)) {
                    Text(if (state.sourceBitmap == null) "Choose page image" else "Replace page image")
                }

                OutlinedButton(onClick = onCaptureScreen, enabled = !state.captureInProgress && !state.running && !state.scanInProgress, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(9.dp)) {
                    Text(if (state.captureInProgress) "Recording screen…" else "Capture ibisPaint screen")
                }
                Text(
                    "Android asks for screen-capture approval each session. Manga Inker waits until ibisPaint is visible, captures one frame, then processes it on this device.",
                    color = InkPalette.secondaryText,
                    style = MaterialTheme.typography.bodySmall
                )

                if (state.sourceBitmap != null) {
                    PagePreview(state = state, onSelectRegion = controller::setScanRegion)
                    Text(
                        "Blue lines show the extracted centreline paths. Choose full page or drag an area to scan only part of it.",
                        color = InkPalette.secondaryText,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { if (state.selectionMode) controller.cancelSelectionMode() else controller.enableSelectionMode() },
                            enabled = !state.running && !state.scanInProgress && !state.captureInProgress && !state.calibrating,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(9.dp)
                        ) { Text(if (state.selectionMode) "Cancel selection" else "Select scan area") }
                        OutlinedButton(
                            onClick = controller::scanFullPage,
                            enabled = !state.running && !state.scanInProgress && !state.captureInProgress && !state.calibrating,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(9.dp)
                        ) { Text("Full page") }
                    }
                    if (state.selectionMode) {
                        Text(
                            "Drag a rectangle over the preview. Only that part of the page will be scanned.",
                            color = InkPalette.warning,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (
                        state.selectedLeft > 0f || state.selectedTop > 0f ||
                        state.selectedRight < 1f || state.selectedBottom < 1f
                    ) {
                        Text(
                            "Selected area: " + ((state.selectedRight - state.selectedLeft) * 100).toInt() +
                                "% × " + ((state.selectedBottom - state.selectedTop) * 100).toInt() +
                                "% of the full page.",
                            color = InkPalette.secondaryText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Box(
                        Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(10.dp))
                            .background(InkPalette.raised),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Your full-page preview will appear here", color = InkPalette.secondaryText)
                    }
                }

                Text("2. Outline scan", style = MaterialTheme.typography.titleMedium)
                Text(
                    state.scanStatus,
                    color = if (state.isError) InkPalette.warning else InkPalette.secondaryText,
                    style = MaterialTheme.typography.bodySmall
                )
                if (state.isError && state.sourceBitmap != null && !state.running && !state.scanInProgress) {
                    OutlinedButton(
                        onClick = controller::scanPage,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(9.dp)
                    ) {
                        Text("Retry scan")
                    }
                }
                if (state.scanInProgress) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = state.scanProgress.coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth(),
                        color = InkPalette.accent,
                        trackColor = InkPalette.raised
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("PATHS", state.paths.size.toString(), Modifier.weight(1f))
                    Metric("INK PIXELS", state.foregroundPixels.toString(), Modifier.weight(1f))
                    Metric("CENTRELINE", state.skeletonPixels.toString(), Modifier.weight(1f))
                }

                Button(
                    onClick = {
                        if (state.scanInProgress) controller.cancelScan()
                        else controller.scanPage()
                    },
                    enabled = !state.running && !state.captureInProgress && !state.calibrating && (
                        state.scanInProgress ||
                            (state.sourceBitmap != null && !state.selectionMode)
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = InkPalette.accent),
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Text(
                        if (state.scanInProgress) "Stop current operation"
                        else if (state.selectedLeft > 0f || state.selectedTop > 0f || state.selectedRight < 1f || state.selectedBottom < 1f) "Scan selected area"
                        else if (state.paths.isEmpty()) "Scan full page"
                        else "Scan full page again"
                    )
                }

                if (state.sourceBitmap != null) {
                    if (state.paths.isNotEmpty()) Text("Scan threshold: " + state.threshold + "  ·  Removed specks: " + state.discardedComponents, color = InkPalette.secondaryText, style = MaterialTheme.typography.labelSmall)
                    Text("Threshold adjustment", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = state.thresholdOffset.toFloat(),
                        onValueChange = { controller.setThresholdOffset(it.toInt()) },
                        valueRange = -30f..30f,
                        steps = 59
                    )
                    Text("Speck removal: " + state.minComponentPixels + " pixels minimum", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = state.minComponentPixels.toFloat(),
                        onValueChange = { controller.setMinComponentPixels(it.toInt()) },
                        valueRange = 1f..20f,
                        steps = 18
                    )
                    Text("Curve smoothing: " + (state.smoothing * 100).toInt() + "%", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = state.smoothing,
                        onValueChange = controller::setSmoothing,
                        valueRange = 0f..1f
                    )
                    Text("Detail preservation: " + (state.detailPreservation * 100).toInt() + "%", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = state.detailPreservation,
                        onValueChange = controller::setDetailPreservation,
                        valueRange = 0f..1f
                    )
                    OutlinedButton(
                        onClick = controller::scanPage,
                        enabled = !state.scanInProgress && !state.running && !state.captureInProgress && !state.calibrating,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(9.dp)
                    ) { Text("Rebuild paths with these settings") }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = InkPalette.raised),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, InkPalette.outline)
        ) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("3. Calibrate and trace", style = MaterialTheme.typography.titleMedium)
                StatusLine("Accessibility", if (state.serviceConnected) "Connected" else "Not connected")
                StatusLine("ibisPaint foreground", if (state.ibisPaintForeground) "Detected" else "Not detected")
                StatusLine("Canvas alignment", if (state.calibration != null) "Saved" else "Not calibrated")
                StatusLine("Test confirmation", if (state.testGestureVerified) "Verified" else "Required")
                Text(
                    "Drag four markers to the exact corners of the visible ibisPaint page. Run the test stroke, check it, undo it, then start the full-page trace.",
                    color = InkPalette.secondaryText,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Set the ibisPaint brush and size before tracing. Android gesture strokes do not set pressure, so ibisPaint's active brush determines line thickness.",
                    color = InkPalette.warning,
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = {
                        controller.beginCalibration()
                        onShowFloatingPanel()
                    },
                    enabled = state.paths.isNotEmpty() && !state.running && !state.scanInProgress && !state.captureInProgress && !state.calibrating,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = InkPalette.accent),
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Text(if (state.calibration != null) "Recalibrate page corners" else "Calibrate page corners")
                }
                OutlinedButton(
                    onClick = onShowFloatingPanel,
                    enabled = overlayGranted,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(9.dp)
                ) { Text("Open floating controls") }
                if (state.isError) {
                    OutlinedButton(onClick = {
                        if (!state.serviceConnected) onOpenAccessibility() else onOpenIbisPaint()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (!state.serviceConnected) "Open accessibility settings" else "Open ibisPaint")
                    }
                }
                if (state.paths.isNotEmpty()) {
                    OutlinedButton(
                        onClick = controller::clearPage,
                        enabled = !state.running && !state.scanInProgress && !state.captureInProgress && !state.calibrating,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(9.dp)
                    ) { Text("Clear page session") }
                }
            }
        }

        Text(
            state.message,
            color = if (state.isError) InkPalette.warning else InkPalette.secondaryText,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "LOCAL ARTWORK  ·  GitHub update checks only  ·  No artwork upload",
            color = InkPalette.secondaryText,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun PagePreview(state: SessionState, onSelectRegion: (Float, Float, Float, Float) -> Unit) {
    val bitmap = state.sourceBitmap ?: return
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    Box(
        Modifier.fillMaxWidth().height(270.dp).clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF101014))
            .then(
                if (state.selectionMode) Modifier.pointerInput(state.selectionMode, bitmap.width, bitmap.height) {
                    val boxWidth = size.width.toFloat()
                    val boxHeight = size.height.toFloat()
                    detectDragGestures(
                        onDragStart = { point ->
                            dragStart = point
                            dragCurrent = point
                        },
                        onDrag = { change, _ ->
                            dragCurrent = change.position
                            change.consume()
                        },
                        onDragEnd = {
                            val start = dragStart
                            val end = dragCurrent
                            if (start != null && end != null) {
                                val scale = minOf(boxWidth / bitmap.width, boxHeight / bitmap.height)
                                val drawWidth = bitmap.width * scale
                                val drawHeight = bitmap.height * scale
                                val leftInset = (boxWidth - drawWidth) / 2f
                                val topInset = (boxHeight - drawHeight) / 2f
                                fun toU(point: Offset) = ((point.x - leftInset) / drawWidth).coerceIn(0f, 1f)
                                fun toV(point: Offset) = ((point.y - topInset) / drawHeight).coerceIn(0f, 1f)
                                onSelectRegion(
                                    minOf(toU(start), toU(end)),
                                    minOf(toV(start), toV(end)),
                                    maxOf(toU(start), toU(end)),
                                    maxOf(toV(start), toV(end))
                                )
                            }
                            dragStart = null
                            dragCurrent = null
                        },
                        onDragCancel = {
                            dragStart = null
                            dragCurrent = null
                        }
                    )
                } else Modifier
            )
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Imported manga page",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
        Canvas(Modifier.fillMaxSize()) {
            val scale = minOf(size.width / bitmap.width, size.height / bitmap.height)
            val offsetX = (size.width - bitmap.width * scale) / 2f
            val offsetY = (size.height - bitmap.height * scale) / 2f
            state.paths.forEach { inkPath ->
                if (inkPath.points.size < 2) return@forEach
                val path = Path()
                val first = inkPath.points.first()
                path.moveTo(
                    offsetX + (state.scanLeftPx + first.x) * scale,
                    offsetY + (state.scanTopPx + first.y) * scale
                )
                inkPath.points.drop(1).forEach { point ->
                    path.lineTo(
                        offsetX + (state.scanLeftPx + point.x) * scale,
                        offsetY + (state.scanTopPx + point.y) * scale
                    )
                }
                drawPath(
                    path,
                    color = Color(0xFF22B8D6),
                    style = Stroke(width = 1.1.dp.toPx())
                )
            }

            val hasSelection = state.selectedLeft > 0f || state.selectedTop > 0f ||
                state.selectedRight < 1f || state.selectedBottom < 1f
            if (hasSelection) {
                val left = offsetX + state.selectedLeft * bitmap.width * scale
                val top = offsetY + state.selectedTop * bitmap.height * scale
                val right = offsetX + state.selectedRight * bitmap.width * scale
                val bottom = offsetY + state.selectedBottom * bitmap.height * scale
                drawRect(
                    color = Color(0xFF22B8D6),
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            if (state.selectionMode && dragStart != null && dragCurrent != null) {
                drawRect(
                    color = InkPalette.warning,
                    topLeft = Offset(
                        minOf(dragStart!!.x, dragCurrent!!.x),
                        minOf(dragStart!!.y, dragCurrent!!.y)
                    ),
                    size = Size(
                        kotlin.math.abs(dragCurrent!!.x - dragStart!!.x),
                        kotlin.math.abs(dragCurrent!!.y - dragStart!!.y)
                    ),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun TraceLogScreen(
    modifier: Modifier,
    state: SessionState,
    logs: List<String>,
    controller: SessionController
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Trace log", style = MaterialTheme.typography.titleLarge)
        Card(
            colors = CardDefaults.cardColors(containerColor = InkPalette.surface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, InkPalette.outline)
        ) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        state.captureInProgress -> "Recording screen"
                        state.scanInProgress -> "Scanning"
                        state.running && state.paused -> "Paused"
                        state.running -> "Tracing"
                        state.isError -> "Needs attention"
                        state.paths.isNotEmpty() -> "Ready"
                        else -> "Idle"
                    },
                    color = if (state.isError) InkPalette.error else InkPalette.accent,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(state.message, color = InkPalette.secondaryText, style = MaterialTheme.typography.bodySmall)
                if (state.running || state.totalPaths > 0) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = state.progress.coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth(),
                        color = InkPalette.accent,
                        trackColor = InkPalette.raised
                    )
                }
                StatusLine("Completed paths", state.completedPaths.toString())
                StatusLine("Total paths", state.totalPaths.toString())
                StatusLine("Failed paths", state.failedPaths.toString())
                StatusLine("Last gesture", state.lastGestureResult)
                if (state.isError && state.sourceBitmap != null && !state.scanInProgress && !state.running) {
                    OutlinedButton(onClick = controller::scanPage, modifier = Modifier.fillMaxWidth()) {
                        Text("Retry scan")
                    }
                }
                if (state.failedPaths > 0) {
                    Button(
                        onClick = controller::retryTraceFromFailedPath,
                        enabled = state.serviceConnected && state.ibisPaintForeground && !state.running,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Retry from first failed path")
                    }
                    Text(
                        "Previously completed paths are not replayed. Failed and skipped paths are attempted again.",
                        color = InkPalette.secondaryText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (state.resumeAvailable && !state.serviceConnected) {
                    Text(
                        "Reconnect Accessibility in Android settings, then return to ibisPaint. The trace resumes after the last completed path.",
                        color = InkPalette.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (state.pathLog.isNotEmpty()) {
                    Text("Paths", style = MaterialTheme.typography.titleMedium)
                    state.pathLog.take(120).forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Box(
                                Modifier.size(8.dp).clip(CircleShape).background(
                                    when (entry.status) {
                                        "done" -> InkPalette.success
                                        "failed" -> InkPalette.error
                                        "skipped" -> InkPalette.warning
                                        else -> InkPalette.outline
                                    }
                                )
                            )
                            Column(Modifier.weight(1f)) {
                                Text("Path " + entry.pathNumber + " · " + entry.status.uppercase(), style = MaterialTheme.typography.bodySmall)
                                if (entry.detail.isNotBlank()) {
                                    Text(entry.detail, color = InkPalette.secondaryText, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = InkPalette.surface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, InkPalette.outline)
        ) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Events", style = MaterialTheme.typography.titleMedium)
                if (logs.isEmpty()) {
                    Text("No events yet.", color = InkPalette.secondaryText)
                } else {
                    logs.takeLast(200).asReversed().forEach { line ->
                        Text(
                            line,
                            color = InkPalette.secondaryText,
                            style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        )
                        androidx.compose.material3.HorizontalDivider(color = InkPalette.outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    state: SessionState,
    logs: List<String>,
    controller: SessionController,
    updateManager: GitHubUpdateManager,
    onShowDebugLog: (Boolean) -> Unit
) {
    val updateState by updateManager.state.collectAsState()
    var updateToken by remember { mutableStateOf("") }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)

        Card(colors = CardDefaults.cardColors(containerColor = InkPalette.surface), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Scan quality", style = MaterialTheme.typography.titleMedium)
                Text("Threshold adjustment: " + state.thresholdOffset, color = InkPalette.secondaryText)
                Slider(
                    value = state.thresholdOffset.toFloat(),
                    onValueChange = { controller.setThresholdOffset(it.toInt()) },
                    valueRange = -60f..60f,
                    steps = 23
                )
                Text("Minimum component size: " + state.minComponentPixels + " pixels", color = InkPalette.secondaryText)
                Slider(
                    value = state.minComponentPixels.toFloat(),
                    onValueChange = { controller.setMinComponentPixels(it.toInt()) },
                    valueRange = 1f..20f,
                    steps = 18
                )
                Text("Curve smoothing: " + (state.smoothing * 100f).toInt() + "%", color = InkPalette.secondaryText)
                Slider(
                    value = state.smoothing,
                    onValueChange = controller::setSmoothing,
                    valueRange = 0f..1f
                )
                Text("Detail preservation: " + (state.detailPreservation * 100f).toInt() + "%", color = InkPalette.secondaryText)
                Slider(
                    value = state.detailPreservation,
                    onValueChange = controller::setDetailPreservation,
                    valueRange = 0f..1f
                )
                Text(
                    "Adjust these values and rescan to rebuild the paths. Higher speck filtering removes tiny marks; higher detail preservation keeps tighter corners and small features.",
                    color = InkPalette.secondaryText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = InkPalette.surface), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Drawing speed", style = MaterialTheme.typography.titleMedium)
                Text(state.drawingSpeedPxPerSecond.toInt().toString() + " pixels per second", color = InkPalette.secondaryText)
                Slider(
                    value = state.drawingSpeedPxPerSecond,
                    onValueChange = controller::setDrawingSpeed,
                    valueRange = 250f..3_000f
                )
                Text("Gesture segment length", style = MaterialTheme.typography.titleMedium)
                Text(state.gestureSpeedMs.toString() + " ms maximum", color = InkPalette.secondaryText)
                Slider(
                    value = state.gestureSpeedMs.toFloat(),
                    onValueChange = { controller.setGestureSpeed(it.toInt()) },
                    valueRange = 100f..500f,
                    steps = 7
                )
                Text("Each Android gesture is capped at 500 ms. Longer paths are split into connected segments.", color = InkPalette.secondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = InkPalette.surface), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Panel auto-collapse", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (state.panelAutoCollapseMs == 0L) "Off" else (state.panelAutoCollapseMs / 1_000L).toString() + " seconds",
                    color = InkPalette.secondaryText
                )
                Slider(
                    value = state.panelAutoCollapseMs.toFloat() / 1_000f,
                    onValueChange = { controller.setPanelAutoCollapse((it * 1_000f).toLong()) },
                    valueRange = 0f..30f,
                    steps = 5
                )
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = InkPalette.surface), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Show debug log", style = MaterialTheme.typography.titleMedium)
                        Text("Recent MI_GESTURE entries held in memory.", color = InkPalette.secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = state.showDebugLog, onCheckedChange = onShowDebugLog)
                }
                if (state.showDebugLog) {
                    if (logs.isEmpty()) {
                        Text("No log entries yet.", color = InkPalette.secondaryText)
                    } else {
                        Column(
                            Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(8.dp))
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
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("App updates", style = MaterialTheme.typography.titleMedium)
                Text("Installed version: " + updateState.currentVersionName, color = InkPalette.secondaryText, style = MaterialTheme.typography.bodySmall)
                Text(
                    "Last checked: " + (updateState.lastCheckedAtMs?.let {
                        android.text.format.DateFormat.format("dd MMM yyyy, hh:mm a", it).toString()
                    } ?: "Not checked yet"),
                    color = InkPalette.secondaryText,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Check on launch", style = MaterialTheme.typography.bodyLarge)
                        Text("Also checks every 6 hours while the app is running.", color = InkPalette.secondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = updateState.checkOnLaunch,
                        onCheckedChange = updateManager::setCheckOnLaunch
                    )
                }
                Text(
                    updateState.message,
                    color = if (updateState.updateAvailable) InkPalette.success else InkPalette.secondaryText,
                    style = MaterialTheme.typography.bodySmall
                )
                if (updateState.downloading) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = updateState.downloadProgress.coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth(),
                        color = InkPalette.accent,
                        trackColor = InkPalette.raised
                    )
                    Text(
                        "Download " + (updateState.downloadProgress * 100f).toInt() + "%",
                        color = InkPalette.secondaryText,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (updateState.releaseNotes.isNotBlank()) {
                    Text("Release notes", style = MaterialTheme.typography.labelLarge)
                    Text(updateState.releaseNotes.take(1800), color = InkPalette.secondaryText, style = MaterialTheme.typography.bodySmall)
                }
                if (!updateState.tokenConfigured) {
                    OutlinedTextField(
                        value = updateToken,
                        onValueChange = { updateToken = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("One-time update access token") },
                        supportingText = { Text("One-time setup for this private update channel. Read-only access is stored securely on this phone.") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            updateManager.saveTokenAndCheck(updateToken)
                            updateToken = ""
                        },
                        enabled = updateToken.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save token and check") }
                } else {
                    OutlinedButton(
                        onClick = updateManager::checkForUpdate,
                        enabled = !updateState.checking && !updateState.downloading,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (updateState.checking) "Checking…" else "Check for updates") }
                    if (updateState.updateAvailable) {
                        Button(
                            onClick = updateManager::installUpdate,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = InkPalette.accent)
                        ) { Text("Install update " + updateState.latestVersionName) }
                    }
                    TextButton(onClick = updateManager::clearToken) { Text("Remove GitHub update access") }
                }
                Text(
                    "Manga Inker checks for new official releases automatically and downloads verified updates in the background. You only need to approve installation in Android. The private repository requires a one-time read-only access token, stored encrypted with Android Keystore.",
                    color = InkPalette.secondaryText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = InkPalette.surface), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("About", style = MaterialTheme.typography.titleMedium)
                Text("Manga Inker · Full-page outline tracing", color = InkPalette.secondaryText)
                Text("Images and paths stay in the active app session. Clear the page to release the imported image.", color = InkPalette.secondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
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
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(if (granted) InkPalette.success else InkPalette.warning))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, color = InkPalette.secondaryText, style = MaterialTheme.typography.bodySmall)
            Text(
                if (granted) "Granted" else "Needs action",
                color = if (granted) InkPalette.success else InkPalette.warning,
                style = MaterialTheme.typography.labelSmall
            )
        }
        OutlinedButton(onClick = onAction, shape = RoundedCornerShape(8.dp)) { Text(actionLabel) }
    }
}

@Composable
private fun SurfaceTag(label: String) {
    androidx.compose.material3.Surface(
        color = InkPalette.raised,
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, InkPalette.outline)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), color = InkPalette.secondaryText, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(InkPalette.raised)
            .padding(horizontal = 9.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, color = InkPalette.secondaryText, style = MaterialTheme.typography.labelSmall)
        Text(value, color = InkPalette.primaryText, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = InkPalette.secondaryText, modifier = Modifier.weight(1f))
        Text(value, color = InkPalette.primaryText, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LineArtMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRoundRect(
            color = InkPalette.raised,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.24f)
        )
        val page = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.17f)
            lineTo(size.width * 0.59f, size.height * 0.17f)
            lineTo(size.width * 0.73f, size.height * 0.31f)
            lineTo(size.width * 0.73f, size.height * 0.79f)
            lineTo(size.width * 0.28f, size.height * 0.79f)
            close()
        }
        drawPath(page, color = InkPalette.primaryText)
        val fold = Path().apply {
            moveTo(size.width * 0.59f, size.height * 0.17f)
            lineTo(size.width * 0.59f, size.height * 0.31f)
            lineTo(size.width * 0.73f, size.height * 0.31f)
            close()
        }
        drawPath(fold, color = InkPalette.accent)
        drawLine(InkPalette.raised, Offset(size.width * 0.37f, size.height * 0.43f), Offset(size.width * 0.62f, size.height * 0.43f), 1.5.dp.toPx())
        drawLine(InkPalette.raised, Offset(size.width * 0.37f, size.height * 0.53f), Offset(size.width * 0.57f, size.height * 0.53f), 1.5.dp.toPx())
        val pen = Path().apply {
            moveTo(size.width * 0.64f, size.height * 0.57f)
            lineTo(size.width * 0.73f, size.height * 0.66f)
            lineTo(size.width * 0.52f, size.height * 0.84f)
            lineTo(size.width * 0.43f, size.height * 0.87f)
            lineTo(size.width * 0.46f, size.height * 0.78f)
            close()
        }
        drawPath(pen, color = InkPalette.accent)
    }
}
