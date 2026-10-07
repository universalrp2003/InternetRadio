package com.universalrp.tamilnadufm.player

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A tiny in-process noticeboard between the playback service and the UI.
 *
 * The service reports what the player is doing; the screens read it. This keeps the
 * UI honest about buffering, errors and whether sound is actually coming out,
 * without either side holding a reference to the other.
 */
object PlayerBus {

    data class Snapshot(
        val isPlaying: Boolean = false,
        val playbackState: Int = Player.STATE_IDLE,
        val error: String? = null,
    ) {
        val isBuffering: Boolean get() = playbackState == Player.STATE_BUFFERING
        val isReady: Boolean get() = playbackState == Player.STATE_READY
    }

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state

    /** Set while the service is alive, so the UI can nudge playback along. */
    @Volatile
    var player: ExoPlayer? = null
        private set

    fun attach(player: ExoPlayer) {
        this.player = player
        _state.value = Snapshot(
            isPlaying = player.isPlaying,
            playbackState = player.playbackState,
            error = null,
        )
    }

    fun detach() {
        player = null
        _state.value = Snapshot()
    }

    fun reportPlaying(isPlaying: Boolean) {
        _state.value = _state.value.copy(isPlaying = isPlaying)
    }

    fun reportState(playbackState: Int) {
        _state.value = _state.value.copy(playbackState = playbackState)
    }

    fun reportError(message: String) {
        _state.value = _state.value.copy(error = message)
    }

    fun clearError() {
        if (_state.value.error != null) {
            _state.value = _state.value.copy(error = null)
        }
    }

    /** Starts the service so the session/notification exist before playback. */
    fun ensureService(context: android.content.Context) {
        try {
            context.startService(
                android.content.Intent(context, PlaybackService::class.java)
            )
        } catch (t: Throwable) {
            // Background-start restrictions; the controller can still start it.
        }
    }
}
