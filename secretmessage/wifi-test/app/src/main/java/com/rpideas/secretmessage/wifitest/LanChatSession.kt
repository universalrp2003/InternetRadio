package com.rpideas.secretmessage.wifitest

import android.content.Context
import com.rpideas.secretmessage.preview.model.DecryptedChatPayload
import com.rpideas.secretmessage.preview.model.PayloadValidator
import com.rpideas.secretmessage.preview.transport.MlsSessionStatus
import com.rpideas.secretmessage.preview.transport.SecretMessageMlsTransport
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where this phone is in setting up or holding a Wi-Fi chat. */
internal sealed class ConnectionState {
    /** No chat; [lastResult] says how the previous one ended. */
    data class Idle(val lastResult: String?) : ConnectionState()

    /** Waiting up to three minutes for the other phone to open [invite]. */
    data class Hosting(val invite: String, val stopping: Boolean = false) : ConnectionState()

    data object Joining : ConnectionState()

    /** Both people compare [prompt]'s code; [answer] is this person's answer once given. */
    data class Comparing(val prompt: SafetyPrompt, val answer: Boolean? = null) : ConnectionState()

    /** Both people confirmed the code; messages flow inside the MLS session. */
    data class Connected(val prompt: SafetyPrompt) : ConnectionState()

    data class Ending(val note: String) : ConnectionState()
}

/**
 * One Wi-Fi chat at a time between two phones, run by the native mls-rs session (Wi-Fi chat
 * profile) and offered to the WhisperPad chat as its [SecretMessageMlsTransport].
 *
 * The native call that hosts or joins blocks a worker thread until the chat ends; a coroutine polls
 * the native session for the safety-code prompt and chat events. Payloads pass only after both
 * people confirmed the code; nothing is queued for later, stored, or logged.
 */
internal class LanChatSession(context: Context) : SecretMessageMlsTransport {
    private val app = context.applicationContext
    val identity = IdentityStore(app)
    val report = SessionReport()

    private val mutableConnection = MutableStateFlow<ConnectionState>(ConnectionState.Idle(null))
    val connection: StateFlow<ConnectionState> = mutableConnection.asStateFlow()

    private val status = MutableStateFlow(MlsSessionStatus.DISCONNECTED)
    private val description = MutableStateFlow(IDLE_DESCRIPTION)
    override val sessionStatus: StateFlow<MlsSessionStatus> = status.asStateFlow()
    override val statusDescription: StateFlow<String> = description.asStateFlow()

    @Volatile
    private var listener: ((DecryptedChatPayload) -> Unit)? = null
    private val running = AtomicBoolean(false)

    @Volatile
    private var stopRequested = false
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Starts hosting a chat; returns a message for the screen if it could not start. */
    fun startHost(): String? {
        val hostIp = try {
            LocalNetwork.privateIpv4()
        } catch (noWifi: IllegalStateException) {
            return noWifi.message
        }
        val port = 40_000 + SecureRandom().nextInt(20_000)
        val token = IdentityStore.randomHex(16)
        val invite = InviteLink(hostIp, port, token).toUri()
        return begin(ConnectionState.Hosting(invite), "host", "Waiting for the other phone") { identityText, pins ->
            NativeBridge.nativeRunHost(hostIp, port, token, identityText, pins)
        }
    }

    /** Joins the chat in [inviteText]; returns a message for the screen if it could not start. */
    fun join(inviteText: String): String? {
        val invite = try {
            InviteLink.parse(inviteText)
        } catch (bad: IllegalArgumentException) {
            return bad.message
        }
        return begin(ConnectionState.Joining, "guest", "Connecting to the other phone") { identityText, pins ->
            NativeBridge.nativeRunJoin(invite.host, invite.port, invite.token, identityText, pins)
        }
    }

    /** Stops waiting for the other phone (hosting only). */
    fun stopWaiting() {
        synchronized(lock) {
            val hosting = mutableConnection.value as? ConnectionState.Hosting ?: return
            stopRequested = true
            runCatching { NativeBridge.nativeCancelHostWait() }
            mutableConnection.value = hosting.copy(stopping = true)
        }
    }

    /** Passes this person's comparison answer; ignored if the comparison already ended. */
    fun answer(codesMatch: Boolean) {
        synchronized(lock) {
            val comparing = mutableConnection.value as? ConnectionState.Comparing ?: return
            if (comparing.answer != null) return
            val reply = runCatching { NativeBridge.nativeSubmitSafetyAnswer(if (codesMatch) 1 else 0) }.getOrDefault("")
            if (reply != NativeBridge.ANSWER_ACCEPTED) return
            mutableConnection.value = comparing.copy(answer = codesMatch)
            report.answered(codesMatch, comparing.prompt.code)
            description.value = if (codesMatch) {
                "You confirmed the code; waiting for the other phone's answer."
            } else {
                "You reported different codes; stopping on both phones."
            }
        }
    }

