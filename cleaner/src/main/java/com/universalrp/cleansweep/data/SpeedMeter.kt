package com.universalrp.cleansweep.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * A real download/upload speed test.
 *
 * It pulls (or pushes) an exact number of bytes from Cloudflare's public speed-test host and
 * measures the time, so the number on screen is measured, not estimated. Two things the
 * screen says plainly:
 *
 *  * **it uses your data** — 1 MB to 100 MB of it, whichever size you pick, so on a metered
 *    plan pick small, and on an unlimited 5G plan the big sizes are safe (and are the only
 *    way to see the real top speed, because small files finish before the link ramps up);
 *  * a speed test on a phone is limited by the phone and the Wi-Fi/mobile link at that
 *    moment — testing twice, minutes apart, is more useful than one big number.
 */
data class SpeedProgress(
    val phase: String,
    val doneBytes: Long,
    val totalBytes: Long,
    val mbps: Double,
    val elapsedMs: Long,
) {
    val percent: Int
        get() = if (totalBytes <= 0L) 0 else ((doneBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
}

data class SpeedResult(
    val ok: Boolean,
    val mbps: Double,
    val bytes: Long,
    val seconds: Double,
    val error: String? = null,
)

object SpeedMeter {

    const val DOWN_URL = "https://speed.cloudflare.com/__down?bytes="
    const val UP_URL = "https://speed.cloudflare.com/__up"

    /** Sizes offered in the UI, smallest first. 100 MB is for unlimited 5G plans. */
    val SIZES_MB: List<Int> = listOf(1, 5, 10, 25, 50, 100)

    /** Warns before a big test on a metered connection. */
    fun isMetered(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        when {
            caps == null -> false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> !caps.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_METERED
            )
            else -> !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
    } catch (e: Exception) {
        false
    }

    /** True when the active link is mobile data (so a warning makes sense). */
    fun onMobileData(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
    } catch (e: Exception) {
        false
    }

    suspend fun download(
        bytes: Long,
        onProgress: (SpeedProgress) -> Unit,
    ): SpeedResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL("$DOWN_URL$bytes").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 120_000
                setRequestProperty("Cache-Control", "no-store")
                setRequestProperty("User-Agent", "CleanSweep-speedtest/2.3 (Android)")
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                return@withContext SpeedResult(
                    ok = false, mbps = 0.0, bytes = 0L, seconds = 0.0,
                    error = "The speed-test server answered HTTP $status.",
                )
            }
            val started = System.nanoTime()
            var read = 0L
            var lastEmit = 0L
            val buffer = ByteArray(64 * 1024)
            connection.inputStream.use { input ->
                while (read < bytes) {
                    val n = input.read(buffer)
                    if (n <= 0) break
                    read += n
                    val elapsedMs = (System.nanoTime() - started) / 1_000_000
                    if (elapsedMs - lastEmit >= 160 || read >= bytes) {
                        lastEmit = elapsedMs
                        val seconds = elapsedMs / 1000.0
                        onProgress(
                            SpeedProgress(
                                phase = "Downloading",
                                doneBytes = read,
                                totalBytes = bytes,
                                mbps = if (seconds > 0) (read * 8.0 / 1_000_000.0) / seconds else 0.0,
                                elapsedMs = elapsedMs,
                            )
                        )
                    }
                }
            }
            val seconds = (System.nanoTime() - started) / 1_000_000_000.0
            SpeedResult(
                ok = read > 0,
                mbps = if (seconds > 0) (read * 8.0 / 1_000_000.0) / seconds else 0.0,
                bytes = read,
                seconds = seconds,
                error = if (read > 0) null else "The server sent no data.",
            )
        } catch (e: Exception) {
            SpeedResult(
                ok = false, mbps = 0.0, bytes = 0L, seconds = 0.0,
                error = "${e.javaClass.simpleName}: ${e.message ?: "the test could not finish"}",
            )
        } finally {
            connection?.disconnect()
        }
    }

    /** Upload test: sends [bytes] of harmless zeros and measures how fast they go. */
    suspend fun upload(
        bytes: Long,
        onProgress: (SpeedProgress) -> Unit,
    ): SpeedResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(UP_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 120_000
                doOutput = true
                setFixedLengthStreamingMode(bytes)
                setRequestProperty("Content-Type", "application/octet-stream")
                setRequestProperty("Cache-Control", "no-store")
                setRequestProperty("User-Agent", "CleanSweep-speedtest/2.3 (Android)")
            }
            val started = System.nanoTime()
            var written = 0L
            var lastEmit = 0L
            val chunk = ByteArray(64 * 1024)
            val out: OutputStream = connection.outputStream
            while (written < bytes) {
                val size = minOf(chunk.size.toLong(), bytes - written).toInt()
                out.write(chunk, 0, size)
                written += size
                val elapsedMs = (System.nanoTime() - started) / 1_000_000
                if (elapsedMs - lastEmit >= 160 || written >= bytes) {
                    lastEmit = elapsedMs
                    val seconds = elapsedMs / 1000.0
                    onProgress(
                        SpeedProgress(
                            phase = "Uploading",
                            doneBytes = written,
                            totalBytes = bytes,
                            mbps = if (seconds > 0) (written * 8.0 / 1_000_000.0) / seconds else 0.0,
                            elapsedMs = elapsedMs,
                        )
                    )
                }
            }
            out.flush()
            out.close()
            val status = connection.responseCode
            val seconds = (System.nanoTime() - started) / 1_000_000_000.0
            if (status !in 200..299) {
                return@withContext SpeedResult(
                    ok = false, mbps = 0.0, bytes = written, seconds = seconds,
                    error = "The upload endpoint answered HTTP $status.",
                )
            }
            SpeedResult(
                ok = true,
                mbps = if (seconds > 0) (written * 8.0 / 1_000_000.0) / seconds else 0.0,
                bytes = written,
                seconds = seconds,
            )
        } catch (e: Exception) {
            SpeedResult(
                ok = false, mbps = 0.0, bytes = 0L, seconds = 0.0,
                error = "${e.javaClass.simpleName}: ${e.message ?: "the upload could not finish"}",
            )
        } finally {
            connection?.disconnect()
        }
    }
}
