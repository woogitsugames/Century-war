package com.centurywar.crash

/**
 * R10-04 L1: ślad ANR czytamy w tle po pierwszym ekranie. Pytanie o raport z tego odczytu
 * pokazujemy od razu tylko poza bitwą; w bitwie czeka na najbliższe menu. Najwyżej jedno
 * pytanie na uruchomienie (kolejne awarie zostają w dzienniku i wrócą przy następnym starcie).
 */
class CrashOfferGate {
    private var offered = false
    private var deferred: String? = null

    /** Pytanie o awarię Javy z poprzedniej sesji pokazane przy starcie (jak dotąd). */
    @Synchronized
    fun offeredAtStartup() { offered = true }

    /**
     * Koniec zbierania przyczyn zakończenia. Zwraca nazwę raportu do pokazania teraz albo
     * null (brak, już pytaliśmy, albo trwa bitwa i pytanie czeka na menu).
     */
    @Synchronized
    fun collected(name: String?, inBattle: Boolean): String? {
        if (name == null || offered) return null
        if (inBattle) { deferred = name; return null }
        offered = true
        return name
    }

    /** Wejście do menu: odłożone pytanie (raz). */
    @Synchronized
    fun takeAtMenu(): String? {
        val name = deferred ?: return null
        deferred = null
        offered = true
        return name
    }

    @get:Synchronized
    val hasDeferred: Boolean get() = deferred != null
}
