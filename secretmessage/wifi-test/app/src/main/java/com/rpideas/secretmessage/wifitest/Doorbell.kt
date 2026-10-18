package com.rpideas.secretmessage.wifitest

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * "Ready for chats" (owner choice for 1.1.0: always ready, without Google services). While ready,
 * this phone keeps one WebSocket open to its inbox at the owner's relay. A remembered phone that
 * wants to chat posts a small encrypted ring there; the relay passes it to this socket and keeps
 * nothing. If this phone is offline the caller is told "not reachable" at once.
 *
 * The socket is held by [ChatConnectionService] (a foreground service keeps network access when
 * the screen is off). OkHttp pings it every 25 seconds; a keep-alive alarm about every five minutes
 * reconnects it if Android paused the app, and a network change reconnects it at once.
 */
internal object Doorbell {
    enum class Link { OFF, CONNECTING, ONLINE, RETRYING }

    enum class RingResult { DELIVERED, OFFLINE, BUSY, FAILED }

    enum class Presence { ONLINE, OFFLINE, UNKNOWN }

    private val mutableLink = MutableStateFlow(Link.OFF)
    val link: StateFlow<Link> = mutableLink.asStateFlow()

    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var socket: WebSocket? = null
    private var wanted = false
    private var attempt = 0
    private var lastNetwork: Network? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var pongCheck: Runnable? = null

    /** Counts for the copied report (no content, no addresses). */
    @Volatile
    var connects = 0
        private set

    @Volatile
    var ringsReceived = 0
        private set

    @Volatile
    var ringsDropped = 0
        private set

    @Volatile
    var lastProblem = ""
        private set

    private val retry = Runnable {
        synchronized(lock) {
            if (wanted && socket == null) connectLocked()
        }
    }

    /** Opens the inbox socket (once) and keeps it open until [stop]. */
    fun start(context: Context) {
        synchronized(lock) {
            app = context.applicationContext
            if (wanted) {
                if (socket == null) connectLocked()
                return
            }
            wanted = true
            attempt = 0
            registerNetworkLocked()
            connectLocked()
        }
        KeepAlive.schedule(context.applicationContext)
    }

    fun stop() {
        val context = synchronized(lock) {
            wanted = false
            handler.removeCallbacks(retry)
            pongCheck?.let { handler.removeCallbacks(it) }
            pongCheck = null
            socket?.close(NORMAL_CLOSURE, null)
            socket = null
            unregisterNetworkLocked()
            mutableLink.value = Link.OFF
            app
        }
        context?.let { KeepAlive.cancel(it) }
    }

    /** Reconnects with this phone's current inbox (after Reset key gave it a new one). */
    fun restart(context: Context) {
        synchronized(lock) {
            if (!wanted) return
            socket?.close(NORMAL_CLOSURE, null)
            socket = null
            attempt = 0
            connectLocked()
        }
    }

    /**
     * From the keep-alive alarm: reconnects if the socket is gone; otherwise sends a ping that the
     * relay answers without waking its inbox, and reconnects if no answer comes within 15 seconds.
     */
    fun poke() {
        synchronized(lock) {
            if (!wanted) return
            val current = socket
            if (current == null) {
                handler.removeCallbacks(retry)
                connectLocked()
                return
            }
            if (!current.send("ping")) {
                replaceLocked("the ping could not be sent")
                return
            }
            pongCheck?.let { handler.removeCallbacks(it) }
            val check = Runnable {
                synchronized(lock) {
                    if (socket === current) replaceLocked("no answer to a ping")
                }
            }
            pongCheck = check
            handler.postDelayed(check, PONG_WAIT_MILLIS)
        }
    }

