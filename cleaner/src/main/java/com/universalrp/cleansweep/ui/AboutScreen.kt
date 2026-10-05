package com.universalrp.cleansweep.ui

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
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.universalrp.cleansweep.MainViewModel
import com.universalrp.cleansweep.Screen
import com.universalrp.cleansweep.ui.theme.AccentCyan
import com.universalrp.cleansweep.ui.theme.AccentViolet
import com.universalrp.cleansweep.ui.theme.TextPrimary
import com.universalrp.cleansweep.ui.theme.TextSecondary

@Composable
fun AboutScreen(vm: MainViewModel) {
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
            IconButton(onClick = { vm.navigate(Screen.HOME) }) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Home", tint = TextSecondary)
            }
            Text(
                "About CleanSweep",
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
                        "CleanSweep v1.0",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "A privacy-friendly junk & cache cleaner built for Redmi 13 5G and other " +
                            "Android 8+ phones running MIUI or HyperOS. It finds residual temp files, " +
                            "thumbnail caches, duplicate files, leftover APK installers, empty folders, " +
                            "old downloads and large files — then removes only what you confirm.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Security, contentDescription = null, tint = AccentViolet)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Privacy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "• 100% offline — CleanSweep does not even ask for the INTERNET permission.\n" +
                            "• No ads, no analytics, no accounts.\n" +
                            "• Files are only deleted after you select them and confirm.\n" +
                            "• The Accessibility service is optional and only used to tap " +
                            "“Clear cache” buttons for you; it never reads your content.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Installing & updating (Play Protect)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "• CleanSweep works on any Android 8+ phone (Redmi/MIUI/HyperOS, " +
                            "Samsung One UI, Pixel, OnePlus, Realme, Vivo…), not just Redmi.\n\n" +
                            "• Because this APK is installed outside Google Play and uses an " +
                            "Accessibility service, Play Protect shows a warning. That is Android's " +
                            "standard caution for sideloaded cleaners — CleanSweep contains no " +
                            "malware, ads or internet access. You can safely tap “Install anyway” " +
                            "(or pause Play Protect during installation).\n\n" +
                            "• Builds are signed with one fixed key, so newer APKs install as an " +
                            "update. If an install ever says “app not installed”, uninstall the " +
                            "older copy first (needed once when moving from v1.1 or older).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Why some things can't be cleaned",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Modern Android is built to protect you:\n\n" +
                            "• Other apps' caches can't be wiped silently — that's why CleanSweep " +
                            "either automates the official Settings buttons via Accessibility or guides " +
                            "you through them.\n\n" +
                            "• The Android/data and Android/obb folders are locked by the system, even " +
                            "for cleaners. Redmi's built-in Security app can reach some of them.\n\n" +
                            "• Nothing is ever “RAM boosted” or “cooled down” — apps that claim that are " +
                            "fake. CleanSweep only frees real disk space.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Suggested routine",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "1. Tap “Scan & clean junk” and review what's selected.\n" +
                            "2. Check Large files and Old downloads — unselect anything you want to keep.\n" +
                            "3. Clean, then scan once more to sweep the folders left empty.\n" +
                            "4. Open App cache cleaner once a week for apps like Instagram, YouTube or WhatsApp.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
