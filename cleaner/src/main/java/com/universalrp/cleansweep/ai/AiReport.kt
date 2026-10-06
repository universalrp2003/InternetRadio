package com.universalrp.cleansweep.ai

import com.universalrp.cleansweep.data.AppInventoryReport
import com.universalrp.cleansweep.data.HealthSnapshot
import com.universalrp.cleansweep.data.NetworkReport
import com.universalrp.cleansweep.data.SecurityReport
import com.universalrp.cleansweep.data.StorageInfo
import com.universalrp.cleansweep.data.formatUptime

/**
 * Turns the phone's own numbers into a compact text report for the AI.
 *
 * What goes in: hardware and battery numbers, storage, security findings, and (if
 * you leave the switch on) the names of installed apps with their sizes, tags and
 * last-used dates. What never goes in: your photos, files, messages, contacts,
 * passwords, or any file path. There is no CleanSweep server in the middle — the
 * request goes from this phone to the AI provider you chose.
 */
object AiReport {

    fun systemPrompt(): String = """
        You are a careful Android phone-health expert inside the CleanSweep app.
        The user is a normal phone owner, not a developer. Reply in short sections with
        plain headings and bullet points.

        Rules you must follow:
        1. Never invent numbers that are not in the report.
        2. If a value is missing, say "not reported by this phone" instead of guessing.
        3. Separate what is clearly bloatware or risky from what is normal and should be left alone.
        4. Say clearly when something needs root or a manufacturer tool and therefore cannot be
           fixed by a normal app.
        5. Mention when a "problem" is actually normal for a phone of this age.
        6. End with the three highest-value actions, in priority order, that a non-technical
           person can actually do on this phone.
    """.trimIndent()

