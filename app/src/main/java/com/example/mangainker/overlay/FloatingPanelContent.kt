package com.example.mangainker.overlay

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Minimize
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.example.mangainker.core.SessionCommand
import com.example.mangainker.core.SessionController
import com.example.mangainker.core.SessionState
import com.example.mangainker.ui.InkPalette

@Composable
fun FloatingPanelContent(
    controller: SessionController,
    onDrag: (Int, Int) -> Unit,
    onSnap: () -> Unit,
    onClose: () -> Unit,
    onOpenMainScreen: () -> Unit,
    onReconnectAccessibility: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val state by controller.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(
        expanded, state.running, state.paused, state.calibrating,
        state.scanInProgress, state.panelAutoCollapseMs
    ) {
        if (expanded && !state.running && !state.calibrating &&
            !state.scanInProgress && state.panelAutoCollapseMs > 0L
        ) {
            delay(state.panelAutoCollapseMs)
            expanded = false
        }
    }

    if (!state.captureInProgress) {
        Surface(
            color = InkPalette.surface,
            contentColor = InkPalette.primaryText,
            shape = RoundedCornerShape(if (expanded) 22.dp else 28.dp),
            border = BorderStroke(1.dp, InkPalette.outline),
            shadowElevation = if (expanded) 12.dp else 8.dp,
            modifier = Modifier
                .widthIn(min = 56.dp, max = 320.dp)
                .animateContentSize(
                    animationSpec = spring(dampingRatio = 0.84f, stiffness = 420f)
                )
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (expanded) 12.dp else 0.dp,
                    vertical = if (expanded) 10.dp else 0.dp
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (expanded) 48.dp else 56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .pointerInput(Unit) {
                                var remainderX = 0f
                                var remainderY = 0f
                                detectDragGestures(
                                    onDragStart = { remainderX = 0f; remainderY = 0f },
                                    onDragEnd = onSnap,
                                    onDragCancel = onSnap,
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val totalX = remainderX + dragAmount.x
                                        val totalY = remainderY + dragAmount.y
                                        val dx = totalX.toInt()
                                        val dy = totalY.toInt()
                                        remainderX = totalX - dx
                                        remainderY = totalY - dy
                                        if (dx != 0 || dy != 0) onDrag(dx, dy)
                                    }
                                )
                            }
                            .clickable(
                                enabled = !expanded,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { expanded = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(if (expanded) 34.dp else 42.dp)
                                .clip(CircleShape)
                                .background(InkPalette.raised),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = if (expanded) "Drag to move overlay" else "Open Manga Inker controls",
                                tint = InkPalette.accent,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(if (expanded) 8.dp else 10.dp)
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(stateDot(state))
                        )
                    }

                    if (expanded) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "MANGA INKER",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                panelStatus(state),
                                color = if (state.isError) InkPalette.error else InkPalette.secondaryText,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Box {
                            IconButton(onClick = { moreExpanded = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "More actions")
                            }
                            DropdownMenu(
                                expanded = moreExpanded,
                                onDismissRequest = { moreExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Import page / open app") },
                                    onClick = { moreExpanded = false; onOpenMainScreen() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Select scan area") },
                                    enabled = state.sourceBitmap != null && !state.scanInProgress && !state.running && !state.calibrating,
                                    onClick = {
                                        moreExpanded = false
                                        controller.enableSelectionMode()
                                        onOpenMainScreen()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Scan selected area") },
                                    enabled = state.sourceBitmap != null && !state.scanInProgress && !state.running &&
                                        !state.calibrating && (state.selectedLeft > 0f || state.selectedTop > 0f ||
                                        state.selectedRight < 1f || state.selectedBottom < 1f),
                                    onClick = { moreExpanded = false; controller.scanPage() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Scan full page") },
                                    enabled = state.sourceBitmap != null && !state.scanInProgress && !state.running && !state.calibrating,
                                    onClick = { moreExpanded = false; controller.scanFullPage() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Retry failed trace paths") },
                                    enabled = state.failedPaths > 0 && !state.running && state.serviceConnected &&
                                        state.ibisPaintForeground && state.testGestureVerified && state.calibration != null,
                                    onClick = { moreExpanded = false; controller.retryTraceFromFailedPath() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Calibrate page corners") },
                                    leadingIcon = { Icon(Icons.Outlined.CropFree, contentDescription = null) },
                                    enabled = state.paths.isNotEmpty() && !state.running && !state.scanInProgress && !state.calibrating,
                                    onClick = { moreExpanded = false; controller.beginCalibration() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Stop current scan") },
                                    enabled = state.scanInProgress,
                                    onClick = { moreExpanded = false; controller.cancelScan() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear imported page") },
                                    enabled = state.sourceBitmap != null && !state.running && !state.scanInProgress,
                                    onClick = { moreExpanded = false; controller.clearPage() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                                    onClick = { moreExpanded = false; onOpenSettings() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Turn overlay off") },
                                    leadingIcon = { Icon(Icons.Outlined.Close, contentDescription = null) },
                                    onClick = { moreExpanded = false; onClose() }
                                )
                            }
                        }
                        IconButton(onClick = { expanded = false }) {
                            Icon(Icons.Outlined.ExpandLess, contentDescription = "Collapse controls")
                        }
                    }
                }

                if (expanded) {
                    androidx.compose.material3.HorizontalDivider(color = InkPalette.outline)
                    Column(
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusTag(text = overlayStage(state), state = state)
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (state.sourceBitmap != null) "${state.pageWidth} × ${state.pageHeight}" else "No page loaded",
                                color = InkPalette.secondaryText,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        Text(
                            state.message.ifBlank { "Open the app to import a page and begin." },
                            color = if (state.isError) InkPalette.error else InkPalette.secondaryText,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            maxLines = 3
                        )

                        if (state.showResultDialog) {
                            Surface(
                                color = InkPalette.raised,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, InkPalette.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(11.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "Check the test stroke",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Did the test line appear correctly on the ibisPaint canvas, and have you undone it?",
                                        color = InkPalette.secondaryText,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                    Button(
                                        onClick = { controller.recordTestResult("Yes") },
                                        modifier = Modifier.fillMaxWidth().height(42.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = InkPalette.accent)
                                    ) {
                                        Text("Yes, visible and undone")
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                        OutlinedButton(
                                            onClick = { controller.recordTestResult("No") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) { Text("No") }
                                        OutlinedButton(
                                            onClick = { controller.recordTestResult("Partly") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) { Text("Not sure") }
                                    }
                                }
                            }
                        }

                        if (state.resumeAvailable && !state.serviceConnected) {
                            Button(
                                onClick = onReconnectAccessibility,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = InkPalette.error,
                                    contentColor = Color.White
                                )
                            ) { Text("Reconnect Accessibility") }
                        }

                        if (state.scanInProgress) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                LinearProgressIndicator(
                                    progress = state.scanProgress.coerceIn(0f, 1f),
                                    modifier = Modifier.fillMaxWidth(),
                                    color = InkPalette.accent,
                                    trackColor = InkPalette.raised
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        state.scanStatus,
                                        modifier = Modifier.weight(1f),
                                        color = InkPalette.secondaryText,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 2
                                    )
                                    TextButton(onClick = { controller.cancelScan() }) {
                                        Text("Cancel", color = InkPalette.error)
                                    }
                                }
                            }
                        }

                        if (state.running) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                LinearProgressIndicator(
                                    progress = state.progress.coerceIn(0f, 1f),
                                    modifier = Modifier.fillMaxWidth(),
                                    color = InkPalette.accent,
                                    trackColor = InkPalette.raised
                                )
                                Text(
                                    "Path ${state.currentPath.coerceAtMost(state.totalPaths)} / ${state.totalPaths}   •   ${state.completedPaths} complete",
                                    color = InkPalette.secondaryText,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        if (state.sourceBitmap == null) {
                            Button(
                                onClick = onOpenMainScreen,
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                shape = RoundedCornerShape(13.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = InkPalette.accent)
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Import page / capture screen")
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { controller.scanFullPage() },
                                    enabled = !state.scanInProgress && !state.running && !state.calibrating && !state.captureInProgress,
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = InkPalette.accent)
                                ) {
                                    Text(if (state.paths.isEmpty()) "Scan full page" else "Rescan page", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { controller.beginCalibration() },
                                    enabled = state.paths.isNotEmpty() && !state.running && !state.scanInProgress && !state.calibrating,
                                    modifier = Modifier.height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Icon(Icons.Outlined.CropFree, contentDescription = "Calibrate corners")
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Button(
                                onClick = {
                                    controller.dispatch(
                                        if (state.testGestureVerified) SessionCommand.Start else SessionCommand.RunTest
                                    )
                                },
                                enabled = !state.running && !state.calibrating && !state.scanInProgress && !state.showResultDialog,
                                modifier = Modifier.weight(1.2f).height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = InkPalette.accent,
                                    disabledContainerColor = InkPalette.raised
                                )
                            ) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(if (state.testGestureVerified) "Start tracing" else "Test stroke", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    controller.dispatch(if (state.paused) SessionCommand.Resume else SessionCommand.Pause)
                                },
                                enabled = state.running,
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 7.dp)
                            ) {
                                Icon(
                                    if (state.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (state.paused) "Resume" else "Pause", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { controller.dispatch(SessionCommand.Stop) },
                                enabled = state.running,
                                modifier = Modifier.size(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Outlined.Stop, contentDescription = "Stop tracing", tint = InkPalette.accent)
                            }
                        }

                        if (!state.serviceConnected || !state.ibisPaintForeground || state.calibration == null || state.paths.isEmpty()) {
                            Text(
                                readinessHint(state),
                                color = InkPalette.warning,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (state.testGestureVerified) "Test verified • tracing unlocked" else "A test stroke is required before tracing",
                                color = InkPalette.secondaryText,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { expanded = false }) {
                                Icon(Icons.Outlined.Minimize, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Hide", color = InkPalette.secondaryText)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTag(text: String, state: SessionState) {
    Surface(
        color = stateDot(state).copy(alpha = 0.14f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text.uppercase(),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            color = stateDot(state),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

private fun readinessHint(state: SessionState): String = when {
    state.sourceBitmap == null -> "Next: import a page or capture the ibisPaint screen."
    state.paths.isEmpty() -> "Next: scan the page to detect the outline paths."
    state.calibration == null -> "Next: calibrate the four page corners."
    !state.serviceConnected -> "Enable Manga Inker in Android Accessibility settings to send drawing gestures."
    !state.ibisPaintForeground -> "Bring ibisPaint to the foreground before testing or tracing."
    !state.testGestureVerified -> "Run the test stroke, confirm it appeared correctly, then undo it in ibisPaint."
    else -> "Ready. Start tracing when the canvas is aligned."
}

private fun overlayStage(state: SessionState): String = when {
    state.resumeAvailable && !state.serviceConnected -> "Reconnect needed"
    state.captureInProgress -> "Capturing"
    state.isError -> "Needs attention"
    state.scanInProgress -> "Scanning"
    state.calibrating -> "Calibrating"
    state.running && state.paused -> "Paused"
    state.running -> "Tracing"
    state.testGestureVerified -> "Ready"
    state.paths.isNotEmpty() -> "Page scanned"
    state.sourceBitmap != null -> "Page loaded"
    else -> "Setup"
}

private fun stateDot(state: SessionState): Color = when {
    state.resumeAvailable && !state.serviceConnected -> InkPalette.error
    state.isError -> InkPalette.error
    state.captureInProgress || state.scanInProgress -> InkPalette.accent
    state.running && state.paused -> InkPalette.warning
    state.running -> InkPalette.accent
    state.testGestureVerified -> InkPalette.success
    else -> Color(0xFF85858D)
}

private fun panelStatus(state: SessionState): String = when {
    state.resumeAvailable && !state.serviceConnected -> "Reconnect Accessibility"
    state.captureInProgress -> "Capturing screen"
    state.isError -> "Needs attention"
    state.scanInProgress -> "Scanning page"
    state.calibrating -> "Calibrating canvas"
    state.running && state.paused -> "Paused"
    state.running -> "Tracing outline"
    state.testGestureVerified -> "Ready to trace"
    state.paths.isNotEmpty() -> "Page scanned"
    state.sourceBitmap != null -> "Page loaded"
    else -> "Ready when you are"
}
