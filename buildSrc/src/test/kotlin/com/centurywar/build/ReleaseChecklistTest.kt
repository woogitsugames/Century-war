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
        for (flag in ReleaseChecklist.allowedSkips) {
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
