package com.example.internetradio

import android.Manifest
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class MainActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller = mutableStateOf<MediaController?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                RadioScreen(controller.value, ::playStation)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (controllerFuture == null) {
            val token = SessionToken(this, ComponentName(this, RadioPlaybackService::class.java))
            val future = MediaController.Builder(this, token).buildAsync()
            controllerFuture = future
            future.addListener(
                { controller.value = future.get() },
                MoreExecutors.directExecutor()
            )
        }
    }

    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller.value = null
        super.onStop()
    }

    private fun playStation(index: Int) {
        val c = controller.value ?: return
        val items = defaultStations.map { station ->
            MediaItem.Builder()
                .setMediaId(station.streamUrl)
                .setUri(station.streamUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(station.name)
                        .setArtist(station.genre)
                        .build()
                )
                .build()
        }
        if (c.currentMediaItem?.mediaId == items[index].mediaId) {
            if (c.isPlaying) c.pause() else c.play()
        } else {
            c.setMediaItems(items, index, 0L)
            c.prepare()
            c.play()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(controller: MediaController?, onStationClick: (Int) -> Unit) {

    var isPlaying by remember { mutableStateOf(false) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var currentMediaId by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    DisposableEffect(controller) {
        if (controller == null) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                isPlaying = player.isPlaying
                playbackState = player.playbackState
                currentMediaId = player.currentMediaItem?.mediaId
            }
        }
        isPlaying = controller.isPlaying
        playbackState = controller.playbackState
        currentMediaId = controller.currentMediaItem?.mediaId
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }

    val currentStation = defaultStations.firstOrNull { it.streamUrl == currentMediaId }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Internet Radio") }) },
        bottomBar = {
            NowPlayingBar(
                station = currentStation,
                isPlaying = isPlaying,
                isBuffering = playbackState == Player.STATE_BUFFERING,
                onPlayPause = {
                    controller?.let { if (it.isPlaying) it.pause() else it.play() }
                },
                onPrevious = { controller?.seekToPreviousMediaItem() },
                onNext = { controller?.seekToNextMediaItem() }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            itemsIndexed(defaultStations) { index, station ->
                val isCurrent = station.streamUrl == currentMediaId
                ListItem(
                    modifier = Modifier.clickable { onStationClick(index) },
                    colors = if (isCurrent) {
                        ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    } else {
                        ListItemDefaults.colors()
                    },
                    leadingContent = {
                        Icon(Icons.Filled.Radio, contentDescription = null)
                    },
                    headlineContent = {
                        Text(
                            station.name,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = { Text(station.genre) },
                    trailingContent = {
                        if (isCurrent && playbackState == Player.STATE_BUFFERING) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else if (isCurrent && isPlaying) {
                            Icon(Icons.Filled.Pause, contentDescription = "Now playing")
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun NowPlayingBar(
    station: RadioStation?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            ) {
                Text(
                    station?.name ?: "Select a station",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (isBuffering) "Buffering..." else station?.genre ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onPrevious, enabled = station != null) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous station")
            }
            IconButton(onClick = onPlayPause, enabled = station != null) {
                if (isBuffering) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }
            }
            IconButton(onClick = onNext, enabled = station != null) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next station")
            }
        }
    }
}
