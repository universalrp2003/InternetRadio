package com.universalrp.cleansweep.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.universalrp.cleansweep.MainActivity
import com.universalrp.cleansweep.R
import com.universalrp.cleansweep.data.BatteryReader
import com.universalrp.cleansweep.data.DeviceHealthReader
import com.universalrp.cleansweep.data.formatBytes
import com.universalrp.cleansweep.voice.Announcer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The home-screen widget: what CleanSweep knows, without opening it.
 *
 *  * the battery percentage and the charging wattage,
 *  * how much storage is free,
 *  * the last daily brief, in one line,
 *  * a tap target for each: "Clean" opens the scanner, the body opens the app, and the speaker
 *    reads the brief out loud.
 *
 * Tapping "Clean" cannot launch straight into a scan (Android forbids starting that work from
 * the background), so it opens the app on the scan screen — which is where the user was going
 * anyway.
 */
class CleanSweepWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        ids.forEach { id -> manager.updateAppWidget(id, build(context)) }
    }

    companion object {

        fun refresh(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context.applicationContext)
                val component = ComponentName(context.applicationContext, CleanSweepWidget::class.java)
                val ids = manager.getAppWidgetIds(component)
                if (ids.isEmpty()) return
                val views = build(context.applicationContext)
                ids.forEach { id -> manager.updateAppWidget(id, views) }
            } catch (e: Exception) {
                // No widget on this launcher: nothing to refresh.
            }
        }

        private fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_cleansweep)

            val battery = try {
                BatteryReader.read(context)
            } catch (e: Exception) {
                null
            }
            val health = try {
                DeviceHealthReader.read(context)
            } catch (e: Exception) {
                null
            }

            val percent = battery?.percent?.takeIf { it in 0..100 }?.let { "$it%" } ?: "—"
            val watts = if (battery?.charging == true && battery.powerW != null) {
                " • %.1f W".format(battery.powerW)
            } else if (battery?.charging == false) {
                " • Discharging"
            } else {
                ""
            }
            views.setTextViewText(R.id.widget_battery, percent + watts)

            val free = health?.storage?.free?.formatBytes() ?: "—"
            views.setTextViewText(R.id.widget_storage, free)

            val brief = Announcer.lastBrief(context)
            val line = when {
                brief.isBlank() -> "No brief yet — tap to open CleanSweep"
                else -> brief.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
            }
            views.setTextViewText(R.id.widget_brief, line)

            val stamp = battery?.statusLabel.orEmpty()
            val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            views.setTextViewText(
                R.id.widget_state,
                if (stamp.isNotBlank()) {
                    "$stamp • updated $timeFmt"
                } else {
                    "updated $timeFmt"
                },
            )

            // Open the app (normal tap).
            val open = PendingIntent.getActivity(
                context,
                91,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            // "Clean": open the app on the scanning screen so the user only confirms.
            val clean = PendingIntent.getActivity(
                context,
                92,
                Intent(context, MainActivity::class.java)
                    .setAction(ACTION_CLEAN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_clean, clean)

            // The speaker reads the last brief out loud, from the widget itself.
            val speak = PendingIntent.getBroadcast(
                context,
                93,
                Intent(context, CleanSweepWidget::class.java).setAction(ACTION_SPEAK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_speak, speak)

            return views
        }

        const val ACTION_CLEAN = "com.universalrp.cleansweep.WIDGET_CLEAN"
        const val ACTION_SPEAK = "com.universalrp.cleansweep.WIDGET_SPEAK"
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_SPEAK) {
            val app = context.applicationContext
            val brief = Announcer.lastBrief(app)
            if (brief.isNotBlank()) {
                // The user tapped the speaker: allowed at any hour, still behind the master switch.
                Announcer.speakNow(app, Announcer.shorten(brief, 400), null)
            } else {
                Announcer.speakNow(app, "No daily brief yet. Open CleanSweep first.", null)
            }
            refresh(app)
        }
    }

    /** Kept so an old launcher that still sends APPWIDGET_UPDATE with extras works. */
    private fun idsFrom(intent: Intent): IntArray =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: IntArray(0)
        } else {
            @Suppress("DEPRECATION")
            intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: IntArray(0)
        }
}
