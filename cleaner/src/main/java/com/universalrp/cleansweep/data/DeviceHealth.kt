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
enum class BatteryMeasurementSource {
    ANDROID_BATTERY_BROADCAST,
    KERNEL_THERMAL_ZONE,
    UNAVAILABLE,
}

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
    /** Physical charger connection, including full/paused/not-charging states. */
    val powerConnected: Boolean = charging,
    /** Original broadcast resolution, not a calculated/learned-capacity percentage. */
    val reportedLevel: Int? = null,
    val reportedScale: Int? = null,
    /**
     * 2.42: where [currentA] actually came from, so the UI can say so instead of implying
     * that every phone reports the documented BatteryManager counter. Appended last on
     * purpose — older positional constructions of this class stay valid.
     */
    val currentSource: BatteryValuePolicy.CurrentSource = BatteryValuePolicy.CurrentSource.NONE,
) {
    /** Voltage is read only from Android's battery-changed broadcast in this implementation. */
    val voltageSource: BatteryMeasurementSource
        get() = if (voltageV != null) BatteryMeasurementSource.ANDROID_BATTERY_BROADCAST else BatteryMeasurementSource.UNAVAILABLE

    /** Temperature is read only from Android's battery-changed broadcast in this implementation. */
    val temperatureSource: BatteryMeasurementSource
        get() = if (temperatureC != null) BatteryMeasurementSource.ANDROID_BATTERY_BROADCAST else BatteryMeasurementSource.UNAVAILABLE
}

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
    val batteryTemperatureSource: BatteryMeasurementSource
        get() = when {
            battery.temperatureC != null -> BatteryMeasurementSource.ANDROID_BATTERY_BROADCAST
            batteryTempFromKernelC != null -> BatteryMeasurementSource.KERNEL_THERMAL_ZONE
            else -> BatteryMeasurementSource.UNAVAILABLE
        }

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

    /**
     * Keys of the sticky `ACTION_BATTERY_CHANGED` broadcast's current readings, in microamps.
     *
     * Android only exposed these as `BatteryManager.EXTRA_CURRENT_NOW` /
     * `EXTRA_CURRENT_AVERAGE` in **API 34**; the keys themselves have been in the broadcast
     * long before minSdk 26, and reading them is the way to get a current on kernels whose
     * `getIntProperty` answers `0` or "unsupported" — the owner's OPPO A3s among them. The
     * literals are used deliberately: referencing the API 34 constants would trip NewApi lint
     * on API 26-33, even though String constants are inlined at compile time.
     *
     * If a kernel does not put the key in the broadcast at all, [UNKNOWN] comes back and the
     * reading is reported as *not measured* — never as a fake `0.00 W`.
     */
    private const val EXTRA_CURRENT_NOW = "current_now"
    private const val EXTRA_CURRENT_AVERAGE = "current_average"

    fun read(context: Context): BatteryReading {
        val intent: Intent? = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: Exception) {
            null
        }

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        var percent = if (scale > 0 && level in 0..scale) (level.toDouble() * 100.0 / scale).toInt() else -1
        if (percent < 0) {
            percent = try {
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            } catch (e: Exception) {
                -1
            }
        }

        val tempRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val temperatureC = BatteryValuePolicy.temperatureTenthsToCelsius(tempRaw)

        val voltsRaw = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val voltageV = BatteryValuePolicy.voltageMillivoltsToVolts(voltsRaw)

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: ""

        // 2.42: the documented counter first, then the battery broadcast extra. Many older
        // and OEM kernels (ColorOS on Android 8.1 — the owner's OPPO A3s) answer
        // BATTERY_PROPERTY_CURRENT_NOW with 0 or "unsupported" while ACTION_BATTERY_CHANGED
        // still carries the real charge current. BatteryValuePolicy stays the single decider
        // of which reading may be used and of the unit: a magnitude is never treated as
        // evidence of a different unit, and an unsupported sensor never becomes "0.00 W".
        val current = BatteryValuePolicy.currentReading(
            propertyMicroAmps = property(bm, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            broadcastMicroAmps = intent?.getIntExtra(EXTRA_CURRENT_NOW, UNKNOWN),
        )
        val currentA = current.amps
        val averageCurrentA = BatteryValuePolicy.currentReading(
            propertyMicroAmps = property(bm, BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE),
            broadcastMicroAmps = intent?.getIntExtra(EXTRA_CURRENT_AVERAGE, UNKNOWN),
        ).amps
        val chargeCounter = property(bm, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?.let { it / 1000f } // µAh -> mAh

        val powerConnected = plugged != 0
        val charging = powerConnected && status == BatteryManager.BATTERY_STATUS_CHARGING

        // Power is ONLY charging power when actively plugged into an external power source.
        // During discharging, battery power drain is not charging power and must not be
        // displayed as charging wattage.
        // Furthermore, power measured here is battery-side electrical power (V_batt * I_batt),
        // NOT charger adapter output wattage.
        // 2.41: one implementation decides what may be called charging power — the
        // battery-side policy in ChargingHonestyPolicy (measured voltage × measured current,
        // only while charging, implausible kernel values refused).
        val powerW = ChargingHonestyPolicy.batterySideWatts(charging, powerConnected, voltageV, currentA)

        val signExplanation = when {
            currentA == null -> "Current counter not reported on this phone"
            charging && currentA > 0.05f -> "Positive = current flowing into the battery (battery-side)"
            charging -> "Trickle / full"
            currentA < -0.05f -> "Negative = current drawn out of battery"
            else -> "Near zero right now"
        }
        // 2.42: say which Android source actually supplied the number. On a phone whose
        // current property reports nothing, the broadcast extra did — that is worth stating
        // instead of implying every phone exposes the documented counter.
        val signNote = if (current.source == BatteryValuePolicy.CurrentSource.BROADCAST) {
            signExplanation + " " + tr("Read from the battery broadcast: this phone's current counter property reports no value.")
        } else {
            signExplanation
        }

        val capacity = if (chargeCounter != null && percent in 5..100) {
            (chargeCounter / (percent / 100f)).let { if (it in 500f..12_000f) it else null }
        } else {
            null
        }

        val reading = BatteryReading(
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
            powerConnected = powerConnected,
            reportedLevel = level.takeIf { scale > 0 && it in 0..scale },
            reportedScale = scale.takeIf { it > 0 && level in 0..it },
            currentSource = current.source,
        )
        LiveChargingEta.observe(reading)
        return reading
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

fun batteryTimeMinutes(battery: BatteryReading): Int? {
    if (battery.percent !in 0..100 || !battery.charging || battery.percent >= 100) return null
    return LiveChargingEta.estimate(battery)?.minutesToFull
}

/**
 * A charging ETA is shown only after several recent observations demonstrate a real rise in
 * state of charge. A phone without that evidence says it is still estimating; it never invents
 * a time from a missing current, guessed capacity, or a single reading.
 */
fun batteryTimeLabel(battery: BatteryReading): String? {
    if (battery.percent !in 0..100) return null
    if (battery.charging) {
        if (battery.percent >= 100) return tr("Full — unplug when convenient")
        val minutes = batteryTimeMinutes(battery)
            ?: return tr("Estimating charging time; more observations are needed.")
        return tr("Estimated to 100% from recent charging: %s", formatMinutes(minutes))
    }
    if (battery.powerConnected) return tr("Charging time unavailable while the battery is not charging.")
    return tr("Time-to-empty estimate unavailable")
}

fun formatMinutes(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours <= 0 -> tr("%s min", mins)
        mins == 0 -> tr("%s h", hours)
        else -> tr("%s h %s min", hours, mins)
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
