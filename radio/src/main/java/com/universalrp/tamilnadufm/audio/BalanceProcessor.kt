package com.universalrp.tamilnadufm.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/** PCM-stage balance remains available even when the device has no effect engine. */
class BalanceProcessor : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat =
        if (inputAudioFormat.channelCount == 2 && inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) inputAudioFormat else AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val out = replaceOutputBuffer(inputBuffer.remaining())
        val b = AudioFx.settings.balance.coerceIn(-1f, 1f)
        val left = if (b > 0) 1f - b else 1f
        val right = if (b < 0) 1f + b else 1f
        while (inputBuffer.remaining() >= 4) {
            out.putShort((inputBuffer.short * left).toInt().toShort())
            out.putShort((inputBuffer.short * right).toInt().toShort())
        }
        out.flip()
    }
}
