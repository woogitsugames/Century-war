package com.centurywar.meta

/**
 * Opisy wydanych aktualizacji. Nową wersję dopisujemy razem z tekstami w L.part106().
 *
 * Katalog jest kluczowany linią wydania „major.minor” (np. "0.1"), a nie pełnym
 * versionName: w wydaniu versionName pochodzi z tagu (v0.1.3 -> "0.1.3"), więc
 * dokładne dopasowanie nigdy by nie trafiło i notatki by się nie pokazały.
 * Poprawki (patch) i przyrostki (-dev, -rc1, +build) należą do tej samej linii.
 */
object WhatsNewCatalog {
    data class Release(val version: String, val textKeys: List<String>)

    private val LINE = Regex("""^v?(\d+)\.(\d+)(?:[.\-+].*)?$""")

    /** Najnowsze wydania na początku. Identyfikator to „major.minor” (notesKey(versionName)). */
    val releases: List<Release> = listOf(
        Release("0.1", listOf(
            "wn_010_iron",
            "wn_010_replays",
            "wn_010_accessibility",
        )),
    )

    /** "0.1.3" -> "0.1", "v1.2.0-rc1" -> "1.2"; nieznany format -> null. Tylko poza klatką. */
    fun notesKey(versionName: String): String? {
        val m = LINE.matchEntire(versionName.trim()) ?: return null
        val major = m.groupValues[1].toIntOrNull() ?: return null
        val minor = m.groupValues[2].toIntOrNull() ?: return null
        return "$major.$minor"
    }

    fun forVersion(version: String): Release? {
        val key = notesKey(version) ?: return null
        return releases.firstOrNull { it.version == key }
    }

    /**
     * Nie przerywamy pierwszego uruchomienia. Wcześniejszy zapis bez znacznika
     * oznacza aktualizację ze starej gry, więc pokazujemy dostępne notatki raz.
     * Gracz, który widział notatki linii 0.1 (np. przy 0.1.0), nie zobaczy ich
     * ponownie po poprawce 0.1.1.
     */
    fun shouldShow(existingProfile: Boolean, seenVersion: String, currentVersion: String): Boolean {
        if (!existingProfile) return false
        val current = notesKey(currentVersion) ?: return false
        if (releases.none { it.version == current }) return false
        return notesKey(seenVersion) != current
    }
}
