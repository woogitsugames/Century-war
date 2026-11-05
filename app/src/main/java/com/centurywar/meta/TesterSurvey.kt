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

    /**
     * D57 (R13 TOP 5 poz. 5): ankieta końcowa od 10. dnia testu (dzień liczony jak w
     * [TesterReportReminder], dzień startu = 1), raz, tylko w wersjach testowych. Pokazuje ją
     * baner 10. dnia (jeden baner: krótkie pytania, potem Raport dla testera); [reminders] z bitem
     * [PlaytestLog.REMINDER_FINAL] = baner już był. Gdy pierwsza ankieta była tego samego dnia
     * ([firstSurveyDay] == [today]), czekamy do następnego dnia (bez dwóch ankiet naraz).
     */
    fun finalDue(enabled: Boolean, finalState: Int, firstDay: Long, today: Long, reminders: Int,
                 firstSurveyDay: Long): Boolean =
        finalPending(enabled, finalState, firstDay, today, reminders) && firstSurveyDay != today

    /** Ankieta końcowa czeka (10. dzień, bez odpowiedzi, baner jeszcze nie pokazany). */
    fun finalPending(enabled: Boolean, finalState: Int, firstDay: Long, today: Long, reminders: Int): Boolean =
        enabled && finalState == PlaytestLog.FINAL_NONE && reminders and PlaytestLog.REMINDER_FINAL == 0 &&
            firstDay >= 0L && today >= firstDay && today - firstDay + 1 >= TesterReportReminder.DAY_FINAL

    /** Liczba odpowiedzi w pytaniach z listy ankiety końcowej (układ przycisków). */
    const val FINAL_OPTIONS = 6
}
