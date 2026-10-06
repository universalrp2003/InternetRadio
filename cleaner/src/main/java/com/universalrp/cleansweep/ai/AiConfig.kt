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

    companion object {
        fun fromId(id: String): AiProvider =
            entries.firstOrNull { it.id == id } ?: GEMINI
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
) {
    val resolvedModel: String get() = model.trim().ifBlank { provider.defaultModel }
    val resolvedBaseUrl: String get() = baseUrl.trim().ifBlank { provider.baseUrl }.trimEnd('/')
    val ready: Boolean
        get() = when (provider) {
            AiProvider.FREE -> true
            AiProvider.CUSTOM -> resolvedBaseUrl.isNotBlank() && resolvedModel.isNotBlank()
            else -> apiKey.isNotBlank() && resolvedModel.isNotBlank()
        }
}

object AiSettings {

    private const val PREFS = "cleansweep_ai"

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
        )
    }

    fun save(context: Context, config: AiConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("provider", config.provider.id)
            .putString("api_key", config.apiKey.trim())
            .putString("model", config.model.trim())
            .putString("base_url", config.baseUrl.trim())
            .putBoolean("include_app_names", config.includeAppNames)
            .putBoolean("include_network", config.includeNetwork)
            .apply()
    }

    /**
     * The key never leaves the phone except towards the provider you chose. It is
     * stored in the app's own private preferences file, which other apps cannot read.
     */
    fun keyStorageNote(): String =
        "Stored only inside CleanSweep's private storage on this phone. It is sent to the " +
            "provider you picked and nowhere else. Uninstall the app (or clear the field and " +
            "save) to remove it."
}
