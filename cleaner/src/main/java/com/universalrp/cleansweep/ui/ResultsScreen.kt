package com.universalrp.cleansweep.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.universalrp.cleansweep.MainViewModel
import com.universalrp.cleansweep.UiState
import com.universalrp.cleansweep.data.CategoryResult
import com.universalrp.cleansweep.data.JunkKind
import com.universalrp.cleansweep.data.formatBytes
import com.universalrp.cleansweep.data.shortPath
import com.universalrp.cleansweep.ui.theme.AccentCyan
import com.universalrp.cleansweep.ui.theme.Bg
import com.universalrp.cleansweep.ui.theme.DangerRed
import com.universalrp.cleansweep.ui.theme.GoodGreen
import com.universalrp.cleansweep.ui.theme.SurfaceC
import com.universalrp.cleansweep.ui.theme.TextPrimary
import com.universalrp.cleansweep.ui.theme.TextSecondary

@Composable
fun ResultsScreen(state: UiState, vm: MainViewModel) {
    var showConfirm by remember { mutableStateOf(false) }
    val report = state.report
    val selectedBytes = report?.selectedBytes() ?: 0L
    val selectedCount = report?.selectedCount() ?: 0

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.navigate(com.universalrp.cleansweep.Screen.HOME) }) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Home", tint = TextSecondary)
            }
            Text(
                "Scan results",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.startScan() }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Scan again", tint = TextSecondary)
            }
        }

        if (report == null || report.totalCount == 0) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "🎉",
                    style = MaterialTheme.typography.displayLarge,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Nothing to clean!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (report == null)
                        "Run a scan from the home screen to find junk, duplicates and empty folders."
                    else
                        "No junk was found on this scan. Your storage looks tidy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            report.totalBytes.formatBytes() + " junk found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${report.totalCount} items in ${report.categories.size} categories • " +
                                "${report.filesScanned} files scanned",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
            }

            items(report.categories, key = { it.kind.name }) { category ->
                CategoryCard(category, state.revision, vm)
            }
        }

        // Bottom clean bar
        Box(
            Modifier
                .fillMaxWidth()
                .background(SurfaceC)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(Brush.linearGradient(listOf(AccentCyan.copy(alpha = 0.16f), Color(0xFF8B5CF6).copy(alpha = 0.16f))))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${selectedCount} items selected",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        selectedBytes.formatBytes() + " can be freed",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Button(
                    onClick = { showConfirm = true },
                    enabled = selectedCount > 0 && !state.cleaning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerRed,
                        contentColor = Color(0xFF2B0606),
                    ),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clean", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Delete permanently?") },
            text = {
                Text(
                    "CleanSweep will permanently delete $selectedCount selected items and free " +
                        "${selectedBytes.formatBytes()}. This cannot be undone.",
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    vm.cleanSelected()
                }) {
                    Text("Delete", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
        )
    }

    if (state.cleaning) {
        CleaningOverlay(done = state.cleanDone, total = state.cleanTotal)
    }

    state.freedDialogBytes?.let { freed ->
        AlertDialog(
            onDismissRequest = { vm.dismissFreedDialog() },
            title = { Text("Cleaning complete ✨") },
            text = {
                Text(
                    "Freed ${freed.formatBytes()} by removing ${state.freedDialogItems} items. " +
                        "Tip: run another scan to sweep up folders that are now empty.",
                    color = TextSecondary,
                )
            },
            confirmButton = {
                Button(
                    onClick = { vm.dismissFreedDialog() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoodGreen,
                        contentColor = Color(0xFF052E22),
                    ),
                ) {
                    Text("Great!", fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}

@Composable
private fun CategoryCard(category: CategoryResult, revision: Long, vm: MainViewModel) {
    var expanded by remember(category.kind) { mutableStateOf(false) }
    val kind = category.kind

    PanelCard(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = category.allSelected,
                    onCheckedChange = { checked -> vm.toggleCategory(kind, checked) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = AccentCyan,
                        checkmarkColor = Color(0xFF03202B),
                        uncheckedColor = TextSecondary,
                    ),
                )
                Box(Modifier.size(2.dp))
                Icon(
                    kind.icon(),
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        kind.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${category.files.size} items • ${category.totalBytes.formatBytes()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = TextSecondary,
                )
            }

            if (expanded) {
                Column(Modifier.padding(bottom = 6.dp)) {
                    category.files.forEachIndexed { index, file ->
                        val fileRevisionKey = file.path + ":" + revision + ":" + index
                        FileRow(
                            key = fileRevisionKey,
                            path = file.path,
                            size = file.size,
                            selected = file.selected,
                            note = file.note,
                            onToggle = { vm.toggleFile(file.path) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    @Suppress("UNUSED_PARAMETER") key: String,
    path: String,
    size: Long,
    selected: Boolean,
    note: String?,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = AccentCyan,
                checkmarkColor = Color(0xFF03202B),
                uncheckedColor = TextSecondary,
            ),
        )
        Column(Modifier.weight(1f)) {
            Text(
                path.substringAfterLast('/'),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                path.shortPath(),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentCyan,
                )
            }
        }
        Text(
            if (size > 0) size.formatBytes() else "folder",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
        )
    }
}

@Composable
private fun CleaningOverlay(done: Int, total: Int) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Bg.copy(alpha = 0.88f))
            .clickable(enabled = false) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .background(SurfaceC)
                .padding(28.dp)
                .width(280.dp),
        ) {
            Text(
                "Cleaning…",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { if (total > 0) done / total.toFloat() else 0f },
                modifier = Modifier.fillMaxWidth(),
                color = AccentCyan,
                trackColor = TextSecondary.copy(alpha = 0.2f),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "$done / $total",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}
