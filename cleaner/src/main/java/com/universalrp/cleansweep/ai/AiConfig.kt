package com.universalrp.cleansweep.ai

import android.content.Context

/**
 * Which AI service to ask, and with which key.
 *
 * CleanSweep does not ship a key and does not proxy anything: you pick a provider,
 * paste your own key, and the request goes from your phone straight to that
 * provider. There is also a "Free (no key)" option that uses public, keyless
 * community endpoints — those work with no signup at all, but they are shared,
 * rate-limited and can be slow, which the UI says plainly.
 */
enum class AiProvider(
    val id: String,
    val label: String,
    val baseUrl: String,
    val defaultModel: String,
    val needsKey: Boolean,
    val signupHint: String,
) {
    FREE(
        id = "free",
        label = "Free (no key needed)",
        baseUrl = "",
        defaultModel = "",
        needsKey = false,
        signupHint = "Tries public keyless AI endpoints in turn. Shared and rate-limited; " +
            "if one is busy the app automatically tries the next. No signup, no card.",
    ),
    GEMINI(
        id = "gemini",
        label = "Google Gemini",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        defaultModel = "gemini-2.5-flash",
        needsKey = true,
        signupHint = "Free key at aistudio.google.com → Get API key. Free tier is generous " +
            "(Gemini 2.5 Flash: ~10 requests/min, ~250/day) and needs no credit card.",
    ),
    NVIDIA(
        id = "nvidia",
        label = "NVIDIA NIM",
        baseUrl = "https://integrate.api.nvidia.com/v1",
        defaultModel = "meta/llama-3.3-70b-instruct",
        needsKey = true,
        signupHint = "Key at build.nvidia.com (free credits for developers). Same OpenAI-style " +
            "API as the rest.",
    ),
    OPENROUTER(
        id = "openrouter",
        label = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "meta-llama/llama-3.3-70b-instruct:free",
        needsKey = true,
        signupHint = "Key at openrouter.ai/keys. Several models are marked \":free\" — those keep " +
            "the cost at zero.",
    ),
    GROQ(
        id = "groq",
        label = "Groq",
        baseUrl = "https://api.groq.com/openai/v1",
        defaultModel = "llama-3.3-70b-versatile",
        needsKey = true,
        signupHint = "Key at console.groq.com. Free tier, very fast replies.",
    ),
    OPENAI(
        id = "openai",
        label = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        needsKey = true,
        signupHint = "Key at platform.openai.com. Paid (the cheapest models are still very cheap).",
    ),
    CUSTOM(
        id = "custom",
        label = "Custom (any OpenAI-style)",
        baseUrl = "",
        defaultModel = "",
        needsKey = true,
        signupHint = "Any service that speaks the OpenAI chat-completions format — including a " +
            "model running on your own computer (Ollama / LM Studio) on the same Wi-Fi.",
    );

    /**
     * Models that worked when this version was built. The AI settings screen also has a
     * "Load models" button that asks the provider itself (GET /models) and lists exactly
     * what your key can use — providers retire model ids, and a retired id is what makes
     * an otherwise good key answer "410 model has reached its end of life".
     */
    val suggestedModels: List<String>
        get() = when (this) {
            FREE -> listOf("kilo-auto/free", "openai", "gpt-oss-120b")
            GEMINI -> listOf(
                "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro", "gemini-flash-latest",
            )
            NVIDIA -> listOf(
                "meta/llama-3.3-70b-instruct",
                "nvidia/llama-3.3-nemotron-super-49b-v1",
                "mistralai/mistral-nemotron",
                "qwen/qwen2.5-72b-instruct",
            )
            OPENROUTER -> listOf(
                "meta-llama/llama-3.3-70b-instruct:free",
                "google/gemini-2.0-flash-exp:free",
                "deepseek/deepseek-chat-v3-0324:free",
                "qwen/qwen-2.5-72b-instruct:free",
            )
            GROQ -> listOf(
                "llama-3.3-70b-versatile", "llama-3.1-8b-instant", "openai/gpt-oss-120b",
                "qwen/qwen3-32b",
            )
            OPENAI -> listOf("gpt-4o-mini", "gpt-4.1-mini", "gpt-4o")
            CUSTOM -> emptyList()
        }

    /** Where the user gets a key (or an account), opened from the AI settings screen. */
    val signupUrl: String
        get() = when (this) {
            FREE -> "https://pollinations.ai"
            GEMINI -> "https://aistudio.google.com/apikey"
            NVIDIA -> "https://build.nvidia.com/explore/discover"
            OPENROUTER -> "https://openrouter.ai/keys"
            GROQ -> "https://console.groq.com/keys"
            OPENAI -> "https://platform.openai.com/api-keys"
            CUSTOM -> ""
        }

    /** Short name used for the badge that says which AI answered. */
    val shortLabel: String
        get() = when (this) {
            FREE -> "Free AI"
            GEMINI -> "Gemini"
            NVIDIA -> "NVIDIA NIM"
            OPENROUTER -> "OpenRouter"
            GROQ -> "Groq"
            OPENAI -> "OpenAI"
            CUSTOM -> "Custom AI"
        }

    companion object {
        fun fromId(id: String): AiProvider =
            entries.firstOrNull { it.id == id } ?: GEMINI

        /**
         * Which provider a pasted key obviously belongs to. Keys are prefixed by the
         * service that made them, so CleanSweep can select the right provider for you
         * instead of asking you to pick one from a list.
         */
        fun forKey(key: String): AiProvider? {
            val k = key.trim()
            if (k.isEmpty()) return null
            return when {
                k.startsWith("AIza") -> GEMINI
                k.startsWith("nvapi-") -> NVIDIA
                k.startsWith("sk-or-") -> OPENROUTER
                k.startsWith("gsk_") -> GROQ
                k.startsWith("sk-proj-") || k.startsWith("sk-") -> OPENAI
                else -> null
            }
        }
    }
}

