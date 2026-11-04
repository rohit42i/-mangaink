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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mangainker.core.SessionCommand
import com.example.mangainker.core.SessionController
import com.example.mangainker.core.SessionState
import com.example.mangainker.ui.InkPalette
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun FloatingPanelContent(
    controller: SessionController,
    onDrag: (Int, Int) -> Unit,
    onSnap: () -> Unit
) {
    val state by controller.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var showStopConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(expanded, state.running, state.paused, state.testArmed, state.panelAutoCollapseMs) {
        if (expanded && !state.running && !state.paused && !state.testArmed && state.panelAutoCollapseMs > 0L) {
            delay(state.panelAutoCollapseMs)
            expanded = false
        }
    }

    Surface(
        color = InkPalette.surface,
        contentColor = InkPalette.primaryText,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, InkPalette.outline),
        modifier = Modifier.widthIn(min = if (expanded) 248.dp else 82.dp, max = 272.dp)
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(34.dp).pointerInput(Unit) {
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
                    Box(Modifier.size(11.dp).clip(CircleShape).background(stateDot(state)))
                }

                if (expanded) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        Text("Manga Inker", style = MaterialTheme.typography.labelLarge)
                        Text(panelStatus(state), color = InkPalette.secondaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }

            if (expanded) {
                androidx.compose.material3.HorizontalDivider(color = InkPalette.outline)
                Column(Modifier.padding(top = 10.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SESSION CONTROL", color = InkPalette.secondaryText, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), letterSpacing = 0.8.sp)
                        Text(if (state.running) "ACTIVE" else if (state.testArmed) "ARMED" else "STANDBY", color = if (state.running) InkPalette.success else InkPalette.secondaryText, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(state.message, color = InkPalette.secondaryText, fontSize = 11.sp, maxLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Button(
                            onClick = { controller.dispatch(SessionCommand.Start) },
                            enabled = state.testArmed && !state.running,
                            colors = ButtonDefaults.buttonColors(containerColor = InkPalette.accent, disabledContainerColor = InkPalette.raised),
                            modifier = Modifier.weight(1.15f).height(42.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Start", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { controller.dispatch(if (state.paused) SessionCommand.Resume else SessionCommand.Pause) },
                            enabled = state.running,
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(if (state.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(3.dp))
                            Text(if (state.paused) "Resume" else "Pause", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                if (controller.shouldConfirmStop()) showStopConfirmation = true
                                else controller.dispatch(SessionCommand.Stop)
                            },
                            enabled = state.running || state.testArmed,
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Outlined.Stop, contentDescription = "Stop", tint = InkPalette.accent)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Floating tool overlay", color = InkPalette.secondaryText, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { expanded = false }) {
                            Icon(Icons.Outlined.Minimize, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Minimize", color = InkPalette.secondaryText)
                        }
                    }
                }
            }
        }
    }

    if (showStopConfirmation) {
        AlertDialog(
            onDismissRequest = { showStopConfirmation = false },
            title = { Text("Stop this session?") },
            text = { Text("This run has been active for more than 30 seconds.") },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirmation = false
                    controller.dispatch(SessionCommand.Stop)
                }) { Text("Stop") }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirmation = false }) { Text("Continue") }
            },
            containerColor = InkPalette.surface
        )
    }
}

private fun stateDot(state: SessionState): Color = when {
    state.isError -> InkPalette.accent
    state.running -> InkPalette.success
    state.testArmed -> InkPalette.warning
    else -> Color(0xFF77777F)
}

private fun panelStatus(state: SessionState): String = when {
    state.isError -> "Error"
    state.running && state.paused -> "Paused"
    state.running -> "Running"
    state.testArmed -> "Ready"
    else -> "Idle"
}
