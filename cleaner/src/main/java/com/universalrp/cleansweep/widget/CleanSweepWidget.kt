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
import com.universalrp.cleansweep.data.DataUsageTracker
import com.universalrp.cleansweep.data.DeviceHealthReader
import com.universalrp.cleansweep.data.formatBytes
import com.universalrp.cleansweep.voice.Announcer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The home-screen widget: what CleanSweep knows, without opening it.
 *
 *  * Primary display: Today's Mobile Data usage prominently formatted in GB/MB.
 *  * Secondary display: Pack Used / Pack Quota & Remaining GB left.
 *  * Storage free and battery percentage with state.
 *  * Last daily brief or security alert in one line.
 *  * Quick tap actions: refresh on widget body, clean button to open scanning.
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

            val dataUsage = try {
                DataUsageTracker.getUsageInfo(context)
            } catch (e: Exception) {
                null
            }

            val isUnlimited = dataUsage?.isUnlimited5g == true
            val freeStorage = health?.storage?.free?.formatBytes() ?: "—"

            if (isUnlimited) {
                // Unlimited 5G: Hide pack countdown and daily usage display
                val percent = battery?.percent?.takeIf { it in 0..100 }?.let { "$it%" } ?: "—"
                val topDisplay = if (battery?.charging == true && battery.powerW != null) {
                    "$percent • %.1f W".format(battery.powerW)
                } else {
                    "$percent • Unlimited 5G"
                }
                views.setTextViewText(R.id.widget_battery, topDisplay)
                views.setTextViewText(R.id.widget_storage, "$freeStorage free • Unlimited 5G Plan")
            } else {
                // Standard: Prominent Display of Real Today's Mobile Data Usage
                val todayDataDisplay = if (dataUsage != null) {
                    "${dataUsage.formattedToday} Today"
                } else {
                    val percent = battery?.percent?.takeIf { it in 0..100 }?.let { "$it%" } ?: "—"
                    if (battery?.charging == true && battery.powerW != null) {
                        "$percent • %.1f W".format(battery.powerW)
                    } else {
                        percent
                    }
                }
                views.setTextViewText(R.id.widget_battery, todayDataDisplay)

                val secondaryText = if (dataUsage != null) {
                    "Pack: ${dataUsage.formattedPackTotal} / ${dataUsage.formattedPackLimit} (${dataUsage.formattedRemaining} left) • $freeStorage free"
                } else {
                    "$freeStorage free"
                }
                views.setTextViewText(R.id.widget_storage, secondaryText)
            }

            val brief = Announcer.lastBrief(context)
            val line = when {
                brief.isBlank() -> "No brief yet — tap to open CleanSweep"
                else -> brief.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
            }

            // Check if there are critical security warnings to highlight on the widget
            val prefs = context.getSharedPreferences("cleansweep_state", Context.MODE_PRIVATE)
            val secWarning = prefs.getString("widget_security_alert", null)
            val displayLine = if (!secWarning.isNullOrBlank()) {
                "⚠️ $secWarning"
            } else {
                line
            }
            views.setTextViewText(R.id.widget_brief, displayLine)

            val batteryPart = battery?.percent?.takeIf { it in 0..100 }?.let { "Battery $it%" } ?: ""
            val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            val stateText = if (batteryPart.isNotBlank()) {
                "$batteryPart • $timeFmt"
            } else {
                "updated $timeFmt"
            }
            views.setTextViewText(R.id.widget_state, stateText)

            // Tapping widget body refreshes the widget details rather than opening the app,
            // preventing accidental launcher freezes/crashes. The app is only opened when tapping "Clean".
            val refreshIntent = PendingIntent.getBroadcast(
                context,
                91,
                Intent(context, CleanSweepWidget::class.java).setAction(ACTION_REFRESH_WIDGET),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, refreshIntent)

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

            return views
        }

        const val ACTION_CLEAN = "com.universalrp.cleansweep.WIDGET_CLEAN"
        const val ACTION_SPEAK = "com.universalrp.cleansweep.WIDGET_SPEAK"
        const val ACTION_REFRESH_WIDGET = "com.universalrp.cleansweep.WIDGET_REFRESH"
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val app = context.applicationContext
        when (intent.action) {
            ACTION_SPEAK -> {
                val brief = Announcer.lastBrief(app)
                if (brief.isNotBlank()) {
                    // The user tapped the speaker: allowed at any hour, still behind the master switch.
                    Announcer.speakNow(app, Announcer.shorten(brief, 400), null)
                } else {
                    Announcer.speakNow(app, "No daily brief yet. Open CleanSweep first.", null)
                }
                refresh(app)
            }
            ACTION_REFRESH_WIDGET -> {
                refresh(app)
            }
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
