package com.centurywar.crash

/**
 * Stały bufor 30 zdarzeń. add() modyfikuje wyłącznie już zaalokowane tablice:
 * brak StringBuilder, kolekcji i zapisu na dysk w ścieżce zdarzenia.
 */
class CrashEventRing(private val capacity: Int = 30) {
    companion object {
        const val START = 1
        const val SCREEN = 2
        const val BATTLE = 3
        const val PAUSE = 4
        const val RESUME = 5
        const val FRAME_ERROR = 6
    }

    init { require(capacity in 1..30) }

    private val kinds = IntArray(capacity)
    private val values = IntArray(capacity)
    private val names = arrayOfNulls<String>(capacity)
    private val times = LongArray(capacity)
    private var next = 0
    private var count = 0

    @Synchronized
    fun add(kind: Int, value: Int = 0, screen: String? = null) {
        kinds[next] = kind
        values[next] = value
        names[next] = screen
        times[next] = System.nanoTime() / 1_000_000L
        next = (next + 1) % capacity
        if (count < capacity) count++
    }

    /** Alokacje tylko przy budowaniu raportu, nigdy podczas rejestrowania zdarzenia. */
    @Synchronized
    fun snapshot(): String {
        val out = StringBuilder()
        val now = System.nanoTime() / 1_000_000L
        for (i in 0 until count) {
            val at = (next - count + i + capacity) % capacity
            out.append("- ").append((now - times[at]).coerceAtLeast(0L)).append(" ms temu: ")
            when (kinds[at]) {
                START -> out.append("uruchomienie aplikacji")
                SCREEN -> out.append("ekran ").append(names[at] ?: "nieznany")
                BATTLE -> {
                    out.append("start bitwy")
                    if (values[at] > 0) out.append(" - etap ").append(values[at])
                }
                PAUSE -> out.append("przejście w tło")
                RESUME -> out.append("powrót do gry")
                FRAME_ERROR -> out.append("wyjątek w pętli gry")
                else -> out.append("zdarzenie techniczne")
            }
            out.append('\n')
        }
        return out.toString()
    }
}
