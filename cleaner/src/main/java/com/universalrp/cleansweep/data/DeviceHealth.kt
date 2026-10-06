package com.universalrp.cleansweep.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Battery + hardware readings.
 *
 * Everything here comes from the Android battery broadcast, the BatteryManager
 * counters and the kernel's sysfs files — the same numbers the phone's own
 * settings screen shows. Nothing is guessed and nothing is invented: when a value
 * is not available on this phone (many Oppo/Vivo/Realme kernels hide the current
 * counter) the field stays null and the UI says "not reported".
 */
data class BatteryReading(
    val percent: Int,
    val temperatureC: Float?,
    val voltageV: Float?,
    val currentA: Float?,
    val averageCurrentA: Float?,
    val powerW: Float?,
    val charging: Boolean,
    val pluggedLabel: String,
    val statusLabel: String,
    val healthLabel: String,
    val technology: String,
    val chargeCounterMah: Float?,
    /** Rough pack capacity learned from charge-counter ÷ state-of-charge. */
    val estimatedCapacityMah: Float?,
    val currentSignNote: String,
)

data class DeviceDetails(
    val manufacturer: String,
    val model: String,
    val device: String,
    val hardware: String,
    val soc: String,
    val androidVersion: String,
    val sdk: Int,
    val securityPatch: String,
    val abi: String,
    val cores: Int,
    val uptimeMs: Long,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
)

data class HealthSnapshot(
    val battery: BatteryReading,
    val batteryTempFromKernelC: Float?,
    val cpuTempC: Float?,
    val cpuTempZone: String?,
    val cpuCurrentMhz: Long?,
    val cpuMaxMhz: Long?,
    val cpuLoadAvg: Float?,
    val cpuLoadPercent: Float?,
    val cpuMaxTempC: Float?,
    val device: DeviceDetails,
    val storage: StorageInfo,
    val capturedAtMs: Long,
) {
    /** Rough thermal state from CPU temperature, used for colour + wording. */
    val thermalState: String
        get() = when {
            cpuTempC == null -> "Not reported"
            cpuTempC < 40f -> "Cool"
            cpuTempC < 50f -> "Normal"
            cpuTempC < 60f -> "Warm"
            cpuTempC < 70f -> "Hot"
            else -> "Very hot"
        }
}

object BatteryReader {

    private const val UNKNOWN = Int.MIN_VALUE

    fun read(context: Context): BatteryReading {
        val intent: Intent? = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: Exception) {
            null
        }

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        var percent = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        if (percent < 0) {
            percent = try {
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            } catch (e: Exception) {
                -1
            }
        }

        val tempRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val temperatureC = if (tempRaw > 0) tempRaw / 10f else null

        val voltsRaw = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val voltageV = if (voltsRaw > 0) voltsRaw / 1000f else null

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: ""

        val currentA = property(bm, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)?.let(::toAmps)
        val averageCurrentA = property(bm, BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)?.let(::toAmps)
        val chargeCounter = property(bm, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?.let { it / 1000f } // µAh -> mAh

        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0

        val powerW = if (voltageV != null && currentA != null) {
            val watts = voltageV * abs(currentA)
            // Filter obviously wrong kernel values (some ROMs report 0 or absurd numbers).
            if (watts > 0f && watts < 200f) watts else null
        } else {
            null
        }

        val signNote = when {
            currentA == null -> "Current counter not reported on this phone"
            currentA > 0.05f && charging -> "Positive = current flowing into the battery"
            currentA < -0.05f -> "Negative = current drawn out of the battery"
            charging -> "Trickle / full"
            else -> "Near zero right now"
        }

        val capacity = if (chargeCounter != null && percent in 5..100) {
            (chargeCounter / (percent / 100f)).let { if (it in 500f..12_000f) it else null }
        } else {
            null
        }

        return BatteryReading(
            percent = percent.coerceIn(if (percent < 0) -1 else 0, 100),
            temperatureC = temperatureC,
            voltageV = voltageV,
            currentA = currentA,
            averageCurrentA = averageCurrentA,
            powerW = powerW,
            charging = charging,
            pluggedLabel = pluggedLabel(plugged),
            statusLabel = statusLabel(status),
            healthLabel = healthLabel(health),
            technology = tech.ifBlank { "Not reported" },
            chargeCounterMah = chargeCounter,
            estimatedCapacityMah = capacity,
            currentSignNote = signNote,
        )
    }

