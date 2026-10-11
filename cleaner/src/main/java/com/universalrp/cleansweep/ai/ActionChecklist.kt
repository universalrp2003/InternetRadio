package com.universalrp.cleansweep.ai

import com.universalrp.cleansweep.data.*
import org.json.JSONObject

/** AI supplies explanations, NEVER executable intents, package names, commands or completion state. */
enum class ActionGroup { DO_FIRST, OPTIONAL, LEAVE_ALONE }
enum class ChecklistRoute { SETTINGS, HEALTH, APP_CACHE, DATA_BUDGET, MOBILE }

data class ChecklistAction(
    val observation: SecurityObservation,
    val group: ActionGroup,
    val reviewed: Boolean,
    val instructions: String,
    val canOpenSettings: Boolean,
    val route: ChecklistRoute? = if (canOpenSettings) ChecklistRoute.SETTINGS else null,
)

data class AiActionAdvice(val key: String, val fingerprint: String, val explanation: String, val suggestedReview: String, val atMs: Long)

object ActionChecklistPolicy {
    private val SETTINGS_IDS = setOf(
        "accessibility", "notification_listeners", "device_admin", "install_other_apps", "usage_access",
        "overlay", "sideloaded", "sms_readers", "banking_sms_readers", "facebook_stubs", "odd_installer",
        "no_lock", "developer_options", "old_patch", "root_tools",
    )

    fun build(
        snapshot: SecuritySnapshot?, history: SecurityHistoryState,
        health: HealthSnapshot? = null, storage: StorageInfo? = null,
        data: DataUsageTracker.UsageInfo? = null, network: LiveNetworkQuality.QualitySnapshot? = null,
    ): List<ChecklistAction> {
        val result = snapshot?.observations.orEmpty()
            .filter { it.findingId !in snapshot?.unavailableChecks.orEmpty() }
            .map { item ->
                val reviewed = SecurityHistoryPolicy.isReviewed(history, item)
                val group = when {
                    reviewed -> ActionGroup.LEAVE_ALONE
                    item.findingId == "banking_sms_readers" -> ActionGroup.OPTIONAL
                    item.severity == Severity.INFO -> ActionGroup.LEAVE_ALONE
                    item.severity == Severity.HIGH || item.severity == Severity.MEDIUM -> ActionGroup.DO_FIRST
                    else -> ActionGroup.OPTIONAL
                }
                ChecklistAction(item, group, reviewed, SecuritySettingsRoutes.instructions(item.findingId, item.app), item.findingId in SETTINGS_IDS)
            }.toMutableList()
        fun local(id: String, title: String, evidence: String, severity: Severity, route: ChecklistRoute, instructions: String) {
            val observation = SecurityObservation("local:$id", id, title, evidence, severity, null,
                SecurityHistoryPolicy.evidenceFingerprint("$id|$evidence|$severity"))
            // Acknowledging a hot/low-space observation does not make the condition safe or disappear.
            result += ChecklistAction(observation, if (severity == Severity.HIGH || severity == Severity.MEDIUM) ActionGroup.DO_FIRST else ActionGroup.OPTIONAL,
                SecurityHistoryPolicy.isReviewed(history, observation), instructions, false, route)
        }
        health?.battery?.temperatureC?.takeIf { it.isFinite() && it >= 45 }?.let {
            local("battery_heat", "High battery temperature reported", "Battery sensor: %.1f °C".format(java.util.Locale.US, it), Severity.HIGH, ChecklistRoute.HEALTH,
                "Let the phone cool in a safe shaded place; avoid heavy games while charging. Inspect the actual reading. This is not proof of battery damage and no app can magically cool hardware.")
        }
        health?.cpuTempC?.takeIf { it.isFinite() && it >= 70 }?.let {
            local("cpu_heat", "High CPU temperature reported", "CPU sensor: %.1f °C".format(java.util.Locale.US, it), Severity.MEDIUM, ChecklistRoute.HEALTH,
                "Review the reported thermal sensor, pause heavy work and allow the phone to cool. OEM sensor mapping varies; do not install a fake RAM booster/cooler.")
        }
        storage?.takeIf { it.total > 0 && it.free in 0..it.total && (it.free < 500_000_000 || it.usedFraction >= 0.95f) }?.let {
            local("storage_pressure", "Storage needs review", "Free: ${it.free} bytes / total: ${it.total} bytes", Severity.MEDIUM, ChecklistRoute.APP_CACHE,
                "Review app-cache sizes and your files before deleting anything. Clearing another app's cache is manual; this checklist never deletes files or app data.")
        }
        data?.takeIf { it.readingAvailable && !it.isPartial && !it.isUnlimited5g && it.packLimitBytes > 0 && it.packRemainingBytes <= it.packLimitBytes / 10 }?.let {
            local("data_budget", "Configured mobile pack is nearly used", "Measured mobile usage: ${it.packTotalMobileBytes} of configured ${it.packLimitBytes} bytes", Severity.LOW, ChecklistRoute.DATA_BUDGET,
                "Review the configured quota, expiry and carrier app balance. Android usage is not the carrier's official bill or a guarantee of free 5G.")
        }
        network?.takeIf { it.grade == LiveNetworkQuality.QualityGrade.BAD_QUALITY }?.let {
            local("network_quality", "Poor latency or jitter observed", "Probe: ${it.pingMs} ms / jitter ${it.jitterMs} ms", Severity.LOW, ChecklistRoute.MOBILE,
                "Repeat a small latency check and compare another network if convenient. A probe to one endpoint is not proof that every website is slow; large speed tests consume data.")
        }
        return result.sortedWith(compareBy<ChecklistAction> { it.group.ordinal }.thenBy { it.observation.key })
    }

