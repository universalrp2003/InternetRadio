package com.universalrp.tamilnadufm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import com.universalrp.tamilnadufm.MainViewModel
import com.universalrp.tamilnadufm.Tab
import com.universalrp.tamilnadufm.player.PlayerBus

@Composable
fun AppRoot(vm: MainViewModel, controller: MediaController?) {

    // The screens talk to the player through the ViewModel; hand it the controller
    // whenever the service connection comes or goes.
    LaunchedEffect(controller) {
        if (controller != null) {
            vm.attachController(controller)
        }
    }
    androidx.compose.runtime.DisposableEffect(controller) {
        onDispose { if (controller != null) vm.detachController(controller) }
    }

    val state by vm.state.collectAsState()
    val playback by PlayerBus.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        Box(Modifier.weight(1f)) {
            when (state.tab) {
                Tab.RADIO -> RadioScreen(vm, state)
                Tab.NEWS -> NewsScreen(vm, state)
                Tab.LOCAL -> LocalScreen(vm, state)
                Tab.EQUALIZER -> EqualizerScreen(vm, state)
                Tab.MORE -> MoreScreen(vm, state)
            }
        }

        AnimatedVisibility(visible = state.message != null) {
            state.message?.let { text ->
                MessageBar(text) { vm.dismissMessage() }
            }
        }

        if (state.tab != Tab.MORE || state.nowPlaying != null || state.playingLocalTitle != null) {
            TransportBar(vm, state, playback.isPlaying, playback.isBuffering)
        }

        BottomNav(state.tab) { vm.selectTab(it) }
    }

    if (state.showNowPlaying) {
        NowPlayingSheet(vm, state)
    }

    if (state.showDirectory) {
        DirectorySheet(vm, state)
    }
}

@Composable
private fun MessageBar(text: String, onClose: () -> Unit) {
    Surface(color = SurfaceHigh, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.Close,
                contentDescription = "Dismiss",
                tint = TextSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onClose),
            )
        }
    }
}

@Composable
private fun TransportBar(
    vm: MainViewModel,
    state: com.universalrp.tamilnadufm.UiState,
    isPlaying: Boolean,
    isBuffering: Boolean,
) {
    val station = state.nowPlaying
    val localTitle = state.playingLocalTitle
    val last = vm.lastPlayed()
    val title = station?.name ?: localTitle ?: last?.name ?: "Pick a station to start"
    val subtitle = when {
        station != null -> station.subtitle.ifBlank { "Live stream" }
        isBuffering -> "Connecting…"
        localTitle != null -> "From your phone"
        else -> "Tamil FM • News • Local files"
    }
    val live = station != null

    Surface(color = SurfaceC, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (state.nowPlaying != null || state.playingLocalTitle != null) {
                            vm.openNowPlaying(true)
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                station?.let { StationLogo(it, 42.dp) } ?: Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Radio, contentDescription = null, tint = Saffron)
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (live) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isPlaying) Danger else TextSecondary)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (isPlaying) "LIVE" else "PAUSED",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isPlaying) Danger else TextSecondary,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(26.dp)
                        .clickable { vm.previous() },
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(Saffron)
                        .clickable { vm.togglePlayPause() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color(0xFF2A1200),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(26.dp)
                        .clickable { vm.next() },
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Filled.Stop,
                    contentDescription = "Stop",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { vm.stop() },
                )
            }
        }
    }
}

@Composable
private fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    Surface(color = SurfaceHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            NavItem("Radio", Icons.Filled.Radio, current == Tab.RADIO) { onSelect(Tab.RADIO) }
            NavItem("News", Icons.Filled.Newspaper, current == Tab.NEWS) { onSelect(Tab.NEWS) }
            NavItem("Local", Icons.Filled.LibraryMusic, current == Tab.LOCAL) { onSelect(Tab.LOCAL) }
            NavItem("EQ", Icons.Filled.Equalizer, current == Tab.EQUALIZER) { onSelect(Tab.EQUALIZER) }
            NavItem("More", Icons.Filled.MoreHoriz, current == Tab.MORE) { onSelect(Tab.MORE) }
        }
    }
}

@Composable
private fun RowScope.NavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) Saffron else TextSecondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Saffron else TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