    private fun property(bm: BatteryManager?, id: Int): Int? {
        if (bm == null) return null
        return try {
            val value = bm.getIntProperty(id)
            if (value == UNKNOWN) null else value
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Android's spec says microamps, but several ROMs (Samsung, some MediaTek
     * kernels) report milliamps. Phones draw 100–3,000 mA, i.e. 100,000–3,000,000
     * µA, so anything below 20,000 is far more likely to be milliamps.
     */
    private fun toAmps(raw: Int): Float {
        val value = raw.toFloat()
        val amps = if (abs(raw) < 20_000) value / 1_000f else value / 1_000_000f
        return (amps * 1000f).roundToInt() / 1000f
    }

    private fun pluggedLabel(plugged: Int): String = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> "Charger (AC)"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
        8 -> "Dock" // BATTERY_PLUGGED_DOCK is hidden; value is stable
        0 -> "Not plugged in"
        else -> "Charging source #$plugged"
    }

    private fun statusLabel(status: Int): String = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
        BatteryManager.BATTERY_STATUS_FULL -> "Full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
        else -> "Unknown"
    }

    private fun healthLabel(health: Int): String = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead / worn out"
        5 -> "Over voltage"
        6 -> "Unspecified failure"
        BatteryManager.BATTERY_HEALTH_UNKNOWN -> "Not reported"
        else -> "Not reported"
    }
}

/** CPU temperature, frequencies and load from the kernel interfaces. */
object CpuReader {

    private val CPU_ZONE_HINTS = listOf(
        "cpu", "soc", "ap", "tsens", "cluster", "big", "little", "silver", "gold",
        "mtk", "qti", "board", "gpu", "core",
    )
    private val NOT_CPU_ZONE_HINTS = listOf(
        "battery", "bms", "charger", "usb", "pa_", "skin", "ambient", "display", "backlight",
    )

    data class TempReading(val celsius: Float?, val zone: String?, val maxCelsius: Float?)

    fun temperatures(): TempReading {
        val candidates = mutableListOf<Pair<String, Float>>()
        val all = mutableListOf<Pair<String, Float>>()
        try {
            val base = File("/sys/class/thermal")
            val zones = base.listFiles() ?: emptyArray()
            for (zone in zones) {
                val name = zone.name
                if (!name.startsWith("thermal_zone")) continue
                val type = readText(File(zone, "type")).trim()
                val raw = readText(File(zone, "temp")).trim()
                val value = raw.toFloatOrNull() ?: continue
                val celsius = normaliseTemp(value) ?: continue
                all.add(type.ifBlank { name } to celsius)
                val lower = type.lowercase()
                val looksCpu = CPU_ZONE_HINTS.any { lower.contains(it) } &&
                    NOT_CPU_ZONE_HINTS.none { lower.contains(it) }
                if (looksCpu) candidates.add(type.ifBlank { name } to celsius)
            }
        } catch (e: Exception) {
            // Kernel interfaces are not readable on every phone; that is fine.
        }
        val best = candidates.maxByOrNull { it.second }
        val hottest = all.maxByOrNull { it.second }
        return TempReading(
            celsius = best?.second,
            zone = best?.first,
            maxCelsius = hottest?.second,
        )
    }

    /** Battery temperature from the kernel, used when the broadcast has none. */
    fun batteryTemperature(): Float? {
        try {
            val zones = File("/sys/class/thermal").listFiles() ?: return null
            for (zone in zones) {
                val type = readText(File(zone, "type")).lowercase()
                if (!type.contains("batt") && !type.contains("bms")) continue
                val value = readText(File(zone, "temp")).trim().toFloatOrNull() ?: continue
                normaliseTemp(value)?.let { if (it in 0f..80f) return it }
            }
        } catch (e: Exception) {
            // Ignored.
        }
        return null
    }

    /**
     * Kernels report millidegrees (45000), tenths (450) or plain degrees (45).
     */
    private fun normaliseTemp(value: Float): Float? {
        val celsius = when {
            value > 1000f -> value / 1000f
            value > 200f -> value / 10f
            else -> value
        }
        return if (celsius > -30f && celsius < 130f) (celsius * 10f).roundToInt() / 10f else null
    }

    fun cores(): Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    /**
     * The busy core's clock. A phone has big and little cores with different limits, so
     * this (and the maximum below) are both measured across *all* cores — otherwise a
     * boosting big core looked like it was running faster than "maximum", which is
     * exactly what a user spotted in v2.0.
     */
    fun currentFrequencyMhz(): Long? {
        var best: Long? = null
        for (index in 0 until cores()) {
            val khz = readText(
                File("/sys/devices/system/cpu/cpu$index/cpufreq/scaling_cur_freq")
            ).trim().toLongOrNull() ?: continue
            val mhz = khz / 1000
            if (best == null || mhz > best) best = mhz
        }
        return best
    }