    /** Ends the open chat on both phones. */
    fun endChat() {
        synchronized(lock) {
            if (mutableConnection.value !is ConnectionState.Connected) return
            runCatching { NativeBridge.nativeChatEnd() }
            mutableConnection.value = ConnectionState.Ending("Ending the chat on both phones…")
            status.value = MlsSessionStatus.DISCONNECTED
            description.value = IDLE_DESCRIPTION
        }
    }

    /** Gives this install a new MLS signing key (the device ID stays); only between chats. */
    fun resetKey(): String {
        if (running.get()) return "Wait until the chat has ended."
        NativeBridge.load()?.let { return it }
        return try {
            identity.loadOrCreate(newKey = true)
            "New key created. Phones that remembered this phone will show a key-change warning."
        } catch (failure: Exception) {
            "Could not reset the key: ${failure.javaClass.simpleName} ${failure.message.orEmpty()}"
        }
    }

    fun forgetPhones(): String {
        if (running.get()) return "Wait until the chat has ended."
        identity.forgetPhones()
        return "Remembered phones cleared."
    }

    override suspend fun sendMlsApplicationMessage(payload: DecryptedChatPayload): Boolean {
        if (status.value != MlsSessionStatus.CONNECTED_MLS_VERIFIED) return false
        val text = runCatching { PayloadCodec.encode(payload) }.getOrNull() ?: return false
        if (text.length > PayloadCodec.MAX_ENCODED_CHARS) {
            report.sendFailed("the message is too large for the Wi-Fi session")
            return false
        }
        val reply = withContext(Dispatchers.Default) {
            runCatching { NativeBridge.nativeChatSend(text) }.getOrDefault("FAIL: the MLS engine did not answer")
        }
        val queued = reply.startsWith("OK|")
        if (queued) report.sent(payload) else report.sendFailed(reply.removePrefix("FAIL: "))
        return queued
    }

    override fun setIncomingPayloadListener(listener: (DecryptedChatPayload) -> Unit) {
        this.listener = listener
    }

    /** Called when the chat screen goes away for good (the app was closed). */
    override fun closeSession() {
        listener = null
        when (mutableConnection.value) {
            is ConnectionState.Connected -> endChat()
            is ConnectionState.Hosting -> stopWaiting()
            // A comparison left open ends by itself after two minutes, telling the other phone.
            else -> Unit
        }
    }

    private fun begin(
        first: ConnectionState,
        role: String,
        notice: String,
        run: (identityText: String, pins: String) -> String,
    ): String? {
        NativeBridge.load()?.let { return it }
        if (!running.compareAndSet(false, true)) return "A chat is already starting or open on this phone."
        stopRequested = false
        report.start(role)
        mutableConnection.value = first
        status.value = MlsSessionStatus.AWAITING_MLS_WELCOME_OR_KEYPACKAGE
        description.value = "$notice."
        ChatConnectionService.show(app, notice)
        thread(name = "secretmessage-wifi-chat") {
            val result = try {
                val identityText = identity.loadOrCreate()
                run(identityText, identity.pins())
            } catch (failure: Throwable) {
                "FAIL: ${failure.javaClass.simpleName} while starting the chat: ${failure.message.orEmpty()}"
            }
            finish(result)
        }
        scope.launch {
            while (running.get()) {
                poll()
                delay(POLL_MILLIS)
            }
        }
        return null
    }

    private fun poll() {
        if (stopRequested && mutableConnection.value is ConnectionState.Hosting) {
            runCatching { NativeBridge.nativeCancelHostWait() }
        }
        val promptText = runCatching { NativeBridge.nativeSafetyPrompt() }.getOrDefault("")
        synchronized(lock) {
            if (!running.get()) return
            SafetyPrompt.parse(promptText)?.let { showPrompt(it) }
            drainEvents()
        }
    }

    private fun showPrompt(prompt: SafetyPrompt) {
        val current = mutableConnection.value
        if (current is ConnectionState.Comparing && current.prompt.code == prompt.code) return
        if (current is ConnectionState.Connected || current is ConnectionState.Ending) return
        mutableConnection.value = ConnectionState.Comparing(prompt)
        status.value = MlsSessionStatus.VERIFYING_MLS_EPOCH_AUTHENTICATION
        description.value = "Compare the safety code with the other phone."
        report.prompted(prompt)
        ChatConnectionService.update(app, "Compare the safety code")
    }

