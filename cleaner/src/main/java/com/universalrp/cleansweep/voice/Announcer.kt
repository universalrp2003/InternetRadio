package com.universalrp.cleansweep.voice

import android.content.Context
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.universalrp.cleansweep.data.AppLang
import com.universalrp.cleansweep.data.Lang
import java.util.Locale

/**
 * CleanSweep's voice.
 *
 * Everything spoken goes through here, so one switch can silence the whole app and one
 * "quiet hours" window can keep it from talking while the owner sleeps — which is exactly
 * what the user asked for ("voice announcement at day time, not disturb at user sleeping
 * time").
 *
 * Rules that the code enforces rather than promises:
 *
 *  * **Off the record**: nothing is ever recorded. The microphone is only used when the user
 *    taps the mic button in the assistant, and that goes through Android's own recogniser.
 *  * **Quiet hours win**: [speak] refuses to make a sound between the quiet start and end
 *    hours, whatever the per-event switches say. [speakNow] is for something the user just
 *    asked for by tapping — that is allowed at any hour.
 *  * **One voice at a time**: a new announcement flushes the old one instead of piling up.
 *  * **Honest language**: if the phone has no Tamil voice, Tamil text is not read out in an
 *    English accent — the English wording is spoken instead.
 *  * **Preferences live in plain SharedPreferences**, because a worker or service can wake up
 *    in a fresh process where the DataStore flow has not been read yet.
 */
object Announcer {

    /** Same plain file the charging service already reads. */
    const val PREFS = "cleansweep_state"

    const val KEY_ON = "voice_on"

    /**
     * Prefer a natural online voice over the phone's robotic offline one. On by default —
     * the user asked whether a better voice was possible, and this is the honest way to get
     * one: the phone's own cloud voice, no key, no account, no audio stored anywhere.
     */
    const val KEY_ONLINE = "voice_online"
    const val KEY_QUIET_START = "voice_quiet_start"
    const val KEY_QUIET_END = "voice_quiet_end"
    const val KEY_LOW = "voice_battery_low"
    const val KEY_FULL = "voice_battery_full"
    const val KEY_HOT = "voice_overheat"
    const val KEY_CHARGE = "voice_charging"
    const val KEY_DAILY = "voice_daily"
    const val KEY_ANSWERS = "voice_answers"
    const val KEY_STORAGE = "voice_storage"
    const val KEY_UNPLUGGED = "voice_unplugged"
    const val KEY_UNPLUGGED_START = "voice_unplug_start"
    const val KEY_NET_CHANGE = "voice_net_change"
    const val KEY_NEW_DEVICE = "voice_new_device"
    const val KEY_BATTERY_HEALTH = "voice_battery_health"
    const val KEY_IDLE_CHARGE = "voice_idle_charge"
    const val KEY_DAILY_ON = "daily_scan_on"
    const val KEY_DAILY_HOUR = "daily_hour"

    const val KEY_LAST_DAILY = "last_daily_date"
    const val KEY_LAST_BRIEF = "last_daily_brief"
    const val KEY_LAST_FULL = "last_full_at"
    const val KEY_LAST_LOW = "last_low_at"
    const val KEY_LAST_HOT = "last_hot_at"
    const val KEY_LAST_STORAGE = "last_storage_at"
    const val KEY_LAST_HEALTH = "last_health_at"
    const val KEY_LAST_IDLE = "last_idle_at"

    /** Battery below this speaks a warning while unplugged. */
    const val LOW_PERCENT = 20

    /** Battery temperature (°C) that counts as too hot. */
    const val HOT_BATTERY_C = 45.0

    /** Free storage below this speaks a warning. */
    const val LOW_STORAGE_BYTES = 1024L * 1024L * 1024L

    /** Unplugging before this percentage counts as "removed early". */
    const val UNPLUG_BEFORE_PERCENT = 90

    /** Default: only complain about an early unplug after this hour of the day. */
    const val DEFAULT_UNPLUG_START = 6

    /** CPU temperature (°C) that counts as too hot. */
    const val HOT_CPU_C = 75.0

    /** Default quiet window: 22:00 → 07:00. Daytime announcements pass through. */
    const val DEFAULT_QUIET_START = 22
    const val DEFAULT_QUIET_END = 7

    /** Default hour for the daily brief. */
    const val DEFAULT_DAILY_HOUR = 20

