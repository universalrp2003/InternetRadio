package com.universalrp.cleansweep.voice

/** Pure gates. An enabled switch/accepted request is never proof that sound was heard. */
object VoiceDeliveryPolicy {
    enum class Stage { NOT_REQUESTED, DISABLED, EVENT_OFF, QUIET_HOURS, QUEUED, INITIALIZING, NO_ENGINE,
        INIT_FAILED, INIT_TIMEOUT, LANGUAGE_MISSING, ENGINE_REJECTED, PLAYING, ENGINE_FINISHED,
        ENGINE_ERROR, TIMED_OUT, CANCELLED, DUPLICATE, STALE_EVENT }
    fun blocked(master: Boolean, eventAllowed: Boolean, quiet: Boolean, manual: Boolean): Stage? = when {
        !master -> Stage.DISABLED
        !eventAllowed -> Stage.EVENT_OFF
        quiet && !manual -> Stage.QUIET_HOURS
        else -> null
    }
    fun cooldownReady(nowMs: Long, lastMs: Long, intervalMs: Long): Boolean = lastMs <= 0 || nowMs < lastMs || nowMs - lastMs >= intervalMs
    fun changedTransport(previous: String?, current: String): Boolean = previous in setOf("wifi", "mobile") &&
        current in setOf("wifi", "mobile") && previous != current
    fun retryOffline(networkVoice: Boolean, retried: Boolean, offlineInstalled: Boolean): Boolean = networkVoice && !retried && offlineInstalled
    fun validStatus(code: Int): Boolean = code >= 0 // ERROR (-1), missing data (-2), unsupported (-3) are not ready.
}

data class VoiceDeliveryStatus(
    val stage: VoiceDeliveryPolicy.Stage = VoiceDeliveryPolicy.Stage.NOT_REQUESTED,
    val event: String = "",
    val atMs: Long = 0,
    val tamilAvailable: Boolean? = null,
    val mediaVolume: Int? = null,
    val online: Boolean = false,
    val language: String = "",
)

/** Registration evidence only, never proof that a physical event arrived or audio was heard. Runtime-only. */
data class VoiceObserverStatus(val owners: Int = 0, val battery: Boolean? = null, val network: Boolean? = null, val power: Boolean? = null)
