package com.centurywar.server

import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

/** Liczniki procesu; liczba zapisanych wyników jest zawsze odczytywana z SQLite. */
internal class ServerMetrics(
    private val startedNanos: Long = System.nanoTime(),
) {
    private val accepted = LongAdder()
    private val rejected = LongAdder()
    private val busy = LongAdder()
    private val samples = LongAdder()
    private val totalNanos = LongAdder()
    private val maximumNanos = AtomicLong()

    fun verificationDuration(nanos: Long) {
        val elapsed = nanos.coerceAtLeast(0L)
        samples.increment()
        totalNanos.add(elapsed)
        maximumNanos.updateAndGet { maxOf(it, elapsed) }
    }

    fun verified() { accepted.increment() }
    fun invalidReplay() { rejected.increment() }
    fun unavailable() { busy.increment() }

    fun snapshot(scoreCount: Long): ServerMetricsSnapshot {
        val count = samples.sum()
        return ServerMetricsSnapshot(
            scoreCount = scoreCount,
            verificationAccepted = accepted.sum(),
            verificationRejected = rejected.sum(),
            verificationUnavailable = busy.sum(),
            verificationSamples = count,
            verificationMeanMs = if (count == 0L) 0.0 else totalNanos.sum().toDouble() / count / 1_000_000.0,
            verificationMaxMs = maximumNanos.get() / 1_000_000.0,
            uptimeSeconds = ((System.nanoTime() - startedNanos).coerceAtLeast(0L) / 1_000_000_000L),
        )
    }
}

@Serializable
internal data class ServerMetricsSnapshot(
    val scoreCount: Long,
    val verificationAccepted: Long,
    val verificationRejected: Long,
    val verificationUnavailable: Long,
    val verificationSamples: Long,
    val verificationMeanMs: Double,
    val verificationMaxMs: Double,
    val uptimeSeconds: Long,
)
