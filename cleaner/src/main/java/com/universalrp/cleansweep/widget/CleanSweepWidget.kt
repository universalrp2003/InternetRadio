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
open class CleanSweepWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { refresh(context) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) { refresh(context) }
    override fun onDeleted(context: Context, ids: IntArray) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        ids.forEach { edit.remove("mode_$it"); edit.remove("profile_$it") }; edit.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(ACTION_REFRESH_WIDGET, ACTION_MODE, AppWidgetManager.ACTION_APPWIDGET_UPDATE, AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED)) {
            if (intent.action == ACTION_MODE) {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                if (id !in ownedIds(context).toSet()) return
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val profile = profile(context, id).next()
                prefs.edit().putString("profile_$id", profile.name).apply()
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

        private fun ownedIds(context: Context): IntArray {
            val manager = AppWidgetManager.getInstance(context)
            return listOf(CleanSweepWidget::class.java, CleanSweepDataWidget::class.java, CleanSweepPhoneWidget::class.java)
                .flatMap { manager.getAppWidgetIds(ComponentName(context, it)).toList() }.toIntArray()
        }
        private fun profile(context: Context, id: Int): CompactWidgetPolicy.Profile {
            val component = AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider?.className.orEmpty()
            return CompactWidgetPolicy.parse(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("profile_$id", null),
                CompactWidgetPolicy.defaultFor(component))
        }

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
                } catch (e: CancellationException) { throw e } catch (e: Exception) { /* An unavailable launcher/widget must not break monitoring. */ } finally {
                    // Clear ownership atomically before completion, so a tap in the final-frame race is not lost.
                    val running = currentCoroutineContext()[Job]
                    val followUp = synchronized(CleanSweepWidget.Companion) {
                        if (refreshJob == running) {
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
            val profile = profile(context, id)
            val views = RemoteViews(context.packageName, R.layout.widget_cleansweep_compact)
            val percent = WidgetReadingPolicy.percent(battery?.percent, battery?.reportedLevel, battery?.reportedScale)
            views.setTextViewText(R.id.widget_title, when (profile) {
                CompactWidgetPolicy.Profile.BATTERY -> label("Battery now", "இப்போதைய பேட்டரி")
                CompactWidgetPolicy.Profile.DATA -> label("Data / connection", "தரவு / இணைப்பு")
                CompactWidgetPolicy.Profile.PHONE -> label("Phone snapshot", "போன் சுருக்கம்")
            })
            views.setTextViewText(R.id.widget_battery, percent)
            val unplug = LastUnplugRepo.load(context)
            val unplugLine = when {
                unplug == null -> label("Last unplug: not observed", "கடைசி சார்ஜர் நீக்கம்: கவனிக்கப்படவில்லை")
                !LastUnplugPolicy.usable(unplug, checked) -> label("Last unplug: phone clock changed", "கடைசி சார்ஜர் நீக்கம்: போன் நேரம் மாறியுள்ளது")
                else -> label("Observed unplug: ", "கவனித்த சார்ஜர் நீக்கம்: ") + DisplayTime.dated(unplug.atMs, tamil) + " · " + CompactWidgetPolicy.wholePercent(unplug.percent)
            }
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val wifi = runCatching { manager?.let { it.getNetworkCapabilities(it.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) } == true }.getOrDefault(false)
            val line = when (profile) {
                CompactWidgetPolicy.Profile.BATTERY -> unplugLine
                CompactWidgetPolicy.Profile.DATA -> when {
                    !CompactWidgetPolicy.meteredData(wifi, data?.isUnlimited5g == true) ->
                        (if (wifi) label("Wi-Fi", "வைஃபை") else label("Unlimited 5G · user-set", "வரம்பற்ற 5G · பயனர் தேர்வு")) +
                            label(" · storage free ", " · காலி சேமிப்பு ") + (storage?.free?.formatBytes() ?: "—")
                    data?.readingAvailable != true -> label("Mobile usage unavailable; set up Usage access", "மொபைல் பயன்பாடு இல்லை; பயன்பாட்டு அணுகலை அமைக்கவும்")
                    !DataUsageTracker.hasConfiguredPlan(context) -> label("Today ", "இன்று ") + data.formattedToday + label(" · set plan limit", " · திட்ட வரம்பை அமைக்கவும்")
                    else -> label("Today ", "இன்று ") + data.formattedToday + label(" · left ", " · மீதி ") + data.formattedRemaining +
                        if (data.isPartial) label(" · partial", " · பகுதி") else ""
                }
                CompactWidgetPolicy.Profile.PHONE -> label("Storage free ", "காலி சேமிப்பு ") + (storage?.free?.formatBytes() ?: "—") + " · " + WidgetReadingPolicy.temperature(battery?.temperatureC)
            }
            views.setTextViewText(R.id.widget_data, line)
            val status = when {
                battery == null -> label("Not reported", "தகவல் இல்லை")
                battery.powerConnected && battery.charging -> label("Charging", "சார்ஜ் ஆகிறது")
                battery.powerConnected -> label("Connected", "இணைந்துள்ளது")
                else -> label("On battery", "பேட்டரியில் இயங்குகிறது")
            }
            views.setTextViewText(R.id.widget_state, "$status · ${DisplayTime.clock(checked)}")
            val extra = if (profile == CompactWidgetPolicy.Profile.BATTERY) label("Storage free ", "காலி சேமிப்பு ") + (storage?.free?.formatBytes() ?: "—") else unplugLine
            views.setTextViewText(R.id.widget_brief, extra)
            views.setViewVisibility(R.id.widget_brief, if (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 165) < 145) View.GONE else View.VISIBLE)
            views.setTextViewText(R.id.widget_mode, label("View", "காட்சி"))
            views.setTextViewText(R.id.widget_details, label("Open", "திற"))
            views.setTextViewText(R.id.widget_refresh, label("Refresh", "புதுப்பி"))
            views.setContentDescription(R.id.widget_mode, label("Switch compact battery, data or phone view", "சிறிய பேட்டரி, தரவு, போன் காட்சியை மாற்று"))
            views.setContentDescription(R.id.widget_battery, label("Reported battery level ", "பதிவான பேட்டரி அளவு ") + percent)
            views.setOnClickPendingIntent(R.id.widget_mode, action(context, id, ACTION_MODE, false))
            views.setOnClickPendingIntent(R.id.widget_refresh, action(context, id, ACTION_REFRESH_WIDGET, false))
            views.setOnClickPendingIntent(R.id.widget_details, action(context, id, ACTION_DETAILS, true))
            views.setOnClickPendingIntent(R.id.widget_root, action(context, id, ACTION_DETAILS, true))
            return views
        }

        private fun action(context: Context, id: Int, action: String, activity: Boolean): PendingIntent {
            val intent = if (activity) Intent(context, MainActivity::class.java) else Intent().setComponent(
                AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider ?: ComponentName(context, CleanSweepWidget::class.java))
            intent.setAction(action).setData(Uri.parse("liveguard-widget://view/$id/${action.substringAfterLast('.')}"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            if (activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return if (activity) PendingIntent.getActivity(context, id, intent, flags) else PendingIntent.getBroadcast(context, id, intent, flags)
        }
    }
}

/** Separate launcher entries, each retaining the same compact three-view selector. */
class CleanSweepDataWidget : CleanSweepWidget()
class CleanSweepPhoneWidget : CleanSweepWidget()
