package com.centurywar.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/** Koszt samej syntezy na osobnym wątku. Nie zastępuje pomiaru AudioTrack na telefonie. */
class MusicThreadPerformanceTest {
    @Test fun pcm2048FitsAudioThreadBudget() {
        val result = AtomicReference<Pair<Double, Double>>()
        val failure = AtomicReference<Throwable>()
        val worker = Thread({
            try {
                val synth = MusicSynth(77)
                synth.setEra(4)
                synth.setIntensity(0.8f)
                synth.skipFade()
                val buffer = ShortArray(2048)
                repeat(150) { synth.renderPcm(buffer) }
                val durations = LongArray(600)
                var total = 0L
                for (i in durations.indices) {
                    val start = System.nanoTime()
                    synth.renderPcm(buffer)
                    val elapsed = System.nanoTime() - start
                    durations[i] = elapsed
                    total += elapsed
                }
                durations.sort()
                result.set(Pair(total / 600_000_000.0, durations[569] / 1_000_000.0))
            } catch (e: Throwable) {
                failure.set(e)
            }
        }, "music-perf77")
        worker.start()
        worker.join()
        failure.get()?.let { throw AssertionError("Synteza na wątku audio", it) }
        val (avgMs, p95Ms) = result.get() ?: throw AssertionError("Brak wyniku syntezy")
        val budgetMs = 2048.0 / MusicSynth.SAMPLE_RATE * 1000.0
        println("PERF77 PCM2048: avg=${String.format(Locale.ROOT, "%.3f", avgMs)} ms, " +
            "p95=${String.format(Locale.ROOT, "%.3f", p95Ms)} ms; bufor=${String.format(Locale.ROOT, "%.2f", budgetMs)} ms")
        // Każda porcja musi zostać obliczona przed zużyciem próbek przez AudioTrack.
        assertTrue("PCM p95=$p95Ms ms >= bufor $budgetMs ms", p95Ms < budgetMs)
        assertTrue("PCM średnia=$avgMs ms >= połowa bufora", avgMs < budgetMs * 0.5)
    }
}
