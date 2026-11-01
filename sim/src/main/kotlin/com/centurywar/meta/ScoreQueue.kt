package com.centurywar.meta

/** Ograniczona kolejka; wszystkie metody woła jeden wątek roboczy. */
class ScoreQueue {
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

    /**
     * Po błędzie przejściowym zachowuje pierwsze zgłoszenie i zwraca czas do ponowienia.
     * Pozostałe odpowiedzi usuwają zgłoszenie, tak jak dotychczas w ScoreClient.
     */
    fun recordResponse(code: Int): Long? {
        val item = entries.firstOrNull() ?: return null
        if (code == -1 || code == 408 || code == 429 || code in 500..599) {
            item.attempts++
            return (2_000L * (1L shl item.attempts.coerceIn(0, 11))).coerceAtMost(3_600_000L)
        }
        entries.removeFirst()
        return null
    }

    fun clear() = entries.clear()

    companion object {
        const val MAX_ENTRIES = 20
        const val MAX_AGE_MS = 7L * 86_400_000L
    }
}
