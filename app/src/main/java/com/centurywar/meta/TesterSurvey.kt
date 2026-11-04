package com.centurywar.meta

/**
 * R11 (pozycja 7): jednorazowa ankieta testera w menu, tylko w wersjach testowych
 * (`BuildConfig.TESTER_SURVEY`). Odpowiedź zostaje w [PlaytestLog] i trafia wyłącznie do
 * „Raportu dla testera”, który gracz udostępnia sam.
 *
 * Próg: samouczek ukończony i co najmniej [MIN_BATTLES] bitew albo [MIN_DAYS]. dzień gry.
 * Pięć bitew to ok. 20–30 minut (samouczek i pierwsze etapy kampanii albo potyczki), czyli
 * tyle, by mieć zdanie o pierwszej godzinie, ale zanim zmęczony tester odpadnie. Trzeci dzień
 * łapie testerów, którzy grają krótko codziennie (test zamknięty trwa 14 dni), i nadal
 * zostawia czas na zmiany przed końcem testu.
 */
object TesterSurvey {
    const val MIN_BATTLES = 5
    const val MIN_DAYS = 3
    /** Spokojne menu (bez banerów i okien) przez tyle sekund, zanim pojawi się pytanie. */
    const val QUIET_SECONDS = 1.5f
    const val ISSUES = 4

    fun due(enabled: Boolean, state: Int, tutorialDone: Boolean, battles: Int, daysPlayed: Int): Boolean =
        enabled && state == PlaytestLog.SURVEY_NONE && tutorialDone &&
            (battles >= MIN_BATTLES || daysPlayed >= MIN_DAYS)
}