    fun build(
        config: AiConfig,
        health: HealthSnapshot?,
        apps: AppInventoryReport?,
        security: SecurityReport?,
        network: NetworkReport?,
        storage: StorageInfo?,
    ): String {
        val out = StringBuilder()
        out.appendLine("PHONE HEALTH REPORT (generated on the phone by CleanSweep)")
        out.appendLine()

        health?.let { h ->
            out.appendLine("== Device ==")
            out.appendLine("${h.device.manufacturer} ${h.device.model} (device ${h.device.device}), SoC ${h.device.soc}")
            out.appendLine("Android ${h.device.androidVersion} (API ${h.device.sdk}), security patch ${h.device.securityPatch}")
            out.appendLine("CPU: ${h.device.cores} cores, ABI ${h.device.abi}")
            out.appendLine(
                "RAM: ${gb(h.device.totalRamBytes)} total, ${gb(h.device.availableRamBytes)} available"
            )
            out.appendLine("Uptime: ${formatUptime(h.device.uptimeMs)}")
            out.appendLine()

            out.appendLine("== Battery ==")
            out.appendLine("Charge ${h.battery.percent}% — ${h.battery.statusLabel} (${h.battery.pluggedLabel})")
            out.appendLine("Battery health reported by Android: ${h.battery.healthLabel}")
            out.appendLine("Temperature: ${h.battery.temperatureC?.let { "%.1f C".format(it) } ?: "not reported"}")
            out.appendLine("Voltage: ${h.battery.voltageV?.let { "%.2f V".format(it) } ?: "not reported"}")
            out.appendLine("Current: ${h.battery.currentA?.let { "%.2f A".format(it) } ?: "not reported"}" +
                " (average ${h.battery.averageCurrentA?.let { "%.2f A".format(it) } ?: "n/a"})")
            out.appendLine("Power: ${h.battery.powerW?.let { "%.2f W".format(it) } ?: "not reported"}")
            out.appendLine("Charge counter: ${h.battery.chargeCounterMah?.let { "%.0f mAh".format(it) } ?: "not reported"}")
            out.appendLine("Battery technology: ${h.battery.technology}")
            out.appendLine()

            out.appendLine("== Thermals and CPU ==")
            out.appendLine(
                "CPU temperature: ${h.cpuTempC?.let { "%.1f C".format(it) } ?: "not exposed by this kernel"}" +
                    (h.cpuTempZone?.let { " (zone: $it)" } ?: "") +
                    (h.cpuMaxTempC?.let { ", hottest sensor on the phone: %.1f C".format(it) } ?: "")
            )
            out.appendLine(
                "CPU frequency: ${h.cpuCurrentMhz?.let { "$it MHz now" } ?: "not reported"}" +
                    (h.cpuMaxMhz?.let { " (max $it MHz)" } ?: "")
            )
            out.appendLine(
                "Load average: ${h.cpuLoadAvg?.let { "%.2f".format(it) } ?: "not reported"}" +
                    (h.cpuLoadPercent?.let { " (~${it.toInt()}% of all cores)" } ?: "")
            )
            out.appendLine()
        }

        storage?.let {
            out.appendLine("== Storage ==")
            out.appendLine("${gb(it.used)} used of ${gb(it.total)} — ${gb(it.free)} free (${(it.usedFraction * 100).toInt()}% full)")
            out.appendLine()
        }

        if (config.includeAppNames && apps != null) {
            out.appendLine("== Installed apps (${apps.rows.size} entries) ==")
            out.appendLine(
                "Totals: ${apps.preinstalledCount} preinstalled, ${apps.unusedCount} unused third-party " +
                    "(30+ days), ${apps.sideloadedCount} not from a store, ${apps.riskyCount} with sensitive permissions"
            )
            out.appendLine(
                "Format: name | package | size | last used | tags | permissions of note"
            )
            appLines(apps).forEach { out.appendLine(it) }
            out.appendLine()
        } else if (apps != null) {
            out.appendLine("== Installed apps ==")
            out.appendLine(
                "The user chose not to share app names. Totals only: ${apps.rows.size} apps, " +
                    "${apps.preinstalledCount} preinstalled, ${apps.unusedCount} unused third-party, " +
                    "${apps.sideloadedCount} not from a store, ${apps.riskyCount} with sensitive permissions."
            )
            out.appendLine()
        }

        security?.let { s ->
            out.appendLine("== Security check (on-phone findings) ==")
            out.appendLine("CleanSweep score: ${s.score}/100 (${s.verdict}) over ${s.appsChecked} apps")
            s.findings.forEach { f ->
                out.appendLine(
                    "- [${f.severity}] ${f.title}: ${f.detail}" +
                        (if (f.samples.isNotEmpty()) " Examples: ${f.samples.joinToString(", ")}" else "") +
                        (f.fixHint?.let { " Fix: $it" } ?: "")
                )
            }
            out.appendLine("This is a permissions and settings review, not a virus scan.")
            out.appendLine()
        }

        if (config.includeNetwork && network != null) {
            val w = network.wifi
            out.appendLine("== Network ==")
            out.appendLine(
                "Transport: ${w.transport}" +
                    (w.ssid?.let { ", Wi-Fi \"$it\"" } ?: ", Wi-Fi name hidden (permission not granted)") +
                    (w.band?.let { ", $it" } ?: "") +
                    (w.linkSpeedMbps?.let { ", link $it Mbps" } ?: "") +
                    (w.signalPercent?.let { ", signal $it%" } ?: "")
            )
            out.appendLine("Address: ${w.subnetLabel ?: "n/a"}, gateway ${w.gateway ?: "n/a"}, DNS ${w.dns.joinToString(", ").ifBlank { "n/a" }}")
            out.appendLine("Devices found on the network: ${network.devices.size} (probed ${network.probedHosts} addresses)")
            network.devices.take(20).forEach { device ->
                out.appendLine(
                    "- ${device.ip}${if (device.isSelf) " (this phone)" else ""}" +
                        (if (device.isGateway) " (router)" else "") +
                        (device.vendor?.let { ", $it" } ?: "") +
                        (device.hostname?.let { ", host $it" } ?: "")
                )
            }
            out.appendLine()
        }

        if (!config.includeAppNames) {
            out.appendLine(
                "Note: app names were withheld by the user, so do not name specific apps; give " +
                    "guidance by category instead."
            )
        }

        return out.toString()
    }

    private fun appLines(apps: AppInventoryReport): List<String> {
        val interesting = apps.rows
            .filter { row ->
                row.isKnownBloatStub || row.isPreinstalled || row.isUnused ||
                    row.isSideloaded || row.riskyPermissions.isNotEmpty()
            }
            .sortedWith(
                compareByDescending<com.universalrp.cleansweep.data.AppRow> { it.isKnownBloatStub }
                    .thenByDescending { it.totalBytes }
            )
            .take(60)

        val lines = interesting.map { row ->
            buildString {
                append(row.label.take(40))
                append(" | ").append(row.pkg)
                append(" | ").append(gb(row.totalBytes))
                append(" | ").append(lastUsedLabel(row.lastUsedMs))
                if (row.tags.isNotEmpty()) append(" | ").append(row.tags.joinToString(", "))
                if (row.riskyPermissions.isNotEmpty()) {
                    append(" | ").append(row.riskyPermissions.take(4).joinToString("; "))
                }
            }
        }.toMutableList()

        val remaining = apps.rows.size - interesting.size
        if (remaining > 0) {
            lines.add("(+$remaining more apps with nothing unusual about them)")
        }
        return lines
    }

    private fun lastUsedLabel(ms: Long?): String {
        if (ms == null) return "never used (30 days)"
        val days = ((System.currentTimeMillis() - ms) / (24L * 3600 * 1000)).coerceAtLeast(0)
        return when {
            days <= 0 -> "used today"
            days == 1L -> "used yesterday"
            days < 30 -> "used ${days}d ago"
            else -> "unused ${days}d"
        }
    }

    private fun gb(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) "%.2f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
    }
}
