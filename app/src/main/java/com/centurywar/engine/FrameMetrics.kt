package com.centurywar.engine

import java.util.Arrays

/** Ostatnie 120 klatek; zapis i odczyt bez alokacji w pętli. */
class FrameMetrics(private val window: Int = 120) {
    init { require(window > 0) }
    private val updates = LongArray(window)
    private val renders = LongArray(window)
    private val sorted = LongArray(window)
    private var position = 0
    private var count = 0
    private var sumUpdate = 0L
    private var sumRender = 0L

    var steps = 0
        private set
    var droppedSteps = 0
        private set
    var totalDroppedSteps = 0L
        private set

    fun add(updateNs: Long, renderNs: Long, simulated: Int, dropped: Int) {
        val u = updateNs.coerceAtLeast(0L)
        val r = renderNs.coerceAtLeast(0L)
        if (count == window) {
            sumUpdate -= updates[position]
            sumRender -= renders[position]
        } else count++
        updates[position] = u
        renders[position] = r
        sumUpdate += u
        sumRender += r
        position = (position + 1) % window
        steps = simulated
        droppedSteps = dropped
        totalDroppedSteps += dropped.toLong()
    }

    val avgUpdateMs: Float get() = if (count == 0) 0f else sumUpdate / count / 1_000_000f
    val avgRenderMs: Float get() = if (count == 0) 0f else sumRender / count / 1_000_000f
    val avgCpuMs: Float get() = avgUpdateMs + avgRenderMs
    val samples: Int get() = count

    /** Sortowana jest wyłącznie prealokowana kopia, na żądanie nakładki. */
    fun p95Ms(): Float {
        if (count == 0) return 0f
        for (i in 0 until count) sorted[i] = updates[i] + renders[i]
        Arrays.sort(sorted, 0, count)
        return sorted[((count * 95 + 99) / 100 - 1).coerceIn(0, count - 1)] / 1_000_000f
    }
}
