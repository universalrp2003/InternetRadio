package com.universalrp.appforge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.universalrp.appforge.APP_VERSION
import com.universalrp.appforge.BScreen
import com.universalrp.appforge.BuilderViewModel

@Composable
fun ForgeAboutScreen(vm: BuilderViewModel) {
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
            IconButton(onClick = { vm.navigate(BScreen.HOME) }) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Home", tint = TextSecondary)
            }
            Text(
                "About AppForge",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "AppForge $APP_VERSION",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Accent,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "An offline app builder for your phone. Design screens from simple blocks, " +
                            "preview the result as a real working mini app, then export it as a single " +
                            "HTML file or as a complete Android project that builds into an APK.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Shield, contentDescription = null, tint = Accent2)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Privacy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "• Your projects are stored as small JSON files in AppForge's private folder.\n" +
                            "• Exports go to this app's own folder until you press Share.\n" +
                            "• Nothing is uploaded, and there is no account, ads or analytics.\n" +
                            "• INTERNET is used only to load images you link to in a preview.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Honest limitations",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "• AppForge does not compile an APK on the phone — no Android toolchain can run " +
                            "there. It generates the full project instead, and one GitHub Actions run " +
                            "(or Android Studio on a PC) turns that into an installable APK.\n\n" +
                            "• The exported app is a polished offline HTML app in a WebView shell. That makes " +
                            "it perfect for notes, link hubs, checklists, catalogues and simple tools — " +
                            "but it is not a native app with hardware APIs.\n\n" +
                            "• Everything you create keeps working with no internet, because the HTML file " +
                            "is self-contained.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Turning your app into an APK",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "1. Export the Android project (.zip) and share it to Files or Drive.\n" +
                            "2. Upload the unzipped folder to a new GitHub repository.\n" +
                            "3. Open Actions → Build APK → Run workflow.\n" +
                            "4. Download the app-apk artifact and install it (allow “install unknown apps”).\n\n" +
                            "The generated project already contains that workflow, so build 3 is the only " +
                            "step you have to remember.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
