package com.centurywar.engine

/**
 * R10-04: tryb gry Androida (GameManager, API 31+; Ustawienia gry / Game Dashboard).
 * Wartości jak `GameManager.GAME_MODE_*`, żeby logika była testowalna bez systemu.
 * Tryb jest lokalny: nie trafia do profilu ani do chmury.
 */
object GameModes {
    const val UNSUPPORTED = 0
    const val STANDARD = 1
    const val PERFORMANCE = 2
    const val BATTERY = 3
    /** API 34: własne ustawienia producenta; traktujemy jak standardowy. */
    const val CUSTOM = 4

    /** Limit klatek przy oszczędzaniu baterii. */
    const val BATTERY_FPS = 30

    /** Efektywny limit klatek: wybór gracza, a w trybie baterii najwyżej 30 (0 = ekran). */
    fun effectiveFps(profileCap: Int, gameMode: Int): Int =
        if (gameMode == BATTERY && (profileCap == 0 || profileCap > BATTERY_FPS)) BATTERY_FPS
        else profileCap

    /** Efektywna jakość: w trybie baterii nie wyżej niż średnia (także przy ręcznym wyborze). */
    fun effectiveQuality(level: Int, gameMode: Int): Int =
        if (gameMode == BATTERY) minOf(level, Quality.MED) else level

    /** Stała etykieta do raportu testera (po angielsku, bez tłumaczeń). */
    fun reportName(gameMode: Int): String = when (gameMode) {
        STANDARD, CUSTOM -> "standard"
        BATTERY -> "battery"
        PERFORMANCE -> "performance"
        else -> "unsupported"
    }
}
