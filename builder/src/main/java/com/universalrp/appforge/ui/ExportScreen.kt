package com.universalrp.appforge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalrp.appforge.BScreen
import com.universalrp.appforge.BuilderState
import com.universalrp.appforge.BuilderViewModel
import com.universalrp.appforge.CODE_FILES

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(state: BuilderState, vm: BuilderViewModel) {
    LaunchedEffect(state.codePath, state.codeText) {
        if (state.codeText.isBlank()) vm.loadCode(state.codePath)
    }

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
            IconButton(onClick = { vm.navigate(BScreen.EDITOR) }) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Editor", tint = TextSecondary)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Export",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Take your app out of AppForge",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                PanelCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Accent)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "1 · HTML app (instant)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "One self-contained file you can open in any browser, keep on the phone, or " +
                                "send to a friend. Text fields and checklists save themselves offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(12.dp))
                        GradientButton(
                            text = "Export & share",
                            icon = Icons.Outlined.Share,
                            onClick = { vm.exportHtml(share = true) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.busy,
                        )
                        TextButton(onClick = { vm.exportHtml(share = false) }) {
                            Text("Just export (no share sheet)", color = TextSecondary)
                        }
                    }
                }
            }

            item {
                PanelCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Description, contentDescription = null, tint = Accent2)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "2 · Android project (.zip)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "A complete Android Studio project: Kotlin activity, manifest, Gradle files, " +
                                "your HTML in assets, and a GitHub Actions workflow. Upload it to GitHub, run " +
                                "the workflow, and download a real APK — no PC needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(12.dp))
                        GradientButton(
                            text = "Export & share project",
                            icon = Icons.Outlined.Share,
                            onClick = { vm.exportAndroidProject(share = true) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.busy,
                        )
                    }
                }
            }

            state.exportInfo?.let { info ->
                item {
                    PanelCard(Modifier.fillMaxWidth(), color = SurfaceHigh) {
                        Text(
                            info,
                            style = MaterialTheme.typography.bodySmall,
                            color = Accent,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }

            item {
                PanelCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Code, contentDescription = null, tint = Accent)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "3 · Look at the generated code",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CODE_FILES.forEach { path ->
                                Chip(
                                    label = path.substringAfterLast('/'),
                                    selected = path == state.codePath,
                                    onClick = { vm.loadCode(path) },
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            state.codePath,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .background(Bg, RoundedCornerShape(12.dp))
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                                .padding(10.dp)
                        ) {
                            Text(
                                state.codeText.ifBlank { "Pick a file above to see it here." },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                ),
                                color = TextPrimary,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { vm.copyCode() }) {
                                Icon(
                                    Icons.Outlined.ContentCopy,
                                    contentDescription = null,
                                    tint = Accent,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                                Text("Copy", color = Accent)
                            }
                            TextButton(onClick = { vm.shareCode() }) {
                                Icon(
                                    Icons.Outlined.Share,
                                    contentDescription = null,
                                    tint = Accent,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                                Text("Share", color = Accent)
                            }
                        }
                    }
                }
            }
        }
    }
}
