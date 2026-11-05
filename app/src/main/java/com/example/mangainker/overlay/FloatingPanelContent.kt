package com.example.mangainker.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Minimize
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.example.mangainker.core.SessionCommand
import com.example.mangainker.core.SessionController
import com.example.mangainker.core.SessionState
import com.example.mangainker.ui.InkPalette
import kotlin.math.roundToInt

@Composable
fun FloatingPanelContent(
    controller: SessionController,
    onDrag: (Int, Int) -> Unit,
    onSnap: () -> Unit,
    onClose: () -> Unit,
    onOpenMainScreen: () -> Unit
) {
    val state by controller.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(expanded, state.running, state.paused, state.calibrating, state.scanInProgress, state.panelAutoCollapseMs) {
        if (expanded && !state.running && !state.calibrating && !state.scanInProgress && state.panelAutoCollapseMs > 0L) {
            delay(state.panelAutoCollapseMs)
            expanded = false
        }
    }

    Surface(
        color = InkPalette.surface,
        contentColor = InkPalette.primaryText,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, InkPalette.outline),
        modifier = Modifier.widthIn(min = if (expanded) 276.dp else 68.dp, max = 292.dp)
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(42.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(32.dp).pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = onSnap,
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x.roundToInt(), dragAmount.y.roundToInt())
                            }
                        )
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(stateDot(state)))
                }

                if (expanded) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("MANGA INKER", style = MaterialTheme.typography.labelLarge, letterSpacing = 0.7.sp)
                        Text(panelStatus(state), color = InkPalette.secondaryText, style = MaterialTheme.typography.labelSmall)
                    }
                    Box {
                        IconButton(onClick = { moreExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More controls")
                        }
                        DropdownMenu(
                            expanded = moreExpanded,
                            onDismissRequest = { moreExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Calibrate page corners") },
                                leadingIcon = { Icon(Icons.Outlined.CropFree, contentDescription = null) },
                                onClick = {
                                    moreExpanded = false
                                    controller.beginCalibration()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Stop scanning") },
                                leadingIcon = { Icon(Icons.Outlined.Stop, contentDescription = null) },
                                enabled = state.scanInProgress,
                                onClick = {
                                    moreExpanded = false
                                    controller.cancelScan()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Select scan area") },
                                enabled = state.sourceBitmap != null && !state.scanInProgress && !state.running,
                                onClick = {
                                    moreExpanded = false
                                    expanded = false
                                    controller.enableSelectionMode()
                                    onOpenMainScreen()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Scan selected area") },
                                enabled = state.sourceBitmap != null && !state.scanInProgress && !state.running &&
                                    (state.selectedLeft > 0f || state.selectedTop > 0f ||
                                        state.selectedRight < 1f || state.selectedBottom < 1f),
                                onClick = {
                                    moreExpanded = false
                                    controller.scanPage()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Scan full page") },
                                enabled = state.sourceBitmap != null && !state.scanInProgress && !state.running,
                                onClick = {
                                    moreExpanded = false
                                    controller.scanFullPage()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear page session") },
                                enabled = !state.running && !state.scanInProgress,
                                onClick = {
                                    moreExpanded = false
                                    controller.clearPage()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Turn overlay off") },
                                leadingIcon = { Icon(Icons.Outlined.Close, contentDescription = null) },
                                onClick = {
                                    moreExpanded = false
                                    onClose()
                                }
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "Collapse controls" else "Expand controls"
                    )
                }
            }

            if (expanded) {
                androidx.compose.material3.HorizontalDivider(color = InkPalette.outline)
                Column(
                    Modifier.padding(top = 10.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text(
                        if (state.calibrating) "CALIBRATION ACTIVE"
                        else if (state.running && state.paused) "PAUSED"
                        else if (state.running) "TRACING"
                        else if (state.testGestureVerified) "READY TO TRACE"
                        else "TEST REQUIRED",
                        color = if (state.running) InkPalette.success else InkPalette.secondaryText,
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        state.message,
                        color = if (state.isError) InkPalette.warning else InkPalette.secondaryText,
                        fontSize = 11.sp,
                        maxLines = 3
                    )

                    if (state.resumeAvailable && !state.serviceConnected) {
                        Button(
                            onClick = onOpenMainScreen,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = InkPalette.error,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Reconnect Accessibility")
                        }
                    }

                    if (state.scanInProgress) {
                        LinearProgressIndicator(
                            progress = state.scanProgress.coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth(),
                            color = InkPalette.accent,
                            trackColor = InkPalette.raised
                        )
                        Text(
                            state.scanStatus,
                            color = InkPalette.secondaryText,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2
                        )
                    }

                    if (state.running && !state.totalPaths.equals(0)) {
                        LinearProgressIndicator(
                            progress = state.progress.coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth(),
                            color = InkPalette.accent,
                            trackColor = InkPalette.raised
                        )
                        Text(
                            "Path " + state.currentPath + " / " + state.totalPaths +
                                "  ·  " + state.completedPaths + " complete",
                            color = InkPalette.secondaryText,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Button(
                            onClick = {
                                controller.dispatch(
                                    if (state.testGestureVerified) SessionCommand.Start else SessionCommand.RunTest
                                )
                            },
                            enabled = !state.running && !state.calibrating &&
                                state.serviceConnected && state.ibisPaintForeground &&
                                if (state.testGestureVerified) state.paths.isNotEmpty() && state.calibration != null
                                else state.calibration != null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = InkPalette.accent,
                                disabledContainerColor = InkPalette.raised
                            ),
                            modifier = Modifier.weight(1.2f).height(42.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(if (state.testGestureVerified) "Start" else "Test", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                controller.dispatch(
                                    if (state.paused) SessionCommand.Resume else SessionCommand.Pause
                                )
                            },
                            enabled = state.running,
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                if (state.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.size(3.dp))
                            Text(if (state.paused) "Resume" else "Pause", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { controller.dispatch(SessionCommand.Stop) },
                            enabled = state.running,
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Outlined.Stop, contentDescription = "Stop", tint = InkPalette.accent)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (state.testGestureVerified) "Full-page outline tracing" else "Test before tracing",
                            color = InkPalette.secondaryText,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { expanded = false }) {
                            Icon(Icons.Outlined.Minimize, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.size(3.dp))
                            Text("Minimize", color = InkPalette.secondaryText)
                        }
                    }
                }
            }
        }
    }

}

private fun stateDot(state: SessionState): Color = when {
    state.resumeAvailable && !state.serviceConnected -> InkPalette.error
    state.isError -> InkPalette.error
    state.captureInProgress || state.scanInProgress -> InkPalette.accent
    state.running && state.paused -> InkPalette.warning
    state.running -> InkPalette.accent
    state.testGestureVerified -> InkPalette.success
    else -> Color(0xFF77777F)
}

private fun panelStatus(state: SessionState): String = when {
    state.resumeAvailable && !state.serviceConnected -> "Reconnect Accessibility"
    state.captureInProgress -> "Recording screen"
    state.isError -> "Error"
    state.scanInProgress -> "Scanning"
    state.calibrating -> "Calibrating"
    state.running && state.paused -> "Paused"
    state.running -> "Tracing"
    state.testGestureVerified -> "Ready to trace"
    else -> "Idle"
}
