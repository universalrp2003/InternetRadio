package com.rpideas.secretmessage.preview.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Plays a synthesized tone with notification audio attributes, so it follows the phone's
 * notification volume. A tone is optional feedback, so playback errors are ignored.
 */
object TonePlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun play(sound: BuiltInNotificationSound) {
        scope.launch {
            val samples = ToneSynthesizer.synthesize(sound)
            var track: AudioTrack? = null
            try {
                val created = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(ToneSynthesizer.SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track = created
                created.write(samples, 0, samples.size)
                created.play()
                delay(samples.size * 1_000L / ToneSynthesizer.SAMPLE_RATE + 150L)
            } catch (ignored: Exception) {
                // Some devices refuse a new AudioTrack (for example while audio focus is restricted).
            } finally {
                track?.release()
            }
        }
    }
}
