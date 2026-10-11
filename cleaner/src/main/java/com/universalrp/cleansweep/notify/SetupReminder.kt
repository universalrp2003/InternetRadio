package com.universalrp.cleansweep.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.universalrp.cleansweep.MainActivity
import com.universalrp.cleansweep.R
import com.universalrp.cleansweep.data.*

/** Optional, once-per-day aggregate nudge. No identities, message contents, repeated off-by-choice nagging or new service. */
object SetupReminder {
    const val ACTION = "com.universalrp.cleansweep.REVIEW_SETUP"
    private const val CHANNEL = "liveguard_setup"
    private const val ID = 9071
    fun maybePost(context: Context, features: List<FeatureSetupPolicy.Feature>) {
        val p = FeatureSetupPrefs.prefs(context); val now = System.currentTimeMillis()
        val pending = FeatureSetupPolicy.pending(features).size
        if (!FeatureSetupPolicy.remind(FeatureSetupPrefs.reminders(context), FeatureDirectory.notificationPermission(context), pending, p.getLong("last_reminder", 0), now)) return
        val language = Lang.languageIn(context)
        fun text(key: String, vararg args: Any) = Lang.translate(language, key, *args)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, text("Optional feature setup reminders"), NotificationManager.IMPORTANCE_LOW))
        if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return
        val open = PendingIntent.getActivity(context, ID, Intent(context, MainActivity::class.java).setAction(ACTION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val body = text("%d selected or core features need setup. Off-by-choice features are not treated as failures.", pending)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(text("Review Live Guard setup")).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open).setAutoCancel(true).setSilent(true).build()
        runCatching { NotificationManagerCompat.from(context).notify(ID, notification) }.onSuccess { p.edit().putLong("last_reminder", now).apply() }
    }
}
