package com.universalrp.tamilnadufm.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.universalrp.tamilnadufm.MainViewModel
import com.universalrp.tamilnadufm.UiState
import com.universalrp.tamilnadufm.audio.Bands
import com.universalrp.tamilnadufm.data.LocalTrack
import com.universalrp.tamilnadufm.player.PlayerBus

// --------------------------------------------------------------------- local

@Composable
fun LocalScreen(vm: MainViewModel, state: UiState) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.loadLocalTracks()
    }
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.playPickedFile(uri)
    }

    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(Modifier.padding(top = 16.dp)) {
                Text(
                    "Local audio",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Songs on this phone, played through the same 10-band equalizer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }

        item {
            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconChip(Icons.Filled.Refresh, "Scan device") {
                            permissionLauncher.launch(permission)
                            vm.loadLocalTracks()
                        }
                        Spacer(Modifier.width(8.dp))
                        IconChip(Icons.Filled.Folder, "Open a file") {
                            fileLauncher.launch(arrayOf("audio/*"))
                        }
                    }
                    if (state.localTracks.isEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "No songs listed yet. Tap \u201CScan device\u201D and allow access to your " +
                                "audio files, or pick a single file with \u201COpen a file\u201D. " +
                                "If a file is stored somewhere unusual, the picker still works — " +
                                "the equalizer applies either way.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
            }
        }

        if (state.localTracks.isNotEmpty()) {
            item {
                SectionTitle("${state.localTracks.size} songs found")
            }
            items(state.localTracks, key = { it.uri.toString() }) { track ->
                LocalTrackRow(
                    track = track,
                    current = state.playingLocalTitle == track.title,
                    onPlay = { vm.playLocal(track) },
                )
            }
        }
    }
}

@Composable
private fun LocalTrackRow(track: LocalTrack, current: Boolean, onPlay: () -> Unit) {
    PanelCard(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay),
        color = if (current) SurfaceHigh else SurfaceC,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (current) Saffron.copy(alpha = 0.25f) else SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (current) Icons.Filled.Headphones else Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = if (current) Saffron else TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (current) Saffron else TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    track.subtitle.ifBlank { "Unknown artist" },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                )
            }
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Play",
                tint = TextSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ equalizer

@Composable
fun EqualizerScreen(vm: MainViewModel, state: UiState) {
    val eq = state.eq
    val scroll = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.padding(top = 16.dp)) {
            Text(
                "Equalizer",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "10 bands, 31 Hz to 16 kHz, plus Clear sound. Applies to everything this " +
                    "app plays — online stations and local files.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }

        // Power + engine
        PanelCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Equalizer, contentDescription = null, tint = Saffron)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (eq.enabled) "Equalizer on" else "Equalizer off",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        state.eqEngine.ifBlank { "Attaches when playback starts" },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
                Switch(
                    checked = eq.enabled,
                    onCheckedChange = { vm.setEqEnabled(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Saffron),
                )
            }
        }

        // Quick curves
        SectionTitle("Quick sound")
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val quick = listOf("Bass boost", "Vocal", "Treble boost", "Dance", "Movies", "Flat")
            quick.forEach { name ->
                val preset = vm.presets().firstOrNull { it.name == name }
                Chip(
                    label = name,
                    selected = eq.presetName == name,
                    onClick = { preset?.let { vm.applyPreset(it) } },
                )
            }
        }

        // Presets
        SectionTitle("Presets")
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            vm.presets().forEach { preset ->
                Chip(
                    label = preset.name,
                    selected = eq.presetName == preset.name,
                    onClick = { vm.applyPreset(preset) },
                )
            }
        }
        vm.presets().firstOrNull { it.name == eq.presetName }?.let { preset ->
            if (preset.note.isNotBlank()) {
                Text(
                    preset.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = Teal,
                )
            }
        }

        // The faders
        PanelCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 12.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val effective = com.universalrp.tamilnadufm.audio.AudioFx.effectiveGains()
                    for (index in 0 until Bands.COUNT) {
                        Fader(
                            label = vm.bandLabel(index),
                            gainDb = eq.gains.getOrElse(index) { 0f },
                            extraDb = effective.getOrElse(index) { 0f } -
                                eq.gains.getOrElse(index) { 0f },
                            onChange = { vm.setBand(index, it) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Preset: ${eq.presetName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Preamp %.1f dB".format(eq.preampDb),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }
        }

        // Preamp
        PanelCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                SectionTitle("Preamp")
                Slider(
                    value = eq.preampDb,
                    onValueChange = { vm.setPreamp(it) },
                    valueRange = -12f..0f,
                    steps = 23,
                    colors = SliderDefaults.colors(
                        thumbColor = Saffron,
                        activeTrackColor = Saffron,
                    ),
                )
                Text(
                    "Boosting bands can clip. The preamp pulls the whole output down to keep " +
                        "it clean — it is set automatically when you change a band.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }

        // Clarity
        PanelCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                SectionTitle("Clear sound")
                ToggleRow(
                    title = "Clear sound",
                    subtitle = "Gentle multiband levelling + a limiter, so ads and jingles " +
                        "stop distorting and quiet parts stay audible.",
                    checked = eq.clearSound,
                    onChange = { vm.setClearSound(it) },
                )
                ToggleRow(
                    title = "Rumble cut",
                    subtitle = "Rolls off the sub-bass below 62 Hz — removes the boom from " +
                        "traffic noise, footsteps and wind that phone mics pick up.",
                    checked = eq.rumbleCut,
                    onChange = { vm.setRumbleCut(it) },
                )
                ToggleRow(
                    title = "Hiss cut",
                    subtitle = "Trims the top octave where low-bitrate streams put their hiss. " +
                        "Turn it on if a station sounds fizzy.",
                    checked = eq.hissCut,
                    onChange = { vm.setHissCut(it) },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Volume boost: %.0f dB".format(eq.boostDb),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                )
                Slider(
                    value = eq.boostDb,
                    onValueChange = { vm.setBoost(it) },
                    valueRange = 0f..10f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = Gold,
                        activeTrackColor = Gold,
                    ),
                )
            }
        }

        // Honest limits
        PanelCard(Modifier.fillMaxWidth(), color = SurfaceHigh) {
            Row(Modifier.padding(14.dp)) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = Teal, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "What this equalizer can and cannot do",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "It shapes the sound this app plays. Android gives no app a way to reshape " +
                            "another app's audio without root or a screen-capture consent dialog, " +
                            "so YouTube and other players are out of reach — that is a platform " +
                            "limit, not a missing feature. Everything here is a standard Android " +
                            "audio effect: no root, no hidden APIs, nothing that fakes quality.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Saffron),
        )
    }
}

