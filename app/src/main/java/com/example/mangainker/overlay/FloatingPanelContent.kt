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
    var expanded by remember { mutableStateOf(true) }
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
        modifier = Modifier.widthIn(min = 104.dp, max = 248.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.message, color = InkPalette.secondaryText, fontSize = 12.sp, maxLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { controller.dispatch(SessionCommand.Start) },
                            enabled = !state.running,
                            colors = ButtonDefaults.buttonColors(containerColor = InkPalette.accent),
                            modifier = Modifier.weight(1f)
                        ) { Text("Start") }
                        OutlinedButton(
                            onClick = { controller.dispatch(if (state.paused) SessionCommand.Resume else SessionCommand.Pause) },
                            enabled = state.running,
                            modifier = Modifier.weight(1f)
                        ) { Text(if (state.paused) "Resume" else "Pause") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                if (controller.shouldConfirmStop()) showStopConfirmation = true
                                else controller.dispatch(SessionCommand.Stop)
                            },
                            enabled = state.running || state.testArmed,
                            modifier = Modifier.weight(1f)
                        ) { Text("Stop") }
                        TextButton(onClick = { expanded = false }) { Text("Minimize", color = InkPalette.secondaryText) }
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
    state.testArmed || state.serviceConnected -> InkPalette.warning
    else -> Color(0xFF77777F)
}

private fun panelStatus(state: SessionState): String = when {
    state.isError -> "Error"
    state.running && state.paused -> "Paused"
    state.running -> "Running"
    state.testArmed -> "Ready"
    else -> "Idle"
}
