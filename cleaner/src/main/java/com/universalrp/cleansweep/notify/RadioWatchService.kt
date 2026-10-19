package com.universalrp.cleansweep.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.universalrp.cleansweep.MainActivity
import com.universalrp.cleansweep.R
import com.universalrp.cleansweep.data.DataUsageTracker
import com.universalrp.cleansweep.data.NetworkSplit
import com.universalrp.cleansweep.data.UsageStats
import com.universalrp.cleansweep.voice.Announcer

/**
 * v2.47: the always-on half of the 4G/5G feature.
 *
 * Two jobs, both impossible from an hourly WorkManager pass:
 *
 *  1. **Hear the radio change.** TelephonyCallback delivers a 5G↔LTE change within a second;
 *     an hourly worker would announce yesterday's news.
 *  2. **Split the byte counter.** Every [SAMPLE_MS] the cumulative mobile counter is read and
 *     the delta is filed under the radio that was active, because Android itself only keeps
 *     one mobile total.
 *
 * Started only when the user turns on "count 4G/5G separately" or "announce 4G ⇄ 5G" — it is
 * not part of the default footprint. Runs as a `dataSync` foreground service (local work,
 * no network transfer) with an unimportant notification.
 */
class RadioWatchService : Service() {

    companion object {
        private const val CHANNEL = "radio_watch"
        private const val NOTIFICATION_ID = 4211
        private const val SAMPLE_MS = 3_000L

        /** A modem can flap for a few seconds; do not narrate every hop. */
        private const val MIN_ANNOUNCE_GAP_MS = 20_000L

        fun start(context: Context) {
            val intent = Intent(context, RadioWatchService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RadioWatchService::class.java))
        }

        /** True when either split feature is on, i.e. the service has a reason to exist. */
        fun needed(context: Context): Boolean =
            NetworkSplit.separateEnabled(context) || NetworkSplit.announceEnabled(context)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var telephonyCallback: Any? = null
    private var legacyListener: PhoneStateListener? = null

    /** Updated by the display-info listener; NR here means a real 5G bearer (incl. NSA). */
    @Volatile
    private var overrideType: Int = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE

    @Volatile
    private var lastAnnouncedAt = 0L

    @Volatile
    private var lastAnnouncedNr: Boolean? = null

    private val poll = object : Runnable {
        override fun run() {
            runCatching { sample() }
            handler.postDelayed(this, SAMPLE_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("4G/5G watch is on"))

        NetworkSplit.resetDayIfNeeded(this)
        registerRadioListener()

        // Adopt the current total as the baseline: usage from before the user switched this
        // on must not be counted again.
        if (NetworkSplit.lastCumulative(this) < 0) {
            NetworkSplit.setLastCumulative(this, todayMobileBytes())
        }
        handler.post(poll)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (telephonyCallback as? TelephonyCallback)?.let { cb ->
                runCatching { tm?.unregisterTelephonyCallback(cb) }
            }
        }
        legacyListener?.let { listener ->
            @Suppress("DEPRECATION")
            runCatching { tm?.listen(listener, PhoneStateListener.LISTEN_NONE) }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------- sampling
    private fun todayMobileBytes(): Long = UsageStats.read(this).todayMobileBytes ?: 0L

    private fun sample() {
        NetworkSplit.resetDayIfNeeded(this)

        val cumulative = todayMobileBytes()
        if (cumulative <= 0) return

        val last = NetworkSplit.lastCumulative(this)
        if (last < 0) {
            NetworkSplit.setLastCumulative(this, cumulative)
            return
        }
        val delta = cumulative - last
        when {
            delta > 0 -> {
                NetworkSplit.setLastCumulative(this, cumulative)
                if (NetworkSplit.separateEnabled(this)) {
                    NetworkSplit.addDelta(this, delta, isOn5g())
                }
            }
            // Midnight rollover or a reboot reset the framework counter.
            delta < 0 -> NetworkSplit.setLastCumulative(this, cumulative)
        }
        refreshNotification()
    }

    // ------------------------------------------------------------ radio state
    /**
     * Most Indian 5G is NSA: the modem still reports LTE as the data network type while a
     * 5G carrier carries the traffic, and only TelephonyDisplayInfo says so. Checking
     * dataNetworkType alone shows 4G on those phones.
     */
    internal fun isOn5g(): Boolean {
        if (!onCellular()) return false
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return false
        val type = try {
            tm.dataNetworkType
        } catch (e: Exception) {
            TelephonyManager.NETWORK_TYPE_UNKNOWN
        }
        if (type == TelephonyManager.NETWORK_TYPE_NR) return true
        return when (overrideType) {
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE -> true
            else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                overrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED
        }
    }

    private fun onCellular(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private fun registerRadioListener() {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cb = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                    val before = overrideType
                    overrideType = info.overrideNetworkType
                    if (before != overrideType) maybeAnnounce()
                }
            }
            telephonyCallback = cb
            runCatching { tm.registerTelephonyCallback(mainExecutor, cb) }
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                    overrideType = info.overrideNetworkType
                    maybeAnnounce()
                }
            }
            legacyListener = listener
            @Suppress("DEPRECATION")
            runCatching { tm.listen(listener, PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED) }
        }
    }

    /**
     * Speaks only on an actual NR ⇄ LTE transition, never on 3G/2G noise, and at most once
     * every 20 seconds. [Announcer.speak] applies the user's own switch, quiet hours and the
     * in-app volume, so none of those rules are duplicated here.
     */
    private fun maybeAnnounce() {
        if (!NetworkSplit.announceEnabled(this)) return
        if (!onCellular()) return

        val nowNr = isOn5g()
        if (lastAnnouncedNr == nowNr) return

        val now = System.currentTimeMillis()
        if (now - lastAnnouncedAt < MIN_ANNOUNCE_GAP_MS) return

        lastAnnouncedAt = now
        lastAnnouncedNr = nowNr

        val english = if (nowNr) "5G connected" else "4G connected"
        val tamil = if (nowNr) "5 ஜி இணைப்பு" else "4 ஜி இணைப்பு"
        Announcer.speak(this, english, tamil, Announcer.Event.RADIO_CHANGE)
        refreshNotification()
    }

    // ----------------------------------------------------------- notification
    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "4G / 5G watch", NotificationManager.IMPORTANCE_MIN)
                .apply { description = "Counts 4G and 5G separately and announces radio changes" }
        )
    }

    private fun buildNotification(detail: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val body = if (!NetworkSplit.separateEnabled(this)) {
            "Announcing 4G ⇄ 5G changes"
        } else {
            val limitMb = if (DataUsageTracker.hasConfiguredPlan(this))
                DataUsageTracker.getPackLimitGb(this) * 1024.0 else 0.0
            val status = NetworkSplit.status(this, limitMb)
            val four = if (status.dailyLimitMb > 0) NetworkSplit.format(status.fourGLeftMb) + " left"
            else NetworkSplit.format(status.fourGUsedMb) + " used"
            val five = if (status.fiveGUnlimited) "Unlimited"
            else if (status.dailyLimitMb > 0) NetworkSplit.format(status.fiveGLeftMb) + " left"
            else NetworkSplit.format(status.fiveGUsedMb) + " used"
            "4G $four · 5G $five"
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Live Guard")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$detail\n$body"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(open)
            .build()
    }

    private fun refreshNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification("4G/5G watch is on"))
    }
}
