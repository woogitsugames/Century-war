package com.centurywar.engine

/**
 * Koszty uruchomienia od Application.onCreate do publikacji pierwszej klatki menu.
 * Odmierzanie jest rozdzielone między wątek UI, wątek I/O i wątek gry.
 * Nie zapisujemy niczego na dysku ani nie logujemy w pętli renderowania.
 */
internal class StartupTimings(private val startedNs: Long) {
    enum class Phase(val label: String) {
        APPLICATION("Application"),
        PLACEHOLDER("ekran startowy"),
        PROFILE("profil"),
        SNAPSHOT("zapis bitwy"),
        CRASH("raport awarii"),
        QUALITY("wykrywanie RAM/CPU"),
        LANGUAGES("tabela języków"),
        GAME("Game"),
        HISTORY("historia"),
        PLAY_GAMES("Play Games"),
        MUSIC("muzyka"),
        VIEW("SurfaceView")
    }

    private val elapsed = LongArray(Phase.entries.size)
    private val recorded = BooleanArray(Phase.entries.size)

    @Synchronized
    fun record(phase: Phase, fromNs: Long, toNs: Long) {
        val i = phase.ordinal
        elapsed[i] = (toNs - fromNs).coerceAtLeast(0L)
        recorded[i] = true
    }

    @Synchronized
    fun report(firstMenuFrameNs: Long): String {
        val total = (firstMenuFrameNs - startedNs).coerceAtLeast(0L)
        val result = StringBuilder("Pierwsza klatka menu: ")
            .append(ms(total)).append(" ms od Application.onCreate")
        for (phase in Phase.entries) {
            val i = phase.ordinal
            if (recorded[i]) result.append("; ").append(phase.label).append('=')
                .append(ms(elapsed[i])).append(" ms")
        }
        return result.toString()
    }

    private fun ms(ns: Long): String {
        val tenth = (ns + 50_000L) / 100_000L
        return "${tenth / 10}.${tenth % 10}"
    }
}
