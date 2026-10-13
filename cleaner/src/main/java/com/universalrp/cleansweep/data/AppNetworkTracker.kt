package com.universalrp.cleansweep.data

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.InetAddress

/**
 * On-Device App Network & Tracker Inspector (Pure Passive & NetworkStats Mode).
 * No external or loopback VPN needed.
 * Directly attributes app traffic, detects trackers, and maps known companies/clouds.
 */
object AppNetworkTracker {

    enum class EvidenceSource { LIVE_ESTABLISHED_TCP, NETWORK_STATS_LAST_24_HOURS }

    data class ActiveConnection(
        val appName: String,
        val packageName: String,
        val uid: Int,
        val localPort: Int,
        val remoteIp: String,
        val remotePort: Int,
        val destinationHost: String?,
        val orgOrCompany: String,
        val isTracker: Boolean,
        val category: String, // Heuristic label; not proof of endpoint identity.
        val evidenceSource: EvidenceSource,
    )

    data class TrackerReport(
        val timestamp: Long,
        val connections: List<ActiveConnection>,
        val trackerCount: Int,
        val liveSocketVisibilityAvailable: Boolean,
        val networkStatsHistoryAvailable: Boolean,
        val mode: String,
    ) {
        private val liveConnections: List<ActiveConnection>
            get() = connections.filter { it.evidenceSource == EvidenceSource.LIVE_ESTABLISHED_TCP }

        val visibleEstablishedConnectionCount: Int get() = liveConnections.size
        val visibleEstablishedAppCount: Int
            get() = liveConnections.asSequence()
                .map { it.packageName }
                .filterNot { it.startsWith("uid:") }
                .distinct()
                .count()
        val historicalTrafficAppCount: Int
            get() = connections.asSequence()
                .filter { it.evidenceSource == EvidenceSource.NETWORK_STATS_LAST_24_HOURS }
                .map { it.packageName }
                .distinct()
                .count()
    }

    private val KNOWN_TRACKERS = listOf(
        "google-analytics.com", "analytics.google.com", "crashlytics.com",
        "facebook.com", "graph.facebook.com", "adjust.com", "appsflyer.com",
        "branch.io", "amplitude.com", "mixpanel.com", "flurry.com",
        "scorecardresearch.com", "doubleclick.net", "adservice.google.com",
        "applovin.com", "unityads.unity3d.com", "inmobi.com", "mopub.com",
        "vungle.com", "criteo.com", "chartboost.com", "kochava.com",
        "segment.io", "onesignal.com", "telemetry", "metrics",
    )

    private val KNOWN_ORGS = listOf(
        "142.250." to "Google Cloud & Services",
        "172.217." to "Google Cloud & Services",
        "157.240." to "Meta Platforms (WhatsApp/FB/Insta)",
        "31.13." to "Meta Platforms (WhatsApp/FB/Insta)",
        "13.32." to "Amazon AWS / Cloudfront",
        "13.33." to "Amazon AWS / Cloudfront",
        "13.35." to "Amazon AWS / Cloudfront",
        "52." to "Amazon AWS Cloud",
        "54." to "Amazon AWS Cloud",
        "20." to "Microsoft Azure Cloud",
        "40." to "Microsoft Azure Cloud",
        "104.16." to "Cloudflare Edge CDN",
        "104.17." to "Cloudflare Edge CDN",
        "104.18." to "Cloudflare Edge CDN",
        "104.244." to "X / Twitter",
        "151.101." to "Fastly Edge CDN",
    )

    suspend fun inspectConnections(context: Context): TrackerReport = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val uidMap = getUidAppMap(pm)
        val results = mutableListOf<ActiveConnection>()

        // These are currently visible ESTABLISHED TCP sockets only. Android/OEM kernels may
        // hide some or all rows; a read with no rows is not proof that no app is connected.
        val sockets = parseProcNetSockets()
        for (conn in sockets.connections) {
            val appInfo = uidMap[conn.uid]
            val pkg = appInfo?.first ?: ("uid:" + conn.uid)
            val label = appInfo?.second ?: ("App " + conn.uid)
            val remoteHost = resolveHostFast(conn.remoteIp)
            val isTracker = isKnownTracker(remoteHost)
            val org = identifyOrg(conn.remoteIp, remoteHost)
            val cat = when {
                isTracker -> "Telemetry / Tracker"
                conn.remotePort == 443 || conn.remotePort == 80 -> "Web / HTTP(S)"
                conn.remotePort == 853 -> "Encrypted DNS"
                conn.remotePort in 5228..5230 -> "Google Push Notifications"
                else -> "App Server"
            }
            results.add(
                ActiveConnection(
                    appName = label,
                    packageName = pkg,
                    uid = conn.uid,
                    localPort = conn.localPort,
                    remoteIp = conn.remoteIp,
                    remotePort = conn.remotePort,
                    destinationHost = remoteHost,
                    orgOrCompany = org,
                    isTracker = isTracker,
                    category = cat,
                    evidenceSource = EvidenceSource.LIVE_ESTABLISHED_TCP,
                )
            )
        }

