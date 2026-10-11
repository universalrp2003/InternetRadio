package com.universalrp.cleansweep.voice

import android.content.Context
import com.universalrp.cleansweep.data.AppLang

/** Explicit user-selected voice IDs are local settings, not inferred genders or security history. */
data class AnswerVoicePair(val first: String?, val second: String?) {
    val configured: Boolean get() = first != null && second != null && first != second
    fun voice(slot: Int): String? = if (!configured) null else if (slot == 0) first else second
}

object AnswerVoicePreferences {
    private fun key(language: AppLang, slot: Int): String = "answer_voice_${language.id}_${if (slot == 0) "a" else "b"}"
    fun load(context: Context, language: AppLang): AnswerVoicePair {
        val p = Announcer.prefs(context)
        return AnswerVoicePair(AnswerVoicePolicy.validPreference(p.getString(key(language, 0), null)),
            AnswerVoicePolicy.validPreference(p.getString(key(language, 1), null)))
    }
    internal fun save(context: Context, language: AppLang, slot: Int, name: String) {
        Announcer.prefs(context).edit().putString(key(language, slot), name).putString("assistant_voice_style", "alternate_answers").apply()
    }
    fun clear(context: Context, language: AppLang) {
        Announcer.prefs(context).edit().remove(key(language, 0)).remove(key(language, 1)).apply()
    }
}
