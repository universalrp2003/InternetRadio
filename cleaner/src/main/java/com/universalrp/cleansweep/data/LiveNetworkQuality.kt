package com.universalrp.cleansweep.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.SystemClock
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Passive, zero-waste live network quality meter:
 * Measures throughput (KB/s or MB/s), ping latency, and jitter directly from user's
 * active traffic (both Wi-Fi and Cellular) without running heavy speed tests that burn data.
 *
 * Quality ratings:
 *  - TOP_QUALITY: Throughput > 2 MB/s or Ping < 50ms & Jitter < 15ms -> Gold / Flashing Gold
 *  - MEDIUM_QUALITY: Ping 50-130ms or speed 250 KB/s - 2 MB/s -> Green with Gold outline
 *  - BAD_QUALITY: Ping > 130ms or high packet delay / jitter -> Red blinking
 */
object LiveNetworkQuality {

    enum class QualityGrade {
        TOP_QUALITY,    // Gold flashing
        MEDIUM_QUALITY, // Green with gold outline
        BAD_QUALITY     // Red blink
    }

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
    )

    private var lastRxBytes: Long = 0L
    private var lastTxBytes: Long = 0L
    private var lastTimestamp: Long = 0L

    private val pingHistory = ArrayDeque<Long>(5)

    @Synchronized
    fun measure(context: Context): QualitySnapshot {
        val now = SystemClock.elapsedRealtime()
        val currentRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
        val currentTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)

        var rxSpeed = 0L
        var txSpeed = 0L

        if (lastTimestamp > 0 && now > lastTimestamp) {
            val deltaSec = (now - lastTimestamp) / 1000.0
            if (deltaSec > 0.3) {
                rxSpeed = ((currentRx - lastRxBytes).coerceAtLeast(0L) / deltaSec).toLong()
                txSpeed = ((currentTx - lastTxBytes).coerceAtLeast(0L) / deltaSec).toLong()
            }
        }

        lastRxBytes = currentRx
        lastTxBytes = currentTx
        lastTimestamp = now

        // Check active network type
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNet)

        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isMobile = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        // Measure light passive ping without transferring data (socket connect to Cloudflare DNS 1.1.1.1:53)
        val currentPing = measureLightPing(caps)
        var jitter: Long? = null

        if (currentPing != null) {
            pingHistory.addLast(currentPing)
            if (pingHistory.size > 5) pingHistory.removeFirst()
            if (pingHistory.size >= 2) {
                var diffSum = 0L
                for (i in 1 until pingHistory.size) {
                    diffSum += kotlin.math.abs(pingHistory[i] - pingHistory[i - 1])
                }
                jitter = diffSum / (pingHistory.size - 1)
            }
        }

        // Determine quality grade
        val effectivePing = currentPing ?: 50L
        val effectiveJitter = jitter ?: 10L
        val totalSpeed = rxSpeed + txSpeed

        val grade = when {
            // High speed or very low latency & jitter
            totalSpeed > 1_500_000L || (effectivePing in 1..55 && effectiveJitter <= 15) ->
                QualityGrade.TOP_QUALITY
            // Moderate speed or normal latency
            totalSpeed > 150_000L || (effectivePing <= 125 && effectiveJitter <= 35) ->
                QualityGrade.MEDIUM_QUALITY
            // Slow or high ping / jitter
            else ->
                QualityGrade.BAD_QUALITY
        }

        val (lblTa, lblEn) = when (grade) {
            QualityGrade.TOP_QUALITY -> "அதிவேகம் / மிகச்சிறந்த தரம்" to "Very Good Quality"
            QualityGrade.MEDIUM_QUALITY -> "மிதமான தரம்" to "Medium Quality"
            QualityGrade.BAD_QUALITY -> "மந்தமான / பலவீனமான தரம்" to "Bad / High Latency"
        }

        return QualitySnapshot(
            isWifi = isWifi,
            isMobile = isMobile,
            rxSpeedBytesPerSec = rxSpeed,
            txSpeedBytesPerSec = txSpeed,
            formattedRxSpeed = formatSpeed(rxSpeed),
            formattedTxSpeed = formatSpeed(txSpeed),
            pingMs = currentPing,
            jitterMs = jitter,
            grade = grade,
            labelTamil = lblTa,
            labelEnglish = lblEn,
        )
    }

    private fun measureLightPing(caps: NetworkCapabilities?): Long? {
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return null
        }
        val t0 = SystemClock.elapsedRealtime()
        return try {
            // Light 0-byte socket connect to Cloudflare DNS 1.1.1.1 port 53 with 250ms timeout
            Socket().use { s ->
                s.connect(InetSocketAddress(InetAddress.getByName("1.1.1.1"), 53), 250)
            }
            val elapsed = SystemClock.elapsedRealtime() - t0
            elapsed.coerceAtLeast(1L)
        } catch (e: Exception) {
            null
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1_048_576L -> "%.1f MB/s".format(bytesPerSec / 1_048_576.0)
            bytesPerSec >= 1024L -> "%d KB/s".format(bytesPerSec / 1024)
            else -> "%d B/s".format(bytesPerSec)
        }
    }
}
