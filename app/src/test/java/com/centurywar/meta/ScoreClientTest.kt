package com.centurywar.meta

import com.centurywar.game.SimVersion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.UUID

class ScoreClientTest {
    @Test fun protocolVersionComesFromSimulation() {
        assertEquals(SimVersion.PROTOCOL, ScoreClient.GAME_VERSION)
    }

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
        assertFalse(ScoreClient.configured("https://scores.example@attacker.example", key))
        assertFalse(ScoreClient.configured("https://scores.example?token=abc", key))
        assertFalse(ScoreClient.configured("https://scores.example#fragment", key))
        assertFalse(ScoreClient.configured("https://scores.example:abc", key))
        assertFalse(ScoreClient.configured("https://scores.example/../../else", key))
        assertTrue(ScoreClient.configured("https://scores.example", key))
        assertTrue(ScoreClient.configured("https://scores.example/", key))
    }

    @Test fun deletionQueuePersistsAndDoesNotExpire() {
        val dir = Files.createTempDirectory("cw-deletion-test-")
        val file = dir.resolve("deletions.txt").toFile()
        val a = UUID.randomUUID().toString()
        val b = UUID.randomUUID().toString()
        val playerSecret = "a1".repeat(32)
        try {
            val pending = PendingDeletions(file)
            assertTrue(pending.load())
            assertTrue(pending.add(a, playerSecret))
            assertTrue(pending.add(b, playerSecret))
            assertTrue(pending.add(a, playerSecret))
            assertEquals(a, pending.first()?.player)
            val reopened = PendingDeletions(file)
            assertTrue(reopened.load())
            assertTrue(reopened.contains(a))
            assertTrue(reopened.contains(b))
            assertTrue(reopened.remove(a))
            assertEquals(b, reopened.first()?.player)
            val again = PendingDeletions(file)
            assertTrue(again.load())
            assertFalse(again.contains(a))
            assertEquals(b, again.first()?.player)
            assertTrue(again.remove(b))
            assertFalse(file.exists())
        } finally {
            file.delete()
            dir.toFile().delete()
        }
    }

    @Test fun deletionRequestHasIndependentSignature() {
        val nonce = "2f1c9b12-9420-44bf-81ac-11c10e6db407"
        val body = "{\"player\":\"a16036d5-800a-4898-aea3-11b6548de7b4\"}"
        val scoreSig = ScoreClient.sign(
            "test-secret-at-least-32-characters-long!".toByteArray(), "1800000000",
            nonce, "POST", "/v1/score", body)
        val deleteSig = ScoreClient.sign(
            "test-secret-at-least-32-characters-long!".toByteArray(), "1800000000",
            nonce, "POST", "/v1/player/delete", body)
        assertEquals(64, deleteSig.length)
        assertFalse(scoreSig == deleteSig)
    }

    @Test fun playerSecretIsCoveredByHmac() {
        val key = "test-secret-at-least-32-characters-long!".toByteArray()
        val nonce = UUID.randomUUID().toString()
        val original = ScoreClient.sign(key, "1800000000", nonce, "POST", "/v1/score",
            "{}", "a1".repeat(32))
        assertEquals(64, original.length)
        assertFalse(original == ScoreClient.sign(key, "1800000000", nonce, "POST", "/v1/score",
            "{}", "b2".repeat(32)))
        assertTrue(ScoreClient.validInstallationSecret("a1".repeat(32)))
        assertFalse(ScoreClient.validInstallationSecret("short"))
        assertTrue(ScoreClient.validPublicId("0".repeat(24)))
        assertFalse(ScoreClient.validPublicId(UUID.randomUUID().toString()))
    }

    @Test fun nicknameRulesMatchServer() {
        assertTrue(ScoreClient.validNick("Żuraw_12"))
        assertFalse(ScoreClient.validNick("  "))
        assertFalse(ScoreClient.validNick(" leading"))
        assertFalse(ScoreClient.validNick("a".repeat(25)))
        assertFalse(ScoreClient.validNick("abc\nxyz"))
    }
}
