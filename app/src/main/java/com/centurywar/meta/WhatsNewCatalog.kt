package com.centurywar.meta

/** Opisy wydanych aktualizacji. Nową wersję dopisujemy razem z tekstami w L.part106(). */
object WhatsNewCatalog {
    data class Release(val version: String, val textKeys: List<String>)

    /** Najnowsze wydania na początku. Identyfikator musi zgadzać się z VERSION_NAME. */
    val releases: List<Release> = listOf(
        Release("0.1.0-dev", listOf(
            "wn_010_iron",
            "wn_010_replays",
            "wn_010_accessibility",
        )),
    )

    fun forVersion(version: String): Release? = releases.firstOrNull { it.version == version }

    /**
     * Nie przerywamy pierwszego uruchomienia. Wcześniejszy zapis bez znacznika
     * oznacza aktualizację ze starej gry, więc pokazujemy dostępne notatki raz.
     */
    fun shouldShow(existingProfile: Boolean, seenVersion: String, currentVersion: String): Boolean =
        existingProfile && seenVersion != currentVersion && forVersion(currentVersion) != null
}
