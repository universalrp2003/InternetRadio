package com.universalrp.cleansweep.ai

/** Conservative, provider-independent handling of current-office questions. */
object OfficeEvidence {
    private val office = Regex("""(?i)\b(chief minister|prime minister|vice president|president|governor|chief justice|mayor|cm|pm)\b""")
    private val current = Regex("""(?i)\b(who|current|incumbent|now|latest|new|elected)\b""")
    fun applies(question: String) = office.containsMatchIn(question) && current.containsMatchIn(question)

    // Initials belong to names, not sentence boundaries. Keep matching deliberately narrow.
    private val name = """((?:(?:[A-Z]\.|[A-Z][\p{L}'’\-]+)\s+){0,5}[A-Z][\p{L}'’\-]+)"""
    private val patterns = listOf(
        Regex("""(?i:the incumbent is|current incumbent is|currently held by)\s+""" + name),
        Regex(name + """(?:\s+of\s+(?:the\s+)?[A-Z][\p{L}’\x27 .\-]+)?\s+(?i:is the current|is the incumbent)\b"""),
    )
    fun incumbent(text: String): String? = patterns.firstNotNullOfOrNull {
        it.find(text)?.groupValues?.get(1)?.trim()
    }

    fun answer(question: String, modelAnswer: String, evidence: String): String {
        if (!applies(question)) return modelAnswer
        val holder = incumbent(evidence)
            ?: return "I could not verify the current officeholder from the live sources. " +
                "The retrieved text does not clearly identify the incumbent, so I will not guess a name from older knowledge. " +
                "Please check the office’s official website or try again later."
        // Do not preserve contradictory model prose, even if it mentions the correct name somewhere.
        return "The retrieved Wikipedia office article identifies $holder as the current holder. " +
            "This is what the fetched article reports; check the linked source for updates."
    }
}
