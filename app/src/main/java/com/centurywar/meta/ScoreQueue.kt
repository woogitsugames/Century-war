package com.centurywar.meta

/** Ograniczona kolejka; wszystkie metody woła jeden wątek roboczy. */
internal class ScoreQueue {
    data class Entry(val createdAt: Long, val payload: String, var attempts: Int = 0)
    val entries = ArrayDeque<Entry>()

    fun add(entry: Entry, nowMillis: Long) {
        entries.addLast(entry)
        prune(nowMillis)
        while (entries.size > MAX_ENTRIES) entries.removeFirst()
    }

    fun prune(nowMillis: Long) {
        // Kolejka nie musi być posortowana po czasie (zmiana zegara), więc sprawdzamy każdy wpis.
        entries.removeAll { it.createdAt < nowMillis - MAX_AGE_MS || it.createdAt > nowMillis + 300_000L }
    }

    fun clear() = entries.clear()

    companion object {
        const val MAX_ENTRIES = 20
        const val MAX_AGE_MS = 7L * 86_400_000L
    }
}
