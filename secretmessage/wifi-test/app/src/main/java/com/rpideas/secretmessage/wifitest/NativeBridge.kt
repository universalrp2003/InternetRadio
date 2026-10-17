package com.rpideas.secretmessage.wifitest

/**
 * JNI entry points of the test-only mls-rs/AWS-LC library built from secretmessage/native-mls-rs.
 * Each call runs the Wi-Fi chat profile of the LAN session code, directly on the same Wi-Fi or over
 * the Internet relay's socket pair; android_jni.rs documents the replies. Keep these names in step with the `Java_com_rpideas_secretmessage_wifitest_NativeBridge_*`
 * exports (CI checks them against the built library).
 */
internal object NativeBridge {
    const val LIBRARY = "secretmessage_mls_spike"

    /** The library's reply when an answer reached a waiting comparison (android_jni.rs). */
    const val ANSWER_ACCEPTED = "Answer passed to the LAN test."

    @Volatile
    private var loaded = false

    /** Loads the library once; returns a message for the screen, or null when it is ready. */
    @Synchronized
    fun load(): String? {
        if (loaded) return null
        return try {
            System.loadLibrary(LIBRARY)
            loaded = true
            null
        } catch (failure: Throwable) {
            "The MLS engine could not be loaded (${failure.javaClass.simpleName}: ${failure.message.orEmpty()})."
        }
    }

    /** The MLS library and this build's protocol name. */
    external fun nativeEngineInfo(): String

    /** A new identity text for this device ID, or `FAIL: ...`. */
    external fun nativeGenerateIdentity(deviceId: String): String

    /** Hosts one chat; blocks until it ends and returns the session's status line. */
    external fun nativeRunHost(hostIp: String, port: Int, token: String, identity: String, pins: String): String

    /** Joins one chat; blocks until it ends and returns the session's status line. */
    external fun nativeRunJoin(hostIp: String, port: Int, token: String, identity: String, pins: String): String

    /**
     * Prepares one chat through the Internet relay: the app's end of a local socket pair, as a file
     * descriptor this app now owns (adopt it with ParcelFileDescriptor), or -1. The native session
     * keeps the other end for the next [nativeRunRelayHost] or [nativeRunRelayJoin].
     */
    external fun nativeOpenRelayPipe(): Int

    /** Hosts one chat over the prepared relay pipe once the other phone joined; blocks until it ends. */
    external fun nativeRunRelayHost(token: String, identity: String, pins: String): String

    /** Joins one chat over the prepared relay pipe once both phones are in; blocks until it ends. */
    external fun nativeRunRelayJoin(token: String, identity: String, pins: String): String

    /** Stops a host that is still waiting for the other phone; 1 if one was waiting. */
    external fun nativeCancelHostWait(): Int

    /** The comparison waiting for this phone's person as `code|status|device`, or "". */
    external fun nativeSafetyPrompt(): String

    /** Passes this phone's answer (1 = the codes match); [ANSWER_ACCEPTED] if it was used. */
    external fun nativeSubmitSafetyAnswer(codesMatch: Int): String

    /** The peer verified by the last confirmed session (`<device ID> <key hex>`), or "". */
    external fun nativeTakeVerifiedPeer(): String

    /** Queues one encoded payload for the open chat; `OK|<number>` or `FAIL: ...`. */
    external fun nativeChatSend(text: String): String

    /** The next chat event (`OPEN|…`, `RECEIVED|n|text`, `DELIVERED|n`, `ENDED|summary`), or "". */
    external fun nativeChatPoll(): String

    /** Asks the open chat to end on both phones. */
    external fun nativeChatEnd(): String
}
