package com.centurywar.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreClientTest {
    @Test fun hmacMatchesServerReferenceVector() {
        val signature = ScoreClient.sign(
            "test-secret-at-least-32-characters-long!".toByteArray(Charsets.UTF_8),
            "1800000000",
            "2f1c9b12-9420-44bf-81ac-11c10e6db407",
            "POST",
            "/v1/score",
            """{"score":123,"nick":"Żuraw"}""",
        )
        assertEquals("88f657dcf38f9b5aca6281ece9601cd0f057e34845cad994b6dd235dfa214586",
            signature)
    }

    @Test fun onlyConfiguredHttpsEndpointCanEnableClient() {
        val key = "a".repeat(32)
        assertFalse(ScoreClient.configured("", key))
        assertFalse(ScoreClient.configured("http://scores.example", key))
        assertFalse(ScoreClient.configured("https://scores.example", "short"))
        assertFalse(ScoreClient.configured("https://scores.example/other", key))
        assertFalse(ScoreClient.configured("https://user@scores.example", key))
        assertTrue(ScoreClient.configured("https://scores.example", key))
    }

    @Test fun nicknameRulesMatchServer() {
        assertTrue(ScoreClient.validNick("Żuraw_12"))
        assertFalse(ScoreClient.validNick("  "))
        assertFalse(ScoreClient.validNick(" leading"))
        assertFalse(ScoreClient.validNick("a".repeat(25)))
        assertFalse(ScoreClient.validNick("abc\nxyz"))
    }
}
