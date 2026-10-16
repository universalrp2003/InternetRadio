package com.rpideas.secretmessage.preview.transport

import com.rpideas.secretmessage.preview.model.DecryptedChatPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MlsSessionStatus {
    DISCONNECTED,
    AWAITING_MLS_WELCOME_OR_KEYPACKAGE,
    VERIFYING_MLS_EPOCH_AUTHENTICATION,
    CONNECTED_MLS_VERIFIED,
    ERROR,
}

/**
 * Boundary between the WhisperPad chat/timer UI and Secret Message's MLS (RFC 9420) session engine.
 *
 * An implementation must perform all session setup, peer authentication and encryption inside the
 * reviewed MLS layer, bound to the verified peer identity. It must not queue payloads for an offline
 * peer: a send either reaches the authenticated session or returns false. The payload encoding is
 * chosen together with that binding; this interface carries typed payloads only.
 */
interface SecretMessageMlsTransport {
    val sessionStatus: StateFlow<MlsSessionStatus>
    val statusDescription: StateFlow<String>

    /** Send one application payload through the authenticated two-member MLS session. */
    suspend fun sendMlsApplicationMessage(payload: DecryptedChatPayload): Boolean

    /** Receive only MLS-authenticated, decrypted application payloads. */
    fun setIncomingPayloadListener(listener: (DecryptedChatPayload) -> Unit)

    /** Wipe ephemeral session state from memory and close the transport. */
    fun closeSession()
}

/**
 * The only transport in this preview. No MLS candidate, network client or native library is linked,
 * so nothing can leave the phone and every send reports failure.
 */
class UnavailableMlsTransport : SecretMessageMlsTransport {
    private val status = MutableStateFlow(MlsSessionStatus.DISCONNECTED)
    private val description = MutableStateFlow(NOT_CONNECTED_DESCRIPTION)

    override val sessionStatus: StateFlow<MlsSessionStatus> = status.asStateFlow()
    override val statusDescription: StateFlow<String> = description.asStateFlow()

    override suspend fun sendMlsApplicationMessage(payload: DecryptedChatPayload): Boolean = false

    override fun setIncomingPayloadListener(listener: (DecryptedChatPayload) -> Unit) {
        // No peer can deliver a payload to this preview, so the listener is never called.
    }

    override fun closeSession() {
        status.value = MlsSessionStatus.DISCONNECTED
    }

    companion object {
        const val NOT_CONNECTED_DESCRIPTION =
            "No MLS transport is connected in this preview; nothing leaves this phone."
    }
}
