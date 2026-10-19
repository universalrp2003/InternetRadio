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
 *  * **One voice at a time**: a bounded automatic queue avoids alerts cancelling each other;
 *    an explicit manual read can interrupt it. Each request retains its own language.
 *  * **Honest language**: if the phone has no Tamil voice, Tamil text is not read out in an
 *    English accent. Bilingual automatic alerts can use their English wording;
 *    an explicit Tamil-only read reports missing Tamil data.
 *  * **Preferences live in plain SharedPreferences**, because a worker or service can wake up
 *    in a fresh process where the DataStore flow has not been read yet.
 */
object Announcer {

    /** Same plain file the charging service already reads. */
    const val PREFS = "cleansweep_state"

    const val KEY_ON = "voice_on"

    /**
     * Prefer an available online voice when connected. Engine/provider support varies;
     * alert text can be sent by the engine to its provider. Installed same-language
     * offline data is needed for an offline retry; no developer audio recording occurs.
     */
    const val KEY_ONLINE = "voice_online"
    const val KEY_LOUDER = "voice_louder"

    /**
     * v2.47: in-app volume for everything this app speaks, 0…1.
     * It rides on each utterance through KEY_PARAM_VOLUME, so the phone's own volume is
     * never changed behind the user's back.
     */
    const val KEY_VOLUME = "voice_volume"

    /** v2.47: announce 4G ⇄ 5G changes, separately from Wi-Fi ⇄ mobile data. */
    const val KEY_RADIO_CHANGE = "voice_radio_change"
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

    /**
     * Battery temperature (°C) that counts as too hot. 2.41: one owner-set **44 °C**
     * threshold, kept in step with [com.universalrp.cleansweep.data.ThermalWarningPolicy]
     * (the static gate fails if the two drift apart).
     */
    const val HOT_BATTERY_C = 44.0

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

        /** 4G ⇄ 5G on the same SIM. Off by default: phones hop between radios constantly. */
        RADIO_CHANGE(KEY_RADIO_CHANGE),
        NEW_DEVICE(KEY_NEW_DEVICE),
        BATTERY_HEALTH(KEY_BATTERY_HEALTH),
        CHARGER_IDLE(KEY_IDLE_CHARGE),

