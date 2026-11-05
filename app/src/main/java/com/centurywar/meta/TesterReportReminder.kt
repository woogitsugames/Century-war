package com.centurywar.meta

/**
 * D48 (R13 TOP 5 poz. 3): przypomnienie o „Raporcie dla testera” w 3. i 10. dniu testu, tylko
 * w wersjach testowych (`BuildConfig.TESTER_SURVEY`, ten sam warunek co ankieta). Dzień liczony
 * od pierwszego dnia dziennika testu (kalendarz UTC, dzień startu = 1). Każdy próg raz: znacznik
 * w [PlaytestLog.reminders]. Spóźnione wejście (np. 12. dzień bez banera z 3.) pokazuje tylko
 * wyższy próg i oznacza oba. Baner jest nieblokujący i tylko w menu (nigdy nad bitwą ani na
 * wynikach), po ankiecie, banerach i oknach.
 */
object TesterReportReminder {
    const val DAY_FIRST = 3
    const val DAY_FINAL = 10
    /** Spokojne menu przez tyle sekund, zanim pojawi się baner (jak ankieta). */
    const val QUIET_SECONDS = 1.5f

    /** Próg dnia do pokazania ([DAY_FIRST] albo [DAY_FINAL]) albo 0, gdy nic. */
    fun due(enabled: Boolean, firstDay: Long, today: Long, shown: Int): Int {
        if (!enabled || firstDay < 0L || today < firstDay) return 0
        val day = today - firstDay + 1
        return when {
            day >= DAY_FINAL && shown and PlaytestLog.REMINDER_DAY10 == 0 -> DAY_FINAL
            day >= DAY_FIRST && day < DAY_FINAL && shown and PlaytestLog.REMINDER_DAY3 == 0 -> DAY_FIRST
            else -> 0
        }
    }

    /** Bity do zapisania po pokazaniu progu [threshold]. */
    fun mask(threshold: Int): Int = when (threshold) {
        DAY_FINAL -> PlaytestLog.REMINDER_DAY3 or PlaytestLog.REMINDER_DAY10
        DAY_FIRST -> PlaytestLog.REMINDER_DAY3
        else -> 0
    }

    const val FEEDBACK_NONE = 0
    const val FEEDBACK_EMAIL = 1
    const val FEEDBACK_URL = 2

    /**
     * Rodzaj adresu z `-PtesterFeedback` (`BuildConfig.TESTER_FEEDBACK`). Pusty albo nieznany
     * = [FEEDBACK_NONE]: jak dotąd, tester sam wybiera aplikację i odbiorcę.
     */
    fun feedbackKind(value: String): Int = when {
        value.startsWith("https://") && value.length > 8 && value.none { it.isWhitespace() } -> FEEDBACK_URL
        Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+").matches(value) -> FEEDBACK_EMAIL
        else -> FEEDBACK_NONE
    }
}
