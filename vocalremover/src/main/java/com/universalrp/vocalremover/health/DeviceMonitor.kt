package com.universalrp.vocalremover.health

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import com.universalrp.vocalremover.processing.ThermalLevel
import java.io.File
import java.util.Locale

/** A point-in-time snapshot. Sensor readings are optional and keep their actual source labels. */
data class DeviceSnapshot(
    val thermal: ThermalLevel,
    val thermalLabel: String,
    val batteryTemperatureC: Float?,
    val cpuTemperatureC: Float?,
    val cpuSensorLabel: String?,
    val availableRamBytes: Long?,
    val totalRamBytes: Long?,
    val appPssBytes: Long?,
    val appNativeHeapBytes: Long,
    val appCpuPercent: Float?,
    val availableStorageBytes: Long?,
)

class DeviceMonitor(private val context: Context) {
    private var lastCpuMs: Long? = null
    private var lastElapsedMs: Long? = null

    @Synchronized
    fun read(): DeviceSnapshot {
        val thermal = readThermal()
        val batteryTemp = readBatteryTemperature()
        val cpuTemp = readCpuTemperature()
        val memory = readMemory()
        val cpuPercent = readAppCpuPercent()
        val freeStorage = runCatching {
            android.os.StatFs(context.filesDir.absolutePath).availableBytes
        }.getOrNull()?.takeIf { it >= 0L }
        return DeviceSnapshot(
            thermal = thermal.first,
            thermalLabel = thermal.second,
            batteryTemperatureC = batteryTemp,
            cpuTemperatureC = cpuTemp.first,
            cpuSensorLabel = cpuTemp.second,
            availableRamBytes = memory.first,
            totalRamBytes = memory.second,
            appPssBytes = readPss(),
            appNativeHeapBytes = Debug.getNativeHeapAllocatedSize().coerceAtLeast(0L),
            appCpuPercent = cpuPercent,
            availableStorageBytes = freeStorage,
        )
    }

    private fun readThermal(): Pair<ThermalLevel, String> {
        if (Build.VERSION.SDK_INT < 29) return ThermalLevel.UNKNOWN to "Android thermal status not available"
        val status = runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
        }.getOrNull() ?: return ThermalLevel.UNKNOWN to "Thermal status not reported"
        return when (status) {
            PowerManager.THERMAL_STATUS_NONE -> ThermalLevel.NORMAL to "Normal"
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.LIGHT to "Light"
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE to "Moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE to "Severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> ThermalLevel.CRITICAL to "Critical"
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalLevel.CRITICAL to "Emergency"
            else -> ThermalLevel.UNKNOWN to "Thermal status not reported"
        }
    }

    private fun readBatteryTemperature(): Float? = runCatching {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val tenthsC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        if (tenthsC == Int.MIN_VALUE) null else tenthsC / 10f
    }.getOrNull()?.takeIf { it.isFinite() && it in -30f..90f }

    /**
     * Kernel thermal zones are not a standard public CPU-temperature API. Only return a
     * reading when the zone name itself identifies a CPU; never relabel a random hotspot.
     */
    private fun readCpuTemperature(): Pair<Float?, String?> {
        val root = File("/sys/class/thermal")
        val zones = runCatching { root.listFiles()?.filter { it.name.startsWith("thermal_zone") } }
            .getOrNull().orEmpty()
        val readings = zones.mapNotNull { zone ->
            val type = runCatching { File(zone, "type").readText().trim() }.getOrNull().orEmpty()
            if (!type.contains("cpu", ignoreCase = true)) return@mapNotNull null
            val raw = runCatching { File(zone, "temp").readText().trim().toLongOrNull() }.getOrNull()
                ?: return@mapNotNull null
            val celsius = when {
                raw in 200L..1_999L -> raw.toFloat() / 10f
                raw in 2_000L..200_000L -> raw.toFloat() / 1000f
                raw in 20L..130L -> raw.toFloat()
                else -> return@mapNotNull null
            }
            if (!celsius.isFinite() || celsius !in -30f..130f) return@mapNotNull null
            celsius to type
        }
        val hottest = readings.maxByOrNull { it.first } ?: return null to null
        return hottest.first to hottest.second
    }

    private fun readMemory(): Pair<Long?, Long?> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null to null
        return runCatching {
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            info.availMem to info.totalMem
        }.getOrNull() ?: (null to null)
    }

    private fun readPss(): Long? = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = am.getProcessMemoryInfo(intArrayOf(Process.myPid())).firstOrNull() ?: return null
        info.totalPss.toLong() * 1024L
    }.getOrNull()?.takeIf { it >= 0L }

    /** Percent is this app's CPU time as a fraction of one core; it can exceed 100%. */
    private fun readAppCpuPercent(): Float? {
        val nowWall = SystemClock.elapsedRealtime()
        val nowCpu = Process.getElapsedCpuTime()
        val previousWall = lastElapsedMs
        val previousCpu = lastCpuMs
        lastElapsedMs = nowWall
        lastCpuMs = nowCpu
        if (previousWall == null || previousCpu == null || nowWall <= previousWall) return null
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        return ((nowCpu - previousCpu).coerceAtLeast(0L).toFloat() /
            (nowWall - previousWall).toFloat() * 100f).coerceIn(0f, cores * 100f)
    }

    companion object {
        fun formatBytes(value: Long?): String = value?.takeIf { it >= 0L }?.let {
            val mb = it / (1024.0 * 1024.0)
            if (mb >= 1024.0) String.format(Locale.US, "%.1f GiB", mb / 1024.0)
            else String.format(Locale.US, "%.0f MiB", mb)
        } ?: "Not reported"
    }
}
