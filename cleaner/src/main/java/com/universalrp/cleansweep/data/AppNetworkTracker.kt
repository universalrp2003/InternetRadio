package com.universalrp.cleansweep.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.InetAddress

/**
 * Passive network connection & tracker inspector.
 * Inspects active socket connections (/proc/net/tcp, /proc/net/tcp6) and attributes
 * them to installed applications using UID mappings.
 *
 * Checks destinations against known telemetry, advertising, and tracker endpoints,
 * and provides company/location context without requiring a VPN key icon or root.
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
        val category: String, // "Tracker / Analytics", "Cloud / CDN", "Essential / App Server"
    )

    data class TrackerReport(
        val timestamp: Long,
        val connections: List<ActiveConnection>,
        val trackerCount: Int,
        val appsWithActiveNet: Int,
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

    suspend fun inspectConnections(context: Context): TrackerReport = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val uidMap = getUidAppMap(pm)
        val rawConnections = parseProcNetSockets()

        val results = mutableListOf<ActiveConnection>()

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
                conn.remotePort == 5228 || conn.remotePort == 5229 || conn.remotePort == 5230 -> "Google Push Notifications"
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

        // Distinct by app and remote IP to eliminate duplicate socket rows
        val distinct = results.distinctBy { "${it.packageName}:${it.remoteIp}:${it.remotePort}" }
        val trackerCount = distinct.count { it.isTracker }
        val appsCount = distinct.map { it.packageName }.distinct().size

        TrackerReport(
            timestamp = System.currentTimeMillis(),
            connections = distinct,
            trackerCount = trackerCount,
            appsWithActiveNet = appsCount,
        )
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
                        // 01 is TCP_ESTABLISHED
                        if (state == "01") {
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
            // IPv4: stored in little-endian hex
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
            // IPv6
            "IPv6"
        }
        return ip to port
    }

    private fun resolveHostFast(ip: String): String? {
        if (ip == "IPv6") return null
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
