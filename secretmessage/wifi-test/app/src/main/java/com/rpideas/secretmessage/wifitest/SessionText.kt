package com.rpideas.secretmessage.wifitest

/** Whether this phone has verified the other phone's key before, as the native session reports it. */
internal enum class PeerStatus {
    NEW,
    KNOWN,
    CHANGED,
    ;

    companion object {
        fun fromTag(tag: String): PeerStatus = when (tag) {
            "KNOWN" -> KNOWN
            "CHANGED" -> CHANGED
            else -> NEW
        }
    }
}

/** Remembered phones are kept as one `<device ID> <public key hex>` line each. */
internal object PeerPins {
    fun remember(pins: String, pin: String): String {
        val fields = pin.trim().split(' ')
        if (fields.size != 2 || fields.any { it.isEmpty() }) return pins
        val kept = pins.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith(fields[0] + " ") }
            .toList()
        return (kept + "${fields[0]} ${fields[1]}").joinToString(separator = "\n", postfix = "\n")
    }

    fun forget(pins: String, deviceId: String): String {
        val kept = pins.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("$deviceId ") }
            .toList()
        return if (kept.isEmpty()) "" else kept.joinToString(separator = "\n", postfix = "\n")
    }

    fun count(pins: String): Int = pins.lineSequence().count { it.isNotBlank() }

    /** `0123456789abcdef` as `0123 4567 89ab cdef`, as on the other phone's screen. */
    fun formatDeviceId(deviceId: String): String =
        if (deviceId.length != 16) deviceId else deviceId.chunked(4).joinToString(" ")
}

/** The safety-code prompt as the native session offers it: `code|NEW/KNOWN/CHANGED|device`. */
internal data class SafetyPrompt(val code: String, val peerStatus: PeerStatus, val peerDevice: String) {
    companion object {
        fun parse(text: String): SafetyPrompt? {
            val parts = text.split('|')
            if (parts.size != 3 || parts[0].isBlank()) return null
            return SafetyPrompt(parts[0], PeerStatus.fromTag(parts[1]), parts[2])
        }
    }
}

/** Short, plain results for the screen; the copied report keeps the native wording. */
internal object SessionText {
    private const val NATIVE_KEEP_OPEN_HINT = "Keep the test app open on both phones until the test ends"
    const val CONNECTION_HINT =
        "The other phone may have closed Secret Message or lost its connection (for example, by leaving the Wi-Fi " +
            "or losing mobile data)."

    /** The line shown after a session ends, from the native result and the chat's end summary. */
    fun lastResult(nativeResult: String, chatSummary: String?): String {
        if (chatSummary != null) return chatEnd(chatSummary)
        return when {
            nativeResult.startsWith("PASS") -> "The chat ended."
            nativeResult.startsWith("STOPPED: ") -> nativeResult.removePrefix("STOPPED: ")
                .replaceFirstChar { it.uppercaseChar() }
                .removeSuffix(" TEST ONLY.")
            nativeResult.contains("Stopped waiting for the other phone") -> "You stopped waiting for the other phone."
            nativeResult.contains("Timed out waiting for a LAN peer") ->
                "Nobody joined within 3 minutes. Start a new chat and send the new invite."
            nativeResult.contains("Could not connect to the local Wi-Fi test host") ->
                "Could not reach the first phone. Both phones must be on the same Wi-Fi (not a guest network), " +
                    "and the first phone must still be waiting. " + detail(nativeResult)
            nativeResult.startsWith("FAIL: ") -> withoutNativeHint(nativeResult.removePrefix("FAIL: "))
            else -> withoutNativeHint(nativeResult)
        }
    }

    /**
     * The native result as the copied report shows it. The native session shares its PASS wording
     * with the same-Wi-Fi smoke test, so a relay chat says where its messages really went, and the
     * smoke test's "TEST ONLY." tag is left out.
     */
    fun forReport(nativeResult: String, internet: Boolean): String {
        val placed = if (internet) {
            nativeResult.replace("directly over local Wi-Fi", "through the Internet relay")
        } else {
            nativeResult
        }
        return placed.removeSuffix(" TEST ONLY.").trim()
    }

    /** How a chat ended, from the native summary (`Ended on this phone. Sent …`). */
    fun chatEnd(summary: String): String = when {
        summary.startsWith("Ended on this phone") -> "You ended the chat."
        summary.startsWith("Ended on the other phone") -> "The other phone ended the chat."
        summary.startsWith("Ended at the") -> summary.substringBefore(" Sent ").replace("Ended at", "The chat ended at")
        summary.startsWith("Stopped. ") ->
            "The chat stopped: " + withoutNativeHint(summary.removePrefix("Stopped. ").substringBeforeLast(" Sent "))
        else -> summary.substringBefore(" Sent ")
    }

    private fun withoutNativeHint(text: String): String {
        val at = text.indexOf(NATIVE_KEEP_OPEN_HINT)
        return if (at < 0) text.trim() else (text.substring(0, at).trim() + " " + CONNECTION_HINT).trim()
    }

    private fun detail(nativeResult: String): String =
        "(" + nativeResult.substringAfter("host: ", nativeResult.removePrefix("FAIL: ")).trim() + ")"
}
