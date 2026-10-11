package com.universalrp.cleansweep.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.SystemClock
import java.net.InetSocketAddress
import java.net.Socket

/** Throughput counters plus a SMALL ACTIVE TCP latency probe (not a zero-data/passive speed test). */
object LiveNetworkQuality {
    enum class QualityGrade { UNKNOWN, TOP_QUALITY, MEDIUM_QUALITY, BAD_QUALITY }

    data class QualitySnapshot(
        val isWifi: Boolean,
        val isMobile: Boolean,
        val rxSpeedBytesPerSec: Long,
        val txSpeedBytesPerSec: Long,
        val formattedRxSpeed: String,
        val formattedTxSpeed: String,
        val pingMs: Long?,
        val jitterMs: Long?,
        val grade: QualityGrade,
        val labelTamil: String,
        val labelEnglish: String,
        val capturedAtMs: Long = 0,
        val throughputMeasured: Boolean = true,
        val networkAvailable: Boolean = true,
    )

    private var lastRxBytes = -1L
    private var lastTxBytes = -1L
    private var lastTimestamp = 0L
    private var lastNetwork: Network? = null
    private val pingHistory = ArrayDeque<Long>(5)
    @Volatile private var snapshot: QualitySnapshot? = null
    fun cached(): QualitySnapshot? = snapshot

    @Synchronized fun measure(context: Context): QualitySnapshot {
        val now = SystemClock.elapsedRealtime()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val active = runCatching { cm?.activeNetwork }.getOrNull()
        val caps = runCatching { active?.let { cm?.getNetworkCapabilities(it) } }.getOrNull()
        val changed = active != lastNetwork
        if (!changed && lastTimestamp > 0 && now - lastTimestamp in 0..4_999) snapshot?.let { return it }
        if (changed) { pingHistory.clear(); lastTimestamp = 0; lastRxBytes = -1; lastTxBytes = -1 }
        lastNetwork = active
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val dt = now - lastTimestamp
        val measured = !changed && lastTimestamp > 0 && dt > 300 && rx >= lastRxBytes && tx >= lastTxBytes && lastRxBytes >= 0 && lastTxBytes >= 0
        val rxSpeed = if (measured) ((rx - lastRxBytes) / (dt / 1000.0)).toLong() else 0L
        val txSpeed = if (measured) ((tx - lastTxBytes) / (dt / 1000.0)).toLong() else 0L
        lastRxBytes = rx; lastTxBytes = tx; lastTimestamp = now
        val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val mobile = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val ping = if (connected) lightPing() else null
        // A failed probe cannot reuse old jitter as if it were a current observation.
        var jitter: Long? = null
        if (ping != null) {
            pingHistory.addLast(ping)
            if (pingHistory.size > 5) pingHistory.removeFirst()
            if (pingHistory.size >= 2) jitter = (1 until pingHistory.size).sumOf {
                kotlin.math.abs(pingHistory[it] - pingHistory[it - 1])
            } / (pingHistory.size - 1)
        } else pingHistory.clear()
        val grade = when (NetworkQualityPolicy.grade(ping, jitter, connected)) {
            NetworkQualityPolicy.Grade.UNKNOWN -> QualityGrade.UNKNOWN
            NetworkQualityPolicy.Grade.GOOD -> QualityGrade.TOP_QUALITY
            NetworkQualityPolicy.Grade.FAIR -> QualityGrade.MEDIUM_QUALITY
            NetworkQualityPolicy.Grade.POOR -> QualityGrade.BAD_QUALITY
        }
        val (ta, en) = when (grade) {
            QualityGrade.UNKNOWN -> "தர அளவீடு இன்னும் இல்லை" to "Quality not measured"
            QualityGrade.TOP_QUALITY -> "அளவிடப்பட்ட தாமதம்: சிறந்த தரம்" to "Very Good (measured probe)"
            QualityGrade.MEDIUM_QUALITY -> "அளவிடப்பட்ட தாமதம்: மிதமான தரம்" to "Medium (measured probe)"
            QualityGrade.BAD_QUALITY -> "அளவிடப்பட்ட தாமதம்: கவனம் தேவை" to "Poor latency / jitter"
        }
        return QualitySnapshot(wifi, mobile, rxSpeed, txSpeed,
            if (measured) formatSpeed(rxSpeed) else "Not measured", if (measured) formatSpeed(txSpeed) else "Not measured",
            ping, jitter, grade, ta, en, System.currentTimeMillis(), measured, connected).also { snapshot = it }
    }

    private fun lightPing(): Long? = try {
        val started = SystemClock.elapsedRealtime()
        Socket().use { it.connect(InetSocketAddress("1.1.1.1", 53), 250) }
        (SystemClock.elapsedRealtime() - started).coerceAtLeast(1)
    } catch (e: Exception) { null }

    private fun formatSpeed(bytes: Long): String = when {
        bytes >= 1_048_576 -> "%.1f MB/s".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "${bytes / 1024} KB/s"
        else -> "$bytes B/s"
    }
}
