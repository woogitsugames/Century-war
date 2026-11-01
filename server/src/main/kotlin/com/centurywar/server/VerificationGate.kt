package com.centurywar.server

import com.centurywar.game.Replay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/** Przekroczenie budżetu weryfikacji: serwer odrzuca ją bez zapisywania wyniku. */
class VerificationBudgetExceeded : RuntimeException("verification_budget_exceeded")

/** Zegar monotoniczny jest używany tylko na serwerze, nigdy w symulacji. */
class VerificationBudget internal constructor(
    val maxReplaySteps: Int,
    timeoutMillis: Long,
) {
    private val startedAt = System.nanoTime()
    private val durationNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)

    internal fun remainingNanos(): Long =
        (durationNanos - (System.nanoTime() - startedAt)).coerceAtLeast(0L)

    fun check() {
        if (Thread.currentThread().isInterrupted || remainingNanos() == 0L) {
            throw VerificationBudgetExceeded()
        }
    }
}

/**
 * Stała pula CPU i ograniczona kolejka. Future.get pozwala zwrócić 503 także wtedy,
 * gdy kod zadania nie zawiesza korutyny. Anulowanie przerywa wątek, a kontrola
 * budżetu w pętli Replay kończy obliczenia przy najbliższym punkcie kontrolnym.
 */
class VerificationGate(
    parallel: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 8),
    waiting: Int = 16,
    private val timeoutMillis: Long = 15_000L,
    private val maxReplaySteps: Int = Replay.MAX_STEPS,
) : AutoCloseable {
    init {
        require(parallel > 0 && waiting >= 0 && timeoutMillis > 0L)
        require(maxReplaySteps in 60..Replay.MAX_STEPS)
    }

    private val nextThread = AtomicInteger()
    private val pool = ThreadPoolExecutor(
        parallel, parallel, 30L, TimeUnit.SECONDS,
        if (waiting == 0) SynchronousQueue() else ArrayBlockingQueue(waiting),
        { task ->
            Thread(task, "cw-replay-${nextThread.incrementAndGet()}").apply { isDaemon = true }
        },
        ThreadPoolExecutor.AbortPolicy(),
    ).apply { allowCoreThreadTimeOut(true) }

    /** null = pełna kolejka; wynik błędny = timeout lub błąd weryfikatora. */
    suspend fun <T> run(block: (VerificationBudget) -> T): Result<T>? {
        val budget = VerificationBudget(maxReplaySteps, timeoutMillis)
        val task = FutureTask {
            budget.check()
            block(budget).also { budget.check() }
        }
        try {
            pool.execute(task)
        } catch (_: RejectedExecutionException) {
            return null
        }

        try {
            return withContext(Dispatchers.IO) {
                try {
                    Result.success(task.get(budget.remainingNanos(), TimeUnit.NANOSECONDS))
                } catch (_: TimeoutException) {
                    Result.failure(VerificationBudgetExceeded())
                } catch (e: ExecutionException) {
                    Result.failure(e.cause ?: e)
                } catch (_: CancellationException) {
                    Result.failure(VerificationBudgetExceeded())
                }
            }
        } finally {
            if (!task.isDone) task.cancel(true)
            if (task.isCancelled) pool.remove(task)
        }
    }

    override fun close() {
        pool.shutdownNow()
    }
}
