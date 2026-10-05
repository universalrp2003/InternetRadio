package com.universalrp.tamilnadufm.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.universalrp.tamilnadufm.audio.AudioFx

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

    private val listener = object : Player.Listener {
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
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            PlayerBus.reportState(playbackState)
        }
    }

    override fun onCreate() {
        super.onCreate()
        AudioFx.load(this)

        val loadControl = DefaultLoadControl.Builder()
            // Radio-friendly buffering: quick to start, generous against dropouts.
            .setBufferDurationsMs(20_000, 45_000, 1_500, 3_000)
            .build()

        val exo = ExoPlayer.Builder(this)
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

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val exo = player
        if (exo == null || !exo.isPlaying) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
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
