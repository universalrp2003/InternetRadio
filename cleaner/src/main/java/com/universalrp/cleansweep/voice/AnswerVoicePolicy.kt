package com.universalrp.cleansweep.voice

/** A/B are chosen by preview, not guessed gender. One answer has one slot for automatic and manual rereads. */
object AnswerVoicePolicy {
    enum class Mode {
        SINGLE, ALTERNATE;
        companion object {
            fun parse(value: String?): Mode = when (value) { "SINGLE" -> SINGLE; else -> ALTERNATE }
        }
    }
    fun slot(answerIndex: Long, mode: Mode): Int = if (mode == Mode.SINGLE) 0 else (answerIndex.coerceAtLeast(0) % 2).toInt()
    fun retryInAnotherVoice(playbackStarted: Boolean, isAnswer: Boolean): Boolean = !playbackStarted && !isAnswer
    fun validPreference(name: String?): String? = name?.trim()?.takeIf { it.isNotEmpty() && it.length <= 200 }
}

/** Phone-engine capability labels only; Android does not standardize male/female metadata. Runtime inventory. */
data class InstalledAnswerVoice(val name: String, val language: String, val online: Boolean)