    private fun drainEvents() {
        repeat(MAX_EVENTS_PER_POLL) {
            val event = runCatching { NativeBridge.nativeChatPoll() }.getOrDefault("")
            if (event.isEmpty()) return
            handleEvent(event)
        }
    }

    private fun handleEvent(event: String) {
        val kind = event.substringBefore('|')
        val rest = event.substringAfter('|', "")
        when (kind) {
            "OPEN" -> {
                val verifiedPeer = runCatching { NativeBridge.nativeTakeVerifiedPeer() }.getOrDefault("")
                if (verifiedPeer.isNotEmpty()) identity.remember(verifiedPeer)
                val prompt = (mutableConnection.value as? ConnectionState.Comparing)?.prompt
                    ?: SafetyPrompt("", PeerStatus.NEW, verifiedPeer.substringBefore(' '))
                mutableConnection.value = ConnectionState.Connected(prompt)
                status.value = MlsSessionStatus.CONNECTED_MLS_VERIFIED
                description.value = connectedDescription(prompt)
                report.connected()
                ChatConnectionService.update(app, "Chat open")
            }
            "RECEIVED" -> {
                val text = rest.substringAfter('|', "")
                val payload = PayloadCodec.decode(text)
                if (payload != null && PayloadValidator.isValidPayload(payload)) {
                    report.received(payload)
                    listener?.invoke(payload)
                } else {
                    report.refused()
                }
            }
            "DELIVERED" -> report.engineAcknowledged()
            "ENDED" -> {
                report.chatEnded(rest)
                mutableConnection.value = ConnectionState.Ending(SessionText.chatEnd(rest))
                status.value = MlsSessionStatus.DISCONNECTED
                description.value = IDLE_DESCRIPTION
            }
            else -> Unit
        }
    }

    private fun finish(result: String) {
        synchronized(lock) {
            drainEvents()
            running.set(false)
            stopRequested = false
            if (result.startsWith("PASS")) {
                runCatching { NativeBridge.nativeTakeVerifiedPeer() }.getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { identity.remember(it) }
            }
            report.finished(result)
            mutableConnection.value = ConnectionState.Idle(SessionText.lastResult(result, report.chatSummary()))
            status.value = if (result.startsWith("FAIL")) MlsSessionStatus.ERROR else MlsSessionStatus.DISCONNECTED
            description.value = IDLE_DESCRIPTION
        }
        ChatConnectionService.stop(app)
    }

    private fun connectedDescription(prompt: SafetyPrompt): String {
        val which = when (prompt.peerStatus) {
            PeerStatus.KNOWN -> "the remembered phone"
            PeerStatus.CHANGED -> "the phone whose key changed"
            PeerStatus.NEW -> "the new phone"
        }
        val device = if (prompt.peerDevice.isEmpty()) "" else " (device ${PeerPins.formatDeviceId(prompt.peerDevice)})"
        return "Connected over Wi-Fi to $which$device after both people confirmed the safety code. " +
            "TEST BUILD: not security-reviewed."
    }

    companion object {
        private const val POLL_MILLIS = 120L
        private const val MAX_EVENTS_PER_POLL = 200
        const val IDLE_DESCRIPTION = "Not connected."
    }
}

/** The single chat session of this app process. */
internal object WifiChat {
    @Volatile
    private var session: LanChatSession? = null

    fun session(context: Context): LanChatSession =
        session ?: synchronized(this) {
            session ?: LanChatSession(context.applicationContext).also { session = it }
        }
}

/** Finds this phone's private Wi-Fi (or Ethernet) IPv4 address for the invite. */
internal object LocalNetwork {
    fun privateIpv4(): String {
        val interfaces = NetworkInterface.getNetworkInterfaces()
            ?: throw IllegalStateException("No active network was found. Connect to Wi-Fi first.")
        for (network in interfaces.toList()) {
            if (!network.isUp || network.isLoopback) continue
            val name = network.name.lowercase(Locale.ROOT)
            val local = listOf("wlan", "wifi", "eth", "en", "ap", "swlan").any { name.startsWith(it) }
            if (!local) continue
            for (address in network.inetAddresses.toList()) {
                val text = address.hostAddress ?: continue
                if (address is Inet4Address && InviteLink.isPrivateIpv4(text)) return text
            }
        }
        throw IllegalStateException(
            "No private Wi-Fi address was found. Connect both phones to the same Wi-Fi and turn off any VPN.",
        )
    }
}
