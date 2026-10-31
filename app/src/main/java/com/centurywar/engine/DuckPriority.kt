package com.centurywar.engine

/** Priorytet i czas muzycznego duckingu; poniżej 1 oznacza silniejsze wyciszenie. */
internal class DuckPriority {
    var amount = 1f
        private set
    private var holdUntilMs = 0L

    /** Pozostały czas bieżącego wyciszenia, liczony zegarem monotonicznym. */
    fun remainingSeconds(nowMs: Long): Float =
        (holdUntilMs - nowMs).coerceAtLeast(0L) / 1000f

    /** Zwraca true tylko wtedy, gdy syntezator musi dostać nowe polecenie. */
    fun request(rawAmount: Float, rawSeconds: Float, nowMs: Long): Boolean {
        val nextAmount = if (rawAmount.isFinite()) rawAmount.coerceIn(0.12f, 1f) else 1f
        val seconds = if (rawSeconds.isFinite()) rawSeconds.coerceIn(0f, 12f) else 0f
        if (seconds <= 0f || nextAmount >= 1f) return false
        val until = nowMs + (seconds * 1000f).toLong().coerceAtLeast(1L)
        if (nowMs >= holdUntilMs) {
            amount = nextAmount
            holdUntilMs = until
            return true
        }
        if (nextAmount > amount) return false
        val longer = maxOf(holdUntilMs, until)
        if (nextAmount == amount && longer == holdUntilMs) return false
        amount = nextAmount
        holdUntilMs = longer
        return true
    }
}