    /** Why something is being said. Each one has its own switch. */
    enum class Event(val key: String?) {
        BATTERY_LOW(KEY_LOW),
        BATTERY_FULL(KEY_FULL),
        OVERHEAT(KEY_HOT),
        CHARGING(KEY_CHARGE),
        DAILY(KEY_DAILY),
        ANSWER(KEY_ANSWERS),
        STORAGE_LOW(KEY_STORAGE),
        UNPLUGGED_EARLY(KEY_UNPLUGGED),
        NETWORK_CHANGE(KEY_NET_CHANGE),
        NEW_DEVICE(KEY_NEW_DEVICE),
        BATTERY_HEALTH(KEY_BATTERY_HEALTH),
        CHARGER_IDLE(KEY_IDLE_CHARGE),

        /** The "hear the voice" button in Settings: always allowed. */
        TEST(null),
    }

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var ready = false

    @Volatile
    private var tamilVoice = false

    /**
     * v2.6 — the natural voice. The phone's built-in engine ships a robotic, offline voice;
     * the same engine also installs better, "network" voices (Google's cloud voices appear
     * here). When the user turns this on, the best available voice for the language is
     * chosen instead, and if the network is down the engine fails — at which point the app
     * puts the offline voice back and says the same line again, so nothing ever goes silent.
     */
    @Volatile
    private var usingNetworkVoice = false

    @Volatile
    private var networkRetried = false

    @Volatile
    private var lastSpoken: String? = null

