package com.universalrp.tamilnadufm.audio

import android.app.*
import android.content.*
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import com.universalrp.tamilnadufm.MainActivity
import com.universalrp.tamilnadufm.player.PlayerBus

/** PulseEQ-style session effects embedded in Radio. No capture or claim of universal coverage. */
class ExternalEqService : Service() {
    private val effects = mutableMapOf<Int, Equalizer>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var registered = false
    private var global: Equalizer? = null
    private var globalResult = "Global mix not requested"
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
            if (id <= 0 || id == PlayerBus.player?.audioSessionId) return
            if (intent.action == AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION) {
                effects.remove(id)?.let { runCatching { it.release() } }
            } else if (intent.action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION && id !in effects && effects.size < 32) {
                runCatching { effects[id] = Equalizer(0, id) }
            }
            apply()
        }
    }
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        AudioFx.load(this)
        val channel = NotificationChannel("external_eq", "Other-app equalizer", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { stopSelf(); return START_NOT_STICKY }
        val stop = PendingIntent.getService(this, 72, Intent(this, javaClass).setAction("stop"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 73, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notice = NotificationCompat.Builder(this, "external_eq")
            .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Ramesh Radio • Other-app EQ")
            .setContentText("Listening for compatible audio sessions; coverage varies by player")
            .setContentIntent(open).setOngoing(true).addAction(0, "Stop EQ", stop).build()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(72, notice, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(72, notice)
        } catch (_: Exception) { status.value = "Android blocked the background EQ service"; stopSelf(); return START_NOT_STICKY }
        running.value = true
        if (!registered) {
            val filter = IntentFilter(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply { addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION) }
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            registered = true
            scope.launch { while (isActive) { apply(); delay(1000) } }
        }
        if (intent?.action == "global" && global == null) {
            globalResult = try {
                global = Equalizer(0, 0)
                "Global mix attached; Android may still bypass it for some outputs"
            } catch (_: Exception) { "This phone blocks global output effects; compatible sessions only" }
        }
        apply()
        return START_NOT_STICKY
    }
    private fun apply() {
        val gains = AudioFx.effectiveGains()
        val s = AudioFx.settings
        fun configure(eq: Equalizer): Boolean = runCatching {
            if (!eq.hasControl()) return@runCatching false
            val range = eq.bandLevelRange
            for (i in 0 until eq.numberOfBands.toInt()) {
                val band = i.toShort()
                val gain = AudioFx.interpolate(eq.getCenterFreq(band) / 1000f, gains) + s.preampDb
                eq.setBandLevel(band, (gain * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort())
            }
            eq.enabled = s.enabled
            true
        }.getOrDefault(false)
        val ownPlayback = PlayerBus.state.value.isPlaying
        val hasGlobal = if (ownPlayback) {
            runCatching { global?.enabled = false }
            false // Avoid applying both the global curve and our own session curve.
        } else global?.let { configure(it) } == true
        val active = effects.values.count { eq ->
            if (hasGlobal) { runCatching { eq.enabled = false }; false } else configure(eq)
        }
        status.value = (if (ownPlayback && global != null) "Global mix paused while Radio plays to avoid double EQ" else globalResult) + ". $active compatible session(s) controlled. " +
            (if (s.enabled) "Uses the radio EQ curve; device band count may differ." else "EQ curve is switched off.")
    }
    override fun onDestroy() {
        scope.cancel()
        if (registered) unregisterReceiver(receiver)
        effects.values.forEach { runCatching { it.release() } }
        effects.clear()
        runCatching { global?.release() }
        running.value = false
        status.value = "Other-app EQ stopped"
        super.onDestroy()
    }
    companion object {
        val running = MutableStateFlow(false)
        val status = MutableStateFlow("Off — other players must expose compatible audio sessions")
    }
}
