package com.centurywar.game

/** Wspólna wersja protokołu klienta i serwera. Zmiany symulacji wersjonuje koordynator. */
object SimVersion {
    const val PROTOCOL = "0.8.12"

    /**
     * Formaty [Replay] przyjmowane do rankingu wersji zasad [PROTOCOL] (A156-04).
     * Tylko bieżący format: v5 wyłącza stopnie wież i dowódcę, v6 wyłącza dowódcę,
     * więc ich symulacja ma inne zasady niż ranking 0.8.12. Starsze formaty zostają
     * wyłącznie do lokalnego odtwarzania nagrań. Zmiana [Replay.VERSION] wymaga
     * świadomej decyzji tutaj (pilnuje tego test RankedReplayFormatsTest).
     */
    val RANKED_REPLAY_FORMATS: Set<Int> = setOf(7)
}
