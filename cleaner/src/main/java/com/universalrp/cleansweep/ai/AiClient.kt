package com.universalrp.cleansweep.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import com.universalrp.cleansweep.net.BoundedHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * A very small OpenAI-compatible client.
 *
 * Gemini, NVIDIA, OpenRouter, Groq, OpenAI and any local model server all accept the
 * same `POST /chat/completions` shape, so one implementation covers every provider
 * — Gemini through its official OpenAI-compatibility endpoint. The "Free" provider
 * walks a list of public keyless endpoints until one answers.
 */
object AiClient {

    data class Result(
        val ok: Boolean,
        val text: String,
        val providerLabel: String,
        val error: String? = null,
        /** Which keyless endpoint answered, so the app can reuse the one that works. */
        val endpointUrl: String? = null,
        val model: String = "",
        val searchUsed: Boolean = false,
        val nativeSources: List<String> = emptyList(),
    )

    /** Kept for old callers while progress is moved to per-request state; no global retry callbacks are fired. */
    @Deprecated("Use request-owned progress") var attemptListener: ((attempt: Int, of: Int, reason: String) -> Unit)? = null

    suspend fun ask(
        config: AiConfig,
        systemPrompt: String,
        userPrompt: String,
        /**
         * v2.6: ask the provider to search the web when it can. Used for the assistant, where
         * the question can be "today's latest news" and an answer from memory is worthless —
         * not for the phone reports, which are about numbers this app measured itself.
         */
        allowSearch: Boolean = false,
        budget: RequestBudget = RequestBudget(AiRequestPolicy.Use.REPORT),
    ): Result = withTimeoutOrNull(budget.remainingMs().coerceAtLeast(1)) { withContext(Dispatchers.IO) {
        if (!config.ready) {
            return@withContext Result(
                ok = false,
                text = "",
                providerLabel = config.provider.label,
                error = "Add your API key in AI settings first (or switch to the free option).",
            )
        }

        // Google can look things up; the OpenAI-style endpoint cannot. Try the searching
        // endpoint first and quietly fall back to the plain one, so an older model or a
        // locked-down key still answers.
        if (allowSearch && config.canSearchWeb) {
            val grounded = postGoogleWithSearch(config, systemPrompt, userPrompt, budget)
            if (grounded.ok) {
                val parts = grounded.text
                return@withContext Result(
                    ok = true,
                    text = parts,
                    providerLabel = config.provider.label,
                    model = config.resolvedModel,
                    searchUsed = grounded.sources.isNotEmpty(),
                    nativeSources = grounded.sources,
                )
            }
        }

        if (config.provider == AiProvider.FREE) {
            var lastError = "No free endpoint answered."
            // Try the endpoint that worked last time first.
            val ordered = AiSettings.KEYLESS.sortedByDescending { it.url == config.freeEndpointUrl }
            for (endpoint in ordered.take(AiRequestPolicy.MAX_FREE_ENDPOINTS)) {
                if (!AiRequestPolicy.mayFallback(budget.remainingMs())) break
                val which = endpoint.label.substringBefore(" (")
                val result = postWithRetry(
                    url = endpoint.url,
                    apiKey = null,
                    model = endpoint.model,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    budget = budget,
                )
                if (result.first) {
                    return@withContext Result(
                        ok = true,
                        text = result.second,
                        providerLabel = "Free AI • $which",
                        endpointUrl = endpoint.url,
                        model = endpoint.model,
                    )
                }
                lastError = "${endpoint.label}: ${result.second}"
            }
            return@withContext Result(
                ok = false,
                text = "",
                providerLabel = "Free (no key)",
                error = AiFailurePolicy.message(lastError),
            )
        }

        if (budget.remainingMs() <= 0) return@withContext Result(false, "", config.provider.label, "The AI request timed out. Retry or choose another provider.")
        val base = config.resolvedBaseUrl
        val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        val (ok, payload) = postWithRetry(
            url = url,
            apiKey = config.apiKey,
            model = config.resolvedModel,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            budget = budget,
        )
        if (ok) {
            Result(
                ok = true,
                text = payload,
                providerLabel = config.provider.label,
                model = config.resolvedModel,
            )
        } else {
            Result(
                ok = false,
                text = "",
                providerLabel = config.provider.label,
                error = AiFailurePolicy.message(payload),
                model = config.resolvedModel,
            )
        }
    } } ?: Result(false, "", config.provider.label, "The AI request timed out. Retry or choose another provider.", model = config.resolvedModel)