        // NetworkStatsManager is a separate, historical byte counter. It cannot establish
        // that an app currently has an open connection, so keep its entries and count apart.
        val stats = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getRecentTrafficApps(context, uidMap)
        } else {
            NetworkStatsRead(available = false, apps = emptyList())
        }
        for ((uid, pkg, label, bytes) in stats.apps) {
            val formattedMb = "%.1f MB transferred in the last 24 h".format(bytes / 1024.0 / 1024.0)
            val org = identifyOrgForPackage(pkg)
            results.add(
                ActiveConnection(
                    appName = label,
                    packageName = pkg,
                    uid = uid,
                    localPort = 0,
                    remoteIp = org,
                    remotePort = 0,
                    destinationHost = formattedMb,
                    orgOrCompany = org,
                    isTracker = false,
                    category = categorizeAppTraffic(pkg),
                    evidenceSource = EvidenceSource.NETWORK_STATS_LAST_24_HOURS,
                )
            )
        }

        val distinct = results.distinctBy {
            listOf(it.evidenceSource.name, it.packageName, it.localPort.toString(), it.remoteIp, it.remotePort.toString())
                .joinToString(":")
        }
        val trackerCount = distinct.count {
            it.evidenceSource == EvidenceSource.LIVE_ESTABLISHED_TCP && it.isTracker
        }
        TrackerReport(
            timestamp = System.currentTimeMillis(),
            connections = distinct,
            trackerCount = trackerCount,
            liveSocketVisibilityAvailable = sockets.readable,
            networkStatsHistoryAvailable = stats.available,
            mode = "Best-effort local visibility",
        )
    }

    private fun identifyOrgForPackage(pkg: String): String {
        val p = pkg.lowercase()
        return when {
            p.contains("brave") -> "Brave Software / Web"
            p.contains("youtube") -> "Google / YouTube CDN"
            p.contains("chrome") || p.contains("google") || p.contains("vending") -> "Google LLC"
            p.contains("whatsapp") || p.contains("facebook") || p.contains("instagram") -> "Meta Platforms"
            p.contains("radio") || p.contains("cleaner") -> "Local / CDN Stream"
            p.contains("qualcomm") || p.contains("qti") -> "Qualcomm Telecom Network"
            p.contains("xiaomi") || p.contains("miui") -> "Xiaomi Cloud Services"
            p.contains("warpath") || p.contains("game") -> "Game Online Server"
            else -> "Public Cloud / Internet"
        }
    }

    private fun categorizeAppTraffic(pkg: String): String {
        val p = pkg.lowercase()
        return when {
            p.contains("brave") || p.contains("chrome") || p.contains("browser") -> "Web Browsing (HTTPS)"
            p.contains("youtube") || p.contains("netflix") || p.contains("hotstar") -> "Video Streaming CDN"
            p.contains("whatsapp") || p.contains("telegram") -> "Encrypted Messaging"
            p.contains("vending") || p.contains("market") -> "App Store Updates"
            p.contains("radio") -> "Audio Streaming"
            p.contains("game") || p.contains("warpath") -> "Multiplayer Game Server"
            else -> "App Cloud Sync"
        }
    }

    private data class AppTransfer(val uid: Int, val pkg: String, val label: String, val bytes: Long)
    private data class NetworkStatsRead(val available: Boolean, val apps: List<AppTransfer>)

    private fun getRecentTrafficApps(context: Context, uidMap: Map<Int, Pair<String, String>>): NetworkStatsRead {
        val list = mutableListOf<AppTransfer>()
        val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            ?: return NetworkStatsRead(available = false, apps = emptyList())
        var available = false
        val end = System.currentTimeMillis()
        val start = end - 24L * 3600L * 1000L

        fun tally(networkType: Int) {
            var stats: NetworkStats? = null
            try {
                stats = manager.querySummary(networkType, null, start, end)
                available = true
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val total = bucket.rxBytes.coerceAtLeast(0L) + bucket.txBytes.coerceAtLeast(0L)
                    if (total > 500 * 1024L && bucket.uid > 10_000) {
                        val info = uidMap[bucket.uid]
                        if (info != null) list.add(AppTransfer(bucket.uid, info.first, info.second, total))
                    }
                }
            } catch (_: Exception) {
                // Per-app NetworkStats can require Usage Access or be restricted by the OEM.
            } finally {
                try { stats?.close() } catch (_: Exception) { }
            }
        }

        tally(ConnectivityManager.TYPE_WIFI)
        tally(ConnectivityManager.TYPE_MOBILE)
        val grouped = list.groupBy { it.uid }.map { (uid, items) ->
            val first = items.first()
            AppTransfer(uid, first.pkg, first.label, items.sumOf { it.bytes })
        }.sortedByDescending { it.bytes }
        return NetworkStatsRead(available, grouped)
    }

    private data class RawSocket(
        val localPort: Int,
        val remoteIp: String,
        val remotePort: Int,
        val uid: Int,
    )

    private data class SocketReadResult(val connections: List<RawSocket>, val readable: Boolean)
    private data class SocketFileResult(val connections: List<RawSocket>, val readable: Boolean)

    private fun parseProcNetSockets(): SocketReadResult {
        val files = listOf(parseSocketFile("/proc/net/tcp"), parseSocketFile("/proc/net/tcp6"))
        return SocketReadResult(
            connections = files.flatMap { it.connections },
            readable = files.any { it.readable },
        )
    }

    private fun parseSocketFile(path: String): SocketFileResult {
        val file = File(path)
        if (!file.exists() || !file.canRead()) return SocketFileResult(emptyList(), readable = false)
        val sockets = mutableListOf<RawSocket>()
        var readable = false
        try {
            BufferedReader(FileReader(file)).use { reader ->
                readable = true // The file was opened; an empty result still does not prove no sockets exist.
                reader.readLine() // Skip header.
                while (true) {
                    val line = reader.readLine() ?: break
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size < 10 || parts[3] != "01") continue // TCP_ESTABLISHED only.
                    val uid = parts[7].toIntOrNull() ?: continue
                    val localPort = parseHexPort(parts[1])
                    val (remoteIp, remotePort) = parseHexEndpoint(parts[2]) ?: continue
                    if (remoteIp != "0.0.0.0" && remoteIp != "127.0.0.1" && !remoteIp.startsWith("fe80")) {
                        sockets.add(RawSocket(localPort, remoteIp, remotePort, uid))
                    }
                }
            }
        } catch (_: Exception) {
            // Some OEM kernels deny access or stop a partial read; visibility remains best-effort.
        }
        return SocketFileResult(sockets, readable)
    }

    private fun parseHexPort(endpoint: String): Int {
        val idx = endpoint.indexOf(':')
        if (idx == -1) return 0
        return endpoint.substring(idx + 1).toIntOrNull(16) ?: 0
    }

    private fun parseHexEndpoint(endpoint: String): Pair<String, Int>? {
        val idx = endpoint.indexOf(':')
        if (idx == -1) return null
        val hexIp = endpoint.substring(0, idx)
        val port = endpoint.substring(idx + 1).toIntOrNull(16) ?: return null

        val ip = if (hexIp.length == 8) {
            try {
                val b0 = hexIp.substring(6, 8).toInt(16)
                val b1 = hexIp.substring(4, 6).toInt(16)
                val b2 = hexIp.substring(2, 4).toInt(16)
                val b3 = hexIp.substring(0, 2).toInt(16)
                "$b0.$b1.$b2.$b3"
            } catch (e: Exception) {
                return null
            }
        } else {
            "IPv6"
        }
        return ip to port
    }

    private fun resolveHostFast(ip: String): String? {
        if (ip == "IPv6" || ip.contains(" ")) return null
        return try {
            val addr = InetAddress.getByName(ip)
            val host = addr.canonicalHostName
            if (host != ip && !host.isNullOrBlank()) host else null
        } catch (e: Exception) {
            null
        }
    }

    private fun isKnownTracker(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val lower = host.lowercase()
        return KNOWN_TRACKERS.any { lower.contains(it) }
    }

    private fun identifyOrg(ip: String, host: String?): String {
        if (host != null) {
            val h = host.lowercase()
            when {
                h.contains("google") || h.contains("1e100.net") -> return "Google (Services / Cloud)"
                h.contains("facebook") || h.contains("fbcdn") || h.contains("instagram") -> return "Meta Platforms"
                h.contains("amazon") || h.contains("aws") || h.contains("cloudfront") -> return "Amazon Web Services"
                h.contains("microsoft") || h.contains("azure") || h.contains("live.com") -> return "Microsoft Corporation"
                h.contains("cloudflare") -> return "Cloudflare Network"
                h.contains("apple") || h.contains("icloud") -> return "Apple Inc."
                h.contains("xiaomi") || h.contains("miui") || h.contains("mi.com") -> return "Xiaomi Services"
                h.contains("whatsapp") -> return "WhatsApp"
                h.contains("telegram") -> return "Telegram"
            }
        }
        for ((prefix, org) in KNOWN_ORGS) {
            if (ip.startsWith(prefix)) return org
        }
        return "Public Internet Server"
    }

    private fun getUidAppMap(pm: PackageManager): Map<Int, Pair<String, String>> {
        val map = mutableMapOf<Int, Pair<String, String>>()
        try {
            val installed = pm.getInstalledApplications(0)
            for (app in installed) {
                val label = try {
                    pm.getApplicationLabel(app).toString()
                } catch (e: Exception) {
                    app.packageName
                }
                map[app.uid] = app.packageName to label
            }
        } catch (e: Exception) {
            // Ignore
        }
        return map
    }
}
