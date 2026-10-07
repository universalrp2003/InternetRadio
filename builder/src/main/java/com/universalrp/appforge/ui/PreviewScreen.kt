package com.universalrp.appforge.ui

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.universalrp.appforge.BScreen
import com.universalrp.appforge.BuilderState
import com.universalrp.appforge.BuilderViewModel

@Composable
fun PreviewScreen(state: BuilderState, vm: BuilderViewModel) {
    val html = remember(state.project, state.activeScreen) { vm.previewHtml() }

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
                    "Live preview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "This is the real app, running offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = Accent,
                )
            }
            IconButton(onClick = { vm.navigate(BScreen.PREVIEW) }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Reload", tint = TextSecondary)
            }
            IconButton(onClick = { vm.navigate(BScreen.EXPORT) }) {
                Icon(Icons.Outlined.Share, contentDescription = "Export", tint = Accent)
            }
        }

        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    setBackgroundColor(0xFF0B0F1E.toInt())
                }
            },
            update = { web ->
                // Reload only when the generated HTML actually changed.
                if (web.tag != html) {
                    web.tag = html
                    web.loadDataWithBaseURL(
                        "https://appforge.local/",
                        html,
                        "text/html",
                        "utf-8",
                        null,
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}
