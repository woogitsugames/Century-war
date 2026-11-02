package com.centurywar.meta

import com.centurywar.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Brak kluczy w CI/debug nie może ujawnić przełącznika rankingu. */
class ReleaseConfigTest {
    @Test fun defaultFieldsAreSafeAndClientCannotUsePartialConfiguration() {
        assertEquals("0.6.0", ScoreClient.GAME_VERSION)
        val server = BuildConfig.CW_SERVER_URL
        val key = BuildConfig.CW_HMAC_KEY
        val policy = BuildConfig.CW_PRIVACY_URL
        val endpoint = ScoreClient.configured(server, key)
        val policyValid = runCatching {
            val uri = java.net.URI(policy)
            uri.scheme == "https" && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
        if (server.isBlank() || key.isBlank()) assertFalse(endpoint)
        if (policy.isBlank()) assertFalse(policyValid)
        // Każda konfiguracja uznana za aktywną używa TLS i jawnej polityki.
        if (endpoint && policyValid) {
            assertTrue(server.startsWith("https://"))
            assertTrue(policy.startsWith("https://"))
        }
    }
}
