package com.rpideas.secretmessage.wifitest

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * The Internet path of the app. Both phones open a WebSocket (TLS) to the owner's relay
 * (secretmessage/relay) for the same random room, and the relay forwards bytes between them.
 *
 * The native MLS session runs unchanged over one end of a local socket pair
 * ([NativeBridge.nativeOpenRelayPipe]); this class copies bytes between the other end and the
 * WebSocket, both ways, unchanged. The relay never sees message content: everything after setup is
 * inside the MLS session, and a relay that tried to stand in for the other phone would make the two
 * safety codes differ. Nothing is stored or logged here.
 */
internal class RelayLink(
    private val relayUrl: String,
    private val room: String,
    private val role: Role,
    private val onStatus: (String) -> Unit,
) {
    enum class Role(val query: String) { HOST("host"), GUEST("guest") }

    private val lock = Any()
    private val decided = CountDownLatch(1)

    @Volatile
    private var paired = false

    @Volatile
    private var ended: String? = null
    private var pipe: ParcelFileDescriptor? = null
    private var toSession: FileOutputStream? = null
    private val startedAt = SystemClock.elapsedRealtime()

    @Volatile
    private var connectedAt = 0L

    @Volatile
    private var pairedAt = 0L

    /** What happened on the relay, for the copied report: timings and how it ended, no content. */
    @Volatile
    var summary: String = "Internet relay: not started"
        private set

    /**
     * Connects to the relay and waits up to [waitMillis] for the other phone (or until [cancelled]
     * says to stop), then runs [session], the blocking native call, and returns its status line.
     * Returns a `FAIL: …` line instead if the chat could not start.
     */
    fun run(waitMillis: Long, cancelled: () -> Boolean, session: () -> String): String {
        val descriptor = runCatching { NativeBridge.nativeOpenRelayPipe() }.getOrDefault(-1)
        if (descriptor < 0) {
            summary = "Internet relay: the local connection could not be prepared"
            return "FAIL: Could not prepare the connection to the relay."
        }
        val local = ParcelFileDescriptor.adoptFd(descriptor)
        val fromSession = FileInputStream(local.fileDescriptor)
        synchronized(lock) {
            pipe = local
            toSession = FileOutputStream(local.fileDescriptor)
        }
        onStatus(RelayText.CONNECTING)
        val request = Request.Builder().url("$relayUrl/v1/rooms/$room?role=${role.query}").build()
        val webSocket = client.newWebSocket(request, listener)
        var uplink: Thread? = null
        try {
            awaitPartner(waitMillis, cancelled)?.let { return it }
            uplink = thread(name = "secretmessage-relay-uplink", isDaemon = true) {
                copyToRelay(fromSession, webSocket)
            }
            return session()
        } finally {
            shutDownPipe()
            webSocket.close(NORMAL_CLOSURE, null)
            uplink?.join(UPLINK_JOIN_MILLIS)
            // Writes into the pipe also hold the lock, so none can reach a closed descriptor.
            synchronized(lock) {
                toSession = null
                pipe = null
                runCatching { local.close() }
            }
            summary = RelayText.summary(
                connectedAfterMillis = if (connectedAt > 0) connectedAt - startedAt else null,
                pairedAfterMillis = if (pairedAt > 0) pairedAt - startedAt else null,
                ended = ended,
            )
        }
    }

    /** Null once both phones are in the room; otherwise the FAIL line for why the chat did not start. */
    private fun awaitPartner(waitMillis: Long, cancelled: () -> Boolean): String? {
        val deadline = SystemClock.elapsedRealtime() + waitMillis
        while (true) {
            if (decided.await(WAIT_STEP_MILLIS, TimeUnit.MILLISECONDS)) {
                return if (paired) null else "FAIL: " + (ended ?: RelayText.closed(NORMAL_CLOSURE, ""))
            }
            if (cancelled()) return "FAIL: Stopped waiting for the other phone to join."
            if (SystemClock.elapsedRealtime() >= deadline) {
                return "FAIL: " + RelayText.timedOut(role == Role.HOST, waitMillis)
            }
        }
    }

    /** Copies what the native session writes to the relay until either side ends. */
    private fun copyToRelay(fromSession: FileInputStream, webSocket: WebSocket) {
        val buffer = ByteArray(CHUNK_BYTES)
        try {
            while (true) {
                val read = fromSession.read(buffer)
                if (read < 0) break
                if (read > 0 && !webSocket.send(buffer.toByteString(0, read))) break
            }
        } catch (closed: IOException) {
            // The pipe was shut down because the relay connection ended.
        }
        // Sends anything still queued, then a close frame; the relay then tells the other phone.
        webSocket.close(NORMAL_CLOSURE, null)
    }

    /** The native session reads what was already passed to it, then the end of the stream. */
    private fun shutDownPipe() {
        synchronized(lock) {
            val local = pipe ?: return
            try {
                Os.shutdown(local.fileDescriptor, OsConstants.SHUT_RDWR)
            } catch (alreadyClosed: ErrnoException) {
                // Nothing left to stop.
            }
        }
    }

    private fun end(why: String) {
        if (ended == null) ended = why
        decided.countDown()
        shutDownPipe()
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            connectedAt = SystemClock.elapsedRealtime()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            when (text) {
                "waiting" -> onStatus(RelayText.WAITING)
                "paired" -> {
                    pairedAt = SystemClock.elapsedRealtime()
                    paired = true
                    onStatus(RelayText.PAIRED)
                    decided.countDown()
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            // OkHttp delivers messages in order on one thread, so the session reads them in order.
            // A write blocks only while the session is busy; once it ends, writes fail at once.
            val delivered = synchronized(lock) {
                val out = toSession ?: return
                try {
                    bytes.write(out)
                    true
                } catch (closed: IOException) {
                    false
                }
            }
            if (!delivered) webSocket.cancel()
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            end(RelayText.closed(code, reason))
            webSocket.close(NORMAL_CLOSURE, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            end(RelayText.closed(code, reason))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            end(RelayText.failed(role == Role.GUEST, response?.code, t.javaClass.simpleName, t.message))
        }
    }

    companion object {
        /** The owner's relay (Cloudflare free plan), deployed by secret-message-relay.yml. */
        const val RELAY_URL = "wss://secretmessage-relay.universalrp2003.workers.dev"

        private const val NORMAL_CLOSURE = 1000
        private const val CHUNK_BYTES = 64 * 1024
        private const val WAIT_STEP_MILLIS = 200L
        private const val UPLINK_JOIN_MILLIS = 2_000L

        /** Pings keep mobile-network connections open and notice a dead one within about 40 s. */
        private const val PING_SECONDS = 20L

        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .pingInterval(PING_SECONDS, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build()
        }
    }
}

/** Plain words for what the relay connection did, for the screen and the copied report. */
internal object RelayText {
    const val CONNECTING = "Connecting to the relay…"
    const val WAITING = "Connected to the relay. Waiting for the other phone…"
    const val PAIRED = "The other phone joined. Starting the secure session…"

    /** The relay's close codes (secretmessage/relay/src/index.js). */
    fun closed(code: Int, reason: String): String = when (code) {
        4001 -> "The other phone left the chat or lost its connection."
        4002 -> "The relay's time limit for one chat was reached."
        4003 -> "The relay closed the chat because the data broke its rules."
        1000 -> "The relay connection closed."
        else -> "The relay connection closed (code $code" + (if (reason.isBlank()) ")." else ": $reason).")
    }

    /** A connection that failed, with the relay's HTTP answer if there was one. */
    fun failed(guest: Boolean, httpCode: Int?, error: String, message: String?): String = when (httpCode) {
        404 -> if (guest) {
            "The first phone is no longer waiting for this invite. Ask for a new invite."
        } else {
            "The relay did not accept this chat."
        }
        409 -> "This invite is already in use. Ask for a new invite."
        null -> "Could not reach the Secret Message relay ($error" +
            (if (message.isNullOrBlank()) "" else ": $message") + "). Check that this phone is online."
        else -> "The relay refused the connection (HTTP $httpCode)."
    }

    fun timedOut(host: Boolean, waitMillis: Long): String = if (host) {
        "Nobody joined within ${waitMillis / 60_000} minutes. Start a new chat and send the new invite."
    } else {
        "The relay did not connect the two phones in time. Ask for a new invite."
    }

    fun summary(connectedAfterMillis: Long?, pairedAfterMillis: Long?, ended: String?): String {
        val parts = mutableListOf(
            connectedAfterMillis?.let { "connected in ${seconds(it)}" } ?: "not connected",
        )
        pairedAfterMillis?.let { parts += "other phone joined after ${seconds(it)}" }
        ended?.let { parts += "ended: $it" }
        return "Internet relay: " + parts.joinToString("; ")
    }

    private fun seconds(millis: Long): String = String.format(Locale.ROOT, "%.1f s", millis / 1000.0)
}
