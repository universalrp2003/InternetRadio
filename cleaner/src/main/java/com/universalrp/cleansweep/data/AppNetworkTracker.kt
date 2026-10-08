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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Dual-Mode Network Connection & Tracker Inspector:
 *
 * 1. Passive / NetworkStats Inspection:
 *    - Uses `/proc/net/tcp` socket inspection where available.
 *    - Queries `NetworkStatsManager` for active per-app data transfer bytes today.
 * 2. On-Device Local VpnService Inspection:
 *    - Records outbound packets intercepted by [com.universalrp.cleansweep.vpn.CleanSweepVpnService].
 *    - Tracks destination IPs, ports, and DNS queried domains in real-time.
 */
object AppNetworkTracker {

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
        val category: String, // "Tracker / Telemetry", "Secure Web (HTTPS)", "App Network Server"
    )

    data class TrackerReport(
        val timestamp: Long,
        val connections: List<ActiveConnection>,
        val trackerCount: Int,
        val appsWithActiveNet: Int,
        val mode: String, // "Passive / NetworkStats", "Local VPN Inspector"
    )

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
        "142.250." to "Google LLC",
        "172.217." to "Google LLC",
        "157.240." to "Meta / Facebook",
        "31.13." to "Meta / Facebook",
        "13.32." to "Amazon AWS Cloud",
        "13.33." to "Amazon AWS Cloud",
        "13.35." to "Amazon AWS Cloud",
        "52." to "Amazon AWS Cloud",
        "54." to "Amazon AWS Cloud",
        "20." to "Microsoft Azure",
        "40." to "Microsoft Azure",
        "104.16." to "Cloudflare CDN",
        "104.17." to "Cloudflare CDN",
        "104.18." to "Cloudflare CDN",
        "104.244." to "X / Twitter",
        "151.101." to "Fastly CDN",
    )

    // In-memory buffer for real-time VPN intercepted connections
    private val vpnCaptured = CopyOnWriteArrayList<ActiveConnection>()

    fun recordVpnConnection(context: Context, remoteIp: String, remotePort: Int, domain: String?) {
        val isTracker = isKnownTracker(domain)
        val org = identifyOrg(remoteIp, domain)
        val cat = when {
            isTracker -> "Tracker / Telemetry"
            remotePort == 443 || remotePort == 80 -> "Secure Web (HTTPS)"
            remotePort == 853 -> "Encrypted DNS"
            remotePort == 53 -> "DNS Lookup"
            remotePort in 5228..5230 -> "Google Push Notifications"
            else -> "App Network Server"
        }

        val conn = ActiveConnection(
            appName = domain?.takeIf { it.isNotBlank() } ?: "Network Destination",
            packageName = domain ?: remoteIp,
            uid = 0,
            localPort = 0,
            remoteIp = remoteIp,
            remotePort = remotePort,
            destinationHost = domain ?: resolveHostFast(remoteIp),
            orgOrCompany = org,
            isTracker = isTracker,
            category = cat,
        )

        // Keep last 100 captured connections
        if (vpnCaptured.size > 100) {
            vpnCaptured.removeAt(0)
        }
        vpnCaptured.add(0, conn)
    }

    suspend fun inspectConnections(context: Context, preferVpnMode: Boolean = false): TrackerReport = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val uidMap = getUidAppMap(pm)

        val results = mutableListOf<ActiveConnection>()
        var modeLabel = "Passive / NetworkStats"

        if (preferVpnMode && vpnCaptured.isNotEmpty()) {
            modeLabel = "Local VPN Inspector"
            results.addAll(vpnCaptured.take(50))
        } else {
            // 1. Passive /proc/net sockets
            val rawConnections = parseProcNetSockets()
            for (conn in rawConnections) {
                val appInfo = uidMap[conn.uid]
                val pkg = appInfo?.first ?: ("uid:" + conn.uid)
                val label = appInfo?.second ?: ("App " + conn.uid)

                val remoteHost = resolveHostFast(conn.remoteIp)
                val isTracker = isKnownTracker(remoteHost)
                val org = identifyOrg(conn.remoteIp, remoteHost)
                val cat = when {
                    isTracker -> "Tracker / Telemetry"
                    conn.remotePort == 443 || conn.remotePort == 80 -> "Secure Web (HTTPS)"
                    conn.remotePort == 853 -> "Encrypted DNS"
                    conn.remotePort in 5228..5230 -> "Google Push Notifications"
                    else -> "App Network Server"
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
                    )
                )
            }

            // 2. If modern Android blocks /proc/net (0 connections returned), check apps with active data transfer today via NetworkStatsManager
            if (results.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val statsApps = getActiveDataApps(context, uidMap)
                for ((uid, pkg, label, bytes) in statsApps) {
                    val formattedMb = "%.1f MB".format(bytes / 1024.0 / 1024.0)
                    results.add(
                        ActiveConnection(
                            appName = label,
                            packageName = pkg,
                            uid = uid,
                            localPort = 0,
                            remoteIp = "Active Transfer Today",
                            remotePort = 443,
                            destinationHost = "$formattedMb data used today",
                            orgOrCompany = "Active Internet App",
                            isTracker = false,
                            category = "Active Data Transfer",
                        )
                    )
                }
            }
        }

        val distinct = results.distinctBy { "${it.packageName}:${it.remoteIp}:${it.remotePort}" }
        val trackerCount = distinct.count { it.isTracker }
        val appsCount = distinct.map { it.packageName }.distinct().size

        TrackerReport(
            timestamp = System.currentTimeMillis(),
            connections = distinct,
            trackerCount = trackerCount,
            appsWithActiveNet = appsCount,
            mode = modeLabel,
        )
    }

    private data class AppTransfer(val uid: Int, val pkg: String, val label: String, val bytes: Long)

    private fun getActiveDataApps(context: Context, uidMap: Map<Int, Pair<String, String>>): List<AppTransfer> {
        val list = mutableListOf<AppTransfer>()
        val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager ?: return list
        try {
            val start = System.currentTimeMillis() - 24 * 3600 * 1000L
            val end = System.currentTimeMillis()

            fun tally(networkType: Int) {
                val stats = manager.querySummary(networkType, null, start, end)
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val total = bucket.rxBytes + bucket.txBytes
                    if (total > 100 * 1024L && bucket.uid > 10000) { // UID > 10000 is installed apps
                        val info = uidMap[bucket.uid]
                        if (info != null) {
                            list.add(AppTransfer(bucket.uid, info.first, info.second, total))
                        }
                    }
                }
                stats.close()
            }

            tally(ConnectivityManager.TYPE_WIFI)
            tally(ConnectivityManager.TYPE_MOBILE)
        } catch (e: Exception) {
            // Permission not granted or query failed
        }
        return list.groupBy { it.uid }.map { (uid, items) ->
            val first = items.first()
            val totalBytes = items.sumOf { it.bytes }
            AppTransfer(uid, first.pkg, first.label, totalBytes)
        }.sortedByDescending { it.bytes }
    }

    private data class RawSocket(
        val localPort: Int,
        val remoteIp: String,
        val remotePort: Int,
        val uid: Int,
    )

    private fun parseProcNetSockets(): List<RawSocket> {
        val list = mutableListOf<RawSocket>()
        list.addAll(parseSocketFile("/proc/net/tcp"))
        list.addAll(parseSocketFile("/proc/net/tcp6"))
        return list
    }

    private fun parseSocketFile(path: String): List<RawSocket> {
        val file = File(path)
        if (!file.exists() || !file.canRead()) return emptyList()
        val sockets = mutableListOf<RawSocket>()
        try {
            BufferedReader(FileReader(file)).use { reader ->
                var line = reader.readLine() // Skip header
                while (reader.readLine().also { line = it } != null) {
                    val parts = line!!.trim().split(Regex("\\s+"))
                    if (parts.size >= 10) {
                        val state = parts[3]
                        if (state == "01") { // TCP_ESTABLISHED
                            val local = parts[1]
                            val remote = parts[2]
                            val uid = parts[7].toIntOrNull() ?: continue

                            val localPort = parseHexPort(local)
                            val (remoteIp, remotePort) = parseHexEndpoint(remote) ?: continue
                            if (remoteIp != "0.0.0.0" && remoteIp != "127.0.0.1" && !remoteIp.startsWith("fe80")) {
                                sockets.add(RawSocket(localPort, remoteIp, remotePort, uid))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore access errors on some locked OEM kernels
        }
        return sockets
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
