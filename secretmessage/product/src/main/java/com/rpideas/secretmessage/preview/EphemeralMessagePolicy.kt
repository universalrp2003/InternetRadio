package com.rpideas.secretmessage.preview

/** In-memory preview messages expire one minute after the local send action. */
internal const val PREVIEW_MESSAGE_TTL_MILLIS = 60_000L

internal data class PreviewPoint(
    val x: Float,
    val y: Float,
)

internal data class EphemeralPreviewMessage(
    val id: Long,
    val text: String?,
    val drawing: List<List<PreviewPoint>>,
    val expiresAtElapsedRealtime: Long,
)

internal object EphemeralMessagePolicy {
    fun expiresAt(sentAtElapsedRealtime: Long): Long =
        sentAtElapsedRealtime + PREVIEW_MESSAGE_TTL_MILLIS

    fun isExpired(nowElapsedRealtime: Long, expiresAtElapsedRealtime: Long): Boolean =
        nowElapsedRealtime >= expiresAtElapsedRealtime

    fun remainingSeconds(nowElapsedRealtime: Long, expiresAtElapsedRealtime: Long): Int {
        val remainingMillis = (expiresAtElapsedRealtime - nowElapsedRealtime).coerceAtLeast(0L)
        return ((remainingMillis + 999L) / 1_000L).toInt()
    }

    fun visibleMessages(
        messages: List<EphemeralPreviewMessage>,
        nowElapsedRealtime: Long,
    ): List<EphemeralPreviewMessage> =
        messages.filterNot { isExpired(nowElapsedRealtime, it.expiresAtElapsedRealtime) }
}
