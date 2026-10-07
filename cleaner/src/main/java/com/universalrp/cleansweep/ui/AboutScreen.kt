package com.universalrp.cleansweep.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.TextButton
import com.universalrp.cleansweep.MainViewModel
import com.universalrp.cleansweep.Screen
import com.universalrp.cleansweep.data.Lang
import com.universalrp.cleansweep.data.tr
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import com.universalrp.cleansweep.ui.theme.AccentCyan
import com.universalrp.cleansweep.ui.theme.AccentViolet
import com.universalrp.cleansweep.ui.theme.TextPrimary
import com.universalrp.cleansweep.ui.theme.TextSecondary

@Composable
fun AboutScreen(state: com.universalrp.cleansweep.UiState, vm: MainViewModel) {
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
            IconButton(onClick = { vm.goBack() }) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Home", tint = TextSecondary)
            }
            Text(
                tr("About") + " " + tr(Lang.appName()),
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
            // ------------------------------------------------------ who made this
            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        tr("Author"),
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(listOf(AccentCyan, AccentViolet))
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "ர",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF04202A),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            // Always both scripts, so the author is recognisable whichever
                            // language the menu is in.
                            Text(
                                "ரமேஷ் பிரதாப்",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Ramesh prathap .R",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val context = LocalContext.current
                    TextButton(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_SENDTO,
                                        Uri.parse("mailto:universalrp2003@gmail.com"),
                                    )
                                )
                            } catch (e: Exception) {
                                // No mail app: the address is on screen to copy.
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Email,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "universalrp2003@gmail.com",
                            color = AccentCyan,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    TextButton(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://github.com/universalrp2003"),
                                    )
                                )
                            } catch (e: Exception) {
                                // No browser: the address is on screen to copy.
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Public,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "github.com/universalrp2003",
                            color = AccentCyan,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tr("Free software under the GNU GPL v3") + "\n" +
                            "github.com/universalrp2003/InternetRadio",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentCyan,
                    )
                    Text(
                        tr("App name") + ": " + Lang.appName() +
                            if (Lang.isTamil) "  (CleanSweep)" else "  (சுத்தம் செய்பவர்)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }

            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr("CleanSweep v2.8"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "A privacy-friendly cleaner for every Android phone — Redmi and other Xiaomi " +
                            "phones (HyperOS / MIUI), Samsung One UI, Oppo, Vivo, Realme, OnePlus, " +
                            "Motorola, Nokia, Tecno and stock Android 8+. It finds residual temp files, " +
                            "thumbnail caches, duplicate files, leftover APK installers, empty folders, " +
                            "old downloads and large files — then removes only what you confirm.\n\n" +
                            "New in 2.0: live battery, temperature, voltage, charging current and watt " +
                            "readings; an installed-app list that flags bloatware, unused and sideloaded " +
                            "apps; a security review of permissions and system settings; a Wi-Fi scanner " +
                            "that shows how many devices share your network; and optional AI analysis " +
                            "with your own key (or a free keyless option). The Accessibility service " +
                            "stays gone, and nothing is uploaded unless you tap Analyse.",
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
                        Text(tr("Privacy"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "• Offline by default — scanning, cleaning, health, security and the " +
                            "assistant all run on this phone. The internet is used for exactly two " +
                            "things you tap yourself: the AI analysis and the speed test.\n" +
                            "• No Accessibility service: dropped in v1.3, so CleanSweep cannot read " +
                            "your screen or tap inside other apps.\n" +
                            "• Nothing is uploaded to us — there is no CleanSweep server. An AI " +
                            "request goes straight from your phone to the provider whose key you " +
                            "pasted, and “See exactly what was sent” shows the whole request.\n" +
                            "• Location is asked for only on the Wi-Fi and Mobile screens (Android " +
                            "hides network and tower details without it); Phone access is used only " +
                            "on the Mobile screen.\n" +
                            "• No ads, no analytics, no accounts.\n" +
                            "• Files are only deleted after you select them and confirm.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr("Installing & updating (Play Protect)"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "• CleanSweep works on any Android 8+ phone (Redmi/MIUI/HyperOS, " +
                            "Samsung One UI, Pixel, OnePlus, Realme, Vivo…), not just Redmi.\n\n" +
                            "• This APK is installed outside Google Play, so Play Protect may show a " +
                            "warning — Android's standard caution for sideloaded apps. CleanSweep " +
                            "never uploads anything by itself. It has internet access for one reason " +
                            "only: the optional AI analysis you start yourself with your own key. It " +
                            "contains no ads, no trackers and no Accessibility service. You can safely " +
                            "tap “Install anyway”.\n\n" +
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
                    Text(tr("Why some things can't be cleaned"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Modern Android is built to protect you:\n\n" +
                            "• Other apps' caches can't be wiped silently — no app can do that without " +
                            "root, and CleanSweep refuses to fake it with Accessibility. Instead it " +
                            "opens each app's storage page and you tap “Clear cache” (two taps per app, " +
                            "with a “Next app” button so you never lose your place).\n\n" +
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
                    Text(tr("Suggested routine"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "1. Tap “Scan & clean junk” and review what's selected.\n" +
                            "2. Check Large files and Old downloads — unselect anything you want to keep.\n" +
                            "3. Clean, then scan once more to sweep the folders left empty.\n" +
                            "4. Open App cache cleaner once a week for apps like Instagram, YouTube or WhatsApp.\n" +
                            "5. Stuck on something? Ask the on-device assistant — it answers from your " +
                            "real numbers, offline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
            // v2.6: a crash used to be invisible — "CleanSweep keeps stopping" with nothing
            // to act on. The app now keeps the last one on the phone and shows it here.
            if (state.lastCrash.isNotBlank()) {
                PanelCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            tr("Last crash"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = com.universalrp.cleansweep.ui.theme.WarnAmber,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            tr(
                                "The last time CleanSweep stopped by itself. Nothing is sent " +
                                    "anywhere — this is here so you can read it out or screenshot it."
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.lastCrash.take(1400),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = { vm.clearCrashReport() }) {
                            Text(
                                tr("Clear"),
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
