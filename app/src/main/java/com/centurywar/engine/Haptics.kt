package com.centurywar.engine

/** Rodzaje krótkich impulsów, od dotknięcia po finał bitwy. */
enum class HapticKind(val priority: Int) {
    TAP(0),
    HEAVY_HIT(4),
    BARRAGE(2),
    EVOLVE(3),
    VICTORY(6),
    DEFEAT(6),
    BOSS(5),
}

/** Limit wibracji niezależny od Androida; silniejszy sygnał może przerwać słabszy. */
class HapticGate(private val intervalMs: Long = 120L) {
    private var lastMs = Long.MIN_VALUE
    private var lastPriority = -1

    fun allow(kind: HapticKind, nowMs: Long): Boolean {
        if (lastMs != Long.MIN_VALUE && nowMs >= lastMs &&
            nowMs - lastMs < intervalMs && kind.priority <= lastPriority) return false
        lastMs = nowMs
        lastPriority = kind.priority
        return true
    }

    fun reset() {
        lastMs = Long.MIN_VALUE
        lastPriority = -1
    }
}
