package com.universalrp.tamilnadufm.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.universalrp.tamilnadufm.audio.AudioFx
import com.universalrp.tamilnadufm.widget.RadioWidgetProvider

/**
 * Owns the player so sound keeps coming when the screen is off or the app is
 * closed. Media3 shows the notification and lock-screen controls automatically.
 *
 * This is also where the equalizer attaches: the effects hang off the player's own
 * audio session, which is why the 10-band EQ applies to both the online stations
 * and the local files the app plays.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var pausedAt = 0L
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val stalled = Runnable {
        player?.let { p ->
            if (p.playWhenReady && p.playbackState == Player.STATE_BUFFERING) {
                p.stop()
                PlayerBus.reportError("The stream did not start within 30 seconds. Check the connection and tap Play to retry, or choose another station.")
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val p = player ?: return
            if (!playWhenReady) { pausedAt = android.os.SystemClock.elapsedRealtime(); return }
            val reconnect = pausedAt > 0 && android.os.SystemClock.elapsedRealtime() - pausedAt > 60000 &&
                p.currentMediaItem?.mediaId?.startsWith("http") == true
            pausedAt = 0
            if (reconnect) { p.stop(); p.seekToDefaultPosition() }
            if (p.mediaItemCount > 0 && (reconnect || p.playbackState == Player.STATE_IDLE || p.playerError != null)) {
                PlayerBus.clearError()
                p.prepare()
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            AudioFx.load(this@PlaybackService)
            AudioFx.attach(audioSessionId)
        }

        override fun onPlayerError(error: PlaybackException) {
            // Keep it for the UI to show; the station itself stays selected.
            PlayerBus.reportError(
                error.cause?.message ?: error.message ?: "Playback failed"
            )
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            PlayerBus.reportPlaying(isPlaying)
            publishWidgetState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            publishWidgetState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            PlayerBus.reportState(playbackState)
            handler.removeCallbacks(stalled)
            if (playbackState == Player.STATE_BUFFERING) handler.postDelayed(stalled, 30000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        AudioFx.load(this)

        val loadControl = DefaultLoadControl.Builder()
            // Radio-friendly buffering: quick to start, generous against dropouts.
            .setBufferDurationsMs(20_000, 45_000, 1_500, 3_000)
            .build()

        val renderers = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: android.content.Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): androidx.media3.exoplayer.audio.AudioSink =
                androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(false)
                    .setAudioProcessors(arrayOf(com.universalrp.tamilnadufm.audio.BalanceProcessor()))
                    .build()
        }
        val http = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(12000).setReadTimeoutMs(12000).setAllowCrossProtocolRedirects(true)
        val sources = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
            androidx.media3.datasource.DefaultDataSource.Factory(this, http))
        val exo = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(sources)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_NETWORK)  // keep the stream alive with the screen off
            .setLoadControl(loadControl)
            .build()

        exo.addListener(listener)
        player = exo

        // If a session already exists (player reused), attach straight away.
        val sessionId = exo.audioSessionId
        if (sessionId != C.AUDIO_SESSION_ID_UNSET) AudioFx.attach(sessionId)

        mediaSession = MediaSession.Builder(this, exo).build()
        PlayerBus.attach(exo)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /**
     * The home-screen widget drives playback through this service, so the widget
     * keeps working with the app closed. Anything that is not one of our widget
     * actions is left to MediaSessionService (media buttons, session commands).
     */
    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            RadioWidgetProvider.ACTION_TOGGLE -> {
                val exo = player
                when {
                    exo == null -> Unit
                    exo.mediaItemCount == 0 -> resumeLastStation(exo)
                    exo.isPlaying -> exo.pause()
                    else -> {
                        if (exo.currentMediaItem?.mediaId?.startsWith("http") == true) {
                            exo.stop(); exo.seekToDefaultPosition(); exo.prepare()
                        } else if (exo.playbackState == Player.STATE_IDLE || exo.playerError != null) exo.prepare()
                        exo.play()
                    }
                }
            }
            RadioWidgetProvider.ACTION_NEXT -> stepQueue(+1)
            RadioWidgetProvider.ACTION_PREVIOUS -> stepQueue(-1)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * v1.3: next/previous for the widget (the notification, lock screen and headset follow
     * the player queue on their own). While the app is alive the player holds the whole
     * visible list, so this is a plain seek; with a single item left — or nothing, after
     * the service restarted — the last queue the app saved is rebuilt around the new
     * position instead. Before this, a single-item player made both buttons dead.
     */
    private fun stepQueue(direction: Int) {
        val exo = player ?: return
        if (exo.currentTimeline.windowCount > 1) {
            if (direction > 0) exo.seekToNextMediaItem() else exo.seekToPreviousMediaItem()
            return
        }
        val (urls, names) = com.universalrp.tamilnadufm.data.StationRepository(this)
            .loadQueue()?.let { it.first to it.second } ?: (emptyList<String>() to emptyList())
        if (urls.isEmpty()) {
            if (exo.mediaItemCount == 0) resumeLastStation(exo)
            return
        }
        val prefs = getSharedPreferences("tamilnadufm", MODE_PRIVATE)
        val currentId = exo.currentMediaItem?.mediaId ?: prefs.getString("last_url", null)
        val at = urls.indexOfFirst { it == currentId }
        val nextIndex = if (at < 0) {
            if (direction > 0) 0 else urls.size - 1
        } else {
            (at + direction).mod(urls.size)
        }
        playQueueAt(exo, urls, names, nextIndex)
    }

    /** Used by the widget when nothing is loaded yet: bring back the last station. */
    private fun resumeLastStation(player: ExoPlayer) {
        val prefs = getSharedPreferences("tamilnadufm", MODE_PRIVATE)
        val url = prefs.getString("last_url", null)
        // Resume inside the saved queue when the last station is part of it, so the very
        // next widget tap on next/previous already works.
        val queue = com.universalrp.tamilnadufm.data.StationRepository(this).loadQueue()
        val urls = queue?.first.orEmpty()
        val at = urls.indexOfFirst { it == url }
        if (queue != null && url != null && at >= 0) {
            playQueueAt(player, urls, queue.second, at)
            return
        }
        if (url == null) return
        val name = prefs.getString("last_name", null) ?: "Ramesh Radio"
        val favicon = prefs.getString("last_favicon", null)
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(url)
                .setMediaId(url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(name)
                        .setArtist("Ramesh Radio")
                        .setArtworkUri(
                            favicon?.takeIf { it.startsWith("http") }?.let { android.net.Uri.parse(it) }
                        )
                        .build()
                )
                .build()
        )
        player.prepare()
        player.play()
    }

    private fun playQueueAt(
        player: ExoPlayer,
        urls: List<String>,
        names: List<String>,
        index: Int,
    ) {
        if (urls.isEmpty()) return
        val safe = index.coerceIn(0, urls.size - 1)
        player.setMediaItems(
            urls.mapIndexed { i, url -> queueItem(url, names.getOrElse(i) { "" }) },
            safe,
            0L,
        )
        player.prepare()
        player.play()
    }

    /**
     * One queue entry rebuilt from prefs. Entries that are not http(s) are local files,
     * so they carry the local-title extra the UI reads back (see MainViewModel).
     */
    private fun queueItem(url: String, name: String): MediaItem {
        val local = !(url.startsWith("http://") || url.startsWith("https://"))
        val metadata = MediaMetadata.Builder()
            .setTitle(name.ifBlank { "Ramesh Radio" })
            .setArtist(if (local) "From your phone" else "Ramesh Radio")
        if (local) {
            metadata.setExtras(android.os.Bundle().apply {
                putString(
                    com.universalrp.tamilnadufm.MainViewModel.EXTRA_LOCAL_TITLE,
                    name.ifBlank { "Audio file" },
                )
            })
        }
        return MediaItem.Builder()
            .setUri(url)
            .setMediaId(url)
            .setMediaMetadata(metadata.build())
            .build()
    }

    private fun publishWidgetState() {
        val exo = player ?: return
        val title = exo.currentMediaItem?.mediaMetadata?.title?.toString()
            ?: exo.currentMediaItem?.mediaId
            ?: "Ramesh Radio"
        RadioWidgetProvider.rememberState(this, title, exo.isPlaying)
        RadioWidgetProvider.refresh(this)
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val exo = player
        if (exo == null || !exo.isPlaying) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        AudioFx.release()
        PlayerBus.detach()
        player?.removeListener(listener)
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }
}
