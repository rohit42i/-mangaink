package com.example.mangainker.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.max

private enum class StudioTool(val label: String, val help: String) {
    SELECT("Select / Edit", "Tap an element to select it. Drag to move it or its lower-right handle to resize."),
    PANEL("Panel", "Drag to add a panel frame."),
    TONE("Tone", "Drag an area to add a dotted screentone."),
    GRADIENT("Gradient", "Drag an area for a grayscale gradient."),
    SPEED("Speed lines", "Drag an area for motion lines."),
    BUBBLE("Bubble", "Drag a box for dialogue or narration."),
    SFX("SFX", "Drag an area for a sound effect.")
}

@Composable
fun StudioWorkspaceScreen(
    modifier: Modifier,
    importedPage: Bitmap?,
    store: StudioProjectStore,
    onExportImage: (Bitmap) -> Unit
) {
    var projects by remember { mutableStateOf(store.listProjects()) }
    var project by remember { mutableStateOf<StudioProject?>(null) }
    var page by remember { mutableStateOf<Bitmap?>(null) }
    var projectTitle by remember { mutableStateOf("My manga page") }
    var activeTool by remember { mutableStateOf(StudioTool.PANEL) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    var selectedElementId by remember { mutableStateOf<String?>(null) }
    var draggedElementId by remember { mutableStateOf<String?>(null) }
    var resizeDraggedElement by remember { mutableStateOf(false) }
    var editingElementId by remember { mutableStateOf<String?>(null) }
    var editingText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Create a project from an imported page, or open a saved project.") }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    fun refreshProjects() {
        projects = store.listProjects()
    }

    fun save(updated: StudioProject) {
        try {
            store.saveProject(updated)
            project = updated.copy(updatedAt = System.currentTimeMillis())
            refreshProjects()
            message = "Project saved on this device."
        } catch (error: Exception) {
            message = "Save failed: " + (error.message ?: "unknown error")
        }
    }

    fun openProject(selected: StudioProject) {
        try {
            val loaded = store.loadProject(selected.id)
            if (loaded == null) {
                message = "This project page could not be opened. Its source image may be missing."
                return
            }
            page = loaded.page
            project = loaded.project
            selectedElementId = null
            projectTitle = loaded.project.title
            activeTool = StudioTool.PANEL
            message = "Opened " + loaded.project.title + ". Changes are saved locally."
        } catch (error: Exception) {
            message = "Project open failed: " + (error.message ?: "unknown error")
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Comic studio", style = MaterialTheme.typography.titleLarge)
                Text("Projects, panels, lettering and page effects", color = InkPalette.secondaryText, style = MaterialTheme.typography.bodySmall)
            }
            if (project != null) {
                Text("SAVED LOCALLY", color = InkPalette.success, style = MaterialTheme.typography.labelSmall)
            }
        }

        if (project == null || page == null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = InkPalette.surface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("New page project", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (importedPage == null) "Import a page from the Home tab before creating a project."
                        else "Start a private project from the currently imported page.",
                        color = InkPalette.secondaryText,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = projectTitle,
                        onValueChange = { projectTitle = it.take(60) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Project name") },
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            val source = importedPage
                            if (source == null || source.isRecycled) {
                                message = "Import a manga page in Home first."
                            } else {
                                try {
                                    val created = store.createProject(source, projectTitle)
                                    val loaded = store.loadProject(created.id)
                                    if (loaded == null) {
                                        message = "The project was saved, but its page could not be reopened."
                                    } else {
                                        project = loaded.project
                                        page = loaded.page
                                        refreshProjects()
                                        message = "Project created. Choose a tool and drag on the page."
                                    }
                                } catch (error: Exception) {
                                    message = "Project creation failed: " + (error.message ?: "unknown error")
                                }
                            }
                        },
                        enabled = importedPage != null && !importedPage.isRecycled,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Create project from imported page") }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = InkPalette.surface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Saved projects", style = MaterialTheme.typography.titleMedium)
                    if (projects.isEmpty()) {
                        Text("No projects yet. Your saved projects will appear here.", color = InkPalette.secondaryText, style = MaterialTheme.typography.bodySmall)
                    } else {
                        projects.forEach { saved ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(InkPalette.raised).padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(saved.title, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        saved.pageWidth.toString() + " × " + saved.pageHeight + " · " + saved.elements.size + " elements",
                                        color = InkPalette.secondaryText,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                TextButton(onClick = { openProject(saved) }) { Text("Open") }
                            }
                        }
                    }
                    TextButton(onClick = ::refreshProjects, modifier = Modifier.align(Alignment.End)) { Text("Refresh list") }
                }
            }
        } else {
            val currentProject = project!!
            val currentPage = page!!
            val selectedElement = currentProject.elements.firstOrNull { it.id == selectedElementId }

            // The workspace preview uses the exact compositor used by PNG export.
            val previewBitmap = remember(currentPage, currentProject) {
                store.renderPage(currentPage, currentProject)
            }
            DisposableEffect(currentPage) {
                onDispose {
                    if (!currentPage.isRecycled) currentPage.recycle()
                }
            }
            DisposableEffect(previewBitmap) {
                onDispose {
                    if (!previewBitmap.isRecycled) previewBitmap.recycle()
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    project = null
                    page = null
                    editingElementId = null
                    refreshProjects()
                }) { Text("← Projects") }
                Spacer(Modifier.weight(1f))
                Text(
                    currentProject.pageWidth.toString() + " × " + currentProject.pageHeight,
                    color = InkPalette.secondaryText,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Card(colors = CardDefaults.cardColors(containerColor = InkPalette.surface), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(currentProject.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Choose a tool, then drag on the page. All added elements are stored with this project.",
                        color = InkPalette.secondaryText,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        StudioTool.entries.take(2).forEach { tool ->
                            if (tool == activeTool) Button(onClick = { activeTool = tool }, modifier = Modifier.weight(1f)) { Text(tool.label) }
                            else OutlinedButton(onClick = { activeTool = tool }, modifier = Modifier.weight(1f)) { Text(tool.label) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        StudioTool.entries.drop(2).take(2).forEach { tool ->
                            if (tool == activeTool) Button(onClick = { activeTool = tool }, modifier = Modifier.weight(1f)) { Text(tool.label) }
                            else OutlinedButton(onClick = { activeTool = tool }, modifier = Modifier.weight(1f)) { Text(tool.label) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        StudioTool.entries.drop(4).forEach { tool ->
                            if (tool == activeTool) Button(onClick = { activeTool = tool }, modifier = Modifier.weight(1f)) { Text(tool.label) }
                            else OutlinedButton(onClick = { activeTool = tool }, modifier = Modifier.weight(1f)) { Text(tool.label) }
                        }
                    }
                    Text(activeTool.help, color = InkPalette.secondaryText, style = MaterialTheme.typography.labelSmall)

                    Box(
                        Modifier.fillMaxWidth().height(440.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF101014))
                    ) {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = "Comic project page",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        Canvas(
                            Modifier.fillMaxSize()
                                .pointerInput(activeTool, currentPage.width, currentPage.height, currentProject.elements) {
                                    if (activeTool == StudioTool.SELECT) {
                                        detectTapGestures { point ->
                                            val scale = minOf(size.width / currentPage.width, size.height / currentPage.height)
                                            val drawnWidth = currentPage.width * scale
                                            val drawnHeight = currentPage.height * scale
                                            val insetX = (size.width - drawnWidth) / 2f
                                            val insetY = (size.height - drawnHeight) / 2f
                                            val x = ((point.x - insetX) / drawnWidth).coerceIn(0f, 1f)
                                            val y = ((point.y - insetY) / drawnHeight).coerceIn(0f, 1f)
                                            val hit = currentProject.elements.lastOrNull {
                                                x >= it.left && x <= it.right && y >= it.top && y <= it.bottom
                                            }
                                            selectedElementId = hit?.id
                                            message = if (hit == null) "No element at that position." else
                                                "Selected ${hit.type}. Drag to move it or its lower-right handle to resize."
                                        }
                                    }
                                }
                                .pointerInput(activeTool, currentPage.width, currentPage.height, currentProject.elements, selectedElementId) {
                                val scale = minOf(size.width / currentPage.width, size.height / currentPage.height)
                                val drawnWidth = currentPage.width * scale
                                val drawnHeight = currentPage.height * scale
                                val insetX = (size.width - drawnWidth) / 2f
                                val insetY = (size.height - drawnHeight) / 2f
                                fun normalized(point: Offset): Offset = Offset(
                                    ((point.x - insetX) / drawnWidth).coerceIn(0f, 1f),
                                    ((point.y - insetY) / drawnHeight).coerceIn(0f, 1f)
                                )
                                detectDragGestures(
                                    onDragStart = { point ->
                                        val normalizedPoint = normalized(point)
                                        dragStart = normalizedPoint
                                        dragCurrent = normalizedPoint
                                        if (activeTool == StudioTool.SELECT) {
                                            val hit = currentProject.elements.lastOrNull { element ->
                                                normalizedPoint.x >= element.left - 0.035f &&
                                                    normalizedPoint.x <= element.right + 0.035f &&
                                                    normalizedPoint.y >= element.top - 0.035f &&
                                                    normalizedPoint.y <= element.bottom + 0.035f
                                            }
                                            selectedElementId = hit?.id
                                            draggedElementId = hit?.id
                                            resizeDraggedElement = hit != null &&
                                                normalizedPoint.x >= hit.right - 0.04f &&
                                                normalizedPoint.y >= hit.bottom - 0.04f
                                        } else {
                                            selectedElementId = null
                                            draggedElementId = null
                                            resizeDraggedElement = false
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        dragCurrent = normalized(change.position)
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        val start = dragStart
                                        val end = dragCurrent
                                        if (start != null && end != null) {
                                            if (activeTool == StudioTool.SELECT) {
                                                val target = currentProject.elements.firstOrNull { it.id == draggedElementId }
                                                if (target != null) {
                                                    val updatedElement = if (resizeDraggedElement) {
                                                        target.copy(
                                                            right = end.x.coerceIn(target.left + 0.02f, 1f),
                                                            bottom = end.y.coerceIn(target.top + 0.02f, 1f)
                                                        )
                                                    } else {
                                                        val elementWidth = target.right - target.left
                                                        val elementHeight = target.bottom - target.top
                                                        val left = (target.left + end.x - start.x).coerceIn(0f, 1f - elementWidth)
                                                        val top = (target.top + end.y - start.y).coerceIn(0f, 1f - elementHeight)
                                                        target.copy(
                                                            left = left, top = top,
                                                            right = left + elementWidth, bottom = top + elementHeight
                                                        )
                                                    }
                                                    save(currentProject.copy(elements = currentProject.elements.map {
                                                        if (it.id == target.id) updatedElement else it
                                                    }))
                                                    selectedElementId = target.id
                                                    message = if (resizeDraggedElement) "Element resized." else "Element moved."
                                                } else {
                                                    message = "No element selected. Tap an element first."
                                                }
                                            } else {
                                                val left = min(start.x, end.x)
                                                val top = min(start.y, end.y)
                                                val right = max(start.x, end.x)
                                                val bottom = max(start.y, end.y)
                                                if (right - left >= 0.015f && bottom - top >= 0.015f) {
                                                    val newElement = StudioElement(
                                                        type = activeTool.name.lowercase(),
                                                        left = left,
                                                        top = top,
                                                        right = right,
                                                        bottom = bottom,
                                                        text = when (activeTool) {
                                                            StudioTool.BUBBLE -> "Dialogue"
                                                            StudioTool.SFX -> "SFX!"
                                                            else -> ""
                                                        }
                                                    )
                                                    save(currentProject.copy(elements = currentProject.elements + newElement))
                                                    selectedElementId = newElement.id
                                                    if (activeTool == StudioTool.BUBBLE || activeTool == StudioTool.SFX) {
                                                        editingElementId = newElement.id
                                                        editingText = newElement.text
                                                    }
                                                } else {
                                                    message = "Drag a larger area to create an element."
                                                }
                                            }
                                        }
                                        draggedElementId = null
                                        resizeDraggedElement = false
                                        dragStart = null
                                        dragCurrent = null
                                    },
                                    onDragCancel = {
                                        draggedElementId = null
                                        resizeDraggedElement = false
                                        dragStart = null
                                        dragCurrent = null
                                    }
                                )
                            }
                        ) {
                            if (selectedElement != null) {
                                val scale = minOf(size.width / currentPage.width, size.height / currentPage.height)
                                val offsetX = (size.width - currentPage.width * scale) / 2f
                                val offsetY = (size.height - currentPage.height * scale) / 2f
                                val left = offsetX + selectedElement.left * currentPage.width * scale
                                val top = offsetY + selectedElement.top * currentPage.height * scale
                                val right = offsetX + selectedElement.right * currentPage.width * scale
                                val bottom = offsetY + selectedElement.bottom * currentPage.height * scale
                                drawRect(
                                    color = InkPalette.accent,
                                    topLeft = Offset(left, top),
                                    size = Size(right - left, bottom - top),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                drawCircle(InkPalette.accent, radius = 6.dp.toPx(), center = Offset(right, bottom))
                            }
                            if (dragStart != null && dragCurrent != null && activeTool != StudioTool.SELECT) {
                                val start = dragStart!!
                                val end = dragCurrent!!
                                val scale = minOf(size.width / currentPage.width, size.height / currentPage.height)
                                val offsetX = (size.width - currentPage.width * scale) / 2f
                                val offsetY = (size.height - currentPage.height * scale) / 2f
                                drawRect(
                                    color = InkPalette.accent,
                                    topLeft = Offset(
                                        offsetX + min(start.x, end.x) * currentPage.width * scale,
                                        offsetY + min(start.y, end.y) * currentPage.height * scale
                                    ),
                                    size = Size(
                                        kotlin.math.abs(end.x - start.x) * currentPage.width * scale,
                                        kotlin.math.abs(end.y - start.y) * currentPage.height * scale
                                    ),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedButton(
                            onClick = {
                                val target = selectedElement
                                if (target != null) {
                                    editingElementId = target.id
                                    editingText = target.text
                                }
                            },
                            enabled = selectedElement?.type == "bubble" || selectedElement?.type == "sfx",
                            modifier = Modifier.weight(1f)
                        ) { Text("Edit text") }
                        OutlinedButton(
                            onClick = {
                                val target = selectedElement
                                if (target != null) {
                                    save(currentProject.copy(elements = currentProject.elements.filterNot { it.id == target.id }))
                                    selectedElementId = null
                                    message = "Selected element deleted."
                                }
                            },
                            enabled = selectedElement != null,
                            modifier = Modifier.weight(1f)
                        ) { Text("Delete selected") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedButton(
                            onClick = {
                                val target = selectedElement
                                if (target != null) {
                                    val elementWidth = target.right - target.left
                                    val elementHeight = target.bottom - target.top
                                    val left = (target.left + 0.02f).coerceAtMost(1f - elementWidth)
                                    val top = (target.top + 0.02f).coerceAtMost(1f - elementHeight)
                                    val duplicate = StudioElement(
                                        type = target.type, left = left, top = top,
                                        right = left + elementWidth, bottom = top + elementHeight,
                                        text = target.text
                                    )
                                    save(currentProject.copy(elements = currentProject.elements + duplicate))
                                    selectedElementId = duplicate.id
                                    message = "Element duplicated."
                                }
                            },
                            enabled = selectedElement != null,
                            modifier = Modifier.weight(1f)
                        ) { Text("Duplicate selected") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (currentProject.elements.isNotEmpty()) {
                                    save(currentProject.copy(elements = currentProject.elements.dropLast(1)))
                                }
                            },
                            enabled = currentProject.elements.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text("Undo element") }
                        OutlinedButton(
                            onClick = { showDeleteConfirmation = true },
                            modifier = Modifier.weight(1f)
                        ) { Text("Delete project") }
                    }
                    Button(
                        onClick = {
                            try {
                                val rendered = store.renderPage(currentPage, currentProject)
                                onExportImage(rendered)
                            } catch (error: Exception) {
                                message = "Export failed: " + (error.message ?: "unknown error")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Export finished page as PNG") }
                }
            }
        }

        Text(message, color = InkPalette.secondaryText, style = MaterialTheme.typography.bodySmall)
    }

    if (editingElementId != null) {
        AlertDialog(
            onDismissRequest = { editingElementId = null },
            title = { Text(if (project?.elements?.firstOrNull { it.id == editingElementId }?.type == "bubble") "Edit dialogue" else "Edit sound effect") },
            text = {
                OutlinedTextField(
                    value = editingText,
                    onValueChange = { editingText = it.take(80) },
                    label = { Text("Text") },
                    singleLine = false
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val current = project
                    val id = editingElementId
                    if (current != null && id != null) {
                        save(current.copy(elements = current.elements.map {
                            if (it.id == id) it.copy(text = editingText.trim().ifBlank { if (it.type == "bubble") "Dialogue" else "SFX!" }) else it
                        }))
                    }
                    editingElementId = null
                }) { Text("Save text") }
            },
            dismissButton = {
                TextButton(onClick = { editingElementId = null }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete this project?") },
            text = { Text("The saved page and its studio elements will be permanently deleted from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    val current = project
                    if (current != null) {
                        try {
                            store.deleteProject(current.id)
                            project = null
                            page = null
                            refreshProjects()
                            message = "Project deleted."
                        } catch (error: Exception) {
                            message = "Delete failed: " + (error.message ?: "unknown error")
                        }
                    }
                    showDeleteConfirmation = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}