    /**
     * "Is my AI actually usable right now?" — the check the app runs when it opens.
     * For a keyed provider it asks for the model list (cheap, no tokens); for the free
     * option it sends a two-word prompt to each keyless endpoint until one answers.
     * Returns the label of whatever answered, plus the endpoint url when it is the free
     * option, so the app can remember it.
     */
    suspend fun ping(config: AiConfig): Result = withTimeoutOrNull(AiRequestPolicy.Use.TEST.budgetMs) {
        val budget = RequestBudget(AiRequestPolicy.Use.TEST)
        if (config.provider == AiProvider.FREE) return@withTimeoutOrNull test(config, budget)
        if (!config.ready) return@withTimeoutOrNull Result(false, "", config.provider.label, "No key saved yet.")
        val (models, listError) = listModels(config, budget)
        // 2.41 provider model check: a model the provider's own list does not contain fails
        // fast with the translatable "selected model could not be used" message instead of a
        // doomed generation. An unavailable list is not evidence against the model, so the
        // real probe still runs in that case.
        when (AiOutputBudgetPolicy.checkModel(config.resolvedModel, models)) {
            AiOutputBudgetPolicy.ModelCheck.ACCEPTED ->
                Result(true, "Model listed; generation not tested.", config.provider.label, model = config.resolvedModel)
            AiOutputBudgetPolicy.ModelCheck.REJECTED_UNLISTED ->
                Result(
                    false, "", config.provider.label,
                    AiOutputBudgetPolicy.rejectionMessage(), model = config.resolvedModel,
                )
            else -> {
                val probe = test(config, budget)
                if (probe.ok) probe else probe.copy(error = probe.error ?: listError)
            }
        }
    } ?: Result(false, "", config.provider.label, "The connection check timed out. Try again.")

    /** One documented normalisation, shared with the provider model check. */
    fun normalizeModelId(raw: String): String = AiOutputBudgetPolicy.normalizeModelId(raw)

