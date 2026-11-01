package com.centurywar.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class ServerMetricsTest {
    private val now = 1_800_000_000_000L
    private val secret = "at-least-thirty-two-characters-for-test".toByteArray()
    private val installationSecret = "b1".repeat(32)
    private val token = "c2".repeat(32)

    private fun signature(body: String, nonce: String): String {
        val message = listOf(
            (now / 1000L).toString(), nonce, "POST", "/v1/score", body, installationSecret,
        ).joinToString("\n")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(message.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    @Test fun metricsArePrivateAndReflectVerificationOutcomes() = testApplication {
        val store = SqliteStore("jdbc:sqlite::memory:")
        val metrics = ServerMetrics()
        val valid = ReplayVerifierTest.validEndless()
        val invalid = valid.copy(score = valid.score + 1L)
        application {
            leaderboardModule(
                store, secret, { now }, metrics = metrics, metricsToken = token,
                verifyReplay = { submission, _, _, _ ->
                    if (submission.score == invalid.score) error("invalid replay")
                    VerifiedReplay("endless", submission.score, 900)
                },
            )
        }
        suspend fun send(input: ScoreSubmission): HttpStatusCode {
            val body = Json.encodeToString(input)
            val nonce = UUID.randomUUID().toString()
            return client.post("/v1/score") {
                header("X-CW-Timestamp", (now / 1000L).toString())
                header("X-CW-Nonce", nonce)
                header("X-CW-Player-Secret", installationSecret)
                header("X-CW-Signature", signature(body, nonce))
                setBody(body)
            }.status
        }

        assertEquals(HttpStatusCode.OK, client.get("/health").status)
        assertEquals(HttpStatusCode.OK, client.get("/healthz").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/metrics").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/metrics") {
            header(HttpHeaders.Authorization, "Bearer invalid")
        }.status)

        assertEquals(HttpStatusCode.OK, send(valid))
        assertEquals(HttpStatusCode.UnprocessableEntity, send(invalid))

        val response = client.get("/metrics") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"scoreCount\":1"), body)
        assertTrue(body.contains("\"verificationAccepted\":1"), body)
        assertTrue(body.contains("\"verificationRejected\":1"), body)
        assertTrue(body.contains("\"verificationUnavailable\":0"), body)
        assertTrue(body.contains("\"verificationSamples\":2"), body)
        assertTrue(body.contains("\"verificationMeanMs\":"), body)
        assertTrue(body.contains("\"verificationMaxMs\":"), body)
        store.close()
    }

    @Test fun metricsAreDisabledWithoutTokenAndHealthChecksSqlite() = testApplication {
        val store = SqliteStore("jdbc:sqlite::memory:")
        application { leaderboardModule(store, secret, { now }) }
        assertEquals(HttpStatusCode.NotFound, client.get("/metrics").status)
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
        store.close()
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/health").status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/healthz").status)
    }
}
