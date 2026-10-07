package com.universalrp.tamilnadufm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.universalrp.tamilnadufm.MainActivity
import com.universalrp.tamilnadufm.R
import com.universalrp.tamilnadufm.player.PlaybackService

/**
 * Home-screen widget: shows the station that is playing and gives play/pause,
 * previous and next without opening the app.
 *
 * The buttons talk to [PlaybackService] directly (a service PendingIntent), so the
 * widget works even when the app itself is closed. If nothing has been played yet,
 * pressing play resumes the last station.
 */
class RadioWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val views = buildViews(context)
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
    }

    companion object {

        const val ACTION_TOGGLE = "com.universalrp.tamilnadufm.widget.TOGGLE"
        const val ACTION_NEXT = "com.universalrp.tamilnadufm.widget.NEXT"
        const val ACTION_PREVIOUS = "com.universalrp.tamilnadufm.widget.PREVIOUS"

        private const val PREFS = "tamilnadufm"
        private const val KEY_NAME = "widget_station_name"
        private const val KEY_PLAYING = "widget_is_playing"

        /** Called by the playback service whenever what is playing changes. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, RadioWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }

        fun rememberState(context: Context, name: String, playing: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_NAME, name)
                .putBoolean(KEY_PLAYING, playing)
                .apply()
        }

        private fun buildViews(context: Context): RemoteViews {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val name = prefs.getString(KEY_NAME, null)
                ?: prefs.getString("last_name", null)
                ?: "Ramesh Radio"
            val playing = prefs.getBoolean(KEY_PLAYING, false)

            val views = RemoteViews(context.packageName, R.layout.widget_radio)
            views.setTextViewText(R.id.widget_station, name)
            views.setTextViewText(
                R.id.widget_status,
                when {
                    playing -> context.getString(R.string.widget_status_playing)
                    prefs.getString("last_url", null) != null ->
                        context.getString(R.string.widget_status_paused)
                    else -> context.getString(R.string.widget_status_idle)
                },
            )
            views.setImageViewResource(
                R.id.widget_play,
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            )

            views.setOnClickPendingIntent(R.id.widget_prev, serviceIntent(context, ACTION_PREVIOUS, 1))
            views.setOnClickPendingIntent(R.id.widget_play, serviceIntent(context, ACTION_TOGGLE, 2))
            views.setOnClickPendingIntent(R.id.widget_next, serviceIntent(context, ACTION_NEXT, 3))
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            return views
        }

        private fun serviceIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, PlaybackService::class.java).setAction(action)
            return PendingIntent.getService(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun openAppIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                context,
                4,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