    /** Said before the engine finished starting, spoken as soon as it is up. */
    private val pending = ArrayDeque<Pair<String, Boolean>>()

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ------------------------------------------------------------------ switches

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ON, true)

    /** Voices installed by the phone's engine that need a network connection. */
    fun onlineVoice(context: Context): Boolean = prefs(context).getBoolean(KEY_ONLINE, true)

    fun setOnlineVoice(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONLINE, on).apply()
        // Re-pick the voice the next time something is said.
        try {
            tts?.let { applyLanguage(it, context) }
        } catch (e: Exception) {
            // The next utterance will retry.
        }
    }

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ON, on).apply()
        if (!on) stop()
    }

    /** Per-event switch. Everything defaults to on except nothing — quiet hours do the rest. */
    fun allows(context: Context, event: Event): Boolean {
        val key = event.key ?: return true
        return prefs(context).getBoolean(key, true)
    }

    fun setAllows(context: Context, event: Event, on: Boolean) {
        val key = event.key ?: return
        prefs(context).edit().putBoolean(key, on).apply()
    }

    fun quietStart(context: Context): Int =
        prefs(context).getInt(KEY_QUIET_START, DEFAULT_QUIET_START)

    fun quietEnd(context: Context): Int = prefs(context).getInt(KEY_QUIET_END, DEFAULT_QUIET_END)

    fun setQuietHours(context: Context, start: Int, end: Int) {
        prefs(context).edit()
            .putInt(KEY_QUIET_START, start.coerceIn(0, 23))
            .putInt(KEY_QUIET_END, end.coerceIn(0, 23))
            .apply()
    }

    fun dailyScanOn(context: Context): Boolean = prefs(context).getBoolean(KEY_DAILY_ON, true)

    fun setDailyScanOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_DAILY_ON, on).apply()
    }

    fun dailyHour(context: Context): Int = prefs(context).getInt(KEY_DAILY_HOUR, DEFAULT_DAILY_HOUR)

    /** Earliest hour at which an early-unplug reminder may be spoken. */
    fun unplugStartHour(context: Context): Int =
        prefs(context).getInt(KEY_UNPLUGGED_START, DEFAULT_UNPLUG_START)

    fun setUnplugStartHour(context: Context, hour: Int) {
        prefs(context).edit().putInt(KEY_UNPLUGGED_START, hour.coerceIn(0, 23)).apply()
    }

    fun setDailyHour(context: Context, hour: Int) {
        prefs(context).edit().putInt(KEY_DAILY_HOUR, hour.coerceIn(0, 23)).apply()
    }

    fun lastBrief(context: Context): String = prefs(context).getString(KEY_LAST_BRIEF, "").orEmpty()

    /**
     * Stores the brief. @param markDay is false for a run the user asked for by hand: the
     * brief is saved and shown, but the day is not counted as done, so the scheduled brief
     * still happens at its own hour.
     */
    fun saveBrief(context: Context, date: String, text: String, markDay: Boolean = true) {
        val edit = prefs(context).edit().putString(KEY_LAST_BRIEF, text)
        if (markDay) edit.putString(KEY_LAST_DAILY, date)
        edit.apply()
    }

    fun lastDailyDate(context: Context): String = prefs(context).getString(KEY_LAST_DAILY, "").orEmpty()

    /** True while the clock is inside the quiet window (which may wrap past midnight). */
    fun inQuietHours(context: Context, hour: Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)): Boolean {
        val start = quietStart(context)
        val end = quietEnd(context)
        if (start == end) return false
        return if (start < end) hour in start until end else hour >= start || hour < end
    }

    // ------------------------------------------------------------------ speaking

    /**
     * Says something if the whole chain allows it: master switch on, this event allowed, and
     * the clock outside the quiet window. Returns true when it actually spoke (or queued).
     */
    fun speak(context: Context, english: String, tamil: String, event: Event): Boolean {
        val ctx = context.applicationContext
        if (!enabled(ctx)) return false
        if (!allows(ctx, event)) return false
        if (event != Event.TEST && inQuietHours(ctx)) return false
        val text = pick(ctx, english, tamil)
        if (text.isBlank()) return false
        return utter(ctx, text, flush = true)
    }

    /**
     * Says something the user just asked for (an assistant answer, the test button). Quiet
     * hours do not apply — but the master switch still does.
     */
    fun speakNow(context: Context, text: String): Boolean = speakNow(context, text, null)

    /**
     * The same, but in an explicit language: an AI answer is spoken in the language it was
     * written in, whatever the menu language happens to be.
     */
    fun speakNow(context: Context, text: String, language: AppLang?): Boolean {
        val ctx = context.applicationContext
        if (!enabled(ctx)) return false
        val wanted = language ?: Lang.languageIn(ctx)
        val speaking = if (wanted == AppLang.TA) {
            if (ensureTamilVoice(ctx)) text else null
        } else {
            text
        }
        // A Tamil answer on a phone with no Tamil voice is not read out in an English accent:
        // the caller already fell back to the English wording for the UI, so stay silent here
        // and let the on-screen text be the answer.
        if (speaking == null) return false
        prepareVoice(ctx, wanted)
        return utter(ctx, speaking, flush = true)
    }

    /** Points the engine at the right locale (and voice) before speaking. */
    private fun prepareVoice(context: Context, lang: AppLang) {
        val engine = tts ?: return
        try {
            val locale = if (lang == AppLang.TA) TAMIL else Locale.US
            engine.setLanguage(locale)
            if (onlineVoice(context)) pickNaturalVoice(engine, locale)
        } catch (e: Exception) {
            // Keep whatever voice is loaded.
        }
    }

    /** Picks Tamil only when Tamil is the app language *and* the phone has a Tamil voice. */
    fun pick(context: Context, english: String, tamil: String): String {
        val wantsTamil = Lang.languageIn(context) == AppLang.TA
        if (!wantsTamil) return english
        return if (ensureTamilVoice(context)) tamil else english
    }

    private fun utter(context: Context, text: String, flush: Boolean): Boolean {
        val engine = ensureEngine(context) ?: return false
        if (!ready) {
            // Still starting up: keep the last thing that was asked for.
            pending.clear()
            pending.addLast(text to flush)
            return true
        }
        return say(engine, text, flush)
    }

    private fun say(engine: TextToSpeech, text: String, flush: Boolean): Boolean = try {
        lastSpoken = text
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        engine.speak(text, mode, null, "cleansweep-" + System.currentTimeMillis())
        true
    } catch (e: Exception) {
        false
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            // Engine already gone.
        }
        pending.clear()
    }

    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            // Nothing to do.
        }
        tts = null
        ready = false
        tamilVoice = false
    }

    private fun ensureEngine(context: Context): TextToSpeech? {
        prefsContext = context.applicationContext
        tts?.let { return it }
        synchronized(this) {
            tts?.let { return it }
            return try {
                // The init callback can fire before the constructor returns, so it must close
                // over this holder rather than over `tts` (which would still be null then).
                var created: TextToSpeech? = null
                created = TextToSpeech(context.applicationContext) { status ->
                    ready = status == TextToSpeech.SUCCESS
                    val engine = created ?: return@TextToSpeech
                    if (ready) {
                        applyLanguage(engine, context)
                        val queue = pending.toList()
                        pending.clear()
                        queue.forEach { (text, flush) -> say(engine, text, flush) }
                    }
                }
                try {
                    created.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) = Unit

                        @Suppress("OVERRIDE_DEPRECATION")
                        override fun onError(utteranceId: String?) = recoverFromVoiceError()

                        override fun onError(utteranceId: String?, errorCode: Int) =
                            recoverFromVoiceError()
                    })
                } catch (e: Exception) {
                    // Old engines without a progress listener still speak.
                }
                tts = created
                created
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun applyLanguage(engine: TextToSpeech, context: Context) {
        try {
            val wanted = if (Lang.languageIn(context) == AppLang.TA) TAMIL else Locale.US
            val result = engine.setLanguage(wanted)
            tamilVoice = wanted == TAMIL && result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            if (wanted == TAMIL && !tamilVoice) engine.setLanguage(Locale.US)
            usingNetworkVoice = false
            if (onlineVoice(context)) {
                pickNaturalVoice(engine, if (tamilVoice) TAMIL else Locale.US)
            }
        } catch (e: Exception) {
            tamilVoice = false
        }
    }

    /**
     * Chooses the best voice the engine has for this language, preferring one that needs the
     * network (those are the good ones), then the highest quality. Does nothing when the
     * engine has no such voice — the phone keeps its offline voice and the user hears no
     * difference, which is the honest outcome.
     */
    private fun pickNaturalVoice(engine: TextToSpeech, locale: Locale) {
        try {
            val voices = engine.voices ?: return
            val wanted = locale.language
            val candidates = voices.filter { it.locale.language.equals(wanted, ignoreCase = true) }
            if (candidates.isEmpty()) return
            val best = candidates.sortedWith(
                compareByDescending<Voice> { it.isNetworkConnectionRequired }
                    .thenByDescending { it.quality }
                    .thenBy { it.name }
            ).firstOrNull() ?: return
            engine.voice = best
            usingNetworkVoice = best.isNetworkConnectionRequired
        } catch (e: Exception) {
            // Any trouble here just means the default voice is used.
            usingNetworkVoice = false
        }
    }

    /**
     * A cloud voice with no network fails silently: the line is simply never heard. When that
     * happens the offline voice is put back and the same sentence is said once more, so the
     * user hears the warning either way.
     */
    private fun recoverFromVoiceError() {
        if (!usingNetworkVoice || networkRetried) return
        networkRetried = true
        val engine = tts ?: return
        val text = lastSpoken ?: return
        try {
            usingNetworkVoice = false
            engine.voice = null
            val lang = prefsContext?.let { Lang.languageIn(it) } ?: AppLang.EN
            engine.setLanguage(if (lang == AppLang.TA) TAMIL else Locale.US)
            say(engine, text, flush = true)
        } catch (e: Exception) {
            // Nothing more to try: the on-screen text is still there.
        }
    }

    /** The last context the engine was built for, used by the recovery path above. */
    @Volatile
    private var prefsContext: Context? = null

    /** True when the engine really has a Tamil voice installed. */
    private fun ensureTamilVoice(context: Context): Boolean {
        val engine = ensureEngine(context) ?: return false
        if (!ready) return engine.isLanguageAvailable(TAMIL) >= TextToSpeech.LANG_AVAILABLE
        return tamilVoice
    }

    private val TAMIL: Locale = Locale.forLanguageTag("ta-IN")

    // ------------------------------------------------------------------ text for speech

    /**
     * Turns a markdown-ish answer into something that sounds right when read out: no `**`,
     * no bullets, no code fences, no URLs read letter by letter.
     */
    fun plain(markdown: String): String {
        var text = markdown
        text = text.replace(Regex("```[\\s\\S]*?```"), " ")
        text = text.replace(Regex("`([^`]*)`"), "$1")
        text = text.replace(Regex("\\*\\*([^*]*)\\*\\*"), "$1")
        text = text.replace(Regex("\\*([^*]*)\\*"), "$1")
        text = text.replace(Regex("(?m)^\\s*#{1,6}\\s*"), "")
        text = text.replace(Regex("(?m)^\\s*[-*•]\\s+"), ". ")
        text = text.replace(Regex("(?m)^\\s*\\d+[.)]\\s+"), ". ")
        text = text.replace(Regex("\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
        text = text.replace(Regex("https?://\\S+"), " ")
        text = text.replace(Regex("\\s+"), " ").trim()
        return text
    }

    /**
     * Keeps a long answer to something worth listening to: whole sentences up to [maxChars].
     * Long bullet lists are cut, not read out for two minutes.
     */
    fun shorten(text: String, maxChars: Int = 420): String {
        val clean = plain(text)
        if (clean.length <= maxChars) return clean
        val cut = clean.take(maxChars)
        val lastStop = maxOf(cut.lastIndexOf(". "), cut.lastIndexOf("! "), cut.lastIndexOf("? "))
        return if (lastStop > maxChars / 3) cut.substring(0, lastStop + 1) else cut
    }

    /** Android 11+ hides other apps' intents unless we say what we are looking for. */
    fun speechAvailable(context: Context): Boolean = try {
        android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH.isNotBlank() &&
            context.packageManager.queryIntentActivities(
                android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
                0,
            ).isNotEmpty()
    } catch (e: Exception) {
        false
    }

    /** True when this phone can talk at all — used to keep the UI honest. */
    fun canSpeak(context: Context): Boolean = try {
        val engine = ensureEngine(context)
        engine != null && if (ready) true else engine.engines.isNotEmpty()
    } catch (e: Exception) {
        false
    }
}
