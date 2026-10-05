package com.universalrp.cleansweep.data

import android.content.Context
import android.media.MediaPlayer
import androidx.annotation.RawRes

/** Tiny sound-effects helper. Sounds are short, bundled locally, and optional. */
object SoundFx {

    @Volatile
    var enabled: Boolean = true

    fun play(context: Context, @RawRes res: Int) {
        if (!enabled) return
        try {
            val mp = MediaPlayer.create(context, res) ?: return
            mp.setOnCompletionListener { player -> player.release() }
            mp.setOnErrorListener { player, _, _ ->
                player.release()
                true
            }
            mp.start()
        } catch (e: Exception) {
            // Never let a sound issue break the app.
        }
    }
}
