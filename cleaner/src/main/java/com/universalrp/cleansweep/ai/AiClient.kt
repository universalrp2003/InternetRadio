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
    )

    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 120_000

    suspend fun ask(
        config: AiConfig,
        systemPrompt: String,
        userPrompt: String,
    ): Result = withContext(Dispatchers.IO) {
        if (!config.ready) {
            return@withContext Result(
                ok = false,
                text = "",
                providerLabel = config.provider.label,
                error = "Add your API key in AI settings first (or switch to the free option).",
            )
        }

        if (config.provider == AiProvider.FREE) {
            var lastError = "No free endpoint answered."
            for (endpoint in AiSettings.KEYLESS) {
                val result = post(
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
                        providerLabel = "Free • ${endpoint.label}",
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
        val (ok, payload) = post(
            url = url,
            apiKey = config.apiKey,
            model = config.resolvedModel,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
        )
        if (ok) {
            Result(ok = true, text = payload, providerLabel = config.provider.label)
        } else {
            Result(ok = false, text = "", providerLabel = config.provider.label, error = payload)
        }
    }

    /** Quick check used by the "Test connection" button. */
    suspend fun test(config: AiConfig): Result =
        ask(
            config = config,
            systemPrompt = "You are a test. Answer in five words or fewer.",
            userPrompt = "Reply with: CleanSweep AI connected.",
        )

    private fun post(
        url: String,
        apiKey: String?,
        model: String,
        systemPrompt: String,
        userPrompt: String,
    ): Pair<Boolean, String> {
        var connection: HttpURLConnection? = null
        try {
            val body = JSONObject().apply {
                put("model", model)
                put("temperature", 0.4)
                put("max_tokens", 1600)
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
                return false to explain(status, text)
            }
            return true to extractText(text)
        } catch (e: UnknownHostException) {
            return false to "No internet connection (could not reach the AI service)."
        } catch (e: SocketTimeoutException) {
            return false to "The AI service took too long to answer. Try again."
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
            404 -> "Model not found. Open AI settings and pick a model that your account can use."
            413 -> "The report was too large for this model. Turn off \"send app names\" and retry."
            429 -> "Rate limit or free quota used up (HTTP 429). Wait a minute, or switch provider."
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