/** One keyless endpoint that CleanSweep may use for the "Free" provider. */
data class KeylessEndpoint(
    val label: String,
    val url: String,
    val model: String,
)

data class AiConfig(
    val provider: AiProvider = AiProvider.GEMINI,
    val apiKey: String = "",
    val model: String = "",
    val baseUrl: String = "",
    /** Sending app names gives much better advice; the switch lets you turn it off. */
    val includeAppNames: Boolean = true,
    val includeNetwork: Boolean = true,
    /**
     * The keyless endpoint that last answered (Kilo, Pollinations or OVHcloud). Remembering
     * it means the next question goes straight to the one that works instead of retrying
     * the busy ones every time.
     */
    val freeEndpointUrl: String = "",
) {
    val resolvedModel: String get() = model.trim().ifBlank { provider.defaultModel }
    val resolvedBaseUrl: String get() = baseUrl.trim().ifBlank { provider.baseUrl }.trimEnd('/')
    val ready: Boolean
        get() = when (provider) {
            AiProvider.FREE -> true
            AiProvider.CUSTOM -> resolvedBaseUrl.isNotBlank() && resolvedModel.isNotBlank()
            else -> apiKey.isNotBlank() && resolvedModel.isNotBlank()
        }

    /** "Gemini · gemini-2.5-flash" — shown wherever the app says which AI is answering. */
    val engineLabel: String
        get() = when (provider) {
            AiProvider.FREE -> {
                val endpoint = AiSettings.KEYLESS.firstOrNull { it.url == freeEndpointUrl }
                val which = endpoint?.let { it.label.substringBefore(" (") } ?: "best available free"
                "Free AI · $which"
            }
            AiProvider.CUSTOM -> "Custom · ${resolvedModel.ifBlank { "no model set" }}"
            else -> "${provider.shortLabel} · ${resolvedModel.ifBlank { "no model set" }}"
        }

    /** True when a key is already saved, so the app can just use it. */
    val hasSavedKey: Boolean get() = apiKey.isNotBlank()
}

object AiSettings {

    private const val PREFS = "cleansweep_ai"

    /** Separate from the menu language: the user may read the menu in English and want Tamil
     *  answers, or the other way round. */
    const val KEY_ANSWER_LANGUAGE = "answer_language"