    fun currentAdvice(action: ChecklistAction, advice: List<AiActionAdvice>): AiActionAdvice? = advice.firstOrNull {
        it.key == action.observation.key && it.fingerprint == action.observation.fingerprint
    }
}

object ActionChecklistCodec {
    const val START = "<live_guard_checklist>"
    const val END = "</live_guard_checklist>"
    const val MAX_ADVICE = 100

    private fun token(index: Int) = "step_$index"

    fun instructions(actions: List<ChecklistAction>, shareNames: Boolean): String = buildString {
        appendLine("OPTIONAL MACHINE-READABLE REVIEW CHECKLIST")
        appendLine("After your human-readable answer, append $START followed by one JSON object and $END.")
        appendLine("Schema: {\"steps\":[{\"id\":\"step_0\",\"explanation\":\"why review\",\"review\":\"a cautious manual review step\"}]}")
        appendLine("Use ONLY the step IDs below. These tokens do not authorize actions. Do not invent settings URLs, commands, new apps or a resolved/safe state.")
        appendLine("Do not instruct blanket permission removal/uninstallation. Keep useful banking, accessibility and work-admin access. The app controls grouping from observed facts and user acknowledgements.")
        for ((index, action) in actions.withIndex().take(MAX_ADVICE)) {
            val item = action.observation
            append("${token(index)}: finding=${item.findingId}; severity=${item.severity}; group=${action.group}")
            if (shareNames) item.app?.let { append("; app=${JSONObject.quote(it.label)}; package=${JSONObject.quote(it.packageName)}") }
            appendLine()
        }
        append("If you cannot provide this JSON, still give a useful human-readable answer. Local review steps remain available.")
    }

    fun parse(answer: String, actions: List<ChecklistAction>, atMs: Long): List<AiActionAdvice> {
        val start = answer.indexOf(START)
        val end = answer.indexOf(END, (start + START.length).coerceAtLeast(0))
        if (start < 0 || end < 0 || end - start > 80_000) return emptyList()
        return try {
            val root = JSONObject(answer.substring(start + START.length, end).trim())
            val steps = root.getJSONArray("steps")
            val allowed = actions.take(MAX_ADVICE).mapIndexed { index, action -> token(index) to action }.toMap()
            val found = linkedMapOf<String, AiActionAdvice>()
            for (index in 0 until minOf(steps.length(), MAX_ADVICE)) {
                val step = steps.optJSONObject(index) ?: continue
                val action = allowed[step.optString("id")] ?: continue
                val explanation = text(step.optString("explanation"), 600)
                val review = text(step.optString("review"), 600)
                if (explanation.isBlank() && review.isBlank()) continue
                val item = action.observation
                found.putIfAbsent(item.key, AiActionAdvice(item.key, item.fingerprint, explanation, review, atMs))
            }
            found.values.toList()
        } catch (e: Exception) { emptyList() }
    }

    /** Machine JSON is not displayed as advice cards in the narrative; original reply remains copyable. */
    fun narrative(answer: String): String {
        val start = answer.indexOf(START)
        val end = answer.indexOf(END, (start + START.length).coerceAtLeast(0))
        return when { start >= 0 && end >= 0 -> (answer.substring(0, start) + answer.substring(end + END.length)).trim()
            start >= 0 -> answer.substring(0, start).trim()
            else -> answer }
    }

    private fun text(value: String, max: Int) = value.filter { !it.isISOControl() || it == '\n' }.take(max).trim()
}
