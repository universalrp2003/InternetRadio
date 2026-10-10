package com.universalrp.cleansweep.data

/** TrafficStats fallback: observed deltas only, ALWAYS partial/reset-sensitive, never a carrier balance. */
data class MobileCounterState(
    val lastTotal: Long? = null,
    val packBytes: Long = 0,
    val todayBytes: Long = 0,
    val dayKey: String = "",
    val elapsedMs: Long = 0,
    val bootCount: Int? = null,
)

data class MobileCounterResult(val state: MobileCounterState, val packBytes: Long?, val todayBytes: Long?, val resetDetected: Boolean = false)

object MobileCounterPolicy {
    fun observe(state: MobileCounterState, total: Long?, dayKey: String, elapsedMs: Long, bootCount: Int?): MobileCounterResult {
        if (total == null || total < 0 || elapsedMs < 0) return MobileCounterResult(state, null, null)
        val reset = state.lastTotal != null && (total < state.lastTotal || elapsedMs < state.elapsedMs ||
            (bootCount != null && state.bootCount != null && bootCount != state.bootCount))
        val delta = if (state.lastTotal == null) 0L else if (reset) total else total - state.lastTotal
        val pack = add(state.packBytes.coerceAtLeast(0), delta)
        // A delta spanning midnight cannot honestly be assigned to the new day.
        val today = if (state.dayKey == dayKey) add(state.todayBytes.coerceAtLeast(0), delta) else 0L
        val next = MobileCounterState(total, pack, today, dayKey, elapsedMs, bootCount)
        return MobileCounterResult(next, pack, today, reset)
    }

    fun migrate(lastTotal: Long?, current: Long, packBaseline: Long, todayBaseline: Long,
                packCarried: Long?, todayCarried: Long?, oldDay: String, elapsedMs: Long = 0, bootCount: Int? = null): MobileCounterState {
        val previous = lastTotal ?: current
        return MobileCounterState(lastTotal,
            packCarried ?: (previous - packBaseline).coerceAtLeast(0),
            todayCarried ?: (previous - todayBaseline).coerceAtLeast(0), oldDay, elapsedMs, bootCount)
    }
    private fun add(old: Long, delta: Long) = if (old > Long.MAX_VALUE - delta) Long.MAX_VALUE else old + delta
}
