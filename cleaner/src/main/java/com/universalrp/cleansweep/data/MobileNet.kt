package com.universalrp.cleansweep.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Everything about the mobile side of the phone: the SIM and operator, which kind of
 * network is in use (5G / 4G / 3G / 2G), how strong the signal really is in dBm, the
 * towers within reach, a latency/jitter test, and how much data has been used today.
 *
 * Nothing here is invented: values the phone refuses to give (because a permission is
 * missing, or on a dual-SIM phone where only the default SIM is readable) are reported as
 * "not available" together with the reason, and the screen says what would unlock them.
 */
data class SimInfo(
    val slot: Int,
    val carrier: String,
    val label: String,
    val roaming: Boolean,
    val dataNetworkType: String,
    val signalDbm: Int?,
    val signalLevel: Int?,
    val isDefaultData: Boolean,
)

data class CellTower(
    val technology: String,
    val id: String?,
    val pci: Int?,
    val tac: Int?,
    val dbm: Int?,
    val registered: Boolean,
)

data class MobileSnapshot(
    val readable: Boolean,
    val operatorName: String,
    val sims: List<SimInfo>,
    val dataEnabled: Boolean,
    val roaming: Boolean,
    val networkType: String,
    val signalDbm: Int?,
    val signalLevel: Int?,
    val quality: String,
    /** 0 = good, 1 = fair, 2 = poor. */
    val qualityTone: Int,
    val is5g: Boolean,
    val isVpn: Boolean,
    /** True when the connection carrying the traffic right now is mobile data. */
    val activeOnMobile: Boolean = false,
    /** True when the connection carrying the traffic right now is Wi-Fi. */
    val activeOnWifi: Boolean = false,
    val towers: List<CellTower>,
    val needsPhonePermission: Boolean,
    val permissionHint: String?,
    val notes: List<String>,
)

data class PingStats(
    val host: String,
    val label: String,
    val sent: Int,
    val received: Int,
    val minMs: Double,
    val avgMs: Double,
    val maxMs: Double,
    val jitterMs: Double,
) {
    val lossPercent: Int get() = if (sent == 0) 0 else ((sent - received) * 100) / sent

    val quality: String
        get() = when {
            received == 0 -> "No answer"
            avgMs < 60 && jitterMs < 20 -> "Excellent"
            avgMs < 120 && jitterMs < 40 -> "Good"
            avgMs < 250 -> "Fair"
            else -> "Slow"
        }
}

data class IpInfo(
    val ip: String?,
    val isp: String?,
    val asn: String?,
    val city: String?,
    val country: String?,
    val network: String?,
    val error: String? = null,
)

object MobileNet {

    /** Targets for the latency test: two anycast resolvers and Cloudflare's speed host. */
    private val PING_TARGETS = listOf(
        Triple("1.1.1.1", 53, "Cloudflare DNS"),
        Triple("8.8.8.8", 53, "Google DNS"),
        Triple("speed.cloudflare.com", 443, "Cloudflare speed host"),
    )