    /** Rings [inboxId] with [ring]; blocks, so call it from a worker thread. */
    fun ring(inboxId: String, ring: ByteArray): RingResult {
        val request = Request.Builder()
            .url("$HTTPS_BASE/v1/inbox/$inboxId/ring")
            .post(ring.toRequestBody("application/octet-stream".toMediaType()))
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> RingResult.DELIVERED
                    404 -> RingResult.OFFLINE
                    429 -> RingResult.BUSY
                    else -> RingResult.FAILED
                }
            }
        } catch (failure: IOException) {
            RingResult.FAILED
        }
    }

    /** Whether [inboxId]'s phone is ready for chats now; blocks, so call it from a worker thread. */
    fun presence(inboxId: String): Presence {
        val request = Request.Builder().url("$HTTPS_BASE/v1/inbox/$inboxId/presence").get().build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()?.trim().orEmpty()
                when {
                    response.code != 200 -> Presence.UNKNOWN
                    body == "online" -> Presence.ONLINE
                    body == "offline" -> Presence.OFFLINE
                    else -> Presence.UNKNOWN
                }
            }
        } catch (failure: IOException) {
            Presence.UNKNOWN
        }
    }

    private fun connectLocked() {
        val context = app ?: return
        val secret = Contacts.book(context).ownSecret()
        val request = Request.Builder()
            .url("${RelayLink.RELAY_URL}/v1/inbox/${ContactBook.inboxIdFor(secret)}")
            .header("x-inbox-key", secret)
            .build()
        mutableLink.value = Link.CONNECTING
        socket = socketClient.newWebSocket(request, Listener())
    }

    /** Drops [socket] and connects again at once (for example after a network change). */
    private fun replaceLocked(why: String) {
        lastProblem = why
        socket?.cancel()
        socket = null
        handler.removeCallbacks(retry)
        connectLocked()
    }

    private fun lostLocked(webSocket: WebSocket, why: String, slowRetry: Boolean) {
        if (socket !== webSocket) return
        socket = null
        lastProblem = why
        if (!wanted) return
        mutableLink.value = Link.RETRYING
        val delay = if (slowRetry) SLOW_RETRY_MILLIS else minOf(MAX_RETRY_MILLIS, FIRST_RETRY_MILLIS shl minOf(attempt, 5))
        attempt++
        handler.removeCallbacks(retry)
        handler.postDelayed(retry, delay)
    }

    private fun registerNetworkLocked() {
        val context = app ?: return
        if (networkCallback != null) return
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                synchronized(lock) {
                    val changed = lastNetwork != null && lastNetwork != network
                    lastNetwork = network
                    if (!wanted) return
                    if (socket == null) {
                        handler.removeCallbacks(retry)
                        attempt = 0
                        connectLocked()
                    } else if (changed) {
                        replaceLocked("the network changed")
                    }
                }
            }
        }
        runCatching { connectivity.registerDefaultNetworkCallback(callback) }
            .onSuccess { networkCallback = callback }
    }

    private fun unregisterNetworkLocked() {
        val context = app ?: return
        val callback = networkCallback ?: return
        networkCallback = null
        lastNetwork = null
        runCatching { context.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback) }
    }

    private class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronized(lock) {
                if (socket !== webSocket) return
                attempt = 0
                connects++
                lastProblem = ""
                mutableLink.value = Link.ONLINE
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text == "pong") {
                synchronized(lock) {
                    if (socket === webSocket) {
                        pongCheck?.let { handler.removeCallbacks(it) }
                        pongCheck = null
                    }
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            val context = synchronized(lock) { if (socket === webSocket) app else null } ?: return
            if (Rings.received(context, bytes.toByteArray())) ringsReceived++ else ringsDropped++
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL_CLOSURE, null)
            synchronized(lock) {
                // 4004: a newer connection with this inbox replaced this one (another install that
                // restored this phone's data); try again only now and then.
                lostLocked(webSocket, "closed by the relay ($code)", slowRetry = code == CLOSE_REPLACED)
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            synchronized(lock) { lostLocked(webSocket, "closed ($code)", slowRetry = code == CLOSE_REPLACED) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            synchronized(lock) {
                val why = response?.let { "HTTP ${it.code}" } ?: t.javaClass.simpleName
                lostLocked(webSocket, why, slowRetry = response?.code == 403)
            }
        }
    }

    private const val NORMAL_CLOSURE = 1000
    private const val CLOSE_REPLACED = 4004
    private const val FIRST_RETRY_MILLIS = 2_000L
    private const val MAX_RETRY_MILLIS = 60_000L
    private const val SLOW_RETRY_MILLIS = 5 * 60_000L
    private const val PONG_WAIT_MILLIS = 15_000L
    private val HTTPS_BASE = RelayLink.RELAY_URL.replaceFirst("wss://", "https://")

    private val socketClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}

/** Whether this phone should keep its doorbell open: on by default, off in Settings. */
internal object ReadyMode {
    private const val PREFS = "secretmessage_ready"
    private const val KEY_READY = "ready_for_chats"

    fun enabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_READY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_READY, enabled).apply()
        ChatConnectionService.ensureReady(context)
    }

    /** Ready mode runs only when it is on and a remembered phone has shared its doorbell. */
    fun wanted(context: Context): Boolean = enabled(context) && Contacts.book(context).anyDoorbell()
}

/** About every five minutes (Android may wait longer in deep sleep), checks the doorbell. */
internal object KeepAlive {
    private const val INTERVAL_MILLIS = 5 * 60_000L

    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            alarms.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + INTERVAL_MILLIS,
                pending(context),
            )
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context))
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        3,
        Intent(context, KeepAliveReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** The keep-alive alarm: holds the CPU awake briefly while the doorbell reconnects or pings. */
class KeepAliveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SecretMessage:doorbell")
            ?.acquire(WAKE_MILLIS)
        if (!ReadyMode.wanted(context)) return
        if (ChatConnectionService.isRunning()) {
            Doorbell.poke()
        } else {
            ChatConnectionService.ensureReady(context)
        }
        KeepAlive.schedule(context)
    }

    private companion object {
        const val WAKE_MILLIS = 20_000L
    }
}

/** Turns ready mode back on after the phone restarts or the app is updated. */
class ReadyStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> ChatConnectionService.ensureReady(context)
        }
    }
}
