package com.universalrp.cleansweep.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.universalrp.cleansweep.MainActivity
import com.universalrp.cleansweep.R
import com.universalrp.cleansweep.data.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Launcher widget only; the separate floating status pill retains its existing watts-only charging mode. */
class CleanSweepWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { refresh(context) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) { refresh(context) }
    override fun onDeleted(context: Context, ids: IntArray) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        ids.forEach { edit.remove("mode_$it") }; edit.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(ACTION_REFRESH_WIDGET, ACTION_MODE, AppWidgetManager.ACTION_APPWIDGET_UPDATE, AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED)) {
            if (intent.action == ACTION_MODE) {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                if (id !in ownedIds(context).toSet()) return
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val mode = WidgetReadingPolicy.Mode.parse(prefs.getString("mode_$id", null)).next()
                prefs.edit().putString("mode_$id", mode.name).apply()
            }
            val pending = goAsync()
            val finished = java.util.concurrent.atomic.AtomicBoolean(false)
            fun finish() { if (finished.compareAndSet(false, true)) runCatching { pending.finish() } }
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ finish() }, 8_000)
            refresh(context).invokeOnCompletion { finish() }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_CLEAN = "com.universalrp.cleansweep.WIDGET_CLEAN"
        const val ACTION_SPEAK = "com.universalrp.cleansweep.WIDGET_SPEAK" // Retain legacy constant; no unsolicited widget speech.
        const val ACTION_DETAILS = "com.universalrp.cleansweep.WIDGET_DETAILS"
        const val ACTION_REFRESH_WIDGET = "com.universalrp.cleansweep.WIDGET_REFRESH"
        const val ACTION_MODE = "com.universalrp.cleansweep.WIDGET_MODE"
        private const val PREFS = "cleansweep_widget_views"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var refreshJob: Job? = null
        @Volatile private var anotherFrame = false

        private fun ownedIds(context: Context): IntArray = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, CleanSweepWidget::class.java))

        /** Coalesced IO snapshots; never do battery/stats/storage work on Compose or receiver's main thread. */
        @Synchronized fun refresh(context: Context): Job {
            if (refreshJob?.isActive == true) { anotherFrame = true; return refreshJob!! }
            val app = context.applicationContext
            val job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    for (frame in 0..1) {
                        synchronized(CleanSweepWidget.Companion) { anotherFrame = false }
                        refreshNow(app)
                        if (!synchronized(CleanSweepWidget.Companion) { anotherFrame }) break
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { /* An unavailable launcher/widget must not break monitoring. */ }
                finally {
                    // Clear ownership atomically before completion, so a tap in the final-frame race is not lost.
                    val followUp = synchronized(CleanSweepWidget.Companion) {
                        if (refreshJob == currentCoroutineContext()[Job]) {
                            refreshJob = null; anotherFrame
                        } else false
                    }
                    if (followUp) scope.launch { delay(100); refresh(app) }
                }
            }
            refreshJob = job; job.start()
            return job
        }

        private fun refreshNow(context: Context) {
            val ids = ownedIds(context); if (ids.isEmpty()) return
            val manager = AppWidgetManager.getInstance(context)
            val battery = runCatching { BatteryReader.read(context) }.getOrNull()
            val checked = System.currentTimeMillis()
            val storage = runCatching { StorageInfoProvider.read() }.getOrNull()
            val data = runCatching { DataUsageTracker.getUsageInfo(context) }.getOrNull()
            ids.forEach { id -> manager.updateAppWidget(id, build(context, id, manager.getAppWidgetOptions(id), battery, storage, data, checked)) }
        }

        private fun build(context: Context, id: Int, options: Bundle, battery: BatteryReading?, storage: StorageInfo?, data: DataUsageTracker.UsageInfo?, checked: Long): RemoteViews {
            val tamil = Lang.languageIn(context) == AppLang.TA
            fun label(en: String, ta: String) = if (tamil) ta else en
            val mode = WidgetReadingPolicy.Mode.parse(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("mode_$id", null))
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300)
            val percent = WidgetReadingPolicy.percent(battery?.percent, battery?.reportedLevel, battery?.reportedScale)
            val stacked = mode == WidgetReadingPolicy.Mode.BOTH && percent.length > 6 && width < 360
            val views = RemoteViews(context.packageName, if (stacked) R.layout.widget_cleansweep_stacked else R.layout.widget_cleansweep)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 260)
            val compact = height < (if (stacked) 330 else 260)
            views.setTextViewText(R.id.widget_title, label("Live Guard", "லைவ் கார்டு"))
            views.setTextViewText(R.id.widget_battery_label, label("Battery", "பேட்டரி"))
            views.setTextViewText(R.id.widget_power_label, label("Battery-side watts", "பேட்டரி பக்க வாட்"))
            views.setTextViewText(R.id.widget_battery, WidgetReadingPolicy.percent(battery?.percent, battery?.reportedLevel, battery?.reportedScale))
            views.setTextViewText(R.id.widget_power, WidgetReadingPolicy.watts(battery?.powerConnected == true, battery?.charging == true, battery?.powerW))
            views.setViewVisibility(R.id.widget_battery_group, if (mode == WidgetReadingPolicy.Mode.WATTS) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.widget_power_group, if (mode == WidgetReadingPolicy.Mode.PERCENT) View.GONE else View.VISIBLE)
            val state = when {
                battery == null -> label("Battery not reported", "பேட்டரி தகவல் இல்லை")
                !battery.powerConnected -> label("Unplugged", "சார்ஜர் இல்லை")
                battery.percent == 100 -> label("Connected · full", "இணைந்துள்ளது · முழுமை")
                battery.charging -> label("Charging", "சார்ஜ் ஆகிறது")
                else -> label("Connected · paused/not charging", "இணைந்துள்ளது · சார்ஜ் ஆகவில்லை")
            }
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(checked))
            views.setTextViewText(R.id.widget_state, "$state · $time")
            val valid = battery?.percent?.takeIf { it in 0..100 }
            views.setViewVisibility(R.id.widget_charge_bar, if (valid == null) View.GONE else View.VISIBLE)
            if (valid != null) views.setProgressBar(R.id.widget_charge_bar, 100, valid, false)
            views.setTextViewText(R.id.widget_sensors, "${WidgetReadingPolicy.temperature(battery?.temperatureC)}   ·   ${WidgetReadingPolicy.current(battery?.currentA)}   ·   ${WidgetReadingPolicy.voltage(battery?.voltageV)}")
            views.setViewVisibility(R.id.widget_sensors, if (compact) View.GONE else View.VISIBLE)
            val today = when {
                data == null || !data.readingAvailable -> label("Mobile usage not reported", "மொபைல் பயன்பாடு தெரியவில்லை")
                data.isUnlimited5g -> label("Unlimited 5G · user-set", "வரம்பற்ற 5G · பயனர் தேர்வு")
                else -> label("Today mobile: ", "இன்றைய மொபைல்: ") + data.formattedToday +
                    label(" · left: ", " · மீதி: ") + data.formattedRemaining + if (data.isPartial) label(" · partial", " · பகுதி") else ""
            }
            views.setTextViewText(R.id.widget_data, today)
            views.setViewVisibility(R.id.widget_data, if (height < (if (stacked) 290 else 240)) View.GONE else View.VISIBLE)
            views.setTextViewText(R.id.widget_storage, label("Storage free: ", "காலி சேமிப்பு: ") + (storage?.free?.formatBytes() ?: "—"))
            views.setViewVisibility(R.id.widget_storage, if (compact) View.GONE else View.VISIBLE)
            val briefAt = com.universalrp.cleansweep.voice.Announcer.prefs(context).getLong("last_brief_at_ms", 0L)
            val brief = com.universalrp.cleansweep.voice.Announcer.lastBrief(context).lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
            val datedBrief = if (briefAt > 0 && brief.isNotBlank()) SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(briefAt)) + " · " + brief
                else label("Open app for a dated security review", "தேதியுள்ள பாதுகாப்பு ஆய்வுக்கு ஆப்பைத் திறக்கவும்")
            views.setTextViewText(R.id.widget_brief, datedBrief)
            views.setViewVisibility(R.id.widget_brief, if (height >= (if (stacked) 340 else 290)) View.VISIBLE else View.GONE)
            views.setTextViewText(R.id.widget_mode, when(mode) {
                WidgetReadingPolicy.Mode.BOTH -> "% + W"
                WidgetReadingPolicy.Mode.WATTS -> "W"
                WidgetReadingPolicy.Mode.PERCENT -> "%"
            })
            views.setTextViewText(R.id.widget_clean, label("Clean", "சுத்தம்"))
            views.setTextViewText(R.id.widget_details, label("Details", "விவரங்கள்"))
            views.setTextViewText(R.id.widget_refresh, label("Refresh", "புதுப்பி"))
            views.setContentDescription(R.id.widget_mode, label("Switch battery/watts view", "பேட்டரி/வாட் காட்சியை மாற்று"))
            views.setContentDescription(R.id.widget_battery, label("Reported battery level ", "பதிவான பேட்டரி அளவு ") + WidgetReadingPolicy.percent(battery?.percent, battery?.reportedLevel, battery?.reportedScale))
            views.setOnClickPendingIntent(R.id.widget_mode, action(context, id, ACTION_MODE, false))
            views.setOnClickPendingIntent(R.id.widget_refresh, action(context, id, ACTION_REFRESH_WIDGET, false))
            views.setOnClickPendingIntent(R.id.widget_details, action(context, id, ACTION_DETAILS, true))
            views.setOnClickPendingIntent(R.id.widget_root, action(context, id, ACTION_DETAILS, true))
            views.setOnClickPendingIntent(R.id.widget_clean, action(context, id, ACTION_CLEAN, true))
            return views
        }

        private fun action(context: Context, id: Int, action: String, activity: Boolean): PendingIntent {
            val intent = Intent(context, if (activity) MainActivity::class.java else CleanSweepWidget::class.java)
                .setAction(action).setData(Uri.parse("liveguard-widget://view/$id/${action.substringAfterLast('.')}"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            if (activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return if (activity) PendingIntent.getActivity(context, id, intent, flags) else PendingIntent.getBroadcast(context, id, intent, flags)
        }
    }
}
