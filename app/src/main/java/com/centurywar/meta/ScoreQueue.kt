package com.centurywar.meta

/** Ograniczona kolejka; wszystkie metody woła jeden wątek roboczy. */
internal class ScoreQueue {
    data class Entry(val createdAt: Long, val payload: String, var attempts: Int = 0)
    val entries = ArrayDeque<Entry>()

    fun add(entry: Entry, nowMillis: Long) {
        prune(nowMillis)
        entries.addLast(entry)
        while (entries.size > MAX_ENTRIES) entries.removeFirst()
    }

    fun prune(nowMillis: Long) {
        while (entries.isNotEmpty() &&
            (entries.first().createdAt < nowMillis - MAX_AGE_MS ||
                entries.first().createdAt > nowMillis + 300_000L)
        ) entries.removeFirst()
    }

    fun clear() = entries.clear()

    companion object {
        const val MAX_ENTRIES = 20
        const val MAX_AGE_MS = 7L * 86_400_000L
    }
}