/**
 * A vertical fader. Material3's Slider is horizontal only, and faking it with
 * rotation breaks layout on some phones — a fader is just a track, a fill and a
 * thumb, so it is drawn directly and driven by drag/tap.
 */
@Composable
private fun Fader(
    label: String,
    gainDb: Float,
    extraDb: Float,
    onChange: (Float) -> Unit,
) {
    val range = Bands.MAX_DB - Bands.MIN_DB
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(34.dp),
    ) {
        Text(
            if (gainDb > 0.05f) "+%.0f".format(gainDb) else "%.0f".format(gainDb),
            style = MaterialTheme.typography.labelSmall,
            color = when {
                gainDb > 0.05f -> Saffron
                gainDb < -0.05f -> Teal
                else -> TextSecondary
            },
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        val trackColor = OutlineC
        Box(
            Modifier
                .width(30.dp)
                .height(150.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val ratio = (offset.y / size.height).coerceIn(0f, 1f)
                        onChange(Bands.MAX_DB - ratio * range)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val ratio = (offset.y / size.height).coerceIn(0f, 1f)
                            onChange(Bands.MAX_DB - ratio * range)
                        },
                    ) { change, _ ->
                        val ratio = (change.position.y / size.height).coerceIn(0f, 1f)
                        onChange(Bands.MAX_DB - ratio * range)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val trackWidth = 8f
                val top = 6f
                val bottom = size.height - 6f
                val usable = bottom - top

                // track
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(centerX - trackWidth / 2f, top),
                    size = Size(trackWidth, usable),
                    cornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f),
                )

                // zero line
                val zeroY = top + usable * (Bands.MAX_DB / range)
                drawRect(
                    color = OutlineC.copy(alpha = 0.9f),
                    topLeft = Offset(centerX - 14f, zeroY),
                    size = Size(28f, 1.5f),
                )

                // gain fill from zero to the thumb
                val thumbY = top + usable * ((Bands.MAX_DB - gainDb) / range)
                val fillTop = minOf(zeroY, thumbY)
                val fillHeight = kotlin.math.abs(thumbY - zeroY)
                if (fillHeight > 1f) {
                    drawRoundRect(
                        color = if (gainDb >= 0f) Saffron else Teal,
                        topLeft = Offset(centerX - trackWidth / 2f, fillTop),
                        size = Size(trackWidth, fillHeight),
                        cornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f),
                    )
                }

                // thumb
                drawCircle(
                    color = TextPrimary,
                    radius = 11f,
                    center = Offset(centerX, thumbY),
                )
                drawCircle(
                    color = if (gainDb >= 0f) Saffron else Teal,
                    radius = 7f,
                    center = Offset(centerX, thumbY),
                )

                // the noise-trim band, shown as a faint marker so the effect is visible
                if (kotlin.math.abs(extraDb) > 0.2f) {
                    val extraY = top + usable * ((Bands.MAX_DB - (gainDb + extraDb)) / range)
                    drawRect(
                        color = Teal.copy(alpha = 0.5f),
                        topLeft = Offset(centerX - 12f, extraY),
                        size = Size(24f, 2f),
                    )
                }
            }
        }

        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

// ----------------------------------------------------------------------- more

