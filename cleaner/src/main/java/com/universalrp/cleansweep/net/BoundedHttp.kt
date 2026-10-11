package com.universalrp.cleansweep.net

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.SocketTimeoutException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * A cancellable continuation does not await a blocked socket/DNS task after its deadline.
 * Socket work and disconnect never run on UI; at most four concurrent tasks, no unbounded queue.
 * Native DNS may outlive cancellation briefly; no old result is delivered to the caller.
 */
object BoundedHttp {
    data class Response(val code: Int, val body: String)
    private val io = ThreadPoolExecutor(0, 4, 30, TimeUnit.SECONDS, SynchronousQueue<Runnable>(),
        { work -> Thread(work, "LiveGuard-network").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    private val cleanup = ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS, java.util.concurrent.ArrayBlockingQueue<Runnable>(8),
        { work -> Thread(work, "LiveGuard-network-cleanup").apply { isDaemon = true } }, ThreadPoolExecutor.DiscardPolicy())

    suspend fun request(url: String, timeoutMs: Long, method: String = "GET", body: String? = null,
        headers: Map<String, String> = emptyMap(), maxBytes: Int = 1024 * 1024): Response = withTimeoutOrNull(timeoutMs.coerceAtLeast(1)) {
        suspendCancellableCoroutine<Response> { continuation ->
            val connection = AtomicReference<HttpURLConnection?>()
            val future = try { io.submit {
                var conn: HttpURLConnection? = null
                try {
                    if (!continuation.isActive) return@submit
                    val opened = URL(url).openConnection() as HttpURLConnection
                    conn = opened
                    connection.set(opened)
                    if (!continuation.isActive) return@submit
                    opened.requestMethod = method
                    opened.connectTimeout = minOf(8_000L, timeoutMs).coerceAtLeast(1).toInt()
                    opened.readTimeout = minOf(25_000L, timeoutMs).coerceAtLeast(1).toInt()
                    opened.instanceFollowRedirects = method == "GET" && headers.keys.none { it.equals("Authorization", true) || it.equals("x-goog-api-key", true) } // Never forward POST credentials to a redirect host.
                    headers.forEach { (name, value) -> opened.setRequestProperty(name, value) }
                    if (body != null) {
                        opened.doOutput = true
                        opened.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    }
                    val status = opened.responseCode
                    val stream = if (status in 200..299) opened.inputStream else opened.errorStream
                    val bytes = stream?.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (continuation.isActive) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            if (out.size() + n > maxBytes) throw java.io.IOException("Response exceeds the size limit")
                            out.write(buffer, 0, n)
                        }
                        out.toByteArray()
                    } ?: byteArrayOf()
                    if (continuation.isActive) continuation.resume(Response(status, bytes.toString(Charsets.UTF_8)))
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                } finally { runCatching { conn?.disconnect() }; connection.set(null) }
            } } catch (e: java.util.concurrent.RejectedExecutionException) {
                continuation.resumeWithException(e); null
            }
            continuation.invokeOnCancellation {
                future?.cancel(true)
                cleanup.execute { runCatching { connection.getAndSet(null)?.disconnect() } }
            }
        }
    } ?: throw SocketTimeoutException("Request deadline exceeded")
}
