package com.universalrp.appforge.ui

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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.universalrp.appforge.APP_VERSION
import com.universalrp.appforge.BScreen
import com.universalrp.appforge.BuilderState
import com.universalrp.appforge.BuilderViewModel
import com.universalrp.appforge.model.AppProject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ForgeHomeScreen(state: BuilderState, vm: BuilderViewModel) {
    var deleteCandidate by remember { mutableStateOf<AppProject?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Accent, Accent2))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Widgets,
                    contentDescription = null,
                    tint = Color(0xFF04202A),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "AppForge",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Build an app on your phone • offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            IconButton(onClick = { vm.navigate(BScreen.ABOUT) }) {
                Icon(Icons.Outlined.Info, contentDescription = "About", tint = TextSecondary)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                GradientButton(
                    text = "New app",
                    icon = Icons.Outlined.Add,
                    onClick = { vm.showTemplates(true) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item { HowItWorksCard() }

            item { SectionTitle("Your apps (${state.projects.size})") }

            if (state.projects.isEmpty()) {
                item {
                    PanelCard(Modifier.fillMaxWidth()) {
                        Text(
                            "No apps yet. Tap “New app” and pick a starter template — you can change every " +
                                "word afterwards.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            items(state.projects) { project ->
                ProjectRow(
                    project = project,
                    onOpen = { vm.openProject(project) },
                    onDelete = { deleteCandidate = project },
                )
            }
        }
    }

    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete “${candidate.name}”?") },
            text = {
                Text(
                    "The project is removed from this phone. Exported files you already shared are not affected.",
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteProject(candidate.id)
                    deleteCandidate = null
                }) {
                    Text("Delete", color = Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun ProjectRow(project: AppProject, onOpen: () -> Unit, onDelete: () -> Unit) {
    val updated = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(project.updatedAt))
    PanelCard(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(project.emoji, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    project.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${project.screens.size} screen${if (project.screens.size == 1) "" else "s"} • edited $updated",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = TextSecondary)
            }
        }
    }
}

@Composable
private fun HowItWorksCard() {
    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Smartphone, contentDescription = null, tint = Accent)
                Spacer(Modifier.width(10.dp))
                Text(
                    "How AppForge works",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "1. Design your screens from simple blocks (headings, notes, links, buttons, " +
                    "text fields, checklists).\n" +
                    "2. Preview the real app — it runs offline with no server.\n" +
                    "3. Export a single HTML app to use right away, or a zipped Android Studio " +
                    "project that builds into an APK on GitHub Actions or your PC.\n\n" +
                    "Everything happens on this phone. Nothing is uploaded anywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            SmallLabel("AppForge $APP_VERSION • offline app builder", Accent)
        }
    }
}

@Composable
fun TemplatePickerDialog(vm: BuilderViewModel) {
    val templates = remember { vm.templates() }
    AlertDialog(
        onDismissRequest = { vm.showTemplates(false) },
        title = { Text("Start a new app") },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                templates.forEach { template ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceHigh)
                            .clickable { vm.newProject(template) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(template.emoji, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                template.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                template.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.showTemplates(false) }) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}
