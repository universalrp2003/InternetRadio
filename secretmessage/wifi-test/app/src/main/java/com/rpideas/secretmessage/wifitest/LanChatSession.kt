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

/** Where this phone is in setting up or holding a chat. */
internal sealed class ConnectionState {
    /** No chat; [lastResult] says how the previous one ended. */
    data class Idle(val lastResult: String?) : ConnectionState()

    /**
     * Waiting up to three minutes for the other phone to open [invite], over the Internet relay or
     * on the same Wi-Fi; [link] says how the connection is doing.
     */
    data class Hosting(
        val invite: String,
        val internet: Boolean,
        val link: String = "",
        val stopping: Boolean = false,
        /** The remembered phone this phone is calling (1.1.0), instead of sharing [invite]. */
        val calling: String? = null,
    ) : ConnectionState()

    /** Connecting to the phone that sent the invite; [link] says how the connection is doing. */
    data class Joining(val internet: Boolean, val link: String = "") : ConnectionState()

    /** Both people compare [prompt]'s code; [answer] is this person's answer once given. */
    data class Comparing(val prompt: SafetyPrompt, val answer: Boolean? = null) : ConnectionState()

    /** Both people confirmed the code; messages flow inside the MLS session. */
    data class Connected(val prompt: SafetyPrompt) : ConnectionState()

    data class Ending(val note: String) : ConnectionState()
}

