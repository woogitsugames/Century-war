package com.centurywar.build

import java.net.URI
import java.time.LocalDate

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
    val hasReleaseSigning: Boolean = true,
    val ciTestDebugSigning: Boolean = false,
    val enforceStoreDocuments: Boolean = false,
    val storeDocuments: Map<String, String> = emptyMap(),
    val ownerInputText: String? = null,
)

/** Reguły publikacji uruchamiane przez zadanie :app:releaseChecklist. */
object ReleaseChecklist {
    val allowedSkips = setOf(
        "releaseDay", "playGames", "serverUrl", "privacyUrl", "hmacKey", "versionCode", "ownerInput",
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
        if (!input.hasReleaseSigning && !input.ciTestDebugSigning) {
            failures += "Brak prawidłowego podpisu release: wymagany upload JKS i cztery zmienne RELEASE_*."
        }
        if (input.enforceStoreDocuments) {
            failures += storeDocumentProblems(input.storeDocuments)
            if ("ownerInput" !in skips) failures += ownerInputProblems(input.ownerInputText)
        }

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


    private val requiredDocs = setOf(
        "privacy_policy_pl.md", "privacy_policy_en.md", "content_rating.md", "data_safety.md",
    )
    private val ownerKeys = listOf(
        "publisher_name", "contact_email", "postal_address",
        "privacy_policy_url", "release_date", "play_games_project_id",
    )
    private val forbiddenMarker = Regex(
        """\[(?:INSERT[^\]]*|DO DECYZJI[^\]]*|UZUPEŁNIJ[^\]]*)\]""",
        RegexOption.IGNORE_CASE,
    )
    private val ownerToken = Regex("""\{\{OWNER\.([a-z_]+)\}\}""")

    /** Testowy podpis debug tylko w jawnie oznaczonym workflow CI. */
    fun canUseCiDebugSigning(
        flag: Boolean, githubActions: String?, eventName: String?, smokeMarker: String?,
    ): Boolean = flag && githubActions == "true" && smokeMarker == "true" &&
        eventName in setOf("pull_request", "workflow_dispatch")

    /** Pliki sklepu: żadnych nierozstrzygniętych znaczników poza danymi właściciela. */
    fun storeDocumentProblems(documents: Map<String, String>): List<String> {
        val errors = mutableListOf<String>()
        for (name in requiredDocs.sorted()) {
            if (documents[name].isNullOrBlank()) errors += "Brak docs/store/" + name + "."
        }
        for ((name, content) in documents.toSortedMap()) {
            if (name == "OWNER_INPUT.md") continue
            if (forbiddenMarker.containsMatchIn(content)) {
                errors += "Dokument docs/store/" + name + " ma znacznik INSERT/DO DECYZJI/UZUPEŁNIJ."
            }
            for (token in ownerToken.findAll(content)) {
                if (token.groupValues[1] !in ownerKeys) {
                    errors += "Nieznane pole właściciela w " + name + ": " + token.value
                }
            }
            if (Regex("""\{\{OWNER\.""").findAll(content).count() !=
                ownerToken.findAll(content).count()
            ) errors += "Niepoprawny znacznik właściciela w " + name + "."
        }
        return errors
    }

    /** Jedyne źródło danych wydawcy: dwukolumnowa tabela Markdown. */
    fun ownerFields(source: String?): Map<String, String> {
        val fields = linkedMapOf<String, String>()
        source.orEmpty().lineSequence().forEach { line ->
            val columns = line.trim().split('|')
            if (columns.size >= 4) {
                val key = columns[1].trim().trim(96.toChar())
                if (key in ownerKeys) {
                    // Duplikat pola nie może wybrać arbitralnie jednej z wartości.
                    fields[key] = if (key in fields) "" else columns[2].trim().trim(96.toChar())
                }
            }
        }
        return fields
    }

    fun ownerInputProblems(source: String?): List<String> {
        if (source.isNullOrBlank()) return listOf("Brak docs/store/OWNER_INPUT.md.")
        val fields = ownerFields(source)
        val errors = mutableListOf<String>()
        for (key in ownerKeys) {
            val value = fields[key].orEmpty().trim()
            if (value.isBlank() || value.contains("WYMAGA_DANYCH_WLASCICIELA", true) ||
                value.contains("TODO", true) || value.contains("EXAMPLE", true) ||
                value.startsWith("[")
            ) errors += "OWNER_INPUT.md: pole " + key + " nie jest wypełnione."
        }
        val email = fields["contact_email"].orEmpty()
        if (!email.contains("WYMAGA_DANYCH_WLASCICIELA") &&
            !email.matches(Regex("""[^\s@]+@[^\s@]+\.[^\s@]+"""))
        ) errors += "OWNER_INPUT.md: contact_email musi być adresem e-mail."
        val url = fields["privacy_policy_url"].orEmpty()
        if (!url.contains("WYMAGA_DANYCH_WLASCICIELA") && !isHttpsUrl(url)) {
            errors += "OWNER_INPUT.md: privacy_policy_url musi używać HTTPS."
        }
        val day = fields["release_date"].orEmpty()
        if (!day.contains("WYMAGA_DANYCH_WLASCICIELA") &&
            runCatching { LocalDate.parse(day) }.isFailure
        ) errors += "OWNER_INPUT.md: release_date ma format RRRR-MM-DD."
        val projectId = fields["play_games_project_id"].orEmpty()
        if (!projectId.contains("WYMAGA_DANYCH_WLASCICIELA") &&
            !projectId.matches(Regex("""[1-9][0-9]*"""))
        ) errors += "OWNER_INPUT.md: play_games_project_id musi być liczbą dodatnią."
        return errors
    }

    /** Szablony nie przechowują konkretnych danych identyfikujących wydawcę. */
    fun renderOwnerTemplate(template: String, fields: Map<String, String>): String =
        ownerToken.replace(template) { match ->
            fields[match.groupValues[1]]
                ?: error("Nieznane pole właściciela: " + match.groupValues[1])
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
