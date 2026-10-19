package com.rpideas.secretmessage.preview.chat

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rpideas.secretmessage.preview.audio.BuiltInNotificationSound
import com.rpideas.secretmessage.preview.audio.TonePlayer
import com.rpideas.secretmessage.preview.model.DecryptedChatPayload
import com.rpideas.secretmessage.preview.model.EphemeralMessage
import com.rpideas.secretmessage.preview.model.InlineSegment
import com.rpideas.secretmessage.preview.model.MessageContent
import com.rpideas.secretmessage.preview.model.VectorStroke
import com.rpideas.secretmessage.preview.settings.PreviewSettings
import com.rpideas.secretmessage.preview.settings.PreviewSettingsStore
import com.rpideas.secretmessage.preview.transport.MlsSessionStatus
import com.rpideas.secretmessage.preview.transport.SecretMessageMlsTransport
import com.rpideas.secretmessage.preview.transport.UnavailableMlsTransport
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * RAM-only chat state for the preview, adapted from the WhisperPad ChatViewModel. Messages and the
 * composer draft live only in memory and timers use elapsed realtime. The preview creates it with
 * [UnavailableMlsTransport], so every message stays undelivered and is removed after 30 seconds. A
 * separate test build may pass the transport that connects it to another phone; when that session
 * ends, every message is removed from memory.
 */
class ChatViewModel @JvmOverloads constructor(
    application: Application,
    private val transport: SecretMessageMlsTransport = UnavailableMlsTransport(),
) : AndroidViewModel(application) {
    private val settingsStore = PreviewSettingsStore(application)

    private val mutableSettings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<PreviewSettings> = mutableSettings.asStateFlow()

    private val mutableMessages = MutableStateFlow<List<EphemeralMessage>>(emptyList())
    val messages: StateFlow<List<EphemeralMessage>> = mutableMessages.asStateFlow()

    private val mutableComposer = MutableStateFlow<List<InlineSegment>>(emptyList())
    val composer: StateFlow<List<InlineSegment>> = mutableComposer.asStateFlow()

    private val mutableNow = MutableStateFlow(SystemClock.elapsedRealtime())
    val nowElapsedMillis: StateFlow<Long> = mutableNow.asStateFlow()

    val sessionStatus: StateFlow<MlsSessionStatus> = transport.sessionStatus
    val statusDescription: StateFlow<String> = transport.statusDescription

    private var foreground = false
    private var chatOnScreen = false

    init {
        transport.setIncomingPayloadListener { payload ->
            viewModelScope.launch { handleIncoming(payload) }
        }
        viewModelScope.launch {
            while (isActive) {
                commit(ChatStateMachine.expire(mutableMessages.value, now()))
                delay(TICK_MILLIS)
            }
        }
        viewModelScope.launch {
            // Nothing from an authenticated session may stay on screen after that session ends.
            var wasConnected = false
            transport.sessionStatus.collect { status ->
                val connected = status == MlsSessionStatus.CONNECTED_MLS_VERIFIED
                if (wasConnected && !connected) clearConversation()
                wasConnected = connected
            }
        }
    }

    /** Removes every message from memory, for example when the session ends. */
    fun clearConversation() {
        mutableMessages.value = emptyList()
        mutableNow.value = now()
    }

    fun typeText(text: String) {
        mutableComposer.value = ComposerPolicy.type(mutableComposer.value, text)
    }

    fun backspace() {
        mutableComposer.value = ComposerPolicy.backspace(mutableComposer.value)
    }

    fun insertGlyph(strokes: List<VectorStroke>, displayHeightDp: Float) {
        mutableComposer.value = ComposerPolicy.insertGlyph(
            mutableComposer.value,
            InlineSegment.DrawnGlyph(strokes, displayHeightDp),
        )
    }

    /** Returns false when the draft is empty or exceeds the payload limits. */
    fun sendComposedMessage(): Boolean {
        val segments = ComposerPolicy.prepareForSend(mutableComposer.value)
        if (!ComposerPolicy.isSendable(segments)) return false
        val sent = dispatchSend(MessageContent(inlineSegments = segments))
        if (sent) mutableComposer.value = emptyList()
        return sent
    }

    fun sendPicture(strokes: List<VectorStroke>): Boolean =
        dispatchSend(MessageContent(fullPictureStrokes = strokes))

    fun setViewSeconds(seconds: Int) {
        updateSettings(mutableSettings.value.copy(viewSeconds = seconds))
    }

    fun selectTone(sound: BuiltInNotificationSound) {
        updateSettings(mutableSettings.value.copy(toneId = sound.id))
    }

    fun previewTone(sound: BuiltInNotificationSound) {
        TonePlayer.play(sound)
    }

    fun setAppLockEnabled(enabled: Boolean) {
        updateSettings(mutableSettings.value.copy(appLockEnabled = enabled))
    }

    fun setForeground(isForeground: Boolean) {
        foreground = isForeground
        updateViewing()
    }

    fun setChatOnScreen(onScreen: Boolean) {
        chatOnScreen = onScreen
        updateViewing()
    }

    private fun recipientIsViewing(): Boolean = foreground && chatOnScreen

    private fun updateViewing() {
        if (recipientIsViewing()) commit(ChatStateMachine.recipientViewing(mutableMessages.value, now()))
    }

    private fun dispatchSend(content: MessageContent): Boolean {
        val transition = ChatStateMachine.send(
            messages = mutableMessages.value,
            messageId = UUID.randomUUID().toString(),
            content = content,
            viewSeconds = mutableSettings.value.viewSeconds,
            nowElapsedMillis = now(),
        )
        if (transition.outbound.isEmpty()) return false
        commit(transition)
        return true
    }

    private fun handleIncoming(payload: DecryptedChatPayload) {
        val isNewMessage = payload is DecryptedChatPayload.ChatMessagePayload &&
            mutableMessages.value.none { it.id == payload.messageId }
        val transition = ChatStateMachine.receive(mutableMessages.value, payload, now(), recipientIsViewing())
        commit(transition)
        if (isNewMessage && transition.outbound.isNotEmpty()) TonePlayer.play(mutableSettings.value.tone)
    }

    private fun commit(transition: ChatTransition) {
        mutableMessages.value = transition.messages
        mutableNow.value = now()
        sendOnce(transition.outbound)
    }

    private fun sendOnce(payloads: List<DecryptedChatPayload>) {
        if (payloads.isEmpty()) return
        viewModelScope.launch {
            // No retry and no queue: a failed send leaves the sender copy to expire after 30 seconds.
            payloads.forEach { transport.sendMlsApplicationMessage(it) }
        }
    }

    private fun updateSettings(next: PreviewSettings) {
        val clean = next.sanitized()
        mutableSettings.value = clean
        settingsStore.save(clean)
    }

    private fun now(): Long = SystemClock.elapsedRealtime()

    override fun onCleared() {
        transport.closeSession()
        mutableMessages.value = emptyList()
        mutableComposer.value = emptyList()
        super.onCleared()
    }

    private companion object {
        const val TICK_MILLIS = 250L
    }
}
