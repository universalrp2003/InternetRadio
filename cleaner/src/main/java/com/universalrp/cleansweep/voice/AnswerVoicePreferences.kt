package com.universalrp.cleansweep.voice

import android.content.Context
import com.universalrp.cleansweep.data.AppLang

/**
 * Explicit user-selected voice IDs are local settings, not inferred genders or security history.
 *
 * 2.42: [configured] means "both slots hold a voice". The automatic default may reuse one
 * voice for both slots when the phone reports only one, so [distinct] is the separate
 * question "did the owner pick two different voices" — the manual preview/save gate still
 * requires that, and alternating two identical voices is simply not possible.
 */
data class AnswerVoicePair(val first: String?, val second: String?) {
    val configured: Boolean get() = first != null && second != null
    val distinct: Boolean get() = configured && first != second
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

    /**
     * 2.42: write the **automatic** default pair chosen from the phone's own inventory.
     *
     * A manual save ([Announcer.saveAnswerVoice]) still requires a completed preview and two
     * distinct voices; this path exists so a first answer is spoken instead of being refused
     * with an error, which is what the owner asked for. Names are validated, and writing
     * nothing at all is better than storing an unusable preference.
     */
    fun saveAutomatic(context: Context, language: AppLang, first: String?, second: String?): Boolean {
        val a = AnswerVoicePolicy.validPreference(first) ?: return false
        val b = AnswerVoicePolicy.validPreference(second) ?: return false
        Announcer.prefs(context).edit()
            .putString(key(language, 0), a)
            .putString(key(language, 1), b)
            .putString("assistant_voice_style", "alternate_answers")
            .apply()
        return true
    }

    fun clear(context: Context, language: AppLang) {
        Announcer.prefs(context).edit().remove(key(language, 0)).remove(key(language, 1)).apply()
    }
}
