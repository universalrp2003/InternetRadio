package com.universalrp.cleansweep.updates

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One bounded streaming download; only HTTPS GitHub delivery hosts, no auth headers, no browser or retries. */
object VerifiedApkDownload {
    private val worker = ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, SynchronousQueue<Runnable>(),
        { task -> Thread(task, "LiveGuard-update-download").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    private val cleanup = ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue<Runnable>(2),
        { task -> Thread(task, "LiveGuard-update-cleanup").apply { isDaemon = true } }, ThreadPoolExecutor.DiscardPolicy())
    private val hosts = setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
    suspend fun download(url: String, file: File, expectedBytes: Long, expectedHash: String, progress: (Long) -> Unit): File =
        withTimeoutOrNull(180_000) {
            suspendCancellableCoroutine<File> { continuation ->
                val connection = AtomicReference<HttpURLConnection?>()
                val future = try { worker.submit {
                    var complete = false
                    try {
                        check(UpdatePolicy.validSize(expectedBytes) && UpdatePolicy.digest(expectedHash) != null)
                        var location = URL(url)
                        var finalConnection: HttpURLConnection? = null
                        for (hop in 0..4) {
                            check(location.protocol == "https" && location.host in hosts && location.userInfo == null && location.port in setOf(-1, 443))
                            if (!continuation.isActive) return@submit
                            val conn = location.openConnection() as HttpURLConnection
                            connection.set(conn); conn.instanceFollowRedirects = false; conn.connectTimeout = 8_000; conn.readTimeout = 15_000
                            conn.setRequestProperty("User-Agent", "LiveGuard")
                            val status = conn.responseCode
                            if (status in setOf(301, 302, 303, 307, 308)) {
                                val next = conn.getHeaderField("Location") ?: throw IOException("Update redirect unavailable")
                                location = URL(location, next); conn.disconnect(); connection.set(null)
                                if (hop == 4) throw IOException("Too many update redirects")
                            } else { if (status != 200) throw IOException("Update download unavailable"); finalConnection = conn; break }
                        }
                        val response = finalConnection ?: throw IOException("Update download unavailable")
                        val length = response.contentLengthLong
                        if (length > 0 && length != expectedBytes) throw IOException("Update size differs from release metadata")
                        check(file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true)
                        val hash = MessageDigest.getInstance("SHA-256")
                        var bytes = 0L; var lastProgress = 0L
                        response.inputStream.use { input -> file.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            while (continuation.isActive) {
                                val n = input.read(buffer); if (n < 0) break
                                bytes += n; if (bytes > expectedBytes) throw IOException("Update exceeds release size")
                                output.write(buffer, 0, n); hash.update(buffer, 0, n)
                                val now = android.os.SystemClock.elapsedRealtime()
                                if (now - lastProgress > 200) { progress(bytes); lastProgress = now }
                            }
                        } }
                        if (!continuation.isActive) return@submit
                        val actual = hash.digest().joinToString("") { "%02x".format(it) }
                        if (bytes != expectedBytes || actual != expectedHash) throw IOException("Update digest verification failed")
                        progress(bytes); complete = true; continuation.resume(file)
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) } finally { runCatching { connection.getAndSet(null)?.disconnect() }; if (!complete) file.delete() }
                } } catch (e: RejectedExecutionException) { continuation.resumeWithException(e); null }
                continuation.invokeOnCancellation { future?.cancel(true); cleanup.execute { runCatching { connection.getAndSet(null)?.disconnect() } } }
            }
        } ?: throw SocketTimeoutException("Update download deadline exceeded")
}
