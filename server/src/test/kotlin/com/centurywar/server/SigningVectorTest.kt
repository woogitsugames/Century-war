package com.centurywar.server

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Ten sam wektor jest w androidowym ScoreClientTest. */
class SigningVectorTest {
    @Test fun hmacIsCompatibleWithClient() {
        val secret = "test-secret-at-least-32-characters-long!".toByteArray(Charsets.UTF_8)
        val at = 1_800_000_000_000L
        val nonce = "2f1c9b12-9420-44bf-81ac-11c10e6db407"
        val body = """{"score":123,"nick":"Żuraw"}"""
        val expected = "88f657dcf38f9b5aca6281ece9601cd0f057e34845cad994b6dd235dfa214586"
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            val auth = RequestAuthenticator(secret, store, { at })
            assertTrue(auth.valid("POST", "/v1/score", body, "1800000000", nonce, expected, false))
            assertFalse(auth.valid("POST", "/v1/score", body + " ", "1800000000", nonce, expected, false))
        }
    }
}
