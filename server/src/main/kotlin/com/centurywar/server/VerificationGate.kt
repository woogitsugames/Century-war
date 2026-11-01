package com.centurywar.server

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

/**
 * Dwa aktywne obliczenia i co najwyżej 16 oczekujących. Żaden klient nie może
 * wypełnić nieograniczonej kolejki zadań CPU.
 */
class VerificationGate(
    parallel: Int = 2,
    waiting: Int = 16,
    private val timeoutMillis: Long = 30_000L,
) {
    init { require(parallel > 0 && waiting >= 0 && timeoutMillis > 0L) }
    private val workerSlots = Semaphore(parallel)
    private val capacity = Semaphore(parallel + waiting)

    /** null oznacza pełną kolejkę; Result.failure obejmuje również timeout. */
    suspend fun <T> run(block: suspend () -> T): Result<T>? {
        if (!capacity.tryAcquire()) return null
        try {
            return runCatching { withTimeout(timeoutMillis) { workerSlots.withPermit { block() } } }
        } finally {
            capacity.release()
        }
    }
}
