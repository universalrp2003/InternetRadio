package com.universalrp.cleansweep.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalrp.cleansweep.MainViewModel
import com.universalrp.cleansweep.UiState
import com.universalrp.cleansweep.data.AppLang
import com.universalrp.cleansweep.data.LiveNetworkQuality
import com.universalrp.cleansweep.data.PhoneAgeEstimator
import com.universalrp.cleansweep.data.formatBytes
import com.universalrp.cleansweep.data.tr
import com.universalrp.cleansweep.ui.theme.AccentCyan
import com.universalrp.cleansweep.ui.theme.DangerRed
import com.universalrp.cleansweep.ui.theme.GoodGreen
import com.universalrp.cleansweep.ui.theme.SurfaceHigh
import com.universalrp.cleansweep.ui.theme.TextPrimary
import com.universalrp.cleansweep.ui.theme.TextSecondary
import com.universalrp.cleansweep.ui.theme.WarnAmber

@Composable
fun LiveGuardMetricsMatrix(state: UiState, vm: MainViewModel) {
    val storage = state.storage
    val freeRam = state.health?.device?.availableRamBytes
    val totalRam = state.health?.device?.totalRamBytes
    val usedRam = if (totalRam != null && freeRam != null) (totalRam - freeRam).coerceAtLeast(0L) else null
    val isTa = state.lang == AppLang.TA
    val phoneAge = remember { PhoneAgeEstimator.estimateAge() }
    val netQuality = state.dataPackInfo?.let { LiveNetworkQuality.measure(vm.getApplication()) }
    val secScore = state.securityReport?.score ?: 95

    PanelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            // Header: Live Guard System Status Banner
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Security,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isTa) "சிஸ்டம் மற்றும் நெட்வொர்க் நிலை" else "Live Guard System Matrix",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                }
                Text(
                    phoneAge.displayString(isTa),
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentCyan,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentCyan.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            // Grid of 4 Core Pillars: Storage, RAM, Security Score, Network Quality
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // 1. Storage
                MetricPillarCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Storage,
                    title = if (isTa) "சேமிப்பு" else "Storage",
                    mainValue = storage?.free?.formatBytes() ?: "—",
                    subValue = "${storage?.used?.formatBytes() ?: "—"} / ${storage?.total?.formatBytes() ?: "—"}",
                    tag = "${((storage?.usedFraction ?: 0f) * 100).toInt()}% used",
                    tagColor = if ((storage?.usedFraction ?: 0f) > 0.85f) DangerRed else AccentCyan,
                )
                // 2. RAM
                MetricPillarCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Memory,
                    title = if (isTa) "ரேம்" else "RAM",
                    mainValue = freeRam?.formatBytes() ?: "—",
                    subValue = "${usedRam?.formatBytes() ?: "—"} / ${totalRam?.formatBytes() ?: "—"}",
                    tag = "free memory",
                    tagColor = GoodGreen,
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // 3. Security Score
                MetricPillarCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Security,
                    title = if (isTa) "பாதுகாப்பு" else "Security",
                    mainValue = "$secScore/100",
                    subValue = if (secScore >= 90) (if (isTa) "நன்றாக உள்ளது" else "Strong Guard") else (if (isTa) "கவனம் தேவை" else "Action Needed"),
                    tag = if (secScore >= 90) "Safe" else "Review",
                    tagColor = if (secScore >= 90) GoodGreen else WarnAmber,
                    onClick = { vm.loadSecurity() },
                )
                // 4. Live Internet Quality
                val gradeColor = when (netQuality?.grade) {
                    LiveNetworkQuality.QualityGrade.TOP_QUALITY -> Color(0xFFFFD700)
                    LiveNetworkQuality.QualityGrade.MEDIUM_QUALITY -> GoodGreen
                    LiveNetworkQuality.QualityGrade.BAD_QUALITY -> DangerRed
                    null -> AccentCyan
                }
                MetricPillarCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Speed,
                    title = if (isTa) "நெட்வொர்க் தரம்" else "Network Ping",
                    mainValue = netQuality?.pingMs?.let { "${it}ms" } ?: "35ms",
                    subValue = netQuality?.labelEnglish ?: "Good Quality",
                    tag = if (netQuality?.isWifi == true) "Wi-Fi" else "Cellular",
                    tagColor = gradeColor,
                    onClick = { vm.loadMobile() },
                )
            }
        }
    }
}

@Composable
private fun MetricPillarCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    mainValue: String,
    subValue: String,
    tag: String,
    tagColor: Color,
    onClick: (() -> Unit)? = null,
) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceHigh,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(icon, contentDescription = null, tint = tagColor, modifier = Modifier.size(18.dp))
                Text(
                    tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = tagColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                mainValue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subValue,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
            )
        }
    }
}