/**
 * One chat at a time between two phones, run by the native mls-rs session (Wi-Fi chat profile) and
 * offered to the WhisperPad chat as its [SecretMessageMlsTransport]. The session runs the same way
 * over a direct connection on the same Wi-Fi or through the owner's test relay ([RelayLink]).
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

    /** How the open chat travels, for its status line. */
    @Volatile
    private var via = VIA_WIFI

    /** The other phone's device ID once the chat opened, for its doorbell card. */
    @Volatile
    private var peerDevice: String? = null

    /** A doorbell card that arrived before this phone saw the chat open. */
    @Volatile
    private var pendingCard: ContactCard? = null

    /** Shown instead of the native result when this phone stopped a call itself. */
    @Volatile
    private var stopNote: String? = null

    /** The remembered phone this phone is calling, while it waits for the answer. */
    @Volatile
    private var callingDevice: String? = null

    /** True while this phone answers another phone's call. */
    @Volatile
    private var answering = false

    /** A call to answer as soon as this phone's own waiting (its call or invite) has stopped. */
    @Volatile
    private var queuedAnswer: IncomingRing? = null
    private val contacts = Contacts.book(app)
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Starts hosting a chat through the Internet relay; returns a message for the screen if it could not start. */
    fun startInternetHost(): String? {
        val room = IdentityStore.randomHex(16)
        val token = IdentityStore.randomHex(16)
        val invite = InviteLink.Internet(room, token).toUri()
        val link = RelayLink(RelayLink.RELAY_URL, room, RelayLink.Role.HOST, ::showLink)
        return begin(
            ConnectionState.Hosting(invite, internet = true, link = RelayText.CONNECTING),
            "host, Internet relay",
            "Waiting for the other phone",
        ) { identityText, pins ->
            link.run(HOST_WAIT_MILLIS, { stopRequested }) {
                NativeBridge.nativeRunRelayHost(token, identityText, pins)
            }.also { report.connection(link.summary) }
        }
    }

    /** Starts hosting a chat on the same Wi-Fi; returns a message for the screen if it could not start. */
    fun startWifiHost(): String? {
        val hostIp = try {
            LocalNetwork.privateIpv4()
        } catch (noWifi: IllegalStateException) {
            return noWifi.message
        }
        val port = 40_000 + SecureRandom().nextInt(20_000)
        val token = IdentityStore.randomHex(16)
        val invite = InviteLink.Wifi(hostIp, port, token).toUri()
        return begin(
            ConnectionState.Hosting(invite, internet = false, link = WIFI_LINK),
            "host, same Wi-Fi",
            "Waiting for the other phone",
        ) { identityText, pins ->
            report.connection(WIFI_CONNECTION)
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
        return when (invite) {
            is InviteLink.Internet -> joinRelay(invite.room, invite.token, "guest, Internet relay")
            is InviteLink.Wifi -> begin(
                ConnectionState.Joining(internet = false),
                "guest, same Wi-Fi",
                "Connecting to the other phone",
            ) { identityText, pins ->
                report.connection(WIFI_CONNECTION)
                NativeBridge.nativeRunJoin(invite.host, invite.port, invite.token, identityText, pins)
            }
        }
    }

    /** True while a chat is starting or open. */
    fun busy(): Boolean = running.get()

    /**
     * Calls a remembered phone (1.1.0): waits in a new relay room and rings the phone's doorbell
     * with the room and token, encrypted for that phone. Returns a message if it could not start.
     */
    fun callContact(contact: Contact): String? {
        if (contact.card == null) {
            return "This phone has not shared its doorbell yet. Chat once with an invite, then you can call it."
        }
        val room = IdentityStore.randomHex(16)
        val token = IdentityStore.randomHex(16)
        val invite = InviteLink.Internet(room, token).toUri()
        val rang = AtomicBoolean(false)
        val link = RelayLink(RelayLink.RELAY_URL, room, RelayLink.Role.HOST) { text ->
            showLink(text)
            // Ring once this phone waits in the room, so the other phone can join at once.
            if (text == RelayText.WAITING && rang.compareAndSet(false, true)) {
                thread(name = "secretmessage-ring") { ring(contact, room, token) }
            }
        }
        return begin(
            ConnectionState.Hosting(invite, internet = true, link = RelayText.CONNECTING, calling = contact.label),
            "host, calling a remembered phone",
            "Calling a remembered phone",
            calling = contact.deviceId,
        ) { identityText, pins ->
            link.run(HOST_WAIT_MILLIS, { stopRequested }) {
                NativeBridge.nativeRunRelayHost(token, identityText, pins)
            }.also { report.connection(link.summary) }
        }
    }

    /**
     * Answers a chat request from a remembered phone; returns a message if it could not start. If
     * this phone is itself waiting (for its own call or invite), that waiting stops first and the
     * answer follows at once (1.1.0 phone test: both phones had tapped Chat and waited for each other).
     */
    fun answerRing(ring: IncomingRing): String? {
        synchronized(lock) {
            val current = mutableConnection.value
            if (current is ConnectionState.Hosting) {
                if (current.link == RelayText.PAIRED) return BUSY_NOTE
                queuedAnswer = ring
                stopRequested = true
                runCatching { NativeBridge.nativeCancelHostWait() }
                mutableConnection.value = current.copy(stopping = true)
                Rings.countAnswer()
                return null
            }
            if (current !is ConnectionState.Idle) return BUSY_NOTE
        }
        return joinRelay(ring.invite.room, ring.invite.token, ANSWER_ROLE, answeringRing = true)
            .also { if (it == null) Rings.countAnswer() }
    }

    /** What to do with a call from [callerDevice] that just arrived (see [RingPolicy]). */
    fun ringDecision(callerDevice: String): RingPolicy.Decision = synchronized(lock) {
        RingPolicy.decide(mutableConnection.value, running.get(), callingDevice, callerDevice, identity.deviceId())
    }

    private fun joinRelay(room: String, token: String, role: String, answeringRing: Boolean = false): String? {
        val link = RelayLink(RelayLink.RELAY_URL, room, RelayLink.Role.GUEST, ::showLink)
        return begin(
            ConnectionState.Joining(internet = true, link = RelayText.CONNECTING),
            role,
            "Connecting to the other phone",
            answeringRing = answeringRing,
        ) { identityText, pins ->
            link.run(GUEST_WAIT_MILLIS, { false }) {
                NativeBridge.nativeRunRelayJoin(token, identityText, pins)
            }.also { report.connection(link.summary) }
        }
    }

    private fun ring(contact: Contact, room: String, token: String) {
        val inboxId = contact.card?.inboxId
        val sealed = runCatching { contacts.sealRing(contact, room, token, System.currentTimeMillis()) }.getOrNull()
        val result = if (inboxId == null || sealed == null) Doorbell.RingResult.FAILED else Doorbell.ring(inboxId, sealed)
        report.rang(result.name.lowercase(Locale.ROOT))
        when (result) {
            Doorbell.RingResult.DELIVERED -> showLink(RING_DELIVERED)
            Doorbell.RingResult.OFFLINE -> stopCalling(RING_OFFLINE)
            Doorbell.RingResult.BUSY -> stopCalling(RING_BUSY)
            Doorbell.RingResult.FAILED -> stopCalling(RING_FAILED)
        }
    }

    private fun stopCalling(note: String) {
        stopNote = note
        showLink(note)
        stopWaiting()
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
            // A new key also gets a new doorbell; remembered phones learn it in the next chat.
            contacts.renewOwnCard()
            Doorbell.restart(app)
            "New key created. Phones that remembered this phone will show a key-change warning."
        } catch (failure: Exception) {
            "Could not reset the key: ${failure.javaClass.simpleName} ${failure.message.orEmpty()}"
        }
    }

    fun forgetPhones(): String {
        if (running.get()) return "Wait until the chat has ended."
        identity.forgetPhones()
        contacts.forgetAll()
        ChatConnectionService.ensureReady(app)
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
        calling: String? = null,
        answeringRing: Boolean = false,
        run: (identityText: String, pins: String) -> String,
    ): String? {
        NativeBridge.load()?.let { return it }
        if (!running.compareAndSet(false, true)) return "A chat is already starting or open on this phone."
        stopRequested = false
        via = if ((first as? ConnectionState.Hosting)?.internet == true || (first as? ConnectionState.Joining)?.internet == true) {
            VIA_INTERNET
        } else {
            VIA_WIFI
        }
        report.start(role)
        peerDevice = null
        pendingCard = null
        stopNote = null
        callingDevice = calling
        answering = answeringRing
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

    /** Shows how the relay connection is doing while this phone hosts or joins. */
    private fun showLink(text: String) {
        synchronized(lock) {
            val current = mutableConnection.value
            if (current is ConnectionState.Hosting && !current.stopping) {
                mutableConnection.value = current.copy(link = text)
            } else if (current is ConnectionState.Joining) {
                mutableConnection.value = current.copy(link = text)
            }
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
        // Owner choice for 1.1.0: a remembered phone with an unchanged key connects without the
        // comparison. A new phone or a changed key still needs both people to compare the code.
        if (prompt.peerStatus == PeerStatus.KNOWN) answer(true)
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
                openDoorbell(prompt.peerDevice.ifEmpty { verifiedPeer.substringBefore(' ') })
                mutableConnection.value = ConnectionState.Connected(prompt)
                status.value = MlsSessionStatus.CONNECTED_MLS_VERIFIED
                description.value = connectedDescription(prompt)
                report.connected()
                ChatConnectionService.update(app, "Chat open")
            }
            "RECEIVED" -> {
                val text = rest.substringAfter('|', "")
                // A doorbell card is for this app, never for the chat.
                PayloadCodec.decodeCard(text)?.let { card ->
                    report.cardReceived()
                    val device = peerDevice
                    if (device == null) pendingCard = card else contacts.saveCard(device, card)
                    return
                }
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
        val answer = synchronized(lock) {
            drainEvents()
            running.set(false)
            stopRequested = false
            if (result.startsWith("PASS")) {
                runCatching { NativeBridge.nativeTakeVerifiedPeer() }.getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { identity.remember(it) }
            }
            report.finished(SessionText.forReport(result, internet = via == VIA_INTERNET))
            val next = queuedAnswer?.takeIf { !it.expired() }
            queuedAnswer = null
            mutableConnection.value = ConnectionState.Idle(if (next != null) null else shownResult(result))
            contacts.refresh()
            status.value = if (result.startsWith("FAIL")) MlsSessionStatus.ERROR else MlsSessionStatus.DISCONNECTED
            description.value = IDLE_DESCRIPTION
            next
        }
        // This phone stopped its own waiting to answer a call: join the caller now.
        if (answer != null) {
            val problem = joinRelay(answer.invite.room, answer.invite.token, ANSWER_ROLE, answeringRing = true)
            if (problem == null) return
            synchronized(lock) { mutableConnection.value = ConnectionState.Idle(problem) }
        }
        ChatConnectionService.stop(app)
    }

    /** The line the start screen shows after a session, with wording for calls. */
    private fun shownResult(result: String): String {
        val note = stopNote
        return when {
            note != null && !result.startsWith("PASS") -> note
            callingDevice != null && result.contains("Nobody joined") -> CALL_NO_ANSWER
            answering && result.contains("no longer waiting") -> ANSWER_TOO_LATE
            else -> SessionText.lastResult(result, report.chatSummary())
        }
    }

    /**
     * The chat opened with [device]: remember it in the contact list and send this phone's
     * doorbell card inside the MLS session, so the two phones can call each other later.
     */
    private fun openDoorbell(device: String) {
        if (device.isEmpty()) return
        peerDevice = device
        contacts.refresh()
        pendingCard?.let { contacts.saveCard(device, it) }
        pendingCard = null
        val card = runCatching { PayloadCodec.encodeCard(contacts.ownCard()) }.getOrNull() ?: return
        val reply = runCatching { NativeBridge.nativeChatSend(card) }.getOrDefault("")
        if (reply.startsWith("OK|")) report.cardSent()
    }

    private fun connectedDescription(prompt: SafetyPrompt): String {
        val which = when (prompt.peerStatus) {
            PeerStatus.KNOWN -> "the remembered phone"
            PeerStatus.CHANGED -> "the phone whose key changed"
            PeerStatus.NEW -> "the new phone"
        }
        val device = if (prompt.peerDevice.isEmpty()) "" else " (device ${PeerPins.formatDeviceId(prompt.peerDevice)})"
        return "Connected $via to $which$device."
    }

    companion object {
        private const val POLL_MILLIS = 120L
        private const val MAX_EVENTS_PER_POLL = 200
        const val IDLE_DESCRIPTION = "Not connected."

        /** A host waits as long as on the same Wi-Fi; a guest only until the relay pairs the phones. */
        private const val HOST_WAIT_MILLIS = 3 * 60_000L
        private const val GUEST_WAIT_MILLIS = 30_000L
        private const val VIA_INTERNET = "over the Internet relay"
        private const val VIA_WIFI = "over Wi-Fi"
        const val WIFI_LINK = "Same Wi-Fi only: the other phone connects straight to this phone."
        const val WIFI_CONNECTION = "Same Wi-Fi (direct connection)"
        const val RING_DELIVERED = "The other phone was notified. Waiting for it to answer…"
        const val RING_OFFLINE =
            "Not reachable now: the other phone is offline or not ready for chats. Try again later or send an invite."
        const val RING_BUSY = "Too many calls to this phone. Wait a minute and try again."
        const val RING_FAILED = "Could not reach the relay to call the other phone. Check that this phone is online."
        const val CALL_NO_ANSWER = "No answer. Try again later or send an invite."
        const val ANSWER_TOO_LATE = "The other phone stopped calling. Tap Chat to call it back."
        private const val BUSY_NOTE = "A chat is already starting or open on this phone."
        private const val ANSWER_ROLE = "guest, answering a remembered phone"
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
