package com.universalrp.tamilnadufm

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.universalrp.tamilnadufm.audio.AudioFx
import com.universalrp.tamilnadufm.audio.Bands
import com.universalrp.tamilnadufm.audio.EqPreset
import com.universalrp.tamilnadufm.audio.EqPresets
import com.universalrp.tamilnadufm.data.Category
import com.universalrp.tamilnadufm.data.DirectoryClient
import com.universalrp.tamilnadufm.data.LocalTrack
import com.universalrp.tamilnadufm.data.RadioStation
import com.universalrp.tamilnadufm.data.StationRepository
import com.universalrp.tamilnadufm.player.PlayerBus
import com.universalrp.tamilnadufm.player.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab { RADIO, NEWS, LOCAL, EQUALIZER, MORE }

data class UiState(
    val tab: Tab = Tab.RADIO,
    val query: String = "",
    /** v1.2: the app opens on தமிழ், the user's own language — not on "All channels". */
    val category: String = Category.TAMIL,
    val stations: List<RadioStation> = emptyList(),
    val visible: List<RadioStation> = emptyList(),
    val favourites: Set<String> = emptySet(),
    val nowPlaying: RadioStation? = null,
    val playingLocalTitle: String? = null,
    val showNowPlaying: Boolean = false,
    val directoryBusy: Boolean = false,
    val directoryResults: List<RadioStation> = emptyList(),
    val showDirectory: Boolean = false,
    // local files
    val localTracks: List<LocalTrack> = emptyList(),
    val localPermission: Boolean = false,
    val localFolder: String = "",
    val localLoading: Boolean = false,
    // equalizer
    val eq: AudioFx.Settings = AudioFx.Settings(),
    val eqEngine: String = "",
    // sleep timer
    val sleepMinutesLeft: Int = 0,
    // misc
    val message: String? = null,
    val busy: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context = app.applicationContext
    private val repo = StationRepository(ctx)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var controller: MediaController? = null
    private var sleepJob: Job? = null
    private var folderJob: Job? = null

    /**
     * v1.3: the station lists' scroll positions live here. The Radio screen leaves the
     * composition on every tab switch, which used to drop the list back to the first
     * station ("playing the 100th channel, opened the equalizer, came back to the top").
     * The ViewModel outlives the tab switch (and a rotation), so the position survives.
     */
    val radioListState = LazyListState()
    val newsListState = LazyListState()

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncNowPlaying(mediaItem)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncNowPlaying(controller?.currentMediaItem)
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            mutate {
                it.copy(
                    message = if (controller?.currentMediaItem?.mediaId?.startsWith("content:") == true)
                        "Cannot play this audio file. It may have moved, access may have expired, or its format is unsupported. Choose the folder/file again."
                    else "That station did not respond. It may be offline right now — try another, or check your connection.",
                    busy = false,
                )
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                mutate { it.copy(busy = false) }
            }
        }
    }

    init {
        AudioFx.load(ctx)
        mutate { it.copy(eq = AudioFx.settings, eqEngine = AudioFx.engineName) }
        loadStations()
        ctx.getSharedPreferences("music_folder", Context.MODE_PRIVATE).getString("uri", null)?.let { loadMusicFolder(Uri.parse(it), false) }
        viewModelScope.launch {
            // Keep the EQ screen honest about the engine and the sleep timer ticking.
            while (true) {
                delay(1000)
                val engine = AudioFx.engineName
                val sleep = _state.value.sleepMinutesLeft
                mutate {
                    it.copy(
                        eqEngine = engine,
                        eq = AudioFx.settings,
                        sleepMinutesLeft = if (sleep > 0 && sleepJob?.isActive == true) sleep else 0,
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------- controller

    fun attachController(controller: MediaController) {
        this.controller = controller
        controller.addListener(playerListener)
        syncNowPlaying(controller.currentMediaItem)
        mutate { it.copy(busy = controller.playbackState == Player.STATE_BUFFERING) }
    }

    fun detachController(controller: MediaController) {
        controller.removeListener(playerListener)
        this.controller = null
    }

    private fun syncNowPlaying(mediaItem: MediaItem?) {
        val id = mediaItem?.mediaId
        val local = mediaItem?.mediaMetadata?.extras?.getString(EXTRA_LOCAL_TITLE)
        // v1.3: directory hits are not in the shipped list, so a plain lookup used to clear
        // the transport bar a moment after playback started. Fall back to the last-played
        // record (markPlayed saved it) and then to the stream's own metadata title.
        val station = _state.value.stations.firstOrNull { it.url == id || it.id == id }
            ?: lastPlayed()?.takeIf { it.url == id }
            ?: if (id != null && (id.startsWith("http://") || id.startsWith("https://"))) {
                RadioStation(
                    id = id,
                    name = mediaItem?.mediaMetadata?.title?.toString()
                        ?.takeIf { title -> title.isNotBlank() } ?: "Live stream",
                    url = id,
                )
            } else {
                null
            }
        mutate {
            it.copy(
                nowPlaying = station,
                playingLocalTitle = if (station == null) local else null,
            )
        }
    }

    // --------------------------------------------------------------- stations

    private fun loadStations() {
        viewModelScope.launch {
            val seeds = repo.seedStations()
            val custom = repo.customStations()
            val recent = repo.recent().mapNotNull { url -> seeds.firstOrNull { it.url.lowercase() == url } }
            val all = (custom + seeds)
                .distinctBy { it.url.lowercase() }
                .sortedWith(
                    compareByDescending<RadioStation> { it.verified }
                        .thenByDescending { it.votes }
                        .thenBy { it.name.lowercase() }
                )
            mutate {
                it.copy(
                    stations = all,
                    favourites = repo.favourites(),
                    directoryResults = recent,
                )
            }
            applyFilter()
        }
    }

    fun setQuery(text: String) {
        mutate { it.copy(query = text) }
        applyFilter()
    }

    fun setCategory(category: String) {
        mutate { it.copy(category = category) }
        applyFilter()
    }

    fun toggleFavourite(station: RadioStation) {
        val added = repo.toggleFavourite(station)
        mutate {
            it.copy(
                favourites = repo.favourites(),
                message = if (added) "${station.name} added to favourites" else "${station.name} removed",
            )
        }
        applyFilter()
    }

    fun addStation(name: String, url: String) {
        if (name.isBlank() || !url.startsWith("http")) {
            mutate { it.copy(message = "Enter a name and a stream link starting with http") }
            return
        }
        repo.addCustom(
            RadioStation(
                id = "custom-" + url.hashCode(),
                name = name.trim(),
                url = url.trim(),
                category = Category.CUSTOM,
                isCustom = true,
                country = "My station",
            )
        )
        mutate { it.copy(message = "Station added to My stations") }
        loadStations()
    }

    fun removeStation(station: RadioStation) {
        if (!station.isCustom) return
        repo.removeCustom(station.url)
        mutate { it.copy(message = "${station.name} removed") }
        loadStations()
    }

    private fun applyFilter() {
        val s = _state.value
        val query = s.query.trim().lowercase()
        val list = s.stations.filter { station ->
            val matchesQuery = query.isEmpty() ||
                station.name.lowercase().contains(query) ||
                station.tags.lowercase().contains(query) ||
                station.country.lowercase().contains(query) ||
                station.language.lowercase().contains(query)

            val matchesCategory = when (s.category) {
                Category.ALL -> true
                Category.FAVOURITES -> s.favourites.contains(station.url.lowercase())
                Category.CUSTOM -> station.isCustom
                Category.RECENT -> repo.recent().contains(station.url.lowercase())
                Category.TOWNS -> Category.isTamilTown(station)
                Category.TAMIL -> station.language.lowercase().contains("tamil") ||
                    station.category == Category.TAMIL ||
                    station.category == Category.TAMIL_FM ||
                    station.category == Category.TAMIL_DEVOTIONAL
                Category.ALL_NEWS -> Category.isNews(station.category)
                Category.TAMIL_NEWS -> station.category == Category.TAMIL_NEWS
                Category.WORLD_NEWS -> station.category == Category.WORLD_NEWS
                Category.INDIA_NEWS -> station.category == Category.INDIA_NEWS
                Category.TAMIL_FM -> station.category == Category.TAMIL_FM
                Category.TAMIL_DEVOTIONAL -> station.category == Category.TAMIL_DEVOTIONAL
                Category.ENGLISH -> station.category == Category.ENGLISH ||
                    station.language.lowercase().contains("english")
                else -> station.category == s.category
            }
            matchesQuery && matchesCategory
        }
        mutate { it.copy(visible = list) }
    }

    // -------------------------------------------------------------- directory

    /** Live search against the open directory — thousands of stations. */
    fun searchDirectory() {
        val query = _state.value.query.trim()
        mutate { it.copy(directoryBusy = true, showDirectory = true) }
        viewModelScope.launch {
            val results = runCatching {
                if (query.isBlank()) DirectoryClient.popularTamil()
                else DirectoryClient.search(query)
            }.getOrDefault(emptyList())
            mutate {
                it.copy(
                    directoryBusy = false,
                    directoryResults = results,
                    message = if (results.isEmpty())
                        "No stations came back. Check your connection and try again."
                    else
                        "Found ${results.size} stations in the directory",
                )
            }
        }
    }

    fun discoverNews(language: String) {
        mutate { it.copy(directoryBusy = true, showDirectory = true) }
        viewModelScope.launch {
            val results = runCatching { DirectoryClient.news(language) }.getOrDefault(emptyList())
            mutate {
                it.copy(
                    directoryBusy = false,
                    directoryResults = results,
                    message = "Found ${results.size} news stations",
                )
            }
        }
    }

    fun closeDirectory() = mutate { it.copy(showDirectory = false) }

    /** Adds a directory hit to "My stations" so it survives between sessions. */
    fun saveDirectoryStation(station: RadioStation) {
        repo.addCustom(station.copy(isCustom = true, category = Category.CUSTOM))
        mutate { it.copy(message = "${station.name} saved to My stations") }
        loadStations()
    }

    // ---------------------------------------------------------------- playback

    /**
     * Plays a station — and queues the whole visible list behind it, so Next/Previous work
     * identically in the app, the widget, the notification, the lock screen and a headset.
     * v1.3: the old build loaded a single item, which left the widget's next/previous (and
     * the notification's) with nowhere to go. The queue is also saved for the service, so
     * next/previous keep working with the app closed.
     *
     * @param queueOverride the list to queue instead of the visible one (the directory
     *   results when playing from the directory sheet).
     */
    fun play(station: RadioStation, queueOverride: List<RadioStation>? = null) {
        PlayerBus.ensureService(ctx)
        val c = controller ?: run {
            mutate { it.copy(message = "Player is still starting — tap again in a second.") }
            return
        }
        if (c.currentMediaItem?.mediaId == station.url) {
            if (c.isPlaying) c.pause() else resumePlayer(c)
        } else {
            PlayerBus.clearError()
            val source = queueOverride ?: _state.value.visible
            val queue = if (source.any { it.url == station.url }) source else listOf(station)
            val index = queue.indexOfFirst { it.url == station.url }.coerceAtLeast(0)
            c.setMediaItems(queue.map { toMediaItem(it) }, index, 0L)
            c.prepare()
            c.play()
            repo.saveQueue(queue.map { it.url }, queue.map { it.name }, index)
            repo.markPlayed(station)
        }
        mutate { it.copy(nowPlaying = station, busy = c.playbackState == Player.STATE_BUFFERING) }
    }

    /** Plays a directory hit with the directory results as its next/previous queue. */
    fun playDirectory(station: RadioStation) {
        val results = _state.value.directoryResults
        play(station, queueOverride = results.takeIf { it.isNotEmpty() })
        closeDirectory()
    }

    fun playLocal(track: LocalTrack) {
        PlayerBus.clearError()
        PlayerBus.ensureService(ctx)
        val c = controller ?: return
        // The phone's own tracks queue up the same way stations do: next/previous walk the
        // local list from here, in the widget and the notification too.
        val tracks = _state.value.localTracks
        val index = tracks.indexOfFirst { it.uri == track.uri }
        if (index >= 0) {
            c.setMediaItems(tracks.map { toLocalItem(it) }, index, 0L)
            repo.saveQueue(
                tracks.map { it.uri.toString() },
                tracks.map { it.title },
                index,
            )
        } else {
            c.setMediaItem(toLocalItem(track))
            repo.saveQueue(listOf(track.uri.toString()), listOf(track.title), 0)
        }
        c.prepare()
        c.play()
        mutate { it.copy(playingLocalTitle = track.title, nowPlaying = null, busy = true) }
    }

    private fun resumePlayer(c: MediaController) {
        if (c.mediaItemCount == 0) { resumeLast(); return }
        val uri = c.currentMediaItem?.localConfiguration?.uri ?: c.currentMediaItem?.mediaId?.let { Uri.parse(it) }
        if (uri?.scheme in listOf("http", "https")) {
            // A radio stream must reconnect to NOW, not an hours-old buffered connection.
            c.stop()
            c.seekToDefaultPosition()
            c.prepare()
        } else if (c.playbackState == Player.STATE_IDLE || c.playbackState == Player.STATE_ENDED || c.playerError != null) {
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.prepare()
        }
        PlayerBus.clearError()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: run { mutate { it.copy(message = "Player is connecting. Please try again shortly.") }; return }
        if (c.isPlaying) c.pause() else resumePlayer(c)
    }

    fun stop() {
        val c = controller ?: return
        c.pause()
        c.stop()
        // Keep the active local/radio queue so Play can prepare it again.
        mutate { it.copy(busy = false) }
    }

    fun resumeLast() {
        val prefs = ctx.getSharedPreferences("last_session", Context.MODE_PRIVATE)
        val uri = prefs.getString("uri", null)
        if (uri != null && (uri.startsWith("content:") || uri.startsWith("file:"))) {
            val track = _state.value.localTracks.firstOrNull { it.uri.toString() == uri }
                ?: LocalTrack(Uri.parse(uri), prefs.getString("title", "Audio file") ?: "Audio file", "", 0)
            playLocal(track)
            return
        }
        val last = repo.lastPlayed() ?: return
        play(last)
    }

    fun lastPlayed(): RadioStation? = repo.lastPlayed()

    fun next() = stepQueue(1)

    fun previous() = stepQueue(-1)

    private fun stepQueue(direction: Int) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { resumeLast(); return }
        val index = (c.currentMediaItemIndex + direction).mod(c.mediaItemCount)
        c.seekTo(index, 0L)
        if (c.playbackState == Player.STATE_IDLE || c.playerError != null) c.prepare()
        c.play()
    }

    private fun toMediaItem(station: RadioStation): MediaItem =
        MediaItem.Builder()
            .setUri(station.url)
            .setMediaId(station.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setArtist(
                        listOf(station.subtitle, "Ramesh Radio")
                            .firstOrNull { it.isNotBlank() }
                    )
                    .setArtworkUri(station.favicon.takeIf { it.startsWith("http") }?.let { Uri.parse(it) })
                    .build()
            )
            .build()

    private fun toLocalItem(track: LocalTrack): MediaItem =
        MediaItem.Builder()
            .setUri(track.uri)
            .setMediaId(track.uri.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setExtras(android.os.Bundle().apply {
                        putString(EXTRA_LOCAL_TITLE, track.title)
                    })
                    .build()
            )
            .build()

    fun openNowPlaying(open: Boolean) = mutate { it.copy(showNowPlaying = open) }

    // ------------------------------------------------------------ local files

    fun loadMusicFolder(uri: Uri, persist: Boolean = true) {
        folderJob?.cancel()
        folderJob = viewModelScope.launch {
            mutate { it.copy(localLoading = true) }
            try {
                if (persist) ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val tracks = withContext(Dispatchers.IO) { com.universalrp.tamilnadufm.data.MusicFolder.read(ctx, uri) }
                ctx.getSharedPreferences("music_folder", Context.MODE_PRIVATE).edit().putString("uri", uri.toString()).apply()
                mutate { it.copy(localTracks = tracks, localFolder = uri.toString(), localLoading = false,
                    message = if (tracks.isEmpty()) "No audio files in this folder. Choose the folder containing your songs." else "${tracks.size} songs loaded from your folder") }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                mutate { it.copy(localLoading = false, message = "Cannot read music folder. Select it again to grant access.") }
            }
        }
    }

    fun refreshMusicFolder() {
        val uri = _state.value.localFolder
        if (uri.isNotBlank()) loadMusicFolder(Uri.parse(uri), false)
    }

    fun loadLocalTracks() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { queryLocalAudio() }
            mutate { it.copy(localTracks = list, localPermission = list.isNotEmpty()) }
        }
    }

    private fun queryLocalAudio(): List<LocalTrack> {
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val tracks = ArrayList<LocalTrack>()
        try {
            ctx.contentResolver.query(
                collection, projection, selection, null,
                "${MediaStore.Audio.Media.TITLE} ASC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    tracks.add(
                        LocalTrack(
                            uri = android.content.ContentUris.withAppendedId(collection, id),
                            title = cursor.getString(titleColumn) ?: "Unknown track",
                            artist = cursor.getString(artistColumn) ?: "",
                            durationMs = cursor.getLong(durationColumn),
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            // No permission yet or no media store — the screen offers the file picker.
        }
        return tracks
    }

    /** Plays a file the user picked through the system file picker. */
    fun playPickedFile(uri: Uri) {
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        playLocal(LocalTrack(uri = uri, title = uri.lastPathSegment ?: "Audio file", artist = "", durationMs = 0))
    }

    // -------------------------------------------------------------- equalizer

    fun setBalance(value: Float) {
        AudioFx.setBalance(value)
        mutate { it.copy(eq = AudioFx.settings) }
    }

    fun setEqEnabled(on: Boolean) {
        AudioFx.setEnabled(on)
        mutate { it.copy(eq = AudioFx.settings) }
    }

    fun setBand(index: Int, gainDb: Float) {
        AudioFx.setBand(index, gainDb)
        mutate { it.copy(eq = AudioFx.settings) }
    }

    fun applyPreset(preset: EqPreset) {
        AudioFx.applyPreset(preset)
        mutate { it.copy(eq = AudioFx.settings, message = "${preset.name} applied") }
    }

    fun resetEq() {
        AudioFx.resetFlat()
        mutate { it.copy(eq = AudioFx.settings) }
    }

    fun setPreamp(db: Float) {
        AudioFx.setPreamp(db)
        mutate { it.copy(eq = AudioFx.settings) }
    }

    fun setClearSound(on: Boolean) {
        AudioFx.setClearSound(on)
        mutate { it.copy(eq = AudioFx.settings) }
    }

    fun setRumbleCut(on: Boolean) {
        AudioFx.setRumbleCut(on)
        mutate { it.copy(eq = AudioFx.settings) }
    }

    fun setHissCut(on: Boolean) {
        AudioFx.setHissCut(on)
        mutate { it.copy(eq = AudioFx.settings) }
    }

    fun setBoost(db: Float) {
        AudioFx.setBoost(db)
        mutate { it.copy(eq = AudioFx.settings) }
    }

    fun bandLabel(index: Int): String = Bands.LABELS.getOrElse(index) { "" }

    fun presets(): List<EqPreset> = EqPresets.ALL

    // ------------------------------------------------------------ sleep timer

    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            mutate { it.copy(sleepMinutesLeft = 0, message = "Sleep timer off") }
            return
        }
        mutate { it.copy(sleepMinutesLeft = minutes, message = "Sleep timer set for $minutes minutes") }
        sleepJob = viewModelScope.launch {
            var left = minutes
            while (left > 0) {
                delay(60_000)
                left -= 1
                mutate { it.copy(sleepMinutesLeft = left) }
            }
            controller?.pause()
            mutate { it.copy(message = "Sleep timer finished — playback paused") }
        }
    }

    // ------------------------------------------------------------------- misc

    fun selectTab(tab: Tab) = mutate { it.copy(tab = tab) }

    fun dismissMessage() = mutate { it.copy(message = null) }

    fun shareStation(station: RadioStation) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, station.name)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "${station.name}\n${station.url}\n\nShared from Ramesh Radio",
                )
            }
            ctx.startActivity(
                Intent.createChooser(intent, "Share station").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (t: Throwable) {
            mutate { it.copy(message = "Nothing available to share with.") }
        }
    }

    fun openAppSettings() {
        PlayerBus.ensureService(ctx)
        mutate { it.copy(message = "Playback runs in the background — you can close the app.") }
    }

    /** Plain-text share of the app itself (no link needed — it is sideloaded). */
    fun shareApp() {
        val text = "Ramesh Radio — Tamil FM stations, Tamil and world news radio, " +
            "a local file player and a 10-band equalizer with Clear sound. " +
            "Built by Ramesh prathap .R."
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Ramesh Radio")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            ctx.startActivity(
                Intent.createChooser(intent, "Share app").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (t: Throwable) {
            mutate { it.copy(message = "Nothing available to share with.") }
        }
    }

    fun openRepo() {
        try {
            ctx.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/universalrp2003/InternetRadio"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (t: Throwable) {
            mutate { it.copy(message = "No browser available to open the link.") }
        }
    }

    override fun onCleared() {
        controller?.removeListener(playerListener)
        super.onCleared()
    }

    private fun mutate(block: (UiState) -> UiState) = _state.update(block)

    companion object {
        const val EXTRA_LOCAL_TITLE = "local_title"
        val SERVICE_CLASS = PlaybackService::class.java
    }
}
