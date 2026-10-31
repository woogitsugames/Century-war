package com.centurywar.meta

/**
 * Most do rankingu globalnego. Domyślnie [Local] (tylko lokalne tablice w [Profile]).
 *
 * Integracja z Google Play Games Services v2 (Leaderboards):
 *  1. Utwórz grę w Play Console → Play Games Services, dodaj 3 tablice
 *     (endless, daily, campaign) i wpisz ich ID do implementacji tego interfejsu.
 *  2. Dodaj zależność `com.google.android.gms:play-services-games-v2`
 *     i meta-data `com.google.android.gms.games.APP_ID` w manifeście.
 *  3. `PlayGames.getLeaderboardsClient(activity).submitScore(id, score)`.
 * Tablica dzienna: osobny leaderboard z resetem dziennym (PGS obsługuje okna
 * daily/weekly/all-time), a seed jest wspólny dla wszystkich ([com.centurywar.game.Daily]).
 *
 * Symulacja jest deterministyczna, więc własny backend może odtworzyć bitwę
 * z seeda + listy komend gracza i odrzucić sfałszowane wyniki (anty-cheat).
 */
interface OnlineLeaderboards {
    val available: Boolean
    fun submit(board: String, score: Long)
    fun showUi(board: String) {}

    object Local : OnlineLeaderboards {
        override val available = false
        override fun submit(board: String, score: Long) {}
    }
}
