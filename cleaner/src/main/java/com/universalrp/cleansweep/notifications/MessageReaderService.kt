package com.universalrp.cleansweep.notifications

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationCompat
import com.universalrp.cleansweep.data.AppLang
import com.universalrp.cleansweep.data.Lang
import com.universalrp.cleansweep.voice.Announcer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest

/** Opt-in Android notification access. No backlog replay, content persistence, microphone or remote translation. */
class MessageReaderService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = linkedMapOf<String, Job>()
    private val seen = linkedMapOf<String, String>()
    private val lastSender = linkedMapOf<String, Long>()
    private var epoch = 0L
    private var connectedAt = Long.MAX_VALUE
    private val privacyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (NotificationReaderPrefs.load(this@MessageReaderService).unlockedOnly && intent?.action == Intent.ACTION_SCREEN_OFF) {
                epoch++; jobs.values.forEach { it.cancel() }; jobs.clear(); Announcer.cancelNotifications()
            }
        }
    }
    private fun allowed(config: NotificationReaderConfig): Boolean {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (manager?.currentInterruptionFilter in setOf(NotificationManager.INTERRUPTION_FILTER_NONE,
            NotificationManager.INTERRUPTION_FILTER_PRIORITY, NotificationManager.INTERRUPTION_FILTER_ALARMS)) return false
        return !config.unlockedOnly || ((getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceLocked == false &&
            (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive == true)
    }
    private val preferences = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        epoch++; jobs.values.forEach { it.cancel() }; jobs.clear(); Announcer.cancelNotifications()
        val selected = NotificationReaderPrefs.load(this).rules.map { it.packageName }.toSet()
        sendersState.value = sendersState.value.filterKeys { it in selected }
    }
    override fun onCreate() { super.onCreate(); NotificationReaderPrefs.prefs(this).registerOnSharedPreferenceChangeListener(preferences)
        registerReceiver(privacyReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
    override fun onListenerConnected() { connectedAt = System.currentTimeMillis(); connectedState.value = true; statusState.value = "Notification listener connected" }
    override fun onListenerDisconnected() {
        connectedState.value = false; epoch++; jobs.values.forEach { it.cancel() }; jobs.clear(); Announcer.cancelNotifications()
        statusState.value = "Notification listener disconnected"
    }
    override fun onDestroy() {
        NotificationReaderPrefs.prefs(this).unregisterOnSharedPreferenceChangeListener(preferences)
        runCatching { unregisterReceiver(privacyReceiver) }
        scope.cancel(); connectedState.value = false; sendersState.value = emptyMap(); Announcer.cancelNotifications(); super.onDestroy()
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        jobs.remove(sbn.key)?.cancel()
        if (seen.remove(sbn.key) != null) Announcer.cancelNotifications()
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val config = NotificationReaderPrefs.load(this)
        val rule = config.rules.firstOrNull { it.packageName == sbn.packageName } ?: return
        if (!config.enabled || sbn.postTime < connectedAt || sbn.packageName == packageName || !NotificationReadingPolicy.fresh(sbn.postTime, System.currentTimeMillis())) return
        val notification = sbn.notification
        val excluded = Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE
        if (notification.flags and excluded != 0 || notification.category in setOf(Notification.CATEGORY_CALL, Notification.CATEGORY_TRANSPORT,
                Notification.CATEGORY_SERVICE, Notification.CATEGORY_PROGRESS)) return
        if (!allowed(config)) return
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        val message = style?.messages?.lastOrNull()
        if (message != null && !NotificationReadingPolicy.fresh(message.timestamp, System.currentTimeMillis())) return
        val sender = NotificationReadingPolicy.name(message?.person?.name?.toString()) ?: NotificationReadingPolicy.name(notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()) ?: return
        if (NotificationReadingPolicy.aggregateTitle(sender)) return
        val appLabel = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrNull()
        if (sender.equals(appLabel, true) && message == null) return
        val raw = message?.text?.toString() ?: notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (raw.isBlank() || raw.length > 10_000 || notification.extras.containsKey(Notification.EXTRA_PROGRESS)) return
        // Sender candidates stay in memory for explicit selections only, and only for already-selected applications.
        sendersState.value = (sendersState.value + (sbn.packageName to (sendersState.value[sbn.packageName].orEmpty() + sender).take(30).toSet())).entries.take(100).associate { it.toPair() }
        if (!NotificationReadingPolicy.matches(rule, sender)) return
        val token = MessageDigest.getInstance("SHA-256").digest((sender + "\u001f" + raw + "\u001f" + (message?.timestamp ?: 0L)).toByteArray())
            .joinToString("") { "%02x".format(it) }
        if (seen[sbn.key] == token) return
        seen[sbn.key] = token; if (seen.size > 128) seen.remove(seen.keys.first())
        val senderKey = sbn.packageName + ":" + NotificationReadingPolicy.normalizedSender(sender)
        val now = SystemClock.elapsedRealtime()
        if (!rule.fullMessage && now - (lastSender[senderKey] ?: -30_000) < 15_000) return
        jobs.remove(sbn.key)?.cancel()
        if (jobs.size >= 8) jobs.remove(jobs.keys.first())?.cancel()
        val mine = epoch
        jobs[sbn.key] = scope.launch {
            try {
                delay(900) // Coalesce a notification update burst; never read aggregate counts/ribbons.
                val language = Lang.languageIn(this@MessageReaderService)
                var body: String? = null
                if (rule.fullMessage && !NotificationReadingPolicy.sensitive(raw)) {
                    body = try { LocalMessageTranslator.translate(raw, if (language == AppLang.TA) "ta" else "en") }
                    catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
                    if (body == null) statusState.value = "Translation unavailable; sender name only"
                }
                ensureActive()
                if (mine != epoch || !NotificationReaderPrefs.load(this@MessageReaderService).enabled || !allowed(config) || Lang.languageIn(this@MessageReaderService) != language) return@launch
                val spoken = NotificationReadingPolicy.speech(sender, body, rule.fullMessage)
                lastSender[senderKey] = SystemClock.elapsedRealtime()
                if (lastSender.size > 128) lastSender.remove(lastSender.keys.first())
                // Installed NON-NETWORK TTS only. Private text is never submitted to an online speech/AI provider.
                Announcer.speakNotification(this@MessageReaderService, spoken, language, "message:$token") {
                    mine == epoch && NotificationReaderPrefs.load(this@MessageReaderService).enabled && allowed(config) && Lang.languageIn(this@MessageReaderService) == language &&
                        NotificationReadingPolicy.fresh(sbn.postTime, System.currentTimeMillis())
                }
            } finally { if (mine == epoch && jobs[sbn.key] == coroutineContext[Job]) jobs.remove(sbn.key) }
        }
    }
    companion object {
        private val connectedState = MutableStateFlow(false)
        private val statusState = MutableStateFlow("Notification reading is off")
        private val sendersState = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
        val connected: StateFlow<Boolean> = connectedState
        val status: StateFlow<String> = statusState
        val recentSenders: StateFlow<Map<String, Set<String>>> = sendersState
        fun accessGranted(context: Context): Boolean = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
        fun openAccess(context: Context) {
            val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