@Composable
fun MoreScreen(vm: MainViewModel, state: UiState) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.padding(top = 16.dp)) {
                Text(
                    "More",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Sleep timer, your own stations, and about this app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }

        // Sleep timer
        item {
            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Timer, contentDescription = null, tint = Saffron)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Sleep timer",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.weight(1f))
                        if (state.sleepMinutesLeft > 0) {
                            Text(
                                "${state.sleepMinutesLeft} min left",
                                style = MaterialTheme.typography.labelMedium,
                                color = Gold,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(0, 15, 30, 60, 120).forEach { minutes ->
                            Chip(
                                label = if (minutes == 0) "Off" else "$minutes min",
                                selected = false,
                                onClick = { vm.startSleepTimer(minutes) },
                            )
                        }
                    }
                    Text(
                        "Handy for falling asleep to a station — playback pauses on its own.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }
        }

        // My stations
        item {
            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = Saffron)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "My stations",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        "Add any stream you like. Stream links usually end in .mp3, .m3u8 or " +
                            "/stream — copy one from a station's website.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Station name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors(),
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Stream link (http:// or https://)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconChip(Icons.Filled.Add, "Add station") {
                            vm.addStation(name, url)
                            name = ""
                            url = ""
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${state.stations.count { it.isCustom }} saved",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                    }
                    state.stations.filter { it.isCustom }.forEach { station ->
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                station.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Remove",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { vm.removeStation(station) },
                            )
                        }
                    }
                }
            }
        }

        // About
        item {
            PanelCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Ramesh Radio",
                        style = MaterialTheme.typography.titleLarge,
                        color = Saffron,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Version 1.1 • Tamil FM, world news, home-screen widget and a 10-band equalizer",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Built by Ramesh prathap .R",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "What is inside: thousands of Tamil FM stations from India, Sri Lanka, " +
                            "Malaysia and Singapore plus the Tamil diaspora, Tamil and English news " +
                            "stations from around the world, a local file player, favourites, " +
                            "recently played, a sleep timer, and a 10-band equalizer with Clear " +
                            "sound, rumble cut and hiss cut.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Station lists come from the free, open Radio-Browser directory. The app " +
                            "ships a list so it works offline-on-first-launch, and can search the " +
                            "live directory any time. Directory entries are community maintained: " +
                            "a station can go offline without notice, and a few links are " +
                            "unchecked — those are marked. No ads, no account, no tracking; " +
                            "nothing leaves the phone except the stream you chose and the " +
                            "directory search you asked for.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        IconChip(Icons.Filled.Share, "Share app") { vm.shareApp() }
                        Spacer(Modifier.width(8.dp))
                        IconChip(Icons.Filled.Info, "Source code", tint = Teal) { vm.openRepo() }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

// ------------------------------------------------------------ now playing sheet

@Composable
fun NowPlayingSheet(vm: MainViewModel, state: UiState) {
    val playback by PlayerBus.state.collectAsState()
    val station = state.nowPlaying

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(18.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { vm.openNowPlaying(false) },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (playback.isBuffering) "Connecting…" else if (playback.isPlaying) "Playing" else "Paused",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (playback.isPlaying) Good else TextSecondary,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(24.dp))

            if (station != null) {
                StationLogo(station, 120.dp, Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(16.dp))
                Text(
                    station.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    listOf(station.place, station.language, station.qualityLabel)
                        .filter { it.isNotBlank() }
                        .joinToString(" • "),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (station.category.isNotBlank()) {
                        Pill(com.universalrp.tamilnadufm.data.Category.label(station.category), Saffron)
                    }
                    if (station.bitrate > 0) {
                        Spacer(Modifier.width(8.dp))
                        Pill("${station.bitrate} kbps", Teal)
                    }
                    if (station.codec.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Pill(station.codec.uppercase(), Gold)
                    }
                }
            } else {
                Text(
                    state.playingLocalTitle ?: "Nothing playing",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Playing from your phone's storage",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(29.dp))
                        .background(Saffron)
                        .clickable { vm.togglePlayPause() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF2A1200),
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(SurfaceHigh)
                        .clickable { vm.stop() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = TextSecondary)
                }
            }

            Spacer(Modifier.height(18.dp))

            if (station != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    val isFavourite = state.favourites.contains(station.url.lowercase())
                    IconChip(
                        icon = if (isFavourite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        label = if (isFavourite) "Favourite" else "Add favourite",
                        tint = Gold,
                    ) { vm.toggleFavourite(station) }
                    Spacer(Modifier.width(10.dp))
                    IconChip(Icons.Filled.Share, "Share", tint = Teal) { vm.shareStation(station) }
                }
            }

            Spacer(Modifier.height(18.dp))

            if (playback.error != null || state.message != null) {
                PanelCard(Modifier.fillMaxWidth(), color = SurfaceHigh) {
                    Text(
                        state.message ?: playback.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Warn,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Saffron,
    unfocusedBorderColor = OutlineC,
    focusedLabelColor = Saffron,
    unfocusedLabelColor = TextSecondary,
    cursorColor = Saffron,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = SurfaceHigh,
    unfocusedContainerColor = SurfaceHigh,
)
