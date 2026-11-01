package com.centurywar.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
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

class ModerationApiTest {
    private val now = 1_800_000_000_000L
    private val secret = "test-secret-at-least-32-characters-long!".toByteArray()
    private val playerSecret = "a1".repeat(32)

    private fun sign(method: String, target: String, body: String, nonce: String): String {
        val message = listOf((now / 1000L).toString(), nonce, method, target, body).joinToString("\n") +
            (if (method == "POST" && target == "/v1/score") "\n" + playerSecret else "")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(message.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    @Test fun profaneNicknameProducesNormalReceiptAndMaskedRanking() = testApplication {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            application { leaderboardModule(store, secret, { now }) }
            val input = ReplayVerifierTest.validEndless().copy(nick = "K.U.R.W.@")
            val body = Json.encodeToString(input)
            val nonce = UUID.randomUUID().toString()
            val response = client.post("/v1/score") {
                header("X-CW-Timestamp", (now / 1000L).toString())
                header("X-CW-Nonce", nonce)
                header("X-CW-Player-Secret", playerSecret)
                header("X-CW-Signature", sign("POST", "/v1/score", body, nonce))
                setBody(body)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("\"accepted\":true"))
            val target = "/v1/top?board=endless&period=all"
            val getNonce = UUID.randomUUID().toString()
            val top = client.get(target) {
                header("X-CW-Timestamp", (now / 1000L).toString())
                header("X-CW-Nonce", getNonce)
                header("X-CW-Signature", sign("GET", target, "", getNonce))
            }
            assertEquals(HttpStatusCode.OK, top.status)
            assertTrue(top.bodyAsText().contains(NickModeration.fallbackNick(input.player)))
            assertFalse(top.bodyAsText().contains(input.nick))
        }
    }

    @Test fun blockedPlayerGetsForbiddenBeforeReplayVerification() = testApplication {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            application { leaderboardModule(store, secret, { now }) }
            val input = ReplayVerifierTest.validEndless().copy(replay = "invalid")
            store.blockPlayer(input.player)
            val body = Json.encodeToString(input)
            val nonce = UUID.randomUUID().toString()
            val response = client.post("/v1/score") {
                header("X-CW-Timestamp", (now / 1000L).toString())
                header("X-CW-Nonce", nonce)
                header("X-CW-Player-Secret", playerSecret)
                header("X-CW-Signature", sign("POST", "/v1/score", body, nonce))
                setBody(body)
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertTrue(response.bodyAsText().contains("blocked_player"))
        }
    }
}