    /**
     * Asks the provider which models this key can use (GET /models, the OpenAI-standard
     * endpoint that Gemini, NVIDIA, OpenRouter, Groq and OpenAI all implement). This is
     * what the "Load models" button in AI settings calls, so nobody has to guess a model
     * name or discover that theirs was retired by reading a 410 error.
     */
    suspend fun listModels(config: AiConfig, budget: RequestBudget = RequestBudget(AiRequestPolicy.Use.MODELS)): Pair<List<String>, String?> =
        withTimeoutOrNull(budget.remainingMs().coerceAtLeast(1)) { withContext(Dispatchers.IO) {
            if (config.provider == AiProvider.FREE || config.resolvedBaseUrl.isBlank())
                return@withContext AiProvider.FREE.suggestedModels to null
            try {
                val response = BoundedHttp.request("${config.resolvedBaseUrl}/models", budget.remainingMs(), headers = requestHeaders(config.apiKey))
                if (response.code !in 200..299) return@withContext emptyList<String>() to explain(response.code, response.body)
                val json = JSONObject(response.body)
                val array = json.optJSONArray("data") ?: json.optJSONArray("models") ?: JSONArray()
                val ids = (0 until minOf(array.length(), 1000)).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    item.optString("id").ifBlank { item.optString("name").removePrefix("models/") }.takeIf { it.isNotBlank() }
                }.distinct()
                ids to null
            } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList<String>() to connectionError(e) }
        } } ?: (emptyList<String>() to "Loading models timed out. Try again.")

    /** Quick check used by the "Test connection" button. */
    suspend fun test(config: AiConfig, budget: RequestBudget = RequestBudget(AiRequestPolicy.Use.TEST)): Result =
        ask(
            config = config,
            systemPrompt = "You are a test. Answer in five words or fewer.",
            userPrompt = "Reply with: CleanSweep AI connected.",
            budget = budget,
        )

    /**
     * Google's own `generateContent` endpoint with the Google Search tool switched on.
     *
     * Returns (ok, text). The text carries the answer, and — when the model used the search
     * tool — a short "Sources:" line built from what it actually read, so the user can see
     * where a live answer came from instead of having to trust it.
     */
    private suspend fun postGoogleWithSearch(
        config: AiConfig,
        systemPrompt: String,
        userPrompt: String,
        budget: RequestBudget,
    ): NativeReply {
        val base = config.googleNativeBase ?: return NativeReply(false, "This provider cannot search.")
        val key = config.apiKey.trim()
        if (key.isBlank()) return NativeReply(false, "No key saved.")
        val model = config.resolvedModel.trim().removePrefix("models/")
        val url = "$base/models/$model:generateContent"

        val body = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemPrompt)),
                ),
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
                    }
                ),
            )
            // The whole point: the model may look things up before it answers.
            put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", 0.4)
                    put("maxOutputTokens", AiOutputBudgetPolicy.effectiveOutputTokens(budget.use, budget.remainingMs()))
                },
            )
        }.toString()

        return postJson(url, body, bearer = null, budget = budget, googleKey = key)
    }

    /** A raw JSON POST with optional bearer auth; used by the Google search call. */
    private suspend fun postJson(url: String, body: String, bearer: String?, budget: RequestBudget, googleKey: String? = null): NativeReply = try {
        val response = BoundedHttp.request(url, budget.remainingMs(), "POST", body, requestHeaders(bearer) + (googleKey?.let { mapOf("x-goog-api-key" to it) } ?: emptyMap()))
        if (response.code !in 200..299) NativeReply(false, explain(response.code, response.body))
        else extractGoogleText(response.body)
    } catch (e: CancellationException) { throw e } catch (e: Exception) { NativeReply(false, connectionError(e)) }

    private data class NativeReply(val ok: Boolean, val text: String, val sources: List<String> = emptyList())

    /** Only actual grounding metadata, not the words "Sources" in a model answer, can claim cited search. */
    private fun extractGoogleText(body: String): NativeReply = try {
        val candidate = JSONObject(body).optJSONArray("candidates")?.optJSONObject(0)
        val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
        val answer = if (parts == null) "" else (0 until parts.length()).mapNotNull { index ->
            val part = parts.optJSONObject(index)
            part?.optString("text")?.takeIf { part?.optBoolean("thought") != true && it.isNotBlank() }
        }.joinToString("").trim()
        if (answer.isBlank()) NativeReply(false, "The provider returned no final answer.")
        else NativeReply(true, answer, candidate?.let { readSources(it) }.orEmpty())
    } catch (error: Exception) { NativeReply(false, "The provider returned no final answer.") }

    private fun readSources(candidate: JSONObject): List<String> {
        val out = LinkedHashSet<String>()
        try {
            val metadata = candidate.optJSONObject("groundingMetadata") ?: return emptyList()
            val chunks = metadata.optJSONArray("groundingChunks") ?: return emptyList()
            for (i in 0 until chunks.length()) {
                val web = chunks.optJSONObject(i)?.optJSONObject("web") ?: continue
                val uri = com.universalrp.cleansweep.data.NewsPolicy.canonicalLink(web.optString("uri")) ?: continue
                val title = web.optString("title").take(150).takeIf { it.isNotBlank() }
                out.add(if (title != null) "$title ($uri)" else uri)
                if (out.size >= 4) break
            }
        } catch (e: Exception) {
            // A reply without sources is still a reply.
        }
        return out.toList()
    }

    /** One attempt. Explicit retry has its own request ID/deadline; a timeout never triggers an endless fallback chain. */
    private suspend fun postWithRetry(url: String, apiKey: String?, model: String, systemPrompt: String, userPrompt: String,
        budget: RequestBudget): Pair<Boolean, String> {
        return try {
            val body = JSONObject().apply {
                put("model", model); put("temperature", 0.4)
                put("max_tokens", AiOutputBudgetPolicy.effectiveOutputTokens(budget.use, budget.remainingMs()))
                put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userPrompt)))
            }.toString()
            val response = BoundedHttp.request(url, budget.remainingMs(), "POST", body, requestHeaders(apiKey))
            if (response.code !in 200..299) false to explain(response.code, response.body)
            else extractText(response.body).let { if (it.isBlank()) false to "The provider returned no final answer (possibly an unsupported tool call). Please try another provider." else true to it }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { false to connectionError(e) }
    }

    private fun requestHeaders(key: String?): Map<String, String> = buildMap {
        put("Accept", "application/json"); put("Content-Type", "application/json; charset=utf-8")
        put("User-Agent", "LiveGuard/Android")
        if (!key.isNullOrBlank()) put("Authorization", "Bearer ${key.trim()}")
    }

    private fun connectionError(error: Exception): String = when (error) {
        is UnknownHostException -> "Could not reach the AI host. Check the connection and endpoint."
        is SocketTimeoutException -> "The AI service took too long to answer. Retry or choose another provider."
        is java.util.concurrent.RejectedExecutionException -> "Network tasks are busy; cancel or retry shortly."
        else -> "The AI request failed. Check the connection or retry." // No endpoint/key/raw exception details in user-facing output.
    }

    private fun explain(status: Int, body: String): String =
        AiFailurePolicy.message("HTTP $status " + extractApiMessage(body).orEmpty().take(300))

    private fun extractApiMessage(body: String): String? = try {
        val json = JSONObject(body)
        val error = json.optJSONObject("error")
        error?.optString("message")?.takeIf { it.isNotBlank() }
            ?: json.optString("message").takeIf { it.isNotBlank() }
            ?: body.take(180).takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        body.take(180).takeIf { it.isNotBlank() }
    }

    private fun extractText(body: String): String {
        try {
            val json = JSONObject(body)
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val message = choices.getJSONObject(0).optJSONObject("message")
                val content = message?.opt("content")
                when (content) {
                    is String -> if (content.isNotBlank()) return content
                    is JSONArray -> {
                        val builder = StringBuilder()
                        for (i in 0 until content.length()) {
                            val part = content.optJSONObject(i) ?: continue
                            builder.append(part.optString("text"))
                        }
                        if (builder.isNotBlank()) return builder.toString()
                    }
                }
                val text = choices.getJSONObject(0).optString("text")
                if (text.isNotBlank()) return text
            }
            // Some providers (and Gemini's compatibility layer) wrap it differently.
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val parts = candidates.getJSONObject(0)
                    .optJSONObject("content")?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    return parts.getJSONObject(0).optString("text")
                }
            }
            if (json.has("response")) return json.optString("response")
            return ""
        } catch (e: Exception) {
            // Plain-text endpoints are allowed, but never expose malformed JSON/tool calls.
        }
        return if (body.trimStart().startsWith("{") || body.trimStart().startsWith("[")) "" else body.take(4000)
    }
}
