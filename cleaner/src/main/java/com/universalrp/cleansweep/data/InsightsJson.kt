package com.universalrp.cleansweep.data

import org.json.JSONArray
import org.json.JSONObject

/** Private-file codecs. Tolerant of a missing/corrupt file: establish a new baseline, never resolve old evidence. */
object InsightsJson {
    private fun JSONObject.nullLong(key: String): Long? = if (isNull(key)) null else optLong(key, -1).takeIf { it >= 0 }
    private fun JSONObject.nullDouble(key: String): Double? = if (isNull(key)) null else optDouble(key, Double.NaN).takeIf { it.isFinite() }
    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
    private fun array(items: Iterable<JSONObject>) = JSONArray().apply { items.forEach { put(it) } }
    private fun objects(array: JSONArray?, max: Int): List<JSONObject> = if (array == null) emptyList() else
        (0 until minOf(array.length(), max)).mapNotNull { array.optJSONObject(it) }

    private fun sample(value: ChargeSample) = JSONObject().apply {
        put("at", value.atMs); put("elapsed", value.elapsedMs); put("connected", value.connected)
        putNullable("percent", value.percent); putNullable("watts", value.watts); putNullable("temp", value.temperatureC)
        put("plug", value.connectionEvent); put("unplug", value.disconnectionEvent); putNullable("boot", value.bootCount)
    }
    private fun readSample(value: JSONObject) = ChargeSample(
        value.getLong("at"), value.getLong("elapsed"), value.getBoolean("connected"),
        value.nullLong("percent")?.takeIf { it in 0..100 }?.toInt(), value.nullDouble("watts")?.takeIf { it in 0.0..150.0 },
        value.nullDouble("temp")?.takeIf { it in -30.0..90.0 }, value.optBoolean("plug"), value.optBoolean("unplug"), value.nullLong("boot")?.takeIf { it <= Int.MAX_VALUE }?.toInt(),
    )
    private fun session(value: ChargeSession) = JSONObject().apply {
        put("id", value.id); put("start", value.startedAtMs); put("last", value.lastAtMs)
        put("start_elapsed", value.startedElapsedMs); put("last_elapsed", value.lastElapsedMs)
        putNullable("end", value.endedAtMs); putNullable("start_percent", value.startPercent); putNullable("end_percent", value.endPercent)
        put("samples", array(value.samples.map(::sample))); putNullable("last_sample", value.lastSample?.let(::sample))
        put("from_plug", value.observedFromPlug); put("end_observed", value.endObserved); put("interrupted", value.interrupted)
        put("power_ms", value.measuredPowerMs); put("energy_wh", value.measuredEnergyWh)
        putNullable("peak_w", value.peakWatts); putNullable("max_temp", value.maxTemperatureC)
        putNullable("at20", value.atTwentyElapsedMs); putNullable("at80", value.atEightyElapsedMs); put("threshold_gap", value.thresholdGap); putNullable("boot", value.bootCount)
    }
    private fun readSession(value: JSONObject): ChargeSession = ChargeSession(
        id = value.getString("id"), startedAtMs = value.getLong("start"), lastAtMs = value.getLong("last"),
        startedElapsedMs = value.getLong("start_elapsed"), lastElapsedMs = value.getLong("last_elapsed"), endedAtMs = value.nullLong("end"),
        startPercent = value.nullLong("start_percent")?.takeIf { it in 0..100 }?.toInt(),
        endPercent = value.nullLong("end_percent")?.takeIf { it in 0..100 }?.toInt(),
        samples = objects(value.optJSONArray("samples"), ChargeHistoryPolicy.MAX_POINTS).map(::readSample),
        lastSample = value.optJSONObject("last_sample")?.let(::readSample),
        observedFromPlug = value.optBoolean("from_plug"), endObserved = value.optBoolean("end_observed"), interrupted = value.optBoolean("interrupted"),
        measuredPowerMs = value.optLong("power_ms").coerceAtLeast(0), measuredEnergyWh = value.getDouble("energy_wh").also { require(it.isFinite() && it >= 0) },
        peakWatts = value.nullDouble("peak_w")?.takeIf { it in 0.0..150.0 }, maxTemperatureC = value.nullDouble("max_temp")?.takeIf { it in -30.0..90.0 },
        atTwentyElapsedMs = value.nullLong("at20"), atEightyElapsedMs = value.nullLong("at80"), thresholdGap = value.optBoolean("threshold_gap"), bootCount = value.nullLong("boot")?.takeIf { it <= Int.MAX_VALUE }?.toInt(),
    )
    fun charge(state: ChargeHistoryState): String = JSONObject().apply {
        put("schema", 1); putNullable("active", state.active?.let(::session)); put("sessions", array(state.sessions.map(::session)))
    }.toString()
    private fun decodeCharge(raw: String): ChargeHistoryState {
        val root = JSONObject(raw); require(root.getInt("schema") == 1)
        return ChargeHistoryState(root.optJSONObject("active")?.let(::readSession), objects(root.getJSONArray("sessions"), ChargeHistoryPolicy.MAX_SESSIONS).map(::readSession))
    }
    fun readCharge(raw: String?): ChargeHistoryState = try { if (raw == null) ChargeHistoryState() else decodeCharge(raw) } catch (e: Exception) { ChargeHistoryState() }
    fun validCharge(raw: String): Boolean = runCatching { decodeCharge(raw) }.isSuccess