    fun hasPhonePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    private fun telephony(context: Context): TelephonyManager? =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    fun snapshot(context: Context): MobileSnapshot {
        val tm = telephony(context)
        val notes = mutableListOf<String>()
        val allowed = hasPhonePermission(context)

        var operator = ""
        var dataEnabled = false
        var roaming = false
        var networkType = "Not connected"
        var is5g = false
        var signalDbm: Int? = null
        var signalLevel: Int? = null
        val sims = mutableListOf<SimInfo>()
        val towers = mutableListOf<CellTower>()

        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        var isVpn = false
        var activeOnMobile = false
        var activeOnWifi = false

        try {
            val active = connectivity?.activeNetwork
            val caps = active?.let { connectivity.getNetworkCapabilities(it) }
            isVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            activeOnMobile = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            activeOnWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            if (!activeOnMobile && !isVpn) {
                notes.add("Mobile data is not the active connection right now — these readings are the last known ones.")
            }
        } catch (e: Exception) {
            // Ignore: the screen simply shows what it could read.
        }

        if (tm == null) {
            return MobileSnapshot(
                readable = false,
                operatorName = "Not available",
                sims = emptyList(),
                dataEnabled = false,
                roaming = false,
                networkType = "Not available",
                signalDbm = null,
                signalLevel = null,
                quality = "Unknown",
                qualityTone = 1,
                is5g = false,
                isVpn = isVpn,
                towers = emptyList(),
                needsPhonePermission = !allowed,
                permissionHint = null,
                notes = notes,
            )
        }

        try {
            operator = tm.networkOperatorName.orEmpty()
        } catch (e: Exception) {
            notes.add("The operator name could not be read.")
        }

        try {
            dataEnabled = tm.isDataEnabled
        } catch (e: Exception) {
            // Not readable on every phone without extra permissions.
        }

        try {
            @Suppress("DEPRECATION")
            val type = if (Build.VERSION.SDK_INT >= 24) tm.dataNetworkType else tm.networkType
            networkType = networkTypeLabel(type)
            is5g = is5gType(type)
        } catch (e: SecurityException) {
            notes.add("Reading the network type needs the Phone permission.")
        } catch (e: Exception) {
            // Ignore.
        }

        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val strength = tm.signalStrength
                if (strength != null) {
                    signalLevel = strength.level.coerceIn(0, 4)
                    val nr = strength.cellSignalStrengths.firstOrNull { it is android.telephony.CellSignalStrengthNr }
                    val lte = strength.cellSignalStrengths.firstOrNull { it is android.telephony.CellSignalStrengthLte }
                    val gsm = strength.cellSignalStrengths.firstOrNull { it is android.telephony.CellSignalStrengthGsm }
                    val picked = nr ?: lte ?: gsm
                    val dbm = picked?.dbm
                    if (nr != null) is5g = true
                    if (dbm != null && dbm < 0) signalDbm = dbm
                }
            } catch (e: SecurityException) {
                notes.add("The signal strength needs the Phone permission (it is never used for anything else).")
            } catch (e: Exception) {
                // Ignore.
            }
        }

        try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as? android.telephony.SubscriptionManager
            @Suppress("DEPRECATION")
            val defaultDataSub = if (Build.VERSION.SDK_INT >= 24) {
                android.telephony.SubscriptionManager.getDefaultDataSubscriptionId()
            } else {
                -1
            }
            val infos = subscriptionManager?.activeSubscriptionInfoList.orEmpty()
            infos.forEachIndexed { index, info ->
                val slot = if (info.simSlotIndex >= 0) info.simSlotIndex + 1 else index + 1
                val carrier = info.carrierName?.toString().orEmpty().ifBlank { operator }
                sims.add(
                    SimInfo(
                        slot = slot,
                        carrier = carrier.ifBlank { "Unknown carrier" },
                        label = info.displayName?.toString().orEmpty(),
                        roaming = info.dataRoaming == android.telephony.SubscriptionManager.DATA_ROAMING_ENABLE,
                        dataNetworkType = networkType,
                        signalDbm = signalDbm,
                        signalLevel = signalLevel,
                        isDefaultData = defaultDataSub >= 0 && info.subscriptionId == defaultDataSub,
                    )
                )
            }
            roaming = infos.any {
                it.dataRoaming == android.telephony.SubscriptionManager.DATA_ROAMING_ENABLE
            }
        } catch (e: SecurityException) {
            notes.add("The SIM list needs the Phone permission.")
        } catch (e: Exception) {
            // Dual-SIM phones vary a lot; a missing list is not an error.
        }

        try {
            val all = tm.allCellInfo.orEmpty()
            all.take(12).forEach { cell ->
                towers.add(cellTower(cell))
            }
            if (all.isEmpty()) {
                notes.add("No cell tower details: Android only shares them with Location allowed, and only for the network you are on.")
            }
        } catch (e: SecurityException) {
            notes.add("Tower details need Location permission (Android hides them otherwise).")
        } catch (e: Exception) {
            // Ignore.
        }

        if (signalDbm == null && allowed) {
            notes.add("This phone did not report a dBm value — some models hide it from third-party apps.")
        }

        val tone = when {
            signalDbm == null -> 1
            signalDbm >= -95 -> 0
            signalDbm >= -110 -> 1
            else -> 2
        }

        return MobileSnapshot(
            readable = allowed || operator.isNotBlank(),
            operatorName = operator.ifBlank { sims.firstOrNull()?.carrier ?: "Unknown" },
            sims = sims,
            dataEnabled = dataEnabled,
            roaming = roaming,
            networkType = networkType,
            signalDbm = signalDbm,
            signalLevel = signalLevel,
            quality = qualityLabel(signalDbm, signalLevel),
            qualityTone = tone,
            is5g = is5g,
            isVpn = isVpn,
            activeOnMobile = activeOnMobile,
            activeOnWifi = activeOnWifi,
            towers = towers,
            needsPhonePermission = !allowed,
            permissionHint = if (allowed) {
                null
            } else {
                "Allow “Phone” access and CleanSweep can show the operator, the SIM names, the network " +
                    "type and the real signal in dBm. It is used only on this screen and never sent anywhere."
            },
            notes = notes,
        )
    }

    /**
     * `CellInfoNr` only exists from Android 10, so the 5G branch is entered behind a version
     * check — that keeps the class from ever being loaded on an older phone.
     */
    private fun cellTower(cell: CellInfo): CellTower {
        if (Build.VERSION.SDK_INT >= 29 && cell is CellInfoNr) return nrTower(cell)
        return when (cell) {
        is CellInfoLte -> CellTower(
            technology = "4G",
            id = try { cell.cellIdentity.ci.toString() } catch (e: Exception) { null },
            pci = try { cell.cellIdentity.pci } catch (e: Exception) { null },
            tac = try { cell.cellIdentity.tac } catch (e: Exception) { null },
            dbm = try { cell.cellSignalStrength.dbm } catch (e: Exception) { null },
            registered = cell.isRegistered,
        )
        is CellInfoWcdma -> CellTower(
            technology = "3G",
            id = try { cell.cellIdentity.cid.toString() } catch (e: Exception) { null },
            pci = try { cell.cellIdentity.psc } catch (e: Exception) { null },
            tac = try { cell.cellIdentity.lac } catch (e: Exception) { null },
            dbm = try { cell.cellSignalStrength.dbm } catch (e: Exception) { null },
            registered = cell.isRegistered,
        )
        is CellInfoGsm -> CellTower(
            technology = "2G",
            id = try { cell.cellIdentity.cid.toString() } catch (e: Exception) { null },
            pci = null,
            tac = try { cell.cellIdentity.lac } catch (e: Exception) { null },
            dbm = try { cell.cellSignalStrength.dbm } catch (e: Exception) { null },
            registered = cell.isRegistered,
        )
        else -> CellTower("Cell", null, null, null, null, cell.isRegistered)
        }
    }

    /**
     * 5G cell identity. The fields are read out of the identity's own text so that no
     * Android-10-only property has to be referenced — a phone on Android 8/9 must never even
     * touch the CellIdentityNr class.
     */
    private fun nrTower(cell: CellInfoNr): CellTower {
        val text = try {
            cell.cellIdentity.toString()
        } catch (e: Exception) {
            ""
        }
        fun field(name: String): String? {
            val raw = text.substringAfter("$name = ", "").substringBefore("]").substringBefore(",").trim()
            return if (raw.isNotBlank() && raw != text) raw else null
        }
        val nci = field("nci")
        val pci = field("pci")?.toIntOrNull()
        val tac = field("tac")?.toIntOrNull()
        return CellTower(
            technology = "5G",
            id = nci,
            pci = pci,
            tac = tac,
            dbm = try {
                (cell.cellSignalStrength as android.telephony.CellSignalStrengthNr).dbm
            } catch (e: Exception) {
                null
            },
            registered = cell.isRegistered,
        )
    }

    /** 5G / 4G / 3G / 2G in plain words. */
    fun networkTypeLabel(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        TelephonyManager.NETWORK_TYPE_LTE -> "4G / LTE"
        TelephonyManager.NETWORK_TYPE_IWLAN -> "4G (Wi-Fi calling)"
        TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
        TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_GSM -> "2G"
        TelephonyManager.NETWORK_TYPE_UNKNOWN -> "Unknown"
        else -> "Mobile"
    }

    private fun is5gType(type: Int): Boolean = type == TelephonyManager.NETWORK_TYPE_NR

    /**
     * A dBm reading is only meaningful with the band it came from: -110 dBm is usable on
     * 5G but poor on 4G, so the label is careful about what it promises.
     */
    private fun qualityLabel(dbm: Int?, level: Int?): String = when {
        dbm != null -> when {
            dbm >= -85 -> "Excellent ($dbm dBm)"
            dbm >= -95 -> "Good ($dbm dBm)"
            dbm >= -105 -> "Fair ($dbm dBm)"
            dbm >= -115 -> "Weak ($dbm dBm)"
            else -> "Very weak ($dbm dBm)"
        }
        level != null -> when (level) {
            4 -> "Excellent (bars)"
            3 -> "Good (bars)"
            2 -> "Fair (bars)"
            1 -> "Weak (bars)"
            else -> "Very weak (bars)"
        }
        else -> "Not reported by this phone"
    }

    // ------------------------------------------------------------------ latency

    /**
     * Measures real latency by opening a TCP connection to each target, ten times. TCP
     * connect is used because Android apps cannot send ICMP pings without root; the time to
     * connect is what your apps actually feel. Jitter is the average change between samples,
     * which is what makes calls and games stutter even when the average ping looks fine.
     */
    suspend fun ping(
        samples: Int = 8,
        onSample: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<PingStats> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // IO, always: opening a socket on the main thread throws NetworkOnMainThreadException,
        // which looked exactly like "no answer, 100% loss" on every target (v2.3 bug fixed
        // here). Every sample below is a real network call.
        val perTarget = samples.coerceIn(4, 12)
        val results = mutableListOf<PingStats>()
        val total = PING_TARGETS.size * perTarget
        var done = 0
        for ((host, port, label) in PING_TARGETS) {
            val times = mutableListOf<Double>()
            var received = 0
            repeat(perTarget) {
                val ms = connectTimeMs(host, port)
                if (ms != null) {
                    times.add(ms)
                    received++
                }
                done++
                onSample(done, total)
            }
            val avg = if (times.isEmpty()) 0.0 else times.average()
            val jitter = if (times.size < 2) {
                0.0
            } else {
                times.zipWithNext { a, b -> kotlin.math.abs(b - a) }.average()
            }
            results.add(
                PingStats(
                    host = host,
                    label = label,
                    sent = perTarget,
                    received = received,
                    minMs = times.minOrNull() ?: 0.0,
                    avgMs = avg,
                    maxMs = times.maxOrNull() ?: 0.0,
                    jitterMs = jitter,
                )
            )
        }
        results
    }

    private fun connectTimeMs(host: String, port: Int): Double? {
        var socket: Socket? = null
        return try {
            socket = Socket()
            val started = System.nanoTime()
            socket.connect(InetSocketAddress(host, port), 2500)
            (System.nanoTime() - started) / 1_000_000.0
        } catch (e: Exception) {
            null
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {
                // Nothing to do.
            }
        }
    }

    /**
     * Public IP, ISP and city — one small request, only when the user taps the button.
     *
     * Four independent providers are tried in turn, because a single one is not enough in
     * practice: Cloudflare answers 403 through some VPNs and carriers (exactly what the user
     * hit on Airtel + VPN), and a provider being down must not turn into "no answer".
     */
    suspend fun publicIp(): IpInfo =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var lastError = "No provider answered."
            for (endpoint in IP_ENDPOINTS) {
                val text = httpGet(endpoint.url)
                if (text == null) {
                    lastError = "${endpoint.label}: no answer"
                    continue
                }
                if (text.isHttpError()) {
                    lastError = "${endpoint.label}: ${text.take(40)}"
                    continue
                }
                val info = endpoint.parse(text)
                if (info != null && !info.ip.isNullOrBlank()) return@withContext info
                lastError = "${endpoint.label}: unexpected reply"
            }
            IpInfo(null, null, null, null, null, null, lastError)
        }

    /** One small GET with an honest User-Agent; null when nothing answered. */
    private fun httpGet(url: String): String? {
        var connection: java.net.HttpURLConnection? = null
        return try {
            connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json, text/plain, */*")
                // Some providers reject requests with no User-Agent at all.
                setRequestProperty("User-Agent", "CleanSweep/2.4 (Android)")
            }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty().trim()
            if (status in 200..299) body else "HTTP $status"
        } catch (e: Exception) {
            null
        } finally {
            try {
                connection?.disconnect()
            } catch (e: Exception) {
                // Nothing to do.
            }
        }
    }

    /** "HTTP 403" and friends are status lines, not IP data. */
    private fun String.isHttpError(): Boolean = startsWith("HTTP ")

    private data class IpEndpoint(
        val label: String,
        val url: String,
        val parse: (String) -> IpInfo?,
    )

    private val IP_ENDPOINTS: List<IpEndpoint> = listOf(
        IpEndpoint("Cloudflare", "https://speed.cloudflare.com/meta") { text ->
            try {
                val json = org.json.JSONObject(text)
                IpInfo(
                    ip = json.optString("clientIp").takeIf { it.isNotBlank() },
                    isp = json.optString("asOrganization").takeIf { it.isNotBlank() },
                    asn = json.optString("asn").takeIf { it.isNotBlank() },
                    city = json.optString("city").takeIf { it.isNotBlank() },
                    country = json.optString("country").takeIf { it.isNotBlank() },
                    network = json.optString("httpProtocol").takeIf { it.isNotBlank() },
                )
            } catch (e: Exception) {
                null
            }
        },
        IpEndpoint("ipinfo.io", "https://ipinfo.io/json") { text ->
            try {
                val json = org.json.JSONObject(text)
                val org = json.optString("org").takeIf { it.isNotBlank() }
                IpInfo(
                    ip = json.optString("ip").takeIf { it.isNotBlank() },
                    isp = org?.substringAfter(" ", org),
                    asn = org?.substringBefore(" ")?.takeIf { it.startsWith("AS") },
                    city = json.optString("city").takeIf { it.isNotBlank() },
                    country = json.optString("country").takeIf { it.isNotBlank() },
                    network = null,
                )
            } catch (e: Exception) {
                null
            }
        },
        IpEndpoint("ipapi.co", "https://ipapi.co/json/") { text ->
            try {
                val json = org.json.JSONObject(text)
                IpInfo(
                    ip = json.optString("ip").takeIf { it.isNotBlank() },
                    isp = json.optString("org").takeIf { it.isNotBlank() },
                    asn = json.optString("asn").takeIf { it.isNotBlank() },
                    city = json.optString("city").takeIf { it.isNotBlank() },
                    country = json.optString("country_name").takeIf { it.isNotBlank() },
                    network = null,
                )
            } catch (e: Exception) {
                null
            }
        },
        IpEndpoint("ipify", "https://api.ipify.org?format=json") { text ->
            try {
                val ip = org.json.JSONObject(text).optString("ip").takeIf { it.isNotBlank() }
                if (ip == null) null else IpInfo(ip, null, null, null, null, null)
            } catch (e: Exception) {
                null
            }
        },
    )
}
