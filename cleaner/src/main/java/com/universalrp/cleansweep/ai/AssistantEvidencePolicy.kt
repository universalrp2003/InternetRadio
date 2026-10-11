package com.universalrp.cleansweep.ai

import com.universalrp.cleansweep.data.NewsPolicy
import java.text.Normalizer
import java.util.Locale

/** Do not search the internet to identify this app's configured API; do not keep unrelated evidence as filler. */
object AssistantEvidencePolicy {
    private fun normalized(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")

    fun asksAboutThisAssistant(question: String): Boolean {
        val q = normalized(question)
        if (q in setOf("who are you", "what ai api is this", "what ai is this", "which ai is this", "what api is this",
                "what model are you", "which model are you", "what model is this", "what powers you", "which provider are you using")) return true
        if (q in setOf("நீ யார்", "நீங்கள் யார்", "இது எந்த ai", "இது எந்த api", "நீ எந்த ai", "எந்த ai api இது")) return true
        return Regex("^(what|which) (ai )?(ai|api|model|provider) (are you using|do you use|is this|is this app using|powers you)$").matches(q)
    }

    fun identity(provider: String, model: String, online: Boolean, tamil: Boolean, free: Boolean): String {
        // Only supplied public configuration labels; never include URL, key, arbitrary config or inferred provider identity.
        val name = provider.take(100); val id = model.take(150)
        return if (tamil) buildString {
            append("இது லைவ் கார்டு உதவியாளர். ")
            if (!online) append("இப்போது உள்ளூர்/ஆஃப்லைன் முறையில் உள்ளது. ")
            append("தேர்ந்தெடுத்த ஆன்லைன் சேவை: $name")
            if (id.isNotBlank()) append("; மாடல்: $id")
            append(". ")
            if (free) append("இலவச முறையில் சேவை மாறலாம்; ஒவ்வொரு பதிலின் குறிப்பில் பயன்படுத்திய சேவை தெரியும். ")
            append("இந்த அமைப்புத் தகவல் உள்ளூரில் காட்டப்படுகிறது; இதற்காக AI API அல்லது இணையத் தேடல் அழைக்கப்படவில்லை. தேர்ந்தெடுத்த மாடல் பெயர் மட்டும் அது இயங்குகிறது என்பதற்குச் சான்றல்ல.")
        } else buildString {
            append("This is the Live Guard assistant. ")
            if (!online) append("It is currently in local/offline mode. ")
            append("Configured online provider: $name")
            if (id.isNotBlank()) append("; model: $id")
            append(". ")
            if (free) append("Free mode can switch providers; each reply's metadata identifies the endpoint that answered. ")
            append("This settings answer is local: no AI API or web search was called for it. A configured model name alone does not prove that model is available or answered a request.")
        }
    }

    private val stop = setOf("the", "of", "in", "on", "and", "for", "a", "an", "to", "with", "from", "by", "who", "what",
        "when", "where", "which", "is", "are", "was", "were", "be", "been", "list", "please", "tell", "me", "you", "your",
        "this", "that", "do", "does", "how", "can", "could", "would", "using", "current", "latest", "today",
        "என்ன", "யார்", "எது", "இது", "எந்த", "இன்று", "பற்றி", "சொல்", "சொல்லுங்கள்")
    private fun words(text: String) = normalized(text).split(' ').filter { it.length >= 2 && it !in stop }.flatMap {
        when (it) { "cm" -> listOf("cm", "chief", "minister"); "pm" -> listOf("pm", "prime", "minister"); "tamilnadu" -> listOf("tamil", "nadu"); else -> listOf(it) }
    }.toSet()

    fun relevant(title: String, query: String): Boolean {
        val queryWords = words(query); val titleWords = words(title)
        return ProductEvidencePolicy.exactModel(title, query) && queryWords.isNotEmpty() && queryWords.any { it in titleWords }
    }

    fun evidenceKey(url: String, text: String): String = NewsPolicy.canonicalLink(url) ?: normalized(text).take(160)
}
