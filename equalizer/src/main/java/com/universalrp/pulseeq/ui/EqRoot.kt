package com.universalrp.pulseeq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalrp.pulseeq.EqTab
import com.universalrp.pulseeq.EqViewModel

@Composable
fun EqRoot(vm: EqViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val msg = state.message
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            vm.dismissMessage()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        when (state.tab) {
            EqTab.EQUALIZER -> EqualizerScreen(state, vm)
            EqTab.PRESETS -> PresetsScreen(state, vm)
            EqTab.PLAYER -> PlayerScreen(state, vm)
            EqTab.SETTINGS -> SettingsScreen(state, vm)
            EqTab.ABOUT -> AboutScreen(vm)
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = SurfaceHigh,
                    contentColor = TextPrimary,
                )
            }
            BottomBar(
                current = state.tab,
                onSelect = { vm.navigate(it) },
            )
        }
    }
}

@Composable
private fun BottomBar(current: EqTab, onSelect: (EqTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceC)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomItem(EqTab.EQUALIZER, "Bands", Icons.Outlined.GraphicEq, current, onSelect)
        BottomItem(EqTab.PRESETS, "Presets", Icons.Outlined.Tune, current, onSelect)
        BottomItem(EqTab.PLAYER, "Player", Icons.Outlined.MusicNote, current, onSelect)
        BottomItem(EqTab.SETTINGS, "More", Icons.Outlined.Settings, current, onSelect)
    }
}

@Composable
private fun BottomItem(
    tab: EqTab,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    current: EqTab,
    onSelect: (EqTab) -> Unit,
) {
    val selected = tab == current
    Column(
        Modifier
            .clickable { onSelect(tab) }
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) Cyan else TextSecondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Cyan else TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
