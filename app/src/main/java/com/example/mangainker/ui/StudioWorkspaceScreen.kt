package com.example.mangainker.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import java.util.UUID

private enum class StudioTool(val label: String, val help: String) {
    SELECT("Select / Edit", "Drag an element to select and move it. Drag its lower-right handle to resize."),
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
    onExportImage: (Bitmap) -> Unit,
    onExportCbz: (StudioProject) -> Unit
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
    var undoHistory by remember { mutableStateOf<List<List<StudioElement>>>(emptyList()) }
    var redoHistory by remember { mutableStateOf<List<List<StudioElement>>>(emptyList()) }
    var showDeletePageConfirmation by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Create a project from an imported page, or open a saved project.") }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    fun refreshProjects() {
        projects = store.listProjects()
    }

    fun save(updated: StudioProject, recordHistory: Boolean = true) {
        try {
            val current = project
            if (recordHistory && current != null && current.id == updated.id && current.elements != updated.elements) {
                undoHistory = (undoHistory + listOf(current.elements)).takeLast(50)
                redoHistory = emptyList()
            }
            store.saveProject(updated)
            project = updated.copy(updatedAt = System.currentTimeMillis())
            refreshProjects()
            message = "Project saved on this device."
        } catch (error: Exception) {
            message = "Save failed: " + (error.message ?: "unknown error")
        }
    }

    fun switchPage(pageId: String) {
        val current = project ?: return
        if (pageId == current.activePageId) return
        try {
            val loaded = store.loadPage(current, pageId)
            if (loaded == null) {
                message = "This page could not be opened. Its source image may be missing."
                return
            }
            page = loaded.page
            project = loaded.project
            selectedElementId = null
            draggedElementId = null
            undoHistory = emptyList()
            redoHistory = emptyList()
            message = "Opened " + loaded.project.pages.indexOfFirst { it.id == pageId }.let { "Page " + (it + 1) } + "."
        } catch (error: Exception) {
            message = "Page switch failed: " + (error.message ?: "unknown error")
        }
    }

    fun addImportedPage() {
        val current = project ?: return
        val source = importedPage
        if (source == null || source.isRecycled) {
            message = "Import another page in Home first."
            return
        }
        try {
            val updated = store.addPage(current, source, "Page " + (current.pages.size + 1))
            val loaded = store.loadPage(updated, updated.activePageId.orEmpty())
            if (loaded == null) {
                message = "The page was saved but could not be reopened."
                return
            }
            page = loaded.page
            project = loaded.project
            selectedElementId = null
            undoHistory = emptyList()
            redoHistory = emptyList()
            refreshProjects()
            message = "New page added to this project."
        } catch (error: Exception) {
            message = "Could not add page: " + (error.message ?: "unknown error")
        }
    }

    fun duplicateCurrentPage() {
        val current = project ?: return
        try {
            val updated = store.duplicateActivePage(current)
            val loaded = store.loadPage(updated, updated.activePageId.orEmpty())
            if (loaded == null) {
                message = "The duplicated page could not be reopened."
                return
            }
            page = loaded.page
            project = loaded.project
            selectedElementId = null
            undoHistory = emptyList()
            redoHistory = emptyList()
            refreshProjects()
            message = "Current page duplicated."
        } catch (error: Exception) {
            message = "Page duplication failed: " + (error.message ?: "unknown error")
        }
    }

    fun deleteCurrentPage() {
        val current = project ?: return
        try {
            val updated = store.removePage(current, current.activePageId.orEmpty())
            val loaded = store.loadPage(updated, updated.activePageId.orEmpty())
            if (loaded == null) {
                message = "The remaining page could not be reopened."
                return
            }
            page = loaded.page
            project = loaded.project
            selectedElementId = null
            undoHistory = emptyList()
            redoHistory = emptyList()
            refreshProjects()
            message = "Page deleted. Remaining pages were preserved."
        } catch (error: Exception) {
            message = "Page deletion failed: " + (error.message ?: "unknown error")
        }
    }

    fun moveCurrentPage(offset: Int) {
        val current = project ?: return
        val activeId = current.activePageId ?: return
        try {
            val updated = store.movePage(current, activeId, offset)
            project = updated
            refreshProjects()
            message = "Page order updated."
        } catch (error: Exception) {
            message = "Page reorder failed: " + (error.message ?: "unknown error")
        }
    }

    fun updateSelectedElement(change: (StudioElement) -> StudioElement) {
        val current = project ?: return
        val id = selectedElementId ?: return
        val target = current.elements.firstOrNull { it.id == id } ?: return
        save(current.copy(elements = current.elements.map {
            if (it.id == target.id) change(it) else it
        }))
        message = "Selected element updated."
    }

    fun moveSelectedLayer(direction: Int) {
        val current = project ?: return
        val id = selectedElementId ?: return
        val elements = current.elements.toMutableList()
        val index = elements.indexOfFirst { it.id == id }
        if (index < 0) return
        val targetIndex = (index + direction).coerceIn(0, elements.lastIndex)
        if (targetIndex == index) return
        val selected = elements.removeAt(index)
        elements.add(targetIndex, selected)
        save(current.copy(elements = elements))
        message = if (direction > 0) "Element moved forward." else "Element moved backward."
    }

    fun undo() {
        val current = project ?: return
        val previous = undoHistory.lastOrNull() ?: return
        undoHistory = undoHistory.dropLast(1)
        redoHistory = (redoHistory + listOf(current.elements)).takeLast(50)
        save(current.copy(elements = previous), recordHistory = false)
        message = "Last edit undone."
    }

    fun redo() {
        val current = project ?: return
        val next = redoHistory.lastOrNull() ?: return
        redoHistory = redoHistory.dropLast(1)
        undoHistory = (undoHistory + listOf(current.elements)).takeLast(50)
        save(current.copy(elements = next), recordHistory = false)
        message = "Edit restored."
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
            undoHistory = emptyList()
            redoHistory = emptyList()
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
                    Text("Pages · " + currentProject.pages.size, color = InkPalette.secondaryText, style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = ::addImportedPage,
                            enabled = importedPage != null && !importedPage.isRecycled,
                            modifier = Modifier.weight(1f)
                        ) { Text("Add page") }
                        OutlinedButton(
                            onClick = ::duplicateCurrentPage,
                            modifier = Modifier.weight(1f)
                        ) { Text("Duplicate") }
                        OutlinedButton(
                            onClick = { showDeletePageConfirmation = true },
                            enabled = currentProject.pages.size > 1,
                            modifier = Modifier.weight(1f)
                        ) { Text("Delete page") }
                    }
                    currentProject.pages.forEachIndexed { index, studioPage ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { switchPage(studioPage.id) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    (if (studioPage.id == currentProject.activePageId) "● " else "") +
                                        "Page " + (index + 1) + " · " + studioPage.title,
                                    color = if (studioPage.id == currentProject.activePageId) InkPalette.accent else InkPalette.primaryText,
                                    maxLines = 1
                                )
                            }
                            TextButton(
                                onClick = { moveCurrentPage(-1) },
                                enabled = studioPage.id == currentProject.activePageId && index > 0
                            ) { Text("↑") }
                            TextButton(
                                onClick = { moveCurrentPage(1) },
                                enabled = studioPage.id == currentProject.activePageId && index < currentProject.pages.lastIndex
                            ) { Text("↓") }
                        }
                    }
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
                                                    message = "No element selected. Drag across an element to select and move it."
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
                    if (selectedElement != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { updateSelectedElement { it.copy(rotationDegrees = (it.rotationDegrees - 15f) % 360f) } },
                                modifier = Modifier.weight(1f)
                            ) { Text("Rotate −15°") }
                            OutlinedButton(
                                onClick = { updateSelectedElement { it.copy(rotationDegrees = (it.rotationDegrees + 15f) % 360f) } },
                                modifier = Modifier.weight(1f)
                            ) { Text("Rotate +15°") }
                            OutlinedButton(
                                onClick = { moveSelectedLayer(-1) },
                                enabled = currentProject.elements.indexOfFirst { it.id == selectedElement.id } > 0,
                                modifier = Modifier.weight(1f)
                            ) { Text("Back") }
                            OutlinedButton(
                                onClick = { moveSelectedLayer(1) },
                                enabled = currentProject.elements.indexOfFirst { it.id == selectedElement.id } in 0 until currentProject.elements.lastIndex,
                                modifier = Modifier.weight(1f)
                            ) { Text("Front") }
                        }
                        if (selectedElement.type == "bubble" || selectedElement.type == "sfx") {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { updateSelectedElement { it.copy(textScale = (it.textScale - 0.1f).coerceAtLeast(0.5f)) } },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Text −") }
                                OutlinedButton(
                                    onClick = { updateSelectedElement { it.copy(textScale = (it.textScale + 0.1f).coerceAtMost(2f)) } },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Text +") }
                            }
                        }
                        if (selectedElement.type == "bubble") {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { updateSelectedElement { it.copy(tailOffsetX = (it.tailOffsetX - 0.08f).coerceAtLeast(-0.45f)) } },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Tail left") }
                                OutlinedButton(
                                    onClick = { updateSelectedElement { it.copy(tailOffsetX = (it.tailOffsetX + 0.08f).coerceAtMost(0.45f)) } },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Tail right") }
                            }
                        }
                        if (selectedElement.type == "tone" || selectedElement.type == "speed") {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { updateSelectedElement { it.copy(effectDensity = (it.effectDensity - 0.1f).coerceAtLeast(0.35f)) } },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Density −") }
                                OutlinedButton(
                                    onClick = { updateSelectedElement { it.copy(effectDensity = (it.effectDensity + 0.1f).coerceAtMost(2.5f)) } },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Density +") }
                            }
                        }
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
                                    val duplicate = target.copy(
                                        id = UUID.randomUUID().toString(),
                                        left = left,
                                        top = top,
                                        right = left + elementWidth,
                                        bottom = top + elementHeight
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
                            onClick = ::undo,
                            enabled = undoHistory.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text("Undo") }
                        OutlinedButton(
                            onClick = ::redo,
                            enabled = redoHistory.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text("Redo") }
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
                    OutlinedButton(
                        onClick = {
                            try {
                                onExportCbz(currentProject)
                            } catch (error: Exception) {
                                message = "Comic export failed: " + (error.message ?: "unknown error")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Export whole project as CBZ") }
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

    if (showDeletePageConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeletePageConfirmation = false },
            title = { Text("Delete this page?") },
            text = { Text("This removes only the active page. Other project pages will remain saved.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteCurrentPage()
                    showDeletePageConfirmation = false
                }) { Text("Delete page") }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePageConfirmation = false }) { Text("Cancel") }
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