    /** "auto" (follow what I type), "en" (always English) or "ta" (always Tamil). */
    fun answerLanguage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ANSWER_LANGUAGE, "auto") ?: "auto"

    fun setAnswerLanguage(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ANSWER_LANGUAGE, value)
            .apply()
    }

    /**
     * Public, keyless endpoints, tried in this order by the Free provider. Kilo's gateway
     * allows about 200 free requests an hour per IP, which makes it the most reliable of
     * the keyless options; the others are community services that are often busy.
     */
    val KEYLESS: List<KeylessEndpoint> = listOf(
        KeylessEndpoint(
            label = "Kilo gateway (about 200/hour free)",
            url = "https://api.kilo.ai/api/gateway/chat/completions",
            model = "kilo-auto/free",
        ),
        KeylessEndpoint(
            label = "Pollinations (shared, often busy)",
            url = "https://text.pollinations.ai/openai",
            model = "openai",
        ),
        KeylessEndpoint(
            label = "OVHcloud AI (2 requests/min)",
            url = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/chat/completions",
            model = "gpt-oss-120b",
        ),
    )

    fun load(context: Context): AiConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AiConfig(
            provider = AiProvider.fromId(prefs.getString("provider", "") ?: ""),
            apiKey = prefs.getString("api_key", "") ?: "",
            model = prefs.getString("model", "") ?: "",
            baseUrl = prefs.getString("base_url", "") ?: "",
            includeAppNames = prefs.getBoolean("include_app_names", true),
            includeNetwork = prefs.getBoolean("include_network", true),
            freeEndpointUrl = prefs.getString("free_endpoint_url", "") ?: "",
        )
    }

    /**
     * Fills the gaps in a saved configuration: a pasted key picks its provider and a
     * default model, so "I already put my key in" really does mean the app can use it
     * without another visit to the settings screen.
     */
    fun autoComplete(config: AiConfig): AiConfig {
        var updated = config
        if (updated.model.isBlank() && updated.provider.defaultModel.isNotBlank()) {
            updated = updated.copy(model = updated.provider.defaultModel)
        }
        if (updated.provider == AiProvider.FREE && updated.hasSavedKey) {
            AiProvider.forKey(updated.apiKey)?.let { guessed ->
                updated = updated.copy(
                    provider = guessed,
                    model = updated.model.ifBlank { guessed.defaultModel },
                )
            }
        }
        return updated
    }

    /**
     * Saves the live configuration *and* remembers the key/model per provider, so switching
     * provider later never asks for the key again — the exact complaint that came with the
     * v2.2 screenshots ("after switching it asks for the key and model again").
     */
    fun save(context: Context, config: AiConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("provider", config.provider.id)
            .putString("api_key", config.apiKey.trim())
            .putString("model", config.model.trim())
            .putString("base_url", config.baseUrl.trim())
            .putBoolean("include_app_names", config.includeAppNames)
            .putBoolean("include_network", config.includeNetwork)
            .putString("free_endpoint_url", config.freeEndpointUrl.trim())
            .apply()
        if (config.apiKey.isNotBlank() || config.model.isNotBlank()) {
            saveFor(context, config.provider, config.apiKey.trim(), config.model.trim())
        }
    }

    // ------------------------------------------------- per-provider saved credentials

    /**
     * One line per provider inside the same private prefs file:
     * "geminiAIza…gemini-2.5-flash". Plain text on purpose — it can be read by eye
     * when debugging, and it never leaves the app's private storage.
     */
    private fun keyName(provider: AiProvider) = "saved_" + provider.id

    fun saveFor(context: Context, provider: AiProvider, apiKey: String, model: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(keyName(provider), apiKey + SEP + model)
            .apply()
    }

    /** What was saved for this provider, if anything: key to model. */
    fun savedFor(context: Context, provider: AiProvider): Pair<String, String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(keyName(provider), "") ?: ""
        if (raw.isBlank()) return "" to ""
        val parts = raw.split(SEP)
        return parts.getOrElse(0) { "" } to parts.getOrElse(1) { "" }
    }

    /** True when a key for this provider is already stored (shown as a badge in the UI). */
    fun hasSavedKey(context: Context, provider: AiProvider): Boolean =
        savedFor(context, provider).first.isNotBlank()

    /** Restores the provider's own key/model into the live config when the user switches. */
    fun applySaved(context: Context, provider: AiProvider): AiConfig {
        val (key, model) = savedFor(context, provider)
        val current = load(context)
        return current.copy(
            provider = provider,
            apiKey = key,
            model = model.ifBlank {
                if (provider.needsKey) provider.defaultModel else ""
            },
        )
    }

    private const val SEP = "\u0001"

    /**
     * The key never leaves the phone except towards the provider you chose. It is
     * stored in the app's own private preferences file, which other apps cannot read.
     */
    fun keyStorageNote(): String =
        "Saved keys live only inside CleanSweep's private storage on this phone, one per " +
            "provider, so switching between Gemini, Groq or the free option never asks you to " +
            "paste the same key twice. A key is sent to the provider you picked and nowhere " +
            "else. Uninstall the app (or clear the field and save) to remove them."
}
