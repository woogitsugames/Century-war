package com.centurywar.meta

/**
 * Most do rankingu globalnego. Domyślnie [Local] (tylko lokalne tablice w [Profile]).
 *
 * Adapter PGS v2 obsługuje cztery tablice: endless, daily, campaign i skirmish.
 * Identyfikatory w games_ids.xml muszą zostać skonfigurowane w Play Console.
 * Dzienne wyniki używają okna TIME_SPAN_DAILY, nie fizycznego resetu tablicy.
 *
 * Symulacja jest deterministyczna, więc własny backend może odtworzyć bitwę
 * z seeda + listy komend gracza i odrzucić sfałszowane wyniki (anty-cheat).
 */
/** Stan PGS odczytywany przez ekran ustawień. */
enum class OnlineState { OFFLINE, CHECKING, SIGNED_OUT, CONNECTED, SYNCING, ERROR }

interface OnlineLeaderboards {
    val available: Boolean
    val state: OnlineState get() = OnlineState.OFFLINE
    fun signIn() {}
    fun syncNow() {}
    fun submit(board: String, score: Long)
    fun showUi(board: String) {}

    object Local : OnlineLeaderboards {
        override val available = false
        override fun submit(board: String, score: Long) {}
    }
}
