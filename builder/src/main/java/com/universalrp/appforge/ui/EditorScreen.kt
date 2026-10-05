package com.universalrp.appforge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.universalrp.appforge.BScreen
import com.universalrp.appforge.BuilderState
import com.universalrp.appforge.BuilderViewModel
import com.universalrp.appforge.model.ACCENT_COLORS
import com.universalrp.appforge.model.AppProject
import com.universalrp.appforge.model.Block
import com.universalrp.appforge.model.BlockType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(state: BuilderState, vm: BuilderViewModel) {
    val project = state.project
    if (project == null) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            Text("No project open.", color = TextSecondary)
            TextButton(onClick = { vm.navigate(BScreen.HOME) }) {
                Text("Back to my apps", color = Accent)
            }
        }
        return
    }

    var showAddBlock by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var editHref by remember { mutableStateOf("") }

    val screen = project.screens.getOrNull(state.activeScreen) ?: project.screens.firstOrNull()
    val blocks = screen?.blocks ?: emptyList()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.closeEditor() }) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "My apps", tint = TextSecondary)
            }
            Text(
                project.name.ifBlank { "Untitled app" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.navigate(BScreen.PREVIEW) }) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = "Preview", tint = Accent)
            }
            IconButton(onClick = { vm.navigate(BScreen.EXPORT) }) {
                Icon(Icons.Outlined.Share, contentDescription = "Export", tint = Accent)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { AppDetailsCard(project, vm) }

            item {
                SectionTitle("Screens")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    project.screens.forEachIndexed { index, s ->
                        Chip(
                            label = s.title.ifBlank { "Screen ${index + 1}" },
                            selected = index == state.activeScreen,
                            onClick = { vm.selectScreen(index) },
                        )
                    }
                    Chip(
                        label = "+ Add",
                        selected = false,
                        onClick = { vm.addScreen() },
                    )
                }
                if (project.screens.size > 1) {
                    TextButton(onClick = { vm.deleteActiveScreen() }) {
                        Text("Delete this screen", color = Danger)
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = screen?.title ?: "",
                    onValueChange = { vm.renameActiveScreen(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Screen name", color = TextSecondary) },
                    singleLine = true,
                    colors = fieldColors(),
                )
            }

            item { SectionTitle("Blocks on this screen") }

            items(blocks, key = { it.id }) { block ->
                BlockRow(
                    block = block,
                    onEdit = {
                        editingId = block.id
                        editText = block.text
                        editHref = block.href
                    },
                    onUp = { vm.moveBlock(block.id, -1) },
                    onDown = { vm.moveBlock(block.id, 1) },
                    onDelete = { vm.deleteBlock(block.id) },
                )
            }

            item {
                GradientButton(
                    text = "Add block",
                    icon = Icons.Outlined.Add,
                    onClick = { showAddBlock = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Text(
                    "Tip: a Button or Link can point at a website (https://…) or at another screen " +
                        "with #screen:<screen id>. Text fields save what the user types, and checklists " +
                        "remember their ticks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }

    if (showAddBlock) {
        AddBlockDialog(
            onPick = { type ->
                vm.addBlock(type)
                showAddBlock = false
            },
            onDismiss = { showAddBlock = false },
        )
    }

    editingId?.let { id ->
        val block = blocks.firstOrNull { it.id == id } ?: blocks.firstOrNull()
        if (block != null) {
            EditBlockDialog(
                block = block,
                text = editText,
                href = editHref,
                onTextChange = { editText = it },
                onHrefChange = { editHref = it },
                onSave = {
                    vm.updateBlock(id, editText, editHref)
                    editingId = null
                },
                onDismiss = { editingId = null },
            )
        }
    }
}

// ------------------------------------------------------------------ details

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppDetailsCard(project: AppProject, vm: BuilderViewModel) {
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = project.emoji,
                    onValueChange = { vm.setEmoji(it) },
                    modifier = Modifier.width(86.dp),
                    label = { Text("Icon", color = TextSecondary) },
                    singleLine = true,
                    colors = fieldColors(),
                )
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = project.name,
                    onValueChange = { vm.setAppName(it) },
                    modifier = Modifier.weight(1f),
                    label = { Text("App name", color = TextSecondary) },
                    singleLine = true,
                    colors = fieldColors(),
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = project.description,
                onValueChange = { vm.setDescription(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Short description", color = TextSecondary) },
                colors = fieldColors(),
            )
            Spacer(Modifier.height(12.dp))
            SmallLabel("Accent colour")
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ACCENT_COLORS.forEach { hex ->
                    ColorDot(
                        hex = hex,
                        selected = project.accent.equals(hex, ignoreCase = true),
                        onClick = { vm.setAccent(hex) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockRow(
    block: Block,
    onEdit: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
) {
    PanelCard(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    block.type.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Accent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceHigh)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    block.text.lineSequence().firstOrNull()?.ifBlank { "(empty)" } ?: "(empty)",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (block.href.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    block.href,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onUp) {
                    Icon(Icons.Outlined.ArrowUpward, contentDescription = "Move up", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDown) {
                    Icon(Icons.Outlined.ArrowDownward, contentDescription = "Move down", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = Accent, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Danger, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ dialogs

@Composable
fun AddBlockDialog(onPick: (BlockType) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a block") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BlockType.entries.forEach { type ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceHigh)
                            .clickable { onPick(type) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                type.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                type.hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}

@Composable
fun EditBlockDialog(
    block: Block,
    text: String,
    href: String,
    onTextChange: (String) -> Unit,
    onHrefChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${block.type.label.lowercase()}") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (block.type.hasText) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (block.type.multiline) "Text (one item per line)" else "Text", color = TextSecondary) },
                        maxLines = if (block.type.multiline) 8 else 3,
                        colors = fieldColors(),
                    )
                }
                if (block.type.hasHref) {
                    OutlinedTextField(
                        value = href,
                        onValueChange = onHrefChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Link (https://… or #screen:id)", color = TextSecondary) },
                        singleLine = true,
                        colors = fieldColors(),
                    )
                }
                Text(
                    block.type.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("Save", color = Accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = Accent,
    unfocusedBorderColor = OutlineC,
    cursorColor = Accent,
)
