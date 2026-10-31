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

    // Pomiar SurfaceView kończy się po wysłaniu klatki, osobno od czasu CPU.
    private val postTimes = LongArray(window)
    private val intervals = LongArray(window)
    private var postPosition = 0
    private var postCount = 0
    private var sumPost = 0L
    private var sumInterval = 0L
    var refreshHz = 60f
        private set

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

    /** Czas od lockCanvas do unlockCanvasAndPost oraz rzeczywisty okres klatki. */
    fun addPresentation(lockPostNs: Long, frameIntervalNs: Long, displayHz: Float) {
        val p = lockPostNs.coerceAtLeast(0L)
        val i = frameIntervalNs.coerceAtLeast(0L)
        if (postCount == window) {
            sumPost -= postTimes[postPosition]
            sumInterval -= intervals[postPosition]
        } else postCount++
        postTimes[postPosition] = p
        intervals[postPosition] = i
        sumPost += p
        sumInterval += i
        postPosition = (postPosition + 1) % window
        if (displayHz.isFinite() && displayHz > 0f) refreshHz = displayHz
    }

    val avgPostMs: Float get() = if (postCount == 0) 0f else sumPost / postCount / 1_000_000f
    val avgFrameIntervalMs: Float get() = if (postCount == 0) 0f else sumInterval / postCount / 1_000_000f
    val presentationSamples: Int get() = postCount

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
