package com.universalrp.cleansweep.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalrp.cleansweep.MainViewModel
import com.universalrp.cleansweep.data.SecurityFixHelper
import com.universalrp.cleansweep.data.tr
import com.universalrp.cleansweep.ui.theme.AccentCyan
import com.universalrp.cleansweep.ui.theme.GoodGreen
import com.universalrp.cleansweep.ui.theme.SurfaceHigh
import com.universalrp.cleansweep.ui.theme.TextPrimary
import com.universalrp.cleansweep.ui.theme.TextSecondary
import com.universalrp.cleansweep.ui.theme.WarnAmber

@Composable
fun PermissionHubCard(vm: MainViewModel) {
    val context = LocalContext.current
    val manufacturer = Build.MANUFACTURER.lowercase()
    val isXiaomi = manufacturer.contains("xiaomi") || Build.BRAND.lowercase().contains("redmi") || Build.BRAND.lowercase().contains("poco")
    val isAndroid13Plus = Build.VERSION.SDK_INT >= 33

    PanelCard(
        Modifier
            .fillMaxWidth()
            .border(1.dp, if (isAndroid13Plus) AccentCyan.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(20.dp))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Security, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("Live Guard Permission Setup & Fix"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (isAndroid13Plus) "Android 13+ / MIUI / HyperOS Security Guide" else "Manage or Revoke App Permissions",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }

            if (isAndroid13Plus) {
                Spacer(Modifier.height(12.dp))
                // Xiaomi & Android 13+ Restricted Settings Warning Box
                Surface(
                    color = WarnAmber.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, tint = WarnAmber, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                tr("“Allow restricted settings” Required"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = WarnAmber,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            tr("If a permission (Usage Access or Floating Overlay) appears greyed out or says “Restricted setting: For your security, this setting is unavailable”:"),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "1. Tap “Open Live Guard App Info” below.\n" +
                                "2. Tap the (⋮) 3 dots icon in the top right.\n" +
                                "3. Tap “Allow restricted settings”.\n" +
                                "4. Come back and toggle your desired permission ON or OFF.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { SecurityFixHelper.openAppInfo(context, context.packageName) },
                            colors = ButtonDefaults.buttonColors(containerColor = WarnAmber),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(tr("Open Live Guard App Info"), color = Color(0xFF1E1300), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                tr("App Permissions Hub (Turn ON / Turn OFF)"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = AccentCyan,
            )

            Spacer(Modifier.height(8.dp))

            // 1. Usage Access
            val hasUsage = checkUsageAccess(context)
            PermissionRowItem(
                title = tr("Usage Access"),
                desc = tr("Required for strictly separate Mobile Data vs Wi-Fi usage tracking per app"),
                isGranted = hasUsage,
                onOpen = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        SecurityFixHelper.openAppInfo(context, context.packageName)
                    }
                },
            )

            Spacer(Modifier.height(8.dp))

            // 2. Display Over Other Apps
            val hasOverlay = Settings.canDrawOverlays(context)
            PermissionRowItem(
                title = tr("Display Over Other Apps"),
                desc = tr("Draws the status bar pill and live illuminated D & U LED meter"),
                isGranted = hasOverlay,
                onOpen = {
                    vm.openOverlaySettings()
                },
            )

            Spacer(Modifier.height(8.dp))

            // 3. All Files Access
            val hasFiles = if (Build.VERSION.SDK_INT >= 30) android.os.Environment.isExternalStorageManager() else true
            PermissionRowItem(
                title = tr("All Files Storage Access"),
                desc = tr("Scans for residual cache, temp files, and bloatware junk"),
                isGranted = hasFiles,
                onOpen = {
                    vm.requestAllFilesAccess()
                },
            )

            Spacer(Modifier.height(8.dp))

            // 4. Notifications
            PermissionRowItem(
                title = tr("App Notifications"),
                desc = tr("Shows persistent data tracking and network quality alerts"),
                isGranted = true,
                onOpen = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        SecurityFixHelper.openAppInfo(context, context.packageName)
                    }
                },
            )
        }
    }
}

@Composable
private fun PermissionRowItem(
    title: String,
    desc: String,
    isGranted: Boolean,
    onOpen: () -> Unit,
) {
    Surface(
        color = SurfaceHigh,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isGranted) "ON / ALLOWED" else "OFF / NEEDED",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isGranted) GoodGreen else WarnAmber,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.width(10.dp))
            OutlinedButton(
                onClick = onOpen,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Text(
                    if (isGranted) "Turn OFF" else "Turn ON",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isGranted) WarnAmber else AccentCyan,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun checkUsageAccess(context: Context): Boolean = try {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
    val mode = appOps?.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName,
    )
    mode == AppOpsManager.MODE_ALLOWED
} catch (e: Exception) {
    false
}