    private fun app(value: FindingApp?) = value?.let { JSONObject().put("pkg", it.packageName).put("label", it.label).putNullable("component", it.componentName) }
    private fun readApp(value: JSONObject?) = value?.let {
        FindingApp(it.getString("label"), it.getString("pkg"), if (it.isNull("component")) null else it.getString("component"))
    }
    private fun observation(value: SecurityObservation) = JSONObject().apply {
        put("key", value.key); put("finding", value.findingId); put("title", value.title); put("detail", value.detail)
        put("severity", value.severity.name); putNullable("app", app(value.app)); put("fingerprint", value.fingerprint)
    }
    private fun readObservation(value: JSONObject): SecurityObservation {
        val target = readApp(value.optJSONObject("app")); val finding = value.getString("finding")
        require(value.getString("key") == SecurityHistoryPolicy.key(finding, target))
        return SecurityObservation(value.getString("key"), finding, value.getString("title"), value.getString("detail"),
            Severity.valueOf(value.getString("severity")), target, value.getString("fingerprint"))
    }
    private fun pkg(value: ObservedPackage) = JSONObject().apply {
        put("pkg", value.packageName); put("label", value.label); put("installed", value.installedAtMs); put("updated", value.updatedAtMs); put("system", value.isSystem)
    }
    private fun readPackage(value: JSONObject) = ObservedPackage(value.getString("pkg"), value.getString("label"),
        value.optLong("installed"), value.optLong("updated"), value.optBoolean("system"))
    private fun snapshot(value: SecuritySnapshot) = JSONObject().apply {
        put("at", value.atMs); put("observations", array(value.observations.map(::observation))); put("packages", array(value.packages.map(::pkg)))
        put("unavailable", JSONArray(value.unavailableChecks.toList()))
    }
    private fun readSnapshot(value: JSONObject): SecuritySnapshot {
        val unavailable = value.optJSONArray("unavailable")
        return SecuritySnapshot(value.getLong("at"), objects(value.optJSONArray("observations"), 4096).map(::readObservation),
            objects(value.optJSONArray("packages"), 4096).map(::readPackage),
            (0 until minOf(unavailable?.length() ?: 0, 100)).mapNotNull { unavailable?.optString(it) }.toSet())
    }
    private fun event(value: SecurityChange) = JSONObject().apply {
        put("id", value.id); put("at", value.atMs); put("kind", value.kind.name); put("key", value.key); put("title", value.title)
        putNullable("label", value.appLabel); putNullable("pkg", value.packageName); put("detail", value.detail)
    }
    private fun readEvent(value: JSONObject) = SecurityChange(value.getString("id"), value.getLong("at"),
        SecurityChangeKind.valueOf(value.getString("kind")), value.getString("key"), value.getString("title"),
        if (value.isNull("label")) null else value.getString("label"), if (value.isNull("pkg")) null else value.getString("pkg"), value.optString("detail"))
    fun security(state: SecurityHistoryState): String = JSONObject().apply {
        put("schema", 1); putNullable("latest", state.latest?.let(::snapshot)); put("events", array(state.events.map(::event)))
        put("reviewed", JSONObject(state.reviewed)); put("baselined", JSONArray(state.baselinedChecks.toList()))
    }.toString()
    private fun decodeSecurity(raw: String): SecurityHistoryState {
        val root = JSONObject(raw); require(root.getInt("schema") == 1)
        val reviewed = root.optJSONObject("reviewed") ?: JSONObject()
        val baselined = root.optJSONArray("baselined")
        return SecurityHistoryState(root.optJSONObject("latest")?.let(::readSnapshot), objects(root.getJSONArray("events"), SecurityHistoryPolicy.MAX_EVENTS).map(::readEvent),
            reviewed.keys().asSequence().take(4096).associateWith { reviewed.getString(it) },
            (0 until minOf(baselined?.length() ?: 0, 100)).mapNotNull { baselined?.optString(it) }.toSet())
    }
    fun readSecurity(raw: String?): SecurityHistoryState = try { if (raw == null) SecurityHistoryState() else decodeSecurity(raw) } catch (e: Exception) { SecurityHistoryState() }
    fun validSecurity(raw: String): Boolean = runCatching { decodeSecurity(raw) }.isSuccess

    fun data(history: List<DailyDataPoint>): String = JSONObject().apply {
        put("schema", 1); put("days", array(history.map { point -> JSONObject().apply {
            put("day", point.day); putNullable("bytes", point.bytes); put("complete", point.complete); put("source", point.source); put("at", point.observedAtMs)
        } }))
    }.toString()
    private fun decodeData(raw: String): List<DailyDataPoint> {
        val root = JSONObject(raw); require(root.getInt("schema") == 1)
        return objects(root.getJSONArray("days"), 90).mapNotNull { value ->
            val day = value.getString("day")
            if (DataBudgetPolicy.dateOrNull(day) == null) null else DailyDataPoint(day, value.nullLong("bytes"), value.optBoolean("complete"), value.optString("source"), value.optLong("at"))
        }
    }
    fun readData(raw: String?): List<DailyDataPoint> = try { if (raw == null) emptyList() else decodeData(raw) } catch (e: Exception) { emptyList() }
    fun validData(raw: String): Boolean = runCatching { decodeData(raw) }.isSuccess
}
