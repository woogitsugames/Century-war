package com.centurywar.engine

/** Jedna oczekująca próbka na identyfikator, bez alokacji podczas odtwarzania. */
internal class PendingSfx(count: Int) {
    companion object { const val WINDOW_MS = 150L }

    private val queuedAt = LongArray(count) { -1L }
    private val volumes = FloatArray(count)
    private val gaps = LongArray(count)
    private val pitches = FloatArray(count)
    private val pans = FloatArray(count)
    private val priorities = IntArray(count)

    /** Przy przepełnieniu zostaje nowsze zdarzenie o nie niższym priorytecie. */
    fun offer(id: Int, volume: Float, gap: Long, pitch: Float, pan: Float, priority: Int, now: Long) {
        val previous = queuedAt[id]
        if (previous >= 0L && now >= previous && now - previous < WINDOW_MS &&
            priority < priorities[id]) return
        queuedAt[id] = now
        volumes[id] = volume
        gaps[id] = gap
        pitches[id] = pitch
        pans[id] = pan
        priorities[id] = priority
    }

    /** Jednorazowe pobranie, także gdy próbka jest już przeterminowana. */
    fun pop(id: Int, now: Long): Boolean {
        val at = queuedAt[id]
        queuedAt[id] = -1L
        return at >= 0L && now >= at && now - at < WINDOW_MS
    }

    fun clear(id: Int) { queuedAt[id] = -1L }
    fun volume(id: Int) = volumes[id]
    fun gap(id: Int) = gaps[id]
    fun pitch(id: Int) = pitches[id]
    fun pan(id: Int) = pans[id]
    fun priority(id: Int) = priorities[id]
}
