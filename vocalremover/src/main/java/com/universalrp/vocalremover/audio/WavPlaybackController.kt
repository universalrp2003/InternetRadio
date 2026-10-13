package com.universalrp.vocalremover.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Playback stays in this app so the user always has a pause and stop control. */
enum class WavPlaybackStatus { STOPPED, PREPARING, PLAYING, PAUSED }

data class WavPlaybackState(
    val status: WavPlaybackStatus = WavPlaybackStatus.STOPPED,
    val errorMessage: String? = null,
    val volume: Float = 1f,
)

class WavPlaybackController(context: Context) {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(WavPlaybackState())
    val state = mutableState.asStateFlow()

    private var player: MediaPlayer? = null
    private var playbackVolume: Float = 1f

    fun setVolume(value: Float) {
        playbackVolume = value.coerceIn(0f, 1f)
        runCatching { player?.setVolume(playbackVolume, playbackVolume) }
        mutableState.value = mutableState.value.copy(volume = playbackVolume)
    }

    fun play(uriString: String) {
        stop()
        if (uriString.isBlank()) {
            mutableState.value = WavPlaybackState(errorMessage = "The saved audio location is missing.", volume = playbackVolume)
            return
        }

        val candidate = MediaPlayer()
        player = candidate
        mutableState.value = WavPlaybackState(status = WavPlaybackStatus.PREPARING, volume = playbackVolume)
        try {
            candidate.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            candidate.setOnPreparedListener { prepared ->
                if (player !== prepared) return@setOnPreparedListener
                try {
                    prepared.setVolume(playbackVolume, playbackVolume)
                    prepared.start()
                    mutableState.value = WavPlaybackState(status = WavPlaybackStatus.PLAYING, volume = playbackVolume)
                } catch (failure: RuntimeException) {
                    fail(prepared, failure)
                }
            }
            candidate.setOnCompletionListener { completed ->
                if (player === completed) {
                    releaseCurrentPlayer()
                    mutableState.value = WavPlaybackState(volume = playbackVolume)
                }
            }
            candidate.setOnErrorListener { failedPlayer, what, extra ->
                if (player === failedPlayer) {
                    releaseCurrentPlayer()
                    mutableState.value = WavPlaybackState(
                        errorMessage = "Android could not play this audio (player error $what/$extra).",
                        volume = playbackVolume,
                    )
                }
                true
            }
            candidate.setDataSource(appContext, Uri.parse(uriString))
            candidate.prepareAsync()
        } catch (failure: Exception) {
            fail(candidate, failure)
        }
    }

    fun pause() {
        val current = player ?: return
        if (mutableState.value.status != WavPlaybackStatus.PLAYING) return
        try {
            current.pause()
            mutableState.value = WavPlaybackState(status = WavPlaybackStatus.PAUSED, volume = playbackVolume)
        } catch (failure: RuntimeException) {
            fail(current, failure)
        }
    }

    fun resume() {
        val current = player ?: return
        if (mutableState.value.status != WavPlaybackStatus.PAUSED) return
        try {
            current.start()
            mutableState.value = WavPlaybackState(status = WavPlaybackStatus.PLAYING, volume = playbackVolume)
        } catch (failure: RuntimeException) {
            fail(current, failure)
        }
    }

    /** Stop playback immediately and release the decoder, including while it is preparing. */
    fun stop() {
        val current = player
        player = null
        if (current != null) {
            runCatching { current.setOnPreparedListener(null) }
            runCatching { current.setOnCompletionListener(null) }
            runCatching { current.setOnErrorListener(null) }
            runCatching { current.stop() }
            runCatching { current.release() }
        }
        mutableState.value = WavPlaybackState(volume = playbackVolume)
    }

    fun release() = stop()

    private fun fail(candidate: MediaPlayer, failure: Throwable) {
        if (player !== candidate) return
        releaseCurrentPlayer()
        mutableState.value = WavPlaybackState(
            errorMessage = failure.message?.take(160)?.let { "Could not play the audio: $it" }
                ?: "Could not play the saved audio.",
            volume = playbackVolume,
        )
    }

    private fun releaseCurrentPlayer() {
        val current = player
        player = null
        if (current != null) {
            runCatching { current.setOnPreparedListener(null) }
            runCatching { current.setOnCompletionListener(null) }
            runCatching { current.setOnErrorListener(null) }
            runCatching { current.release() }
        }
    }
}
