package com.centurywar.testloop

/**
 * R11-04: rozkład odstępów między klatkami w pętli testowej (histogram co 0,25 ms do 250 ms,
 * dłuższe klatki w ostatnim przedziale; maksimum dokładne). Zapis bez alokacji w klatce.
 */
internal class FrameStats {
    private val bins = IntArray(BINS)
    var count = 0
        private set
    var maxMs = 0f
        private set

    fun add(intervalNs: Long) {
        val ms = intervalNs.coerceAtLeast(0L) / 1_000_000f
        val bin = (ms / BIN_MS).toInt().coerceIn(0, BINS - 1)
        bins[bin]++
        count++
        if (ms > maxMs) maxMs = ms
    }

    /** Górna granica przedziału, w którym leży percentyl [p] (0..100); 0 bez próbek. */
    fun percentileMs(p: Int): Float {
        if (count == 0) return 0f
        val rank = ((count.toLong() * p + 99) / 100).coerceIn(1L, count.toLong())
        var seen = 0L
        for (i in bins.indices) {
            seen += bins[i]
            if (seen >= rank) return minOf((i + 1) * BIN_MS, maxMs)
        }
        return maxMs
    }

    companion object {
        const val BIN_MS = 0.25f
        const val BINS = 1000
    }
}
