package com.centurywar.build

import java.net.URI

/** Dane wejściowe listy kontrolnej, niezależne od Gradle i Androida. */
data class ReleaseChecklistInput(
    val releaseDay: Long?,
    val todayUtcDay: Long,
    val playGamesEnabled: Boolean,
    val gamesIdsXml: String,
    val serverUrl: String,
    val privacyUrl: String,
    val hasHmacKey: Boolean,
    val versionCode: Int,
    val lastVersionCode: Int?,
    val skipChecks: Set<String> = emptySet(),
)

/** Reguły publikacji uruchamiane przez zadanie :app:releaseChecklist. */
object ReleaseChecklist {
    val allowedSkips = setOf(
        "releaseDay", "playGames", "serverUrl", "privacyUrl", "hmacKey", "versionCode",
    )

    /** Wiele wyjątków można podać po przecinku; nieznana flaga to błąd. */
    fun parseSkips(value: String): Set<String> {
        val skips = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        validateSkips(skips)
        return skips
    }

    /** Wartość pochodzi wyłącznie z Daily.RELEASE_DAY w źródle symulacji. */
    fun releaseDayFromSource(source: String): Long? =
        Regex("""(?m)^\s*const\s+val\s+RELEASE_DAY\s*=\s*([0-9]+)L?\b""")
            .find(source)?.groupValues?.get(1)?.toLongOrNull()

    fun problems(input: ReleaseChecklistInput): List<String> {
        validateSkips(input.skipChecks)
        val failures = mutableListOf<String>()
        val skips = input.skipChecks

        if ("releaseDay" !in skips) {
            val day = input.releaseDay
            if (day == null || day <= 0L) {
                failures += "Nie można odczytać dodatniej stałej Daily.RELEASE_DAY z Modes.kt."
            } else if (day < input.todayUtcDay - 30L || day > input.todayUtcDay + 60L) {
                failures += "Daily.RELEASE_DAY=$day poza zakresem: od 30 dni temu do 60 dni naprzód (UTC)."
            }
        }
        if ("playGames" !in skips && input.playGamesEnabled &&
            input.gamesIdsXml.contains("REPLACE_ME")
        ) {
            failures += "Play Games jest włączony, a games_ids.xml nadal zawiera REPLACE_ME."
        }
        if ("serverUrl" !in skips && !isHttpsUrl(input.serverUrl)) {
            failures += "CW_SERVER_URL musi być poprawnym adresem https://."
        }
        if ("privacyUrl" !in skips && !isHttpsUrl(input.privacyUrl)) {
            failures += "CW_PRIVACY_URL musi być poprawnym adresem https://."
        }
        if ("hmacKey" !in skips && !input.hasHmacKey) {
            failures += "Brak CW_HMAC_KEY (klucza podpisu wyników)."
        }
        if ("versionCode" !in skips) {
            val previous = input.lastVersionCode
            if (previous == null || previous < 0) {
                failures += "Brak poprawnego numeru w release/last_version_code.txt."
            } else if (input.versionCode <= previous) {
                failures += "releaseVersionCode=${input.versionCode} musi być większy od ostatniego wydanego $previous."
            }
        }
        return failures
    }

    private fun validateSkips(skips: Set<String>) {
        val unknown = skips - allowedSkips
        require(unknown.isEmpty()) {
            "Nieznane wartości -PskipCheck: ${unknown.sorted().joinToString(", ")}. " +
                "Dozwolone: ${allowedSkips.sorted().joinToString(", ")}"
        }
    }

    private fun isHttpsUrl(value: String): Boolean = runCatching {
        val uri = URI(value.trim())
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}
