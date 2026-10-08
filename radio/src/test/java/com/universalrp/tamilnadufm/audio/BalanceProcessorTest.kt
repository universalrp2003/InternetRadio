package com.universalrp.tamilnadufm.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BalanceProcessorTest {
    private fun process(balance: Float): Pair<Short, Short> {
        AudioFx.setBalance(balance)
        val processor = BalanceProcessor()
        processor.configure(AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        val input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        input.putShort(12000.toShort()).putShort((-8000).toShort()).flip()
        processor.queueInput(input)
        val out = processor.output.order(ByteOrder.nativeOrder())
        val result = out.short to out.short
        processor.reset()
        AudioFx.setBalance(0f)
        return result
    }
    @Test fun centrePreservesStereo() { assertEquals(12000.toShort() to (-8000).toShort(), process(0f)) }
    @Test fun rightAttenuatesLeft() { assertEquals(0.toShort() to (-8000).toShort(), process(1f)) }
    @Test fun leftAttenuatesRight() { assertEquals(12000.toShort() to 0.toShort(), process(-1f)) }
    @Test fun halfwayDoesNotBoost() { assertEquals(6000.toShort() to (-8000).toShort(), process(0.5f)) }
    @Test fun monoBypassesBalance() {
        val p = BalanceProcessor()
        p.configure(AudioFormat(44100, 1, C.ENCODING_PCM_16BIT))
        assertFalse(p.isActive)
    }
}
