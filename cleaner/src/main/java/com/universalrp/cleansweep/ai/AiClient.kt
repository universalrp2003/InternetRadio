package com.universalrp.cleansweep.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
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
    )

    private const val CONNECT_TIMEOUT_MS = 20_000

    /**
     * Big reports on a free service can genuinely take a minute, and the 503s the user hit
     * come from the provider being busy rather than from anything wrong on the phone. So the
     * read timeout is generous and a busy/timed-out request is retried automatically.
     */
    private const val READ_TIMEOUT_MS = 180_000
    private const val RETRIES = 2

    /** Told which attempt is running, so the screen can say "retrying…" instead of freezing. */
    var attemptListener: ((attempt: Int, of: Int, reason: String) -> Unit)? = null

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
    ): Result = withContext(Dispatchers.IO) {
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
            val grounded = postGoogleWithSearch(config, systemPrompt, userPrompt)
            if (grounded.first) {
                val parts = grounded.second
                return@withContext Result(
                    ok = true,
                    text = parts,
                    providerLabel = config.provider.label + " (with web search)",
                    model = config.resolvedModel,
                )
            }
        }

        if (config.provider == AiProvider.FREE) {
            var lastError = "No free endpoint answered."
            // Try the endpoint that worked last time first.
            val ordered = AiSettings.KEYLESS.sortedByDescending { it.url == config.freeEndpointUrl }
            for (endpoint in ordered) {
                val which = endpoint.label.substringBefore(" (")
                val result = postWithRetry(
                    url = endpoint.url,
                    apiKey = null,
                    model = endpoint.model,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
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
                error = "$lastError\n\nFree endpoints are shared and often busy. Try again in a " +
                    "minute, or add a free Gemini key in AI settings for a reliable answer.",
            )
        }

        val base = config.resolvedBaseUrl
        val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        val (ok, payload) = postWithRetry(
            url = url,
            apiKey = config.apiKey,
            model = config.resolvedModel,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
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
                error = payload,
                model = config.resolvedModel,
            )
        }
    }

    /**
     * "Is my AI actually usable right now?" — the check the app runs when it opens.
     * For a keyed provider it asks for the model list (cheap, no tokens); for the free
     * option it sends a two-word prompt to each keyless endpoint until one answers.
     * Returns the label of whatever answered, plus the endpoint url when it is the free
     * option, so the app can remember it.
     */
    suspend fun ping(config: AiConfig): Result = withContext(Dispatchers.IO) {
        if (config.provider == AiProvider.FREE) {
            var lastError = "No free endpoint answered."
            for (endpoint in AiSettings.KEYLESS.sortedByDescending { it.url == config.freeEndpointUrl }) {
                val which = endpoint.label.substringBefore(" (")
                val (ok, payload) = postWithRetry(
                    url = endpoint.url,
                    apiKey = null,
                    model = endpoint.model,
                    systemPrompt = "You are a health check. Answer with one word.",
                    userPrompt = "Reply with: ready",
                )
                if (ok) {
                    return@withContext Result(
                        ok = true,
                        text = payload,
                        providerLabel = "Free AI • $which",
                        endpointUrl = endpoint.url,
                        model = endpoint.model,
                    )
                }
                lastError = "$which: $payload"
            }
            return@withContext Result(
                ok = false,
                text = "",
                providerLabel = "Free AI",
                error = lastError,
            )
        }

        if (!config.ready) {
            return@withContext Result(
                ok = false,
                text = "",
                providerLabel = config.provider.label,
                error = "No key saved yet.",
            )
        }

        val (models, listError) = listModels(config)
        if (models.isNotEmpty()) {
            val modelOk = config.resolvedModel in models ||
                models.any { it.equals(config.resolvedModel, ignoreCase = true) }
            return@withContext Result(
                ok = modelOk,
                text = "",
                providerLabel = config.provider.label,
                error = if (modelOk) {
                    null
                } else {
                    "\"${config.resolvedModel}\" is not among the ${models.size} models this key " +
                        "can use. Tap Load models and pick a current one."
                },
                model = config.resolvedModel,
            )
        }

        // Some providers do not implement GET /models — fall back to one tiny question.
        val probe = test(config)
        if (probe.ok) probe.copy(error = null) else probe.copy(error = listError ?: probe.error)
    }

    /**
     * Asks the provider which models this key can use (GET /models, the OpenAI-standard
     * endpoint that Gemini, NVIDIA, OpenRouter, Groq and OpenAI all implement). This is
     * what the "Load models" button in AI settings calls, so nobody has to guess a model
     * name or discover that theirs was retired by reading a 410 error.
     */
    suspend fun listModels(config: AiConfig): Pair<List<String>, String?> = withContext(Dispatchers.IO) {
        val base = config.resolvedBaseUrl
        if (config.provider == AiProvider.FREE || base.isBlank()) {
            return@withContext AiProvider.FREE.suggestedModels to null
        }
        var connection: HttpURLConnection? = null
        try {
            connection = (URL("$base/models").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = 30_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "CleanSweep/2.1 (Android)")
                if (config.apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${config.apiKey.trim()}")
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.let { readAll(it) }.orEmpty()
            if (status !in 200..299) {
                return@withContext emptyList<String>() to explain(status, body)
            }
            val ids = mutableListOf<String>()
            val json = JSONObject(body)
            val data = json.optJSONArray("data")
            if (data != null) {
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val id = item.optString("id").ifBlank { item.optString("name") }
                    if (id.isNotBlank()) ids.add(id)
                }
            } else {
                // Gemini's native shape, in case a compatibility layer is not present.
                val models = json.optJSONArray("models")
                if (models != null) {
                    for (i in 0 until models.length()) {
                        val item = models.optJSONObject(i) ?: continue
                        val name = item.optString("name").removePrefix("models/")
                        if (name.isNotBlank()) ids.add(name)
                    }
                }
            }
            ids.toList() to null
        } catch (e: UnknownHostException) {
            emptyList<String>() to "No internet connection (could not reach the AI service)."
        } catch (e: Exception) {
            emptyList<String>() to "${e.javaClass.simpleName}: ${e.message ?: "could not list models"}"
        } finally {
            connection?.disconnect()
        }
    }

    /** Quick check used by the "Test connection" button. */
    suspend fun test(config: AiConfig): Result =
        ask(
            config = config,
            systemPrompt = "You are a test. Answer in five words or fewer.",
            userPrompt = "Reply with: CleanSweep AI connected.",
        )

    /**
     * Google's own `generateContent` endpoint with the Google Search tool switched on.
     *
     * Returns (ok, text). The text carries the answer, and — when the model used the search
     * tool — a short "Sources:" line built from what it actually read, so the user can see
     * where a live answer came from instead of having to trust it.
     */
    private fun postGoogleWithSearch(
        config: AiConfig,
        systemPrompt: String,
        userPrompt: String,
    ): Pair<Boolean, String> {
        val base = config.googleNativeBase ?: return false to "This provider cannot search."
        val key = config.apiKey.trim()
        if (key.isBlank()) return false to "No key saved."
        val model = config.resolvedModel.trim().removePrefix("models/")
        val url = "$base/models/$model:generateContent?key=" + java.net.URLEncoder.encode(key, "UTF-8")

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
                    put("maxOutputTokens", 2400)
                },
            )
        }.toString()

        var last = false to "No answer."
        for (attempt in 1..(RETRIES + 1)) {
            attemptListener?.invoke(attempt, RETRIES + 1, "")
            last = postJson(url, body, bearer = null)
            if (last.first) return last
            val retryable = listOf("429", "502", "503", "504", "timeout", "Unable to resolve host")
                .any { last.second.contains(it) }
            if (!retryable || attempt > RETRIES) return last
            try {
                Thread.sleep(1_200L * attempt)
            } catch (e: InterruptedException) {
                return last
            }
        }
        return last
    }

    /** A raw JSON POST with optional bearer auth; used by the Google search call. */
    private fun postJson(url: String, body: String, bearer: String?): Pair<Boolean, String> {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "CleanSweep/2.11 (Android)")
                if (!bearer.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer ${bearer.trim()}")
                }
            }
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body)
                writer.flush()
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let { readAll(it) }.orEmpty()
            if (status !in 200..299) {
                return false to explain(status, text)
            }
            return true to extractGoogleText(text)
        } catch (e: UnknownHostException) {
            return false to "No internet connection (could not reach the AI service)."
        } catch (e: SocketTimeoutException) {
            return false to "The AI service took too long to answer (${READ_TIMEOUT_MS / 1000} s)."
        } catch (e: Exception) {
            return false to "${e.javaClass.simpleName}: ${e.message ?: "request failed"}"
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Pulls the answer out of a generateContent reply and appends the pages the model used,
     * when it searched. Grounded answers arrive as several parts; all of them are kept.
     */
    private fun extractGoogleText(body: String): String {
        return try {
            val json = JSONObject(body)
            val candidates = json.optJSONArray("candidates")
            val builder = StringBuilder()
            var sources = listOf<String>()
            if (candidates != null && candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val piece = parts.optJSONObject(i)?.optString("text").orEmpty()
                        if (piece.isNotBlank()) builder.append(piece)
                    }
                }
                sources = readSources(candidate)
            }
            val answer = builder.toString().trim().ifBlank { body.take(4000) }
            if (sources.isEmpty()) {
                answer
            } else {
                answer + "\n\n" + "Sources:" + "\n" + sources.joinToString("\n") { "- $it" }
            }
        } catch (e: Exception) {
            body.take(4000)
        }
    }

    private fun readSources(candidate: JSONObject): List<String> {
        val out = LinkedHashSet<String>()
        try {
            val metadata = candidate.optJSONObject("groundingMetadata") ?: return emptyList()
            val chunks = metadata.optJSONArray("groundingChunks") ?: return emptyList()
            for (i in 0 until chunks.length()) {
                val web = chunks.optJSONObject(i)?.optJSONObject("web") ?: continue
                val uri = web.optString("uri").takeIf { it.isNotBlank() } ?: continue
                val title = web.optString("title").takeIf { it.isNotBlank() }
                out.add(if (title != null) "$title ($uri)" else uri)
                if (out.size >= 4) break
            }
        } catch (e: Exception) {
            // A reply without sources is still a reply.
        }
        return out.toList()
    }

    /**
     * Sends the request, retrying when the service is busy (429/5xx) or the connection broke.
     * Returns the last answer either way; a caller never sees an exception from here.
     */
    private fun postWithRetry(
        url: String,
        apiKey: String?,
        model: String,
        systemPrompt: String,
        userPrompt: String,
    ): Pair<Boolean, String> {
        var last: Pair<Boolean, String> = false to "No answer."
        var attempt = 0
        while (attempt <= RETRIES) {
            attempt++
            attemptListener?.invoke(attempt, RETRIES + 1, "")
            val result = post(url, apiKey, model, systemPrompt, userPrompt, attempt, RETRIES + 1)
            last = result
            if (result.first) return result
            val retryable = result.second.contains("503") ||
                result.second.contains("502") ||
                result.second.contains("504") ||
                result.second.contains("429") ||
                result.second.contains("too long to answer") ||
                result.second.contains("timeout") ||
                result.second.contains("Connection reset") ||
                result.second.contains("Unable to resolve host") ||
                result.second.contains("No internet connection")
            if (!retryable || attempt > RETRIES) return result
            // A short pause, longer after each try: providers that say 503 usually recover.
            try {
                Thread.sleep(1_200L * attempt)
            } catch (e: InterruptedException) {
                return result
            }
        }
        return last
    }

    private fun post(
        url: String,
        apiKey: String?,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        attempt: Int = 1,
        attempts: Int = 1,
    ): Pair<Boolean, String> {
        var connection: HttpURLConnection? = null
        try {
            val body = JSONObject().apply {
                put("model", model)
                put("temperature", 0.4)
                put("max_tokens", 3600)
                put(
                    "messages",
                    JSONArray().apply {
                        put(
                            JSONObject().apply {
                                put("role", "system")
                                put("content", systemPrompt)
                            }
                        )
                        put(
                            JSONObject().apply {
                                put("role", "user")
                                put("content", userPrompt)
                            }
                        )
                    }
                )
            }.toString()

            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "CleanSweep/2.0 (Android)")
                if (!apiKey.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                }
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body)
                writer.flush()
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let { readAll(it) }.orEmpty()

            if (status !in 200..299) {
                val explained = explain(status, text)
                return false to if (attempts > 1) "$explained (try $attempt of $attempts)" else explained
            }
            return true to extractText(text)
        } catch (e: UnknownHostException) {
            return false to "No internet connection (could not reach the AI service)."
        } catch (e: SocketTimeoutException) {
            return false to "The AI service took too long to answer (${READ_TIMEOUT_MS / 1000} s). " +
                "Big reports on a free service can be slow — try again, or use a paid/faster provider."
        } catch (e: java.net.SocketException) {
            return false to "The connection dropped while waiting (${e.message ?: "socket error"}). " +
                "Mobile data and some Wi-Fi routers cut long requests; try again or switch network."
        } catch (e: Exception) {
            return false to "${e.javaClass.simpleName}: ${e.message ?: "request failed"}"
        } finally {
            connection?.disconnect()
        }
    }

    private fun readAll(stream: java.io.InputStream): String {
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            val builder = StringBuilder()
            var line = reader.readLine()
            while (line != null) {
                builder.append(line)
                line = reader.readLine()
            }
            return builder.toString()
        }
    }

    private fun explain(status: Int, body: String): String {
        val detail = extractApiMessage(body)
        return when (status) {
            400 -> "The service rejected the request${detail?.let { ": $it" } ?: ""}. " +
                "Check the model name in AI settings."
            401, 403 -> "The API key was refused (HTTP $status). Paste it again — no spaces, whole key."
            404 -> "Model not found. Tap \"Load models\" in AI settings to list the models your key can use."
            410 -> "That model has been retired by the provider (HTTP 410). Tap \"Load models\" in " +
                "AI settings and pick a current one — providers retire model ids every few months."
            413 -> "The report was too large for this model. Turn off \"send app names\" and retry."
            429 -> "The provider is rate-limiting you (HTTP 429) — the free quota is used up for now. " +
                "CleanSweep already retried; wait a minute or switch provider."
            502, 503, 504 -> "The AI service is overloaded right now (HTTP $status) — that is the " +
                "provider's side, not your phone or your key. CleanSweep retried automatically; " +
                "tap Analyse again in a minute, or switch to another provider."
            in 500..599 -> "The AI service had a server error (HTTP $status). Try again shortly."
            else -> "HTTP $status${detail?.let { ": $it" } ?: ""}"
        }
    }

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
        } catch (e: Exception) {
            // Fall through: show the raw body so nothing is hidden from the user.
        }
        return body.take(4000)
    }
}
