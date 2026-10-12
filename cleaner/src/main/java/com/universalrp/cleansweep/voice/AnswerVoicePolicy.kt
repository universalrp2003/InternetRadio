package com.universalrp.cleansweep.voice

/**
 * A/B come from the phone's real installed-voice inventory, never a guessed gender. One
 * answer has one slot for automatic and manual rereads.
 *
 * 2.42: when the owner has not chosen, the two slots are filled **automatically** from that
 * inventory instead of refusing to speak; the Voice & daily watch card still previews and
 * swaps them, and a manual save still requires a completed preview.
 */
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

    /**
     * 2.42: choose Voice A and Voice B automatically instead of refusing to speak.
     *
     * [orderedNames] arrives in the engine's own inventory order — offline voices first,
     * then alphabetically — so "the first two usable" means the first two entries that
     * survive [validPreference]. One usable voice is used for **both** slots rather than
     * leaving the answer unspoken; no usable voice means no choice at all, which the
     * delivery diagnostics report honestly instead of an error message to the owner.
     *
     * Nothing here infers gender, and no voice is invented: every name must come from the
     * phone's actual engine inventory.
     */
    fun automaticPair(orderedNames: List<String>): Pair<String?, String?> {
        val usable = orderedNames.mapNotNull { validPreference(it) }.distinct()
        return when {
            usable.size >= 2 -> usable[0] to usable[1]
            usable.size == 1 -> usable[0] to usable[0]
            else -> null to null
        }
    }

    /**
     * Which pair an answer should actually use: the owner's stored choice when it exists,
     * otherwise the automatic default. [storedFirst]/[storedSecond] are the saved slots and
     * [orderedNames] the current engine inventory, so a saved voice that the engine no
     * longer reports is replaced rather than silently failing.
     */
    fun effectivePair(storedFirst: String?, storedSecond: String?, orderedNames: List<String>): Pair<String?, String?> {
        val usable = orderedNames.mapNotNull { validPreference(it) }.distinct()
        val first = validPreference(storedFirst)?.takeIf { usable.isEmpty() || it in usable }
        val second = validPreference(storedSecond)?.takeIf { usable.isEmpty() || it in usable }
        if (first != null && second != null) return first to second
        if (first == null && second == null) return automaticPair(usable)
        // Exactly one slot survived: fill the other from the inventory, preferring a
        // different voice so the two slots still alternate when the phone has one.
        val kept = first ?: second
        val fill = usable.firstOrNull { it != kept } ?: kept
        return if (first != null) first to fill else fill to second
    }
}

/** Phone-engine capability labels only; Android does not standardize male/female metadata. Runtime inventory. */
data class InstalledAnswerVoice(val name: String, val language: String, val online: Boolean)
