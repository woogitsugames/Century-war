package com.centurywar.server

import com.centurywar.game.SimVersion
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class ServerAbuseTest {
    private val now = 1_800_000_000_000L
    private val hmacKey = "test-secret-at-least-32-characters-long!".toByteArray()
    private val installation = "c3".repeat(32)

    private fun sign(body: String, nonce: String): String {
        val message = listOf((now / 1_000L).toString(), nonce, "POST", "/v1/score", body)
            .joinToString("\n") + "\n" + installation
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKey, "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    @Test fun dailyLimitsAreAtomicAndSurviveRestart() {
        val db = Files.createTempFile("cw-score-quotas-", ".sqlite")
        val url = "jdbc:sqlite:$db"
        try {
            SqliteStore(url).use { store ->
                assertTrue(store.consumeScoreSubmissionQuota("install-a", "203.0.113.1", now, 2, 3))
                assertTrue(store.consumeScoreSubmissionQuota("install-a", "203.0.113.1", now, 2, 3))
                assertTrue(!store.consumeScoreSubmissionQuota("install-a", "203.0.113.2", now, 2, 3))
                assertTrue(store.consumeScoreSubmissionQuota("install-b", "203.0.113.1", now, 2, 3))
                assertTrue(!store.consumeScoreSubmissionQuota("install-c", "203.0.113.1", now, 2, 3))
                // Odrzucenie przez IP nie konsumuje limitu instalacji.
                assertTrue(store.consumeScoreSubmissionQuota("install-c", "203.0.113.2", now, 2, 3))
            }
            SqliteStore(url).use { store ->
                assertTrue(!store.consumeScoreSubmissionQuota("install-a", "203.0.113.3", now, 2, 3))
                assertTrue(!store.consumeScoreSubmissionQuota("install-z", "203.0.113.1", now, 2, 3))
                assertTrue(store.consumeScoreSubmissionQuota(
                    "install-a", "203.0.113.1", now + 86_400_000L, 2, 3))
            }
        } finally {
            Files.deleteIfExists(db)
            Files.deleteIfExists(db.resolveSibling(db.fileName.toString() + "-wal"))
            Files.deleteIfExists(db.resolveSibling(db.fileName.toString() + "-shm"))
        }
    }

    @Test fun endpointReturns429WhenInstallationDailyQuotaIsExhausted() = testApplication {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            val runs = AtomicInteger()
            application {
                leaderboardModule(store, hmacKey, { now },
                    limits = RateLimits(postIp = 100, postPlayer = 100,
                        postInstallDaily = 2, postIpDaily = 20),
                    verifyReplay = { input, _, _, _ ->
                        runs.incrementAndGet()
                        VerifiedReplay("endless", input.score, 900)
                    })
            }
            val sample = ReplayVerifierTest.validEndless()
            suspend fun submit(): Pair<HttpStatusCode, String?> {
                val body = Json.encodeToString(sample)
                val nonce = UUID.randomUUID().toString()
                val response = client.post("/v1/score") {
                    header("X-CW-Timestamp", (now / 1000L).toString())
                    header("X-CW-Nonce", nonce)
                    header("X-CW-Player-Secret", installation)
                    header("X-CW-Signature", sign(body, nonce))
                    setBody(body)
                }
                return response.status to response.headers["Retry-After"]
            }
            assertEquals(HttpStatusCode.OK, submit().first)
            assertEquals(HttpStatusCode.OK, submit().first)
            val third = submit()
            assertEquals(HttpStatusCode.TooManyRequests, third.first)
            assertNotNull(third.second)
            assertEquals(2, runs.get())
        }
    }

    @Test fun stepBudgetRejectsLargeReplayBeforeSimulation() {
        val sample = ReplayVerifierTest.validEndless()
        val budget = VerificationBudget(maxReplaySteps = 500, timeoutMillis = 30_000L)
        val error = assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(sample, now, SimVersion.PROTOCOL, budget)
        }
        assertEquals("verification_step_limit", error.message)
    }

    @Test fun deadlineReturnsWithoutWaitingForBlockingVerifier() = runBlocking {
        val gate = VerificationGate(parallel = 1, waiting = 0, timeoutMillis = 80L)
        try {
            val start = System.nanoTime()
            val result = gate.run {
                Thread.sleep(1_500L)
                1
            }
            assertTrue(result!!.exceptionOrNull() is VerificationBudgetExceeded)
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
            assertTrue(elapsed < 1_000L, "Timeout odpowiedzi trwał $elapsed ms")
        } finally {
            gate.close()
        }
    }

    @Test fun twoHundredParallelSubmissionsNeverExceedWorkerLimit() = testApplication {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            val active = AtomicInteger()
            val peak = AtomicInteger()
            application {
                leaderboardModule(store, hmacKey, { now },
                    limits = RateLimits(postIp = 1_000, postPlayer = 1_000,
                        postInstallDaily = 1_000, postIpDaily = 1_000),
                    verificationGate = VerificationGate(
                        parallel = 2, waiting = 2, timeoutMillis = 5_000L),
                    verifyReplay = { input, _, _, budget ->
                        val count = active.incrementAndGet()
                        peak.accumulateAndGet(count) { a, b -> maxOf(a, b) }
                        try {
                            Thread.sleep(250L)
                            budget.check()
                            VerifiedReplay("endless", input.score, 900)
                        } finally {
                            active.decrementAndGet()
                        }
                    })
            }
            val sample = ReplayVerifierTest.validEndless()
            val replies = withTimeout(30_000L) {
                coroutineScope {
                    (0 until 200).map {
                        async(Dispatchers.Default) {
                            val body = Json.encodeToString(
                                sample.copy(player = UUID.randomUUID().toString()))
                            val nonce = UUID.randomUUID().toString()
                            val response = client.post("/v1/score") {
                                header("X-CW-Timestamp", (now / 1000L).toString())
                                header("X-CW-Nonce", nonce)
                                header("X-CW-Player-Secret", installation)
                                header("X-CW-Signature", sign(body, nonce))
                                setBody(body)
                            }
                            response.status to response.headers["Retry-After"]
                        }
                    }.awaitAll()
                }
            }
            assertEquals(200, replies.size)
            assertTrue(replies.any { it.first == HttpStatusCode.ServiceUnavailable },
                "Oczekiwano odrzuceń po przepełnieniu kolejki")
            assertTrue(replies.all {
                it.first == HttpStatusCode.OK || it.first == HttpStatusCode.ServiceUnavailable
            }, "Nieoczekiwane odpowiedzi: ${replies.groupingBy { it.first }.eachCount()}")
            assertTrue(replies.filter { it.first == HttpStatusCode.ServiceUnavailable }
                .all { it.second == "30" })
            assertTrue(peak.get() in 1..2, "Weryfikowano jednocześnie ${peak.get()} powtórek")
        }
    }
}