        /** The "hear the voice" button in Settings: always allowed. */
        TEST(null),
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var tamilVoice = false
    private var initializing = false
    private var generation = 0
    private var prefsContext: Context? = null
    private val previewedChoices = hashSetOf<String>() // This engine/session only; no claim the preview was physically heard.
    private val automaticAnswerIds = linkedSetOf<String>()
    private data class Request(val english: String?, val tamil: String?, val wanted: AppLang?, val event: Event,
        val manual: Boolean, val done: kotlinx.coroutines.CompletableDeferred<Boolean>? = null, val onStarted: (() -> Unit)? = null,
        val requestKey: String? = null, val preferredVoice: String? = null) {
        val id = java.util.UUID.randomUUID().toString()
        val key = requestKey ?: (event.name + ":" + (tamil ?: english).orEmpty())
        var language = wanted ?: AppLang.TA
        var text = ""
        var network = false
        var retried = false
        var started = false
        var attemptId = ""
    }
    private val pending = ArrayDeque<Request>() // Only touched on main.
    private var active: Request? = null
    private val runtimeState = kotlinx.coroutines.flow.MutableStateFlow(VoiceDeliveryStatus())
    private val automaticState = kotlinx.coroutines.flow.MutableStateFlow(VoiceDeliveryStatus())
    val runtime: kotlinx.coroutines.flow.StateFlow<VoiceDeliveryStatus> = runtimeState
    val automatic: kotlinx.coroutines.flow.StateFlow<VoiceDeliveryStatus> = automaticState

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ------------------------------------------------------------------ switches

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ON, false)

    /** Voices installed by the phone's engine that need a network connection. */
    fun onlineVoice(context: Context): Boolean = prefs(context).getBoolean(KEY_ONLINE, true)

    fun setOnlineVoice(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONLINE, on).apply()
    }

    /** Louder voice alerts: boost alert speech volume (off by default). */
    fun louderVoice(context: Context): Boolean = prefs(context).getBoolean(KEY_LOUDER, false)

    /**
     * The level used for the next utterance. A stored slider value wins; otherwise the old
     * louder switch still decides (1.0 vs 0.9), so upgrading changes nothing for anyone who
     * never touched the slider.
     */
    fun voiceVolume(context: Context): Float {
        val p = prefs(context)
        return if (p.contains(KEY_VOLUME)) {
            p.getFloat(KEY_VOLUME, 1f).coerceIn(0f, 1f)
        } else if (p.getBoolean(KEY_LOUDER, false)) 1f else 0.9f
    }

    fun setVoiceVolume(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_VOLUME, value.coerceIn(0f, 1f)).apply()
    }

    fun setLouderVoice(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOUDER, on).apply()
    }

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ON, on).apply()
        if (!on) stop()
    }

    /** Per-event switch. Everything defaults to on; quiet hours do the rest. */
    fun allows(context: Context, event: Event): Boolean {
        val key = event.key ?: return true
        val default = when (event) {
            // A phone that hops between radios must not narrate it until asked.
            Event.RADIO_CHANGE, Event.NETWORK_CHANGE -> false
            else -> true
        }
        return prefs(context).getBoolean(key, default)
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
        val edit = prefs(context).edit().putString(KEY_LAST_BRIEF, text).putLong("last_brief_at_ms", System.currentTimeMillis())
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

    /** Accepted/queued is NOT proof of playback; actual engine callbacks update diagnostics. */
    fun speak(context: Context, english: String, tamil: String, event: Event, onStarted: (() -> Unit)? = null): Boolean =
        submit(context, Request(english, tamil, null, event, event == Event.TEST, onStarted = onStarted))

    fun speakTamil(context: Context, tamilText: String, event: Event, onStarted: (() -> Unit)? = null): Boolean =
        submit(context, Request(null, tamilText, AppLang.TA, event, event == Event.TEST, onStarted = onStarted))

    fun speakNow(context: Context, text: String): Boolean = speakNow(context, text, null)
    fun speakNow(context: Context, text: String, language: AppLang?): Boolean {
        val wanted = language ?: Lang.languageIn(context)
        return submit(context, Request(if (wanted == AppLang.EN) text else null,
            if (wanted == AppLang.TA) text else null, wanted, Event.TEST, true))
    }

    /** One selected voice per answer. Automatic duplicates and cross-voice retries are forbidden. */
    fun speakAnswer(context: Context, text: String, language: AppLang, slot: Int, answerId: String, manual: Boolean): Boolean {
        val name = AnswerVoicePreferences.load(context, language).voice(slot)
        if (name == null) {
            main.post { record(context.applicationContext, Request(null, null, language, Event.ANSWER, manual), VoiceDeliveryPolicy.Stage.LANGUAGE_MISSING) }
            return false
        }
        return submit(context, Request(if (language == AppLang.EN) text else null, if (language == AppLang.TA) text else null,
            language, Event.ANSWER, manual, requestKey = "answer:$answerId", preferredVoice = name))
    }

    /** Query the actual initialized engine; no guessed male/female names and no voice downloads. */
    suspend fun installedAnswerVoices(context: Context, language: AppLang): List<InstalledAnswerVoice> =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation -> main.post {
            val app = context.applicationContext
            val started = android.os.SystemClock.elapsedRealtime()
            ensureEngine(app)
            fun check() {
                if (!continuation.isActive) return
                val engine = tts
                if (ready && engine != null) {
                    val inventory = voices(engine, language).sortedWith(compareBy<Voice> { it.isNetworkConnectionRequired }.thenBy { it.name })
                        .map { InstalledAnswerVoice(it.name, it.locale.toLanguageTag(), it.isNetworkConnectionRequired) }
                    continuation.resumeWith(Result.success(inventory))
                } else if (!initializing || android.os.SystemClock.elapsedRealtime() - started >= 10_500) {
                    continuation.resumeWith(Result.success(emptyList()))
                } else main.postDelayed({ check() }, 100)
            }
            check()
        } }

    private fun choiceToken(engine: TextToSpeech, language: AppLang, name: String) = engine.defaultEngine.orEmpty() + ":" + language.id + ":" + name

    /** Preview exactly the requested installed voice. No automatic alternative can disguise a failed candidate. */
    suspend fun previewAnswerVoice(context: Context, language: AppLang, name: String): Boolean {
        val valid = AnswerVoicePolicy.validPreference(name) ?: return false
        val done = kotlinx.coroutines.CompletableDeferred<Boolean>()
        val text = if (language == AppLang.TA) "வணக்கம். இது லைவ் கார்டு உதவியாளரின் குரல் முன்னோட்டம். ஒவ்வொரு பதிலும் ஒரு முறை மட்டுமே வாசிக்கப்படும். இந்தக் குரலைக் கேட்டு, குரல் ஏ அல்லது பி ஆகத் தேர்ந்தெடுக்கலாம்."
            else "Hello. This is a Live Guard assistant voice preview. Each answer uses one voice and is read once. Listen to this voice, then choose it as Voice A or Voice B if you prefer."
        val request = Request(if (language == AppLang.EN) text else null, if (language == AppLang.TA) text else null,
            language, Event.TEST, true, done = done, preferredVoice = valid)
        if (!submit(context, request)) return false
        try { return kotlinx.coroutines.withTimeoutOrNull(35_000) { done.await() } ?: false } finally { if (!done.isCompleted) main.post { cancelRequest(request.id, VoiceDeliveryPolicy.Stage.CANCELLED) } }
    }

    suspend fun saveAnswerVoice(context: Context, language: AppLang, slot: Int, name: String): Boolean =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            val engine = tts ?: return@withContext false
            if (!ready || slot !in 0..1 || choiceToken(engine, language, name) !in previewedChoices ||
                voices(engine, language).none { it.name == name }) return@withContext false
            val pair = AnswerVoicePreferences.load(context, language)
            if (name == (if (slot == 0) pair.second else pair.first)) return@withContext false
            AnswerVoicePreferences.save(context, language, slot, name)
            true
        }

    /** Clear/cancel chat speech without cancelling unrelated battery/security alerts. */
    fun cancelAnswers() { main.post {
        pending.filter { it.event == Event.ANSWER }.forEach { pending.remove(it); it.done?.complete(false) }
        if (active?.event == Event.ANSWER) { runCatching { tts?.stop() }; finish(VoiceDeliveryPolicy.Stage.CANCELLED, false) }
    } }

    /** Keep a worker alive until the engine finishes/fails, rather than finishing while TTS is still starting. */
    suspend fun speakAndWait(context: Context, english: String, tamil: String, event: Event,
        timeoutMs: Long = 45_000, onStarted: (() -> Unit)? = null, requestKey: String? = null): Boolean {
        val done = kotlinx.coroutines.CompletableDeferred<Boolean>()
        val request = Request(english.takeIf { it.isNotBlank() }, tamil.takeIf { it.isNotBlank() }, null,
            event, event == Event.TEST, done = done, onStarted = onStarted, requestKey = requestKey)
        if (!submit(context, request)) return false
        var endStage = VoiceDeliveryPolicy.Stage.TIMED_OUT
        return try {
            kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { done.await() } ?: false
        } catch (e: kotlinx.coroutines.CancellationException) {
            endStage = VoiceDeliveryPolicy.Stage.CANCELLED
            throw e
        } finally {
            if (!done.isCompleted) main.post { cancelRequest(request.id, endStage) }
        }
    }

    private fun submit(context: Context, request: Request): Boolean {
        val ctx = context.applicationContext
        val blocked = VoiceDeliveryPolicy.blocked(enabled(ctx), request.manual || allows(ctx, request.event), inQuietHours(ctx), request.manual)
        if (blocked != null) {
            main.post { record(ctx, request, blocked); request.done?.complete(false) }
            return false
        }
        if (request.english.isNullOrBlank() && request.tamil.isNullOrBlank()) return false
        main.post {
            prefsContext = ctx
            val duplicate = active?.key == request.key || pending.any { it.key == request.key } ||
                (request.event == Event.ANSWER && !request.manual && request.key in automaticAnswerIds)
            if (duplicate) { record(ctx, request, VoiceDeliveryPolicy.Stage.DUPLICATE); request.done?.complete(false); return@post }
            if (request.event == Event.ANSWER && !request.manual) {
                automaticAnswerIds.add(request.key)
                if (automaticAnswerIds.size > 128) automaticAnswerIds.remove(automaticAnswerIds.first())
            }
            if ((request.manual || request.event == Event.OVERHEAT && active?.event == Event.ANSWER) && active != null) {
                runCatching { tts?.stop() }; finish(VoiceDeliveryPolicy.Stage.CANCELLED, false, pump = false)
            }
            if (pending.size >= 4) pending.removeLast().also { record(ctx, it, VoiceDeliveryPolicy.Stage.CANCELLED); it.done?.complete(false) }
            if (request.manual || request.event == Event.OVERHEAT) pending.addFirst(request) else pending.addLast(request)
            record(ctx, request, VoiceDeliveryPolicy.Stage.QUEUED)
            drain(ctx)
        }
        return true
    }

    private fun drain(context: Context) {
        if (active != null || pending.isEmpty()) return
        if (!ready) { ensureEngine(context); return }
        val request = pending.removeFirst()
        val blocked = VoiceDeliveryPolicy.blocked(enabled(context), request.manual || allows(context, request.event), inQuietHours(context), request.manual)
        if (blocked != null) { record(context, request, blocked); request.done?.complete(false); drain(context); return }
        active = request
        val engine = tts ?: run { finish(VoiceDeliveryPolicy.Stage.NO_ENGINE, false); return }
        val ta = runCatching { engine.isLanguageAvailable(TAMIL) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        val bare = if (ta < 0) runCatching { engine.isLanguageAvailable(Locale("ta")) }.getOrDefault(-3) else ta
        tamilVoice = VoiceDeliveryPolicy.validStatus(bare)
        request.language = request.wanted ?: if (tamilVoice && request.tamil.orEmpty().any { it.code in 0x0B80..0x0BFF }) AppLang.TA else AppLang.EN
        request.text = if (request.language == AppLang.TA) request.tamil.orEmpty() else request.english.orEmpty()
        if (request.text.isBlank()) { finish(VoiceDeliveryPolicy.Stage.LANGUAGE_MISSING, false); return }
        startUtterance(context, request, offline = false)
    }

    private fun voices(engine: TextToSpeech, language: AppLang): List<Voice> = runCatching {
        engine.voices.orEmpty().filter { it.locale.language == (if (language == AppLang.TA) "ta" else "en") &&
            "notInstalled" !in it.features.orEmpty() }
    }.getOrDefault(emptyList())

    private fun startUtterance(context: Context, request: Request, offline: Boolean) {
        val engine = tts ?: run { finish(VoiceDeliveryPolicy.Stage.NO_ENGINE, false); return }
        try {
            val locale = if (request.language == AppLang.TA) TAMIL else Locale.US
            var languageResult = engine.setLanguage(locale)
            if (languageResult < 0) languageResult = engine.setLanguage(if (request.language == AppLang.TA) Locale("ta") else Locale.ENGLISH)
            if (!VoiceDeliveryPolicy.validStatus(languageResult)) { finish(VoiceDeliveryPolicy.Stage.LANGUAGE_MISSING, false); return }
            val available = voices(engine, request.language)
            val network = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val connected = runCatching { network?.activeNetwork?.let { network.getNetworkCapabilities(it) }
                ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true }.getOrDefault(false)
            val wantOnline = !offline && onlineVoice(context) && connected
            val chosen = if (request.preferredVoice != null) available.firstOrNull { it.name == request.preferredVoice }
                else available.filter { if (wantOnline) it.isNetworkConnectionRequired else !it.isNetworkConnectionRequired }
                    .maxByOrNull { it.quality } ?: available.filter { !it.isNetworkConnectionRequired }.maxByOrNull { it.quality }
            if (request.preferredVoice != null && (chosen == null || chosen.isNetworkConnectionRequired && !connected)) {
                finish(VoiceDeliveryPolicy.Stage.LANGUAGE_MISSING, false); return
            }
            if (offline && chosen == null) { finish(VoiceDeliveryPolicy.Stage.LANGUAGE_MISSING, false); return }
            if (chosen != null && engine.setVoice(chosen) != TextToSpeech.SUCCESS) { finish(VoiceDeliveryPolicy.Stage.LANGUAGE_MISSING, false); return }
            request.network = chosen?.isNetworkConnectionRequired ?: (engine.voice?.isNetworkConnectionRequired == true)
            if (!wantOnline && request.network && request.preferredVoice == null) { finish(VoiceDeliveryPolicy.Stage.LANGUAGE_MISSING, false); return }
            engine.setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
            engine.setPitch(1.02f); engine.setSpeechRate(0.96f)
            val params = android.os.Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, voiceVolume(context))
            }
            request.attemptId = request.id + if (request.retried) "-offline" else "-first"
            val result = engine.speak(plain(request.text).take(TextToSpeech.getMaxSpeechInputLength()), TextToSpeech.QUEUE_FLUSH, params, request.attemptId)
            if (result != TextToSpeech.SUCCESS) { voiceError(request.attemptId, VoiceDeliveryPolicy.Stage.ENGINE_REJECTED); return }
            record(context, request, VoiceDeliveryPolicy.Stage.QUEUED)
            val attempt = request.attemptId
            main.postDelayed({ if (active?.attemptId == attempt && !request.started) {
                runCatching { tts?.stop() }
                voiceError(attempt, VoiceDeliveryPolicy.Stage.TIMED_OUT)
            } }, if (request.network) 12_000 else 20_000)
        } catch (e: Exception) { finish(VoiceDeliveryPolicy.Stage.ENGINE_ERROR, false) }
    }

    private fun voiceError(utteranceId: String?, failedStage: VoiceDeliveryPolicy.Stage = VoiceDeliveryPolicy.Stage.ENGINE_ERROR) {
        val request = active ?: return
        if (request.attemptId != utteranceId) return // Old/duplicate callback cannot affect a newer utterance.
        val ctx = prefsContext ?: return
        val engine = tts ?: return
        val offline = voices(engine, request.language).any { !it.isNetworkConnectionRequired }
        if (request.preferredVoice == null && AnswerVoicePolicy.retryInAnotherVoice(request.started, request.event == Event.ANSWER) &&
            VoiceDeliveryPolicy.retryOffline(request.network, request.retried, offline)) {
            request.retried = true
            startUtterance(ctx, request, offline = true) // Explicit installed OFFLINE voice in the SAME language; once per request.
        } else finish(failedStage, false)
    }

    private fun finish(stage: VoiceDeliveryPolicy.Stage, success: Boolean, pump: Boolean = true) {
        val request = active ?: return
        active = null
        prefsContext?.let { record(it, request, stage) }
        if (success && request.event == Event.TEST && request.preferredVoice != null) tts?.let {
            previewedChoices.add(choiceToken(it, request.language, request.preferredVoice))
        }
        request.done?.complete(success)
        if (pump) prefsContext?.let { drain(it) }
    }

    private fun cancelRequest(id: String, stage: VoiceDeliveryPolicy.Stage) {
        if (active?.id == id) { runCatching { tts?.stop() }; finish(stage, false) }
        else pending.firstOrNull { it.id == id }?.let {
            pending.remove(it); prefsContext?.let { ctx -> record(ctx, it, stage) }; it.done?.complete(false)
        }
    }

    fun stop() { main.post {
        runCatching { tts?.stop() }
        pending.forEach { it.done?.complete(false) }; pending.clear()
        finish(VoiceDeliveryPolicy.Stage.CANCELLED, false, pump = false)
    } }

    fun shutdown() { main.post {
        runCatching { tts?.stop(); tts?.shutdown() }
        pending.forEach { it.done?.complete(false) }; pending.clear()
        finish(VoiceDeliveryPolicy.Stage.CANCELLED, false, pump = false)
        tts = null; ready = false; initializing = false; tamilVoice = false; generation++
        previewedChoices.clear()
    } }

    /** Always constructed/used on main; callback is deferred until the constructor's holder is assigned. */
    private fun ensureEngine(context: Context) {
        if (ready || initializing) return
        initializing = true; generation++
        val mine = generation
        pending.firstOrNull()?.let { record(context, it, VoiceDeliveryPolicy.Stage.INITIALIZING) }
        try {
            var created: TextToSpeech? = null
            val made = TextToSpeech(context) { result -> main.post {
                if (mine != generation) return@post
                val engine = created
                initializing = false
                if (result == TextToSpeech.SUCCESS && engine != null) {
                    tts = engine; ready = true
                    drain(context)
                } else failInitialization(context, VoiceDeliveryPolicy.Stage.INIT_FAILED)
            } }
            created = made
            made.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { main.post {
                    val request = active ?: return@post
                    if (request.attemptId == id) {
                        record(context, request, VoiceDeliveryPolicy.Stage.PLAYING)
                        if (!request.started) {
                            request.started = true
                            runCatching { request.onStarted?.invoke() }
                            // Startup timeout is not a replay timer. A started answer is never restarted in another voice.
                            val limit = if (request.event == Event.ANSWER) 150_000L else 45_000L
                            main.postDelayed({ if (active?.attemptId == id) {
                                runCatching { tts?.stop() }; finish(VoiceDeliveryPolicy.Stage.TIMED_OUT, false)
                            } }, limit)
                        }
                    }
                } }
                override fun onDone(id: String?) { main.post { if (active?.attemptId == id) finish(VoiceDeliveryPolicy.Stage.ENGINE_FINISHED, true) } }
                @Suppress("OVERRIDE_DEPRECATION") override fun onError(id: String?) { main.post { voiceError(id) } }
                override fun onError(id: String?, errorCode: Int) { main.post { voiceError(id) } }
            })
            tts = made
            main.postDelayed({ if (mine == generation && !ready) failInitialization(context, VoiceDeliveryPolicy.Stage.INIT_TIMEOUT) }, 10_000)
        } catch (e: Exception) { failInitialization(context, VoiceDeliveryPolicy.Stage.NO_ENGINE) }
    }

    private fun failInitialization(context: Context, stage: VoiceDeliveryPolicy.Stage) {
        generation++; initializing = false; ready = false
        previewedChoices.clear()
        runCatching { tts?.shutdown() }; tts = null
        val failed = pending.toList(); pending.clear()
        failed.forEach { record(context, it, stage); it.done?.complete(false) }
    }

    private fun record(context: Context, request: Request, stage: VoiceDeliveryPolicy.Stage) {
        val volume = runCatching { (context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager)
            ?.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) }.getOrNull()
        val snapshot = VoiceDeliveryStatus(stage, request.event.name, System.currentTimeMillis(), if (ready) tamilVoice else null,
            volume, request.network, request.language.id)
        runtimeState.value = snapshot
        if (!request.manual) {
            automaticState.value = snapshot
            // One bounded status only; never save utterance text, prompts, audio, keys or engine-private identifiers.
            prefs(context).edit().putString("voice_last_stage", stage.name).putString("voice_last_event", request.event.name)
                .putLong("voice_last_attempt_ms", snapshot.atMs).apply()
        }
    }

    fun noteDelivery(context: Context, event: Event, stage: VoiceDeliveryPolicy.Stage) {
        main.post { record(context.applicationContext, Request(null, null, null, event, false), stage) }
    }

    fun refreshDiagnostics(context: Context) {
        val p = prefs(context)
        if (automatic.value.atMs == 0L) automaticState.value = VoiceDeliveryStatus(
            runCatching { VoiceDeliveryPolicy.Stage.valueOf(p.getString("voice_last_stage", "NOT_REQUESTED")!!) }.getOrDefault(VoiceDeliveryPolicy.Stage.NOT_REQUESTED),
            p.getString("voice_last_event", "").orEmpty(), p.getLong("voice_last_attempt_ms", 0L))
    }

    fun pick(context: Context, english: String, tamil: String): String = if (tamilVoice && tamil.isNotBlank()) tamil else english

    /** Availability hint only; a test's callback/locale result, not this flag, is delivery evidence. */
    fun canSpeak(context: Context): Boolean = runCatching {
        context.packageManager.queryIntentServices(android.content.Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0).isNotEmpty()
    }.getOrDefault(false)

    private val TAMIL = Locale.forLanguageTag("ta-IN")

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

}
