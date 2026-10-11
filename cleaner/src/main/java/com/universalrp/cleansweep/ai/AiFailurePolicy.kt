package com.universalrp.cleansweep.ai

/** Stable translatable errors, without provider HTML, credentials, URLs or raw exception messages. */
object AiFailurePolicy {
    private val knownMessages = setOf(
        "The AI request timed out. Retry or choose another provider.",
        "The provider did not accept the saved key. Check AI settings.",
        "The provider denied access. Check the selected model and account permissions.",
        "The provider is busy or its quota is exhausted. Retry later or choose another provider.",
        "The selected model could not be used. Load available models in AI settings.",
        "The provider is temporarily unavailable. Retry later or choose another provider.",
        "No key is saved for this provider. Open AI settings or select Free AI.",
        "The provider returned no final answer. Try another model or provider.",
        "Network tasks are busy. Cancel or retry shortly.",
        "Could not get an AI answer. Check the connection, provider and model, then retry.",
    )
    fun message(error: String?): String {
        if (error != null && error in knownMessages) return error
        val text = error.orEmpty().lowercase()
        val code = Regex("(?:http|status)\\s*[:=]?\\s*(\\d{3})").find(text)?.groupValues?.get(1)?.toIntOrNull()
        return when {
            "timed out" in text || "too long" in text || "timeout" in text -> "The AI request timed out. Retry or choose another provider."
            code == 401 || "invalid api key" in text -> "The provider did not accept the saved key. Check AI settings."
            code == 403 -> "The provider denied access. Check the selected model and account permissions."
            code == 429 || "rate limit" in text || "quota" in text -> "The provider is busy or its quota is exhausted. Retry later or choose another provider."
            code == 404 || code == 410 || "model" in text && ("not found" in text || "not available" in text || "does not exist" in text) -> "The selected model could not be used. Load available models in AI settings."
            code != null && code in 500..599 -> "The provider is temporarily unavailable. Retry later or choose another provider."
            "no key" in text || "add your api key" in text -> "No key is saved for this provider. Open AI settings or select Free AI."
            "no final answer" in text || "empty reply" in text -> "The provider returned no final answer. Try another model or provider."
            "network tasks are busy" in text -> "Network tasks are busy. Cancel or retry shortly."
            else -> "Could not get an AI answer. Check the connection, provider and model, then retry."
        }
    }
}
