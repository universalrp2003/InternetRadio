package com.rpideas.secretmessage.preview.transport

import com.rpideas.secretmessage.preview.model.DecryptedChatPayload
import com.rpideas.secretmessage.preview.model.InlineSegment
import com.rpideas.secretmessage.preview.model.MessageContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnavailableMlsTransportTest {
    @Test
    fun previewTransportNeverSendsAndStaysDisconnected() = runBlocking {
        val transport = UnavailableMlsTransport()
        var delivered = 0
        transport.setIncomingPayloadListener { delivered += 1 }

        val chat = DecryptedChatPayload.ChatMessagePayload(
            "m-1",
            60,
            MessageContent(listOf(InlineSegment.EnglishText("test"))),
        )
        assertFalse(transport.sendMlsApplicationMessage(chat))
        assertFalse(transport.sendMlsApplicationMessage(DecryptedChatPayload.DeliveryAck("m-1")))
        assertEquals(MlsSessionStatus.DISCONNECTED, transport.sessionStatus.value)
        assertTrue(transport.statusDescription.value.contains("nothing leaves this phone"))

        transport.closeSession()
        assertEquals(MlsSessionStatus.DISCONNECTED, transport.sessionStatus.value)
        assertEquals(0, delivered)
    }
}
