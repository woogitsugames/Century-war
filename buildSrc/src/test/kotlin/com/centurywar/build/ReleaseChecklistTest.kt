package com.centurywar.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseChecklistTest {
    private fun ready() = ReleaseChecklistInput(
        releaseDay = 20_000L,
        todayUtcDay = 20_000L,
        playGamesEnabled = true,
        gamesIdsXml = "<resources><string>12345</string></resources>",
        serverUrl = "https://rankingi.example.org/api",
        privacyUrl = "https://example.org/privacy",
        hasHmacKey = true,
        versionCode = 2,
        lastVersionCode = 1,
    )

    @Test fun readsReleaseDayOnlyFromConstant() {
        assertEquals(21153L, ReleaseChecklist.releaseDayFromSource(
            "object Daily {\n    const val RELEASE_DAY = 21153L // data zastępcza\n}",
        ))
        assertNull(ReleaseChecklist.releaseDayFromSource("const val WEATHER_DAY = 21153L"))
        assertNull(ReleaseChecklist.releaseDayFromSource("const val RELEASE_DAY = someday"))
    }

    @Test fun allowsExactUtcDateBoundaries() {
        assertTrue(ReleaseChecklist.problems(ready().copy(releaseDay = 19_970L)).isEmpty())
        assertTrue(ReleaseChecklist.problems(ready().copy(releaseDay = 20_060L)).isEmpty())
        assertTrue(ReleaseChecklist.problems(ready()).isEmpty())
        assertTrue(ReleaseChecklist.problems(ready().copy(releaseDay = 19_969L)).single()
            .contains("RELEASE_DAY"))
        assertTrue(ReleaseChecklist.problems(ready().copy(releaseDay = 20_061L)).single()
            .contains("RELEASE_DAY"))
        assertTrue(ReleaseChecklist.problems(ready().copy(releaseDay = null)).single()
            .contains("RELEASE_DAY"))
    }

    @Test fun checksPlayGamesOnlyWhenEnabled() {
        val placeholders = ready().copy(gamesIdsXml = "<resources>REPLACE_ME</resources>")
        assertTrue(ReleaseChecklist.problems(placeholders).single().contains("Play Games"))
        assertTrue(ReleaseChecklist.problems(placeholders.copy(playGamesEnabled = false)).isEmpty())
    }

    @Test fun rejectsMissingOrNonHttpsUrls() {
        for (url in listOf("", "http://example.org", "https://", "https:// user", "ftp://host.org")) {
            assertTrue(url, ReleaseChecklist.problems(ready().copy(serverUrl = url))
                .single().contains("CW_SERVER_URL"))
            assertTrue(url, ReleaseChecklist.problems(ready().copy(privacyUrl = url))
                .single().contains("CW_PRIVACY_URL"))
        }
        assertTrue(ReleaseChecklist.problems(ready().copy(serverUrl = "HTTPS://example.org")).isEmpty())
    }

    @Test fun rejectsMissingSigningKeyAndNonIncreasingVersion() {
        assertTrue(ReleaseChecklist.problems(ready().copy(hasHmacKey = false))
            .single().contains("CW_HMAC_KEY"))
        assertTrue(ReleaseChecklist.problems(ready().copy(versionCode = 1))
            .single().contains("releaseVersionCode"))
        assertTrue(ReleaseChecklist.problems(ready().copy(versionCode = 0))
            .single().contains("releaseVersionCode"))
        assertTrue(ReleaseChecklist.problems(ready().copy(lastVersionCode = null))
            .single().contains("last_version_code.txt"))
        assertTrue(ReleaseChecklist.problems(ready().copy(lastVersionCode = -1))
            .single().contains("last_version_code.txt"))
    }

    @Test fun reportsAllErrorsInOneAttempt() {
        val issues = ReleaseChecklist.problems(ready().copy(
            releaseDay = 21_153L,
            gamesIdsXml = "REPLACE_ME",
            serverUrl = "",
            privacyUrl = "http://example.org",
            hasHmacKey = false,
            versionCode = 1,
        ))
        assertEquals(6, issues.size)
    }

    @Test fun eachRuleCanBeConsciouslySkipped() {
        val notReady = ready().copy(
            releaseDay = 21_153L, gamesIdsXml = "REPLACE_ME",
            serverUrl = "", privacyUrl = "", hasHmacKey = false,
            versionCode = 1,
        )
        for (flag in ReleaseChecklist.allowedSkips - "ownerInput") {
            val issues = ReleaseChecklist.problems(notReady.copy(skipChecks = setOf(flag)))
            assertEquals(flag, 5, issues.size)
        }
        assertTrue(ReleaseChecklist.problems(
            notReady.copy(skipChecks = ReleaseChecklist.allowedSkips),
        ).isEmpty())
        assertEquals(setOf("releaseDay", "privacyUrl"),
            ReleaseChecklist.parseSkips("releaseDay, privacyUrl,releaseDay"))
        assertFalse(ReleaseChecklist.parseSkips("").isNotEmpty())
    }


    private fun validOwner() = """
        | Pole | Wartość |
        | --- | --- |
        | publisher_name | Legal Publisher |
        | contact_email | privacy@studio-gier.pl |
        | postal_address | 123 Avenue, City, Country |
        | privacy_policy_url | https://studio-gier.pl/prywatnosc |
        | release_date | 2026-11-01 |
    """.trimIndent()

    private fun docs() = mapOf(
        "privacy_policy_pl.md" to "Adres: {{OWNER.postal_address}}",
        "privacy_policy_en.md" to "Email: {{OWNER.contact_email}}",
        "data_safety.md" to "Przetwarzanie danych rankingu",
        "content_rating.md" to "Publiczne pseudonimy graczy",
    )

    @Test fun signingCannotBeSkippedByChecklistFlag() {
        val missing = ready().copy(hasReleaseSigning = false)
        assertTrue(ReleaseChecklist.problems(missing).single().contains("podpisu release"))
        assertTrue(ReleaseChecklist.problems(missing.copy(
            skipChecks = ReleaseChecklist.allowedSkips,
        )).single().contains("podpisu release"))
        assertTrue(ReleaseChecklist.problems(missing.copy(ciTestDebugSigning = true)).isEmpty())
    }

    @Test fun ciSigningRequiresExplicitSmoketestContext() {
        assertTrue(ReleaseChecklist.canUseCiDebugSigning(true, "true", "pull_request", "true"))
        assertTrue(ReleaseChecklist.canUseCiDebugSigning(true, "true", "workflow_dispatch", "true"))
        assertFalse(ReleaseChecklist.canUseCiDebugSigning(true, "true", "push", "true"))
        assertFalse(ReleaseChecklist.canUseCiDebugSigning(true, "false", "pull_request", "true"))
        assertFalse(ReleaseChecklist.canUseCiDebugSigning(true, "true", "pull_request", "false"))
        assertFalse(ReleaseChecklist.canUseCiDebugSigning(false, "true", "pull_request", "true"))
    }

    @Test fun ciSkipsAreLimitedToManualTestRelease() {
        val test = ReleaseChecklist.testReleaseSkips
        assertEquals(setOf("releaseDay", "serverUrl", "hmacKey"), test)
        // Ręczne wydanie testowe z kluczem upload: dokładnie te trzy wyjątki.
        assertNull(ReleaseChecklist.ciSkipProblem("true", "workflow_dispatch", test, true))
        assertNull(ReleaseChecklist.ciSkipProblem("true", "workflow_dispatch", emptySet(), true))
        // Tag v* (push) nie pomija niczego, niezależnie od podpisu.
        assertTrue(ReleaseChecklist.ciSkipProblem("true", "push", test, true)!!.contains("tagu"))
        assertTrue(ReleaseChecklist.ciSkipProblem("true", "push", setOf("releaseDay"), false) != null)
        assertNull(ReleaseChecklist.ciSkipProblem("true", "push", emptySet(), true))
        // Żaden inny wyjątek w podpisanym buildzie CI, w tym ownerInput i playGames.
        for (extra in ReleaseChecklist.allowedSkips - test) {
            assertTrue(extra, ReleaseChecklist.ciSkipProblem(
                "true", "workflow_dispatch", test + extra, true) != null)
        }
        assertTrue(ReleaseChecklist.ciSkipProblem("true", "pull_request", test, true) != null)
        // Smoke-test bez klucza upload i build lokalny bez ograniczeń tej reguły.
        assertNull(ReleaseChecklist.ciSkipProblem(
            "true", "pull_request", ReleaseChecklist.allowedSkips, false))
        assertNull(ReleaseChecklist.ciSkipProblem(null, null, ReleaseChecklist.allowedSkips, true))
    }

    @Test fun ownerInputDoesNotDuplicatePlayGamesId() {
        assertTrue(ReleaseChecklist.ownerInputProblems(validOwner()).isEmpty())
        assertFalse(ReleaseChecklist.ownerFields(validOwner()).containsKey("play_games_project_id"))
    }

    @Test fun validatesAllPublisherFieldsAndFormats() {
        assertTrue(ReleaseChecklist.ownerInputProblems(validOwner()).isEmpty())
        assertTrue(ReleaseChecklist.ownerInputProblems(null).single().contains("OWNER_INPUT"))
        assertTrue(ReleaseChecklist.ownerInputProblems(
            validOwner().replace("Legal Publisher", "WYMAGA_DANYCH_WLASCICIELA"),
        ).any { it.contains("publisher_name") })
        assertTrue(ReleaseChecklist.ownerInputProblems(
            validOwner().replace("privacy@studio-gier.pl", "invalid"),
        ).any { it.contains("contact_email") })
        assertTrue(ReleaseChecklist.ownerInputProblems(
            validOwner().replace("https://studio-gier.pl/prywatnosc", "http://studio-gier.pl/prywatnosc"),
        ).any { it.contains("privacy_policy_url") })
        assertTrue(ReleaseChecklist.ownerInputProblems(
            validOwner().replace("2026-11-01", "yesterday"),
        ).any { it.contains("release_date") })
        assertTrue(ReleaseChecklist.ownerInputProblems(
            validOwner() + "\n| contact_email | inny@studio-gier.pl |",
        ).any { it.contains("contact_email") })
    }

    @Test fun checksStoreDocsAndBlocksEveryPlaceholder() {
        val full = ready().copy(enforceStoreDocuments = true,
            storeDocuments = docs(), ownerInputText = validOwner())
        assertTrue(ReleaseChecklist.problems(full).isEmpty())
        assertTrue(ReleaseChecklist.problems(full.copy(ownerInputText = null))
            .any { it.contains("OWNER_INPUT") })
        assertTrue(ReleaseChecklist.problems(full.copy(
            storeDocuments = docs() - "data_safety.md",
        )).any { it.contains("data_safety.md") })
        for (marker in listOf("[INSERT_CONTACT_EMAIL]", "[DO DECYZJI]", "[UZUPEŁNIJ_EMAIL]")) {
            assertTrue(ReleaseChecklist.problems(full.copy(
                storeDocuments = docs() + ("content_rating.md" to marker),
            )).any { it.contains("content_rating.md") })
        }
        assertTrue(ReleaseChecklist.problems(full.copy(
            storeDocuments = docs() + ("privacy_policy_pl.md" to "{{OWNER.unknown}}"),
        )).any { it.contains("privacy_policy_pl.md") })
        assertTrue(ReleaseChecklist.problems(full.copy(
            ownerInputText = null, skipChecks = setOf("ownerInput"),
        )).isEmpty())
    }

    @Test fun rejectsExampleValuesInOwnerInput() {
        val replacements = listOf(
            "Legal Publisher" to "Example Publisher",
            "privacy@studio-gier.pl" to "privacy@example.org",
            "123 Avenue, City, Country" to "Example Street",
            "https://studio-gier.pl/prywatnosc" to "https://example.org/privacy",
        )
        for ((valid, placeholder) in replacements) {
            assertTrue(placeholder, ReleaseChecklist.ownerInputProblems(
                validOwner().replace(valid, placeholder),
            ).isNotEmpty())
        }
    }

    @Test fun rendersPrivatePublisherValuesOnlyForPublishableOutput() {
        assertEquals("Contact: privacy@studio-gier.pl",
            ReleaseChecklist.renderOwnerTemplate(
                "Contact: {{OWNER.contact_email}}",
                ReleaseChecklist.ownerFields(validOwner()),
            ))
    }

    @Test fun misspelledSkipFlagFails() {
        var rejected = false
        try {
            ReleaseChecklist.parseSkips("relaseDay")
        } catch (e: IllegalArgumentException) {
            rejected = e.message?.contains("Nieznane") == true
        }
        assertTrue(rejected)
    }
}
