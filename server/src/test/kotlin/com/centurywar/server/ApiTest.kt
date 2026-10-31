package com.centurywar.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class ApiTest {
    private val now = 1_800_000_000_000L
    private val secret = "test-secret-at-least-32-characters-long!".toByteArray()
    private val json = Json

    private fun sign(method: String, uri: String, body: String, nonce: String, at: Long = now): String {
        val msg = listOf((at / 1000L).toString(), nonce, method, uri, body).joinToString("\n")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(msg.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    @Test fun acceptsVerifiedAndReturnsRanking() = testApplication {
        val store = SqliteStore("jdbc:sqlite::memory:")
        application { leaderboardModule(store, secret, { now }) }
        val input = ReplayVerifierTest.validEndless()
        val body = json.encodeToString(input)
        val nonce = UUID.randomUUID().toString()
        val first = client.post("/v1/score") {
            header("X-CW-Timestamp", (now / 1000L).toString())
            header("X-CW-Nonce", nonce)
            header("X-CW-Signature", sign("POST", "/v1/score", body, nonce))
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, first.status)
        assertTrue(first.bodyAsText().contains("\"verifiedScore\":" + input.score))

        val path = "/v1/top?board=endless&period=all"
        val getNonce = UUID.randomUUID().toString()
        val top = client.get(path) {
            header("X-CW-Timestamp", (now / 1000L).toString())
            header("X-CW-Nonce", getNonce)
            header("X-CW-Signature", sign("GET", path, "", getNonce))
        }
        assertEquals(HttpStatusCode.OK, top.status)
        assertTrue(top.bodyAsText().contains(input.player))

        val aroundPath = "/v1/around?board=endless&period=all&player=" + input.player
        val aroundNonce = UUID.randomUUID().toString()
        val around = client.get(aroundPath) {
            header("X-CW-Timestamp", (now / 1000L).toString())
            header("X-CW-Nonce", aroundNonce)
            header("X-CW-Signature", sign("GET", aroundPath, "", aroundNonce))
        }
        assertEquals(HttpStatusCode.OK, around.status)
        assertTrue(around.bodyAsText().contains(input.player))
        store.close()
    }

    /** Body bez Content-Length (chunked) ponad 1 MiB: 413, bez wczytywania całości do pamięci. */
    @Test fun rejectsOversizedChunkedBody() = testApplication {
        val store = SqliteStore("jdbc:sqlite::memory:")
        application { leaderboardModule(store, secret, { now }) }
        val response = client.post("/v1/score") {
            setBody(object : OutgoingContent.WriteChannelContent() {
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    val chunk = ByteArray(64 * 1024) { 'a'.code.toByte() }
                    repeat(40) { channel.writeFully(chunk) }
                }
            })
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        store.close()
    }

    @Test fun rejectsForgedScoreAndReusedNonce() = testApplication {
        val store = SqliteStore("jdbc:sqlite::memory:")
        application { leaderboardModule(store, secret, { now }) }
        val input = ReplayVerifierTest.validEndless()
        val body = json.encodeToString(input.copy(score = input.score + 42L))
        val nonce = UUID.randomUUID().toString()
        suspend fun send(): HttpStatusCode = client.post("/v1/score") {
            header("X-CW-Timestamp", (now / 1000L).toString())
            header("X-CW-Nonce", nonce)
            header("X-CW-Signature", sign("POST", "/v1/score", body, nonce))
            setBody(body)
        }.status
        assertEquals(HttpStatusCode.UnprocessableEntity, send())
        assertEquals(HttpStatusCode.Unauthorized, send())
        store.close()
    }

    @Test fun enforcesHmac() = testApplication {
        val store = SqliteStore("jdbc:sqlite::memory:")
        application { leaderboardModule(store, secret, { now }) }
        val response = client.get("/v1/top?board=endless&period=all")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        store.close()
    }

    @Test fun dailySingleAttemptAndPeriodQueries() {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            val input = ReplayVerifierTest.validEndless()
            val daily = VerifiedReplay("daily", 100L, 900)
            val once = store.save(input, daily, now, "sha1")
            val twice = store.save(input.copy(score = 200L), daily.copy(score = 200L), now, "sha2")
            assertTrue(once.inserted)
            assertFalse(twice.inserted)
            assertEquals(100L, twice.bestScore)
            assertEquals(1, store.top("daily", "day", now).size)
            assertEquals(1, store.top("daily", "week", now).size)
            assertEquals(1, store.top("daily", "all", now).size)
            assertEquals(0, store.top("daily", "day", now + 86_400_000L).size)
        }
    }
}