    fun maxFrequencyMhz(): Long? {
        var best: Long? = null
        for (index in 0 until cores()) {
            val khz = readText(
                File("/sys/devices/system/cpu/cpu$index/cpufreq/cpuinfo_max_freq")
            ).trim().toLongOrNull()
                ?: readText(
                    File("/sys/devices/system/cpu/cpu$index/cpufreq/scaling_max_freq")
                ).trim().toLongOrNull()
                ?: continue
            val mhz = khz / 1000
            if (khz > 0 && (best == null || mhz > best)) best = mhz
        }
        return best
    }

    fun loadAverage(): Float? =
        readText(File("/proc/loadavg")).trim().split(" ").firstOrNull()?.toFloatOrNull()

    private fun readText(file: File): String = try {
        if (file.exists() && file.canRead()) file.readText() else ""
    } catch (e: Exception) {
        ""
    }
}

object DeviceHealthReader {

    fun read(context: Context): HealthSnapshot {
        val temps = CpuReader.temperatures()
        val cores = CpuReader.cores()
        val loadAvg = CpuReader.loadAverage()
        val mem = memory(context)
        val storage = try {
            StorageInfoProvider.read()
        } catch (e: Exception) {
            StorageInfo(0L, 0L)
        }

        val soc = if (Build.VERSION.SDK_INT >= 31) {
            Build.SOC_MODEL.ifBlank { Build.HARDWARE }
        } else {
            Build.HARDWARE
        }

        return HealthSnapshot(
            battery = BatteryReader.read(context),
            batteryTempFromKernelC = CpuReader.batteryTemperature(),
            cpuTempC = temps.celsius,
            cpuTempZone = temps.zone,
            cpuCurrentMhz = CpuReader.currentFrequencyMhz(),
            cpuMaxMhz = CpuReader.maxFrequencyMhz(),
            cpuLoadAvg = loadAvg,
            cpuLoadPercent = loadAvg?.let {
                ((it / cores.toFloat()) * 100f).coerceIn(0f, 100f)
            },
            cpuMaxTempC = temps.maxCelsius,
            device = DeviceDetails(
                manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
                model = Build.MODEL,
                device = Build.DEVICE,
                hardware = Build.HARDWARE,
                soc = soc,
                androidVersion = Build.VERSION.RELEASE,
                sdk = Build.VERSION.SDK_INT,
                securityPatch = Build.VERSION.SECURITY_PATCH.ifBlank { "unknown" },
                abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
                cores = cores,
                uptimeMs = SystemClock.elapsedRealtime(),
                totalRamBytes = mem.first,
                availableRamBytes = mem.second,
            ),
            storage = storage,
            capturedAtMs = System.currentTimeMillis(),
        )
    }

    private fun memory(context: Context): Pair<Long, Long> {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            info.totalMem to info.availMem
        } catch (e: Exception) {
            0L to 0L
        }
    }

    /** Free space on the data partition (where "internal storage" lives). */
    fun internalFreeBytes(): Long = try {
        val stat = StatFs(Environment.getDataDirectory().path)
        stat.availableBytes
    } catch (e: Exception) {
        0L
    }
}

/**
 * "About 42 min to full" / "About 4 h 10 m left", computed from the *real* current
 * instead of a guess. Returns null when the phone does not report enough data.
 */
fun batteryTimeLabel(battery: BatteryReading): String? {
    val current = battery.currentA ?: return null
    if (battery.percent < 0) return null
    val capacity = battery.estimatedCapacityMah ?: return null
    val remainingMah = capacity * (1f - battery.percent / 100f)
    if (battery.charging) {
        if (battery.percent >= 100) return "Full — unplug when convenient"
        if (current <= 0.05f) return "Charging slowly right now"
        val minutes = (remainingMah / (current * 1000f) * 60f).toInt().coerceIn(1, 14 * 60)
        return "About ${formatMinutes(minutes)} to full"
    } else {
        if (current >= -0.05f) return "Not drawing power right now"
        val minutes = (capacity * (battery.percent / 100f) / (-current * 1000f) * 60f)
            .toInt().coerceIn(1, 48 * 60)
        return "About ${formatMinutes(minutes)} left at this rate"
    }
}

fun formatMinutes(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours <= 0 -> "$mins min"
        mins == 0 -> "$hours h"
        else -> "$hours h $mins min"
    }
}

/** "3 h 21 m" style uptime. */
fun formatUptime(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours <= 0L -> "$minutes min"
        minutes <= 0L -> "$hours h"
        else -> "$hours h $minutes min"
    }
}
