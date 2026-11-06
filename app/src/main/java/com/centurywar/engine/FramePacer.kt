package com.centurywar.engine

/**
 * D61 (decyzja właściciela 6.11): poza bitwą (menu, Kodeks, Rekordy, mapa kampanii…) pętla gry
 * rysuje najwyżej [MENU_FPS] klatek na sekundę; bitwa i powtórka zostają bez zmian (tempo ekranu
 * albo limit gracza / trybu baterii). Animacje ekranów liczą czas z `dt`, więc limit nie zmienia
 * ich prędkości, tylko liczbę klatek na ekranach 90/120 Hz.
 *
 * Odstęp liczony jest od początku poprzedniej klatki do początku następnej, z dokładnością do
 * nanosekund (dawne `Thread.sleep(ms)` obcinało 16,67 ms do 16 ms, czyli ~62 kl./s).
 */
class FramePacer {
    /** Początek ostatniej klatki (ns); 0 = brak limitu w poprzedniej klatce. */
    private var lastStart = 0L

    /**
     * Wywoływane na końcu klatki, która zaczęła się w [frameStart]. Zwraca, ile nanosekund
     * odczekać przed następną klatką przy limicie [cap] (0 = bez limitu, bez czekania).
     */
    fun sleepNanos(frameStart: Long, now: Long, cap: Int): Long {
        if (cap <= 0) { lastStart = 0L; return 0L }
        lastStart = frameStart
        // Zaokrąglenie w górę: 60 × 16 666 667 ns > 1 s, więc w żadnej sekundzie nie ma 61. klatki.
        val wait = frameStart + (1_000_000_000L + cap - 1) / cap - now
        return if (wait > 0L) wait else 0L
    }

    companion object {
        const val MENU_FPS = 60

        /**
         * Limit dla bieżącego ekranu: w bitwie [userCap] bez zmian (0 = tempo ekranu),
         * poza nią najwyżej [MENU_FPS], a niższy wybór gracza lub tryb baterii (30) obowiązuje dalej.
         */
        fun capFor(userCap: Int, battle: Boolean): Int =
            if (battle) userCap else if (userCap in 1..MENU_FPS) userCap else MENU_FPS

        /** Usypia wątek na [nanos] (co najmniej tyle); przerwanie (stopThread) kończy czekanie. */
        fun sleep(nanos: Long) {
            if (nanos <= 0L) return
            try { Thread.sleep(nanos / 1_000_000L, (nanos % 1_000_000L).toInt()) } catch (_: InterruptedException) { }
        }
    }
}
