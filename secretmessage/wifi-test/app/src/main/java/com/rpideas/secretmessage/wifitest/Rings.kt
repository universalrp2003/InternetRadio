package com.rpideas.secretmessage.wifitest

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A chat request from a remembered phone, waiting for this person to answer. */
internal data class IncomingRing(val id: String, val invite: RingInvite, val receivedAtElapsed: Long) {
    fun expired(nowElapsed: Long = SystemClock.elapsedRealtime()): Boolean = nowElapsed - receivedAtElapsed > Rings.ANSWER_WINDOW_MILLIS
}

/**
 * Chat requests that arrived at this phone's doorbell. The notification never shows who is calling
 * (owner choice: the name only after unlocking); the app shows the name. Room and token stay in
 * this process: the notification refers to a request by a random ID, so another app cannot make
 * this app join a room of its choosing.
 */
internal object Rings {
    /** The caller waits three minutes; the request expires a little earlier. */
    const val ANSWER_WINDOW_MILLIS = 170_000L

    private val mutablePending = MutableStateFlow<IncomingRing?>(null)
    val pending: StateFlow<IncomingRing?> = mutablePending.asStateFlow()

    /** Counts for the copied report (no names, no content). */
    @Volatile
    var shownCount = 0
        private set

    @Volatile
    var answeredCount = 0
        private set

    @Volatile
    var bothCallingCount = 0
        private set

    @Volatile
    var busyCount = 0
        private set

    /** Handles a ring from the relay; returns false if it was not a valid ring from a remembered phone. */
    fun received(context: Context, ring: ByteArray): Boolean {
        val invite = Contacts.book(context).openRing(ring, System.currentTimeMillis()) ?: return false
        val session = WifiChat.session(context)
        val incoming = IncomingRing(IdentityStore.randomHex(8), invite, SystemClock.elapsedRealtime())
        when (session.ringDecision(invite.contact.deviceId)) {
            RingPolicy.Decision.SHOW -> {
                shownCount++
                mutablePending.value = incoming
                RingNotifier.show(context.applicationContext, incoming)
            }
            RingPolicy.Decision.ANSWER_NOW -> {
                bothCallingCount++
                session.answerRing(incoming)
            }
            RingPolicy.Decision.KEEP_CALLING -> bothCallingCount++
            // One chat at a time: while a chat starts or is open, the caller just hears no answer.
            RingPolicy.Decision.BUSY -> busyCount++
        }
        return true
    }

    fun countAnswer() {
        answeredCount++
    }

    /** The waiting request with [id] (or any, if [id] is null) if it has not expired; it is then cleared. */
    fun take(context: Context, id: String?): IncomingRing? {
        val current = mutablePending.value ?: return null
        if (id != null && current.id != id) return null
        dismiss(context)
        return current.takeIf { !it.expired() }
    }

    fun dismiss(context: Context) {
        mutablePending.value = null
        RingNotifier.cancel(context.applicationContext)
    }
}

/**
 * What a phone does with a call that arrives, from its own state. In the 1.1.0 phone test both
 * phones tapped Chat at about the same time, and each waited for the other while ignoring the
 * other's call. Now the phone with the lower device ID joins the other's call and the other keeps
 * waiting for it, so the two phones decide the same way without talking to each other.
 */
internal object RingPolicy {
    enum class Decision {
        /** Show the request: dialog, banner and notification. */
        SHOW,

        /** Both phones are calling each other and this one has the lower device ID: join the other's call. */
        ANSWER_NOW,

        /** Both phones are calling each other and this one has the higher device ID: the other phone joins. */
        KEEP_CALLING,

        /** A chat is starting or open: the caller hears no answer. */
        BUSY,
    }

    fun decide(state: ConnectionState, running: Boolean, calling: String?, caller: String, own: String?): Decision =
        when (state) {
            is ConnectionState.Idle -> if (running) Decision.BUSY else Decision.SHOW
            is ConnectionState.Hosting -> when {
                // Someone already joined this phone's room; the chat is starting.
                state.link == RelayText.PAIRED -> Decision.BUSY
                state.stopping -> Decision.SHOW
                calling == caller && own != null -> if (own < caller) Decision.ANSWER_NOW else Decision.KEEP_CALLING
                else -> Decision.SHOW
            }
            else -> Decision.BUSY
        }
}

internal object RingNotifier {
    const val ACTION_ANSWER = "com.rpideas.secretmessage.ANSWER_RING"
    const val EXTRA_RING_ID = "ring_id"
    private const val CHANNEL_ID = "chat_requests"
    private const val NOTIFICATION_ID = 42

    fun show(context: Context, ring: IncomingRing) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        Tr.init(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(CHANNEL_ID, tr("Chat requests"), NotificationManager.IMPORTANCE_HIGH)
            channel.description = tr("When a remembered phone wants to chat. It never shows names or messages.")
            manager.createNotificationChannel(channel)
        }
        val answer = PendingIntent.getActivity(
            context,
            2,
            Intent(context, WifiTestActivity::class.java)
                .setAction(ACTION_ANSWER)
                .putExtra(EXTRA_RING_ID, ring.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Secret Message")
            .setContentText(tr("New chat request"))
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Secret Message")
            .setContentText(tr("Someone you remember wants to chat. Tap to answer."))
            .setContentIntent(answer)
            .addAction(0, tr("Answer"), answer)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setTimeoutAfter(Rings.ANSWER_WINDOW_MILLIS)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
