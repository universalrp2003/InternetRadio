package com.universalrp.tamilnadufm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.universalrp.tamilnadufm.MainViewModel
import com.universalrp.tamilnadufm.UiState
import com.universalrp.tamilnadufm.data.Category
import com.universalrp.tamilnadufm.data.RadioStation

// --------------------------------------------------------------------- radio

@Composable
fun RadioScreen(vm: MainViewModel, state: UiState) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(Modifier.padding(top = 10.dp)) {
                Text(
                    "Ramesh Radio",
                    style = MaterialTheme.typography.titleLarge,
                    color = Saffron,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Tamil FM from Tamil Nadu and around the world — live.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }

        item { SearchBar(vm, state, onSearchDirectory = { vm.searchDirectory() }) }

        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val chips = listOf(
                    Category.ALL,
                    Category.FAVOURITES,
                    Category.RECENT,
                    Category.TOWNS,
                    Category.TAMIL,
                    Category.TAMIL_FM,
                    Category.TAMIL_DEVOTIONAL,
                    Category.TAMIL_NEWS,
                    Category.ENGLISH,
                    Category.CUSTOM,
                )
                chips.forEach { id ->
                    Chip(
                        label = Category.label(id),
                        selected = state.category == id,
                        onClick = { vm.setCategory(id) },
                    )
                }
            }
        }

        // Continue where you left off
        val last = vm.lastPlayed()
        if (last != null && state.query.isBlank() && state.category == Category.ALL) {
            item {
                PanelCard(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.play(last) },
                    color = SurfaceHigh,
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Saffron,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Continue listening",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                            Text(
                                last.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(
                    if (state.query.isBlank()) "${state.visible.size} stations"
                    else "${state.visible.size} matches",
                    modifier = Modifier.weight(1f),
                )
                IconChip(Icons.Filled.Language, "Live directory") { vm.searchDirectory() }
            }
        }

        if (state.visible.isEmpty()) {
            item {
                PanelCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Nothing here yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Try another category, clear the search box, or look in the live " +
                                "directory — it has far more stations than the shipped list, " +
                                "including the town and city stations.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        IconChip(Icons.Filled.Refresh, "Search the directory") {
                            vm.searchDirectory()
                        }
                    }
                }
            }
        }

        items(state.visible, key = { it.url }) { station ->
            StationRow(
                station = station,
                playing = state.nowPlaying?.url == station.url,
                favourite = state.favourites.contains(station.url.lowercase()),
                onPlay = { vm.play(station) },
                onFavourite = { vm.toggleFavourite(station) },
                onShare = { vm.shareStation(station) },
                onSave = { vm.saveDirectoryStation(station) },
                onDelete = { vm.removeStation(station) },
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun SearchBar(vm: MainViewModel, state: UiState, onSearchDirectory: () -> Unit) {
    OutlinedTextField(
        value = state.query,
        onValueChange = { vm.setQuery(it) },
        placeholder = { Text("Station, city or language", color = TextSecondary) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary) },
        trailingIcon = {
            Row(Modifier.padding(end = 6.dp)) {
                IconChip(Icons.Filled.Language, "Directory", tint = Teal) { onSearchDirectory() }
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Saffron,
            unfocusedBorderColor = OutlineC,
            focusedContainerColor = SurfaceHigh,
            unfocusedContainerColor = SurfaceHigh,
            cursorColor = Saffron,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
        ),
    )
}

/** Shared station row: logo, name, meta pills, favourite star and a play tap. */
@Composable
fun StationRow(
    station: RadioStation,
    playing: Boolean,
    favourite: Boolean,
    onPlay: () -> Unit,
    onFavourite: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    PanelCard(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay),
        color = if (playing) SurfaceHigh else SurfaceC,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StationLogo(station, 46.dp)

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (playing) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Saffron)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        station.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (playing) Saffron else TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!station.verified) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Warn,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        station.subtitle.ifBlank { "Live stream" },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            IconChip(
                icon = Icons.Filled.Share,
                label = "",
                tint = TextSecondary,
            ) {
                onShare()
            }

            Spacer(Modifier.width(6.dp))

            if (station.isCustom) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Remove",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(onClick = onDelete),
                )
                Spacer(Modifier.width(6.dp))
            }

            Icon(
                if (favourite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (favourite) "Remove favourite" else "Add favourite",
                tint = if (favourite) Gold else TextSecondary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onFavourite),
            )
        }
    }
}

// ---------------------------------------------------------------------- news

@Composable
fun NewsScreen(vm: MainViewModel, state: UiState) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(Modifier.padding(top = 16.dp)) {
                Text(
                    "News radio",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "World news in English and Tamil news, around the clock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }

        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val chips = listOf(
                    Category.ALL_NEWS,
                    Category.TAMIL_NEWS,
                    Category.WORLD_NEWS,
                    Category.INDIA_NEWS,
                    Category.ENGLISH,
                )
                chips.forEach { id ->
                    Chip(
                        label = Category.label(id),
                        selected = state.category == id,
                        onClick = { vm.setCategory(id) },
                    )
                }
            }
        }

        item {
            PanelCard(Modifier.fillMaxWidth(), color = SurfaceHigh) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "Live news stations",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "The shipped list has world and English news channels. Ask the directory " +
                            "for more — it is community maintained and much larger than any list " +
                            "an app can carry.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconChip(Icons.Filled.Refresh, "World news (English)") {
                            vm.setCategory(Category.WORLD_NEWS)
                            vm.discoverNews("english")
                        }
                        IconChip(Icons.Filled.Refresh, "Tamil news", tint = Teal) {
                            vm.setCategory(Category.TAMIL_NEWS)
                            vm.discoverNews("tamil")
                        }
                        IconChip(Icons.Filled.Refresh, "Indian news", tint = Gold) {
                            vm.setCategory(Category.INDIA_NEWS)
                            vm.discoverNews("hindi")
                        }
                    }
                }
            }
        }

        item {
            SectionTitle("${state.visible.size} stations in this list")
        }

        if (state.visible.isEmpty()) {
            item {
                PanelCard(Modifier.fillMaxWidth()) {
                    Text(
                        "No news stations in this category yet. Tap one of the refresh buttons " +
                            "above to pull live results from the directory.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }

        items(state.visible, key = { it.url }) { station ->
            StationRow(
                station = station,
                playing = state.nowPlaying?.url == station.url,
                favourite = state.favourites.contains(station.url.lowercase()),
                onPlay = { vm.play(station) },
                onFavourite = { vm.toggleFavourite(station) },
                onShare = { vm.shareStation(station) },
                onSave = { vm.saveDirectoryStation(station) },
                onDelete = { vm.removeStation(station) },
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ----------------------------------------------------------- directory sheet

@Composable
fun DirectorySheet(vm: MainViewModel, state: UiState) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Bg),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Live directory",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "radio-browser.info • community run, no account needed",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { vm.closeDirectory() },
                )
            }

            if (state.directoryBusy) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Saffron)
                }
            }

            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.directoryResults, key = { it.url }) { station ->
                    PanelCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StationLogo(station, 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    station.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    station.subtitle.ifBlank { "Live stream" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconChip(Icons.Filled.PlayArrow, "Play", tint = Saffron) {
                                vm.play(station)
                                vm.closeDirectory()
                            }
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Save",
                                tint = Teal,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable { vm.saveDirectoryStation(station) },
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}
