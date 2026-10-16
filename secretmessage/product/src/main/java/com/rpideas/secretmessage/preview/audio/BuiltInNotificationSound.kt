package com.rpideas.secretmessage.preview.audio

/** Six original tones generated in code by [ToneSynthesizer]; no audio files are bundled. */
enum class BuiltInNotificationSound(val id: Int, val displayName: String, val subtitle: String) {
    CRYSTAL_PULSE(1, "Crystal Pulse", "Dual-harmonic 880 Hz and 1318 Hz bell chime"),
    CYBER_PING(2, "Cyber Ping", "Rising sweep from 1046 Hz to 1568 Hz"),
    ZEN_DROP(3, "Zen Drop", "Warm 528 Hz resonant drop"),
    STEALTH_TAP(4, "Stealth Double-Tap", "Low-profile twin pulse"),
    AURORA_CHORD(5, "Aurora Triad", "Ascending C6–E6–G6 arpeggio"),
    QUANTUM_SHIMMER(6, "Quantum Shimmer", "Gently modulated harmonic alert"),
    ;

    companion object {
        val DEFAULT: BuiltInNotificationSound = CRYSTAL_PULSE

        fun fromId(id: Int): BuiltInNotificationSound = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
