package com.universalrp.cleansweep.notify

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import com.universalrp.cleansweep.data.BatteryReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The status-bar charging card the user asked for: while the charger is connected, an
 * ongoing notification shows the real numbers — watts, current, temperature, percentage and
 * an estimated time to full — and it goes away on its own when the plug comes out.
 *
 * v2.6 note: this service is now only *one* of the two ways CleanSweep watches a charger.
 * Android 12+ refuses to let the plug-in broadcast start a foreground service, so the
 * broadcast first hands the work to [com.universalrp.cleansweep.work.ChargerWatchWorker].
 * That job tries to start this service; if the system still says no, the job shows the same
 * card and speaks the same line itself. Either way the user gets the reading — which is what
 * was missing before, when the plug-in was simply silent.
 */
class ChargeMonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var ticker: Job? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = plugged != 0 ||
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            if (!charging) {
                // Unplugged: the notification has done its job.
                stopSelf()
            } else {
                update()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        alive = true
        ChargeNotifier.channel(this)
        try {
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: Exception) {
            // Already registered, or the platform refused: the ticker still updates the card.
        }
        startForeground(ChargeNotifier.NOTIFICATION_ID, initialNotification())
        ticker = scope.launch {
            while (isActive) {
                delay(5_000)
                update()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        update()
        // The spoken line is shared with the fallback job and de-duplicated in prefs, so a
        // plug-in can never be announced twice. On its own thread: sleeping here would block
        // the service's first few seconds.
        scope.launch { ChargeNotifier.announcePluggedIn(this@ChargeMonitorService) }
        return START_STICKY
    }

    /**
     * A first card built from whatever the battery reports right now. startForeground needs a
     * notification within seconds, so this can never be allowed to return nothing.
     */
    private fun initialNotification(): android.app.Notification = try {
        ChargeNotifier.build(this, BatteryReader.read(this))
    } catch (e: Exception) {
        ChargeNotifier.buildFallback(this)
    }

    private fun update() {
        val battery = ChargeNotifier.read(this) ?: return
        if (!battery.charging) {
            stopSelf()
            return
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.notify(ChargeNotifier.NOTIFICATION_ID, ChargeNotifier.build(this, battery))
        } catch (e: Exception) {
            // If notifications were switched off, there is nothing to show.
        }
        ChargeNotifier.updatePill(this, battery)
    }

    override fun onDestroy() {
        alive = false
        // Unplugged or switched off: the status-bar reading goes with it.
        StatusPill.remove()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Already gone.
        }
        scope.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = ChargeNotifier.CHANNEL_ID
        const val NOTIFICATION_ID = ChargeNotifier.NOTIFICATION_ID

        /** These switches live outside DataStore so a service or receiver can read them. */
        const val PILL_PREFS = "cleansweep_state"
        const val PILL_KEY = "status_pill"
        const val CARD_KEY = "charge_monitor"

        /** True while the foreground service is actually running, in this process. */
        @Volatile
        var alive: Boolean = false
            private set

        /** Is the charging card wanted at all? (The user's switch in Settings.) */
        fun cardEnabled(context: Context): Boolean =
            context.getSharedPreferences(PILL_PREFS, Context.MODE_PRIVATE)
                .getBoolean(CARD_KEY, true)

        /**
         * Starts the service if it is wanted and the phone is on the charger. Returns true
         * only when Android actually let it run — the caller then knows the card is covered.
         */
        fun start(context: Context): Boolean {
            if (!cardEnabled(context)) return false
            val battery = ChargeNotifier.read(context) ?: return false
            if (!battery.charging) {
                context.stopService(Intent(context, ChargeMonitorService::class.java))
                return false
            }
            if (alive) return true
            val intent = Intent(context, ChargeMonitorService::class.java)
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (e: Exception) {
                // Android 12+ can refuse a background start: the caller falls back to a job.
                false
            }
        }

        /** Starts or stops the monitor to match the current charger state. */
        fun sync(context: Context, enabled: Boolean) {
            if (!enabled) {
                context.stopService(Intent(context, ChargeMonitorService::class.java))
                return
            }
            start(context)
        }
    }
}
