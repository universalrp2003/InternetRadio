package com.universalrp.vocalremover.processing

/** Resource modes change bounded CPU parallelism; they never select a lower-quality model. */
enum class ProcessingProfile(val title: String, val description: String) {
    SLOW("Slow", "One inference thread with a longer cool-down"),
    BALANCED("Medium", "Moderate CPU use"),
    FAST("Fast", "Use more cores when Android reports normal thermal status"),
}

enum class ThermalLevel { UNKNOWN, NORMAL, LIGHT, MODERATE, SEVERE, CRITICAL }
enum class PauseReason { USER, THERMAL, LOW_MEMORY, STORAGE, SERVICE_LIMIT, ERROR }

data class SafetySnapshot(
    val thermal: ThermalLevel,
    val batteryTemperatureC: Float?,
    val cpuTemperatureC: Float?,
    val availableRamBytes: Long?,
    val availableStorageBytes: Long?,
)

data class WorkDecision(
    val pause: Boolean,
    val reason: PauseReason?,
    val threads: Int,
    val interChunkDelayMs: Long,
)

/**
 * Resource guards rely on Android's thermal status and system memory/storage headroom. Reported
 * temperature sensors are informational only: this prototype intentionally has no hard-coded
 * battery or CPU degree threshold. Unknown readings never become zero.
 */
object ProcessingPolicy {
    // Conservative headroom for the native model session and short stereo inference buffers.
    // This is a guardrail, not a claim about the model's exact peak RAM requirement.
    const val LOW_MEMORY_PAUSE_BYTES = 512L * 1024L * 1024L
    const val LOW_MEMORY_AUTO_RESUME_BYTES = 1536L * 1024L * 1024L
    const val LOW_MEMORY_AUTO_RESUME_STABLE_MS = 15_000L
    const val STORAGE_SAFETY_RESERVE_BYTES = 96L * 1024L * 1024L
    const val LOW_STORAGE_PAUSE_BYTES = STORAGE_SAFETY_RESERVE_BYTES
    private const val MEMORY_THROTTLE_BYTES = 768L * 1024L * 1024L

    fun profileThreads(profile: ProcessingProfile, availableCores: Int): Int {
        val cores = availableCores.coerceAtLeast(1)
        return when (profile) {
            ProcessingProfile.SLOW -> 1
            ProcessingProfile.BALANCED -> minOf(2, cores)
            ProcessingProfile.FAST -> minOf(4, cores)
        }
    }

    fun decide(
        profile: ProcessingProfile,
        availableCores: Int,
        snapshot: SafetySnapshot,
    ): WorkDecision {
        // Null means the OS did not report a metric; zero is a real low-memory/storage reading.
        val ram = snapshot.availableRamBytes?.takeIf { it >= 0L }
        val storage = snapshot.availableStorageBytes?.takeIf { it >= 0L }
        val hardPause = when {
            snapshot.thermal >= ThermalLevel.SEVERE && snapshot.thermal != ThermalLevel.UNKNOWN ->
                PauseReason.THERMAL
            ram != null && ram < LOW_MEMORY_PAUSE_BYTES -> PauseReason.LOW_MEMORY
            storage != null && storage < LOW_STORAGE_PAUSE_BYTES -> PauseReason.STORAGE
            else -> null
        }
        if (hardPause != null) return WorkDecision(true, hardPause, 0, 0L)

        // Android's MODERATE thermal status requests back-off before its SEVERE pause signal.
        val thermallyThrottled = snapshot.thermal == ThermalLevel.MODERATE
        val memoryThrottled = ram != null && ram < MEMORY_THROTTLE_BYTES
        val effectiveThreads = if (thermallyThrottled || memoryThrottled) 1
        else profileThreads(profile, availableCores)
        val delay = when {
            thermallyThrottled -> 800L
            memoryThrottled -> 300L
            profile == ProcessingProfile.SLOW -> 250L
            profile == ProcessingProfile.BALANCED -> 80L
            else -> 0L
        }
        return WorkDecision(false, null, effectiveThreads, delay)
    }
}

/** Requires sustained memory headroom so a pause does not immediately oscillate into another. */
internal class LowMemoryAutoResumeGate(
    private val requiredBytes: Long = ProcessingPolicy.LOW_MEMORY_AUTO_RESUME_BYTES,
    private val stableDurationMs: Long = ProcessingPolicy.LOW_MEMORY_AUTO_RESUME_STABLE_MS,
    private val maxSampleGapMs: Long = 6_000L,
) {
    private var headroomSinceMs: Long? = null
    private var lastSampleMs: Long? = null

    fun shouldResume(nowElapsedMs: Long, reason: PauseReason?, availableRamBytes: Long?): Boolean {
        val hasHeadroom = reason == PauseReason.LOW_MEMORY &&
            availableRamBytes != null && availableRamBytes >= requiredBytes
        if (!hasHeadroom) {
            reset()
            return false
        }
        val last = lastSampleMs
        if (last != null && (nowElapsedMs < last || nowElapsedMs - last > maxSampleGapMs)) {
            headroomSinceMs = null
        }
        lastSampleMs = nowElapsedMs
        val since = headroomSinceMs ?: nowElapsedMs.also { headroomSinceMs = it }
        return nowElapsedMs >= since && nowElapsedMs - since >= stableDurationMs
    }

    fun reset() {
        headroomSinceMs = null
        lastSampleMs = null
    }
}
