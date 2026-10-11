package com.universalrp.cleansweep.notifications

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import com.universalrp.cleansweep.voice.Announcer

data class NotificationReaderConfig(val enabled: Boolean = false, val unlockedOnly: Boolean = true,
    val rules: List<NotificationReadingPolicy.Rule> = emptyList())

/** Stores explicit choices only. Never saves a message, inferred contact list, audio, or notification history. */
object NotificationReaderPrefs {
    const val PREFS = "liveguard_notification_reader"
    fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun load(context: Context): NotificationReaderConfig = try {
        val p = prefs(context)
        val array = JSONArray(p.getString("rules", "[]"))
        val rules = (0 until minOf(array.length(), 100)).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val pkg = item.optString("package").takeIf { it.matches(Regex("[a-zA-Z0-9_.]+")) } ?: return@mapNotNull null
            val senders = item.optJSONArray("senders") ?: JSONArray()
            NotificationReadingPolicy.Rule(pkg, item.optBoolean("full"), item.optBoolean("contacts_only"),
                (0 until minOf(senders.length(), 100)).mapNotNull { NotificationReadingPolicy.name(senders.optString(it)) }.toSet())
        }
        NotificationReaderConfig(p.getBoolean("enabled", false), p.getBoolean("unlocked_only", true), rules)
    } catch (_: Exception) { NotificationReaderConfig() }
    fun save(context: Context, config: NotificationReaderConfig) {
        val array = JSONArray()
        config.rules.distinctBy { it.packageName }.take(100).forEach { rule -> array.put(JSONObject().apply {
            put("package", rule.packageName); put("full", rule.fullMessage); put("contacts_only", rule.contactsOnly)
            put("senders", JSONArray(rule.senders.mapNotNull { NotificationReadingPolicy.name(it) }.take(100)))
        }) }
        prefs(context).edit().putBoolean("enabled", config.enabled).putBoolean("unlocked_only", config.unlockedOnly)
            .putString("rules", array.toString()).apply()
        Announcer.setAllows(context, Announcer.Event.MESSAGES, config.enabled)
    }
    fun clear(context: Context) { save(context, NotificationReaderConfig()); prefs(context).edit().remove("rules").apply() }
}
