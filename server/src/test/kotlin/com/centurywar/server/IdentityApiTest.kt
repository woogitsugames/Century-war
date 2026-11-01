package com.centurywar.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class IdentityApiTest {
    private val now = 1_800_000_000_000L
    private val hmacKey = "test-secret-at-least-32-characters-long!".toByteArray()
    private val publicKey = "server-only-independent-key-12345678".toByteArray()
    private val ownerSecret = "a1".repeat(32)
    private val strangerSecret = "b2".repeat(32)

    private fun sign(method: String, path: String, body: String, nonce: String,
                     installationSecret: String? = null): String {
        val base = listOf((now / 1000L).toString(), nonce, method, path, body).joinToString("\n")
        val bytes = (base + (if (installationSecret != null) "\n" + installationSecret else ""))
            .toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKey, "HmacSHA256"))
        return mac.doFinal(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun score(player: String, nick: String) =
        ReplayVerifierTest.validEndless().copy(player = player, nick = nick)

    @Test fun cannotOverwriteOrDeleteAnotherPlayersIdentity() = testApplication {
        SqliteStore("jdbc:sqlite::memory:", PublicPlayerIds(publicKey)).use { store ->
            application {
                leaderboardModule(store, hmacKey, { now }, verifyReplay = { input, _, _ ->
                    VerifiedReplay("endless", input.score, 900)
                })
            }
            val victim = score(UUID.randomUUID().toString(), "Zwyciezca")
            suspend fun sendScore(input: ScoreSubmission, installation: String): HttpStatusCode {
                val body = Json.encodeToString(input)
                val nonce = UUID.randomUUID().toString()
                return client.post("/v1/score") {
                    header("X-CW-Timestamp", (now / 1000L).toString())
                    header("X-CW-Nonce", nonce)
                    header("X-CW-Player-Secret", installation)
                    header("X-CW-Signature", sign("POST", "/v1/score", body, nonce, installation))
                    setBody(body)
                }.status
            }
            assertEquals(HttpStatusCode.OK, sendScore(victim, ownerSecret))
            assertEquals(HttpStatusCode.Forbidden,
                sendScore(victim.copy(nick = "Przejety", score = victim.score + 1), strangerSecret))
            val stored = store.recentScores().single()
            assertEquals("Zwyciezca", stored.nick)
            assertEquals(victim.score, stored.score)

            val topPath = "/v1/top?board=endless&period=all"
            val topNonce = UUID.randomUUID().toString()
            val topResponse = client.get(topPath) {
                header("X-CW-Timestamp", (now / 1000L).toString())
                header("X-CW-Nonce", topNonce)
                header("X-CW-Signature", sign("GET", topPath, "", topNonce))
            }
            val publicId = store.publicIdFor(victim.player)!!
            assertEquals(24, publicId.length)
            assertEquals(HttpStatusCode.OK, topResponse.status)
            assertTrue(topResponse.bodyAsText().contains(publicId))
            assertFalse(topResponse.bodyAsText().contains(victim.player))
            assertNotEquals(publicId, victim.player)

            val aroundPath = "/v1/around?board=endless&period=all&player=" + victim.player
            suspend fun around(installation: String): Pair<HttpStatusCode, String> {
                val nonce = UUID.randomUUID().toString()
                val res = client.get(aroundPath) {
                    header("X-CW-Timestamp", (now / 1000L).toString())
                    header("X-CW-Nonce", nonce)
                    header("X-CW-Player-Secret", installation)
                    header("X-CW-Signature", sign("GET", aroundPath, "", nonce, installation))
                }
                return res.status to res.bodyAsText()
            }
            assertEquals(HttpStatusCode.Forbidden, around(strangerSecret).first)
            val ownAround = around(ownerSecret)
            assertEquals(HttpStatusCode.OK, ownAround.first)
            assertFalse(ownAround.second.contains(victim.player))
            assertTrue(ownAround.second.contains("\"self\":\"$publicId\""))

            val deleteBody = Json.encodeToString(DeletePlayerRequest(victim.player))
            suspend fun delete(installation: String): HttpStatusCode {
                val nonce = UUID.randomUUID().toString()
                return client.post("/v1/player/delete") {
                    header("X-CW-Timestamp", (now / 1000L).toString())
                    header("X-CW-Nonce", nonce)
                    header("X-CW-Player-Secret", installation)
                    header("X-CW-Signature", sign("POST", "/v1/player/delete",
                        deleteBody, nonce, installation))
                    setBody(deleteBody)
                }.status
            }
            assertEquals(HttpStatusCode.Forbidden, delete(strangerSecret))
            assertEquals(1, store.top("endless", "all", now).size)
            assertEquals(HttpStatusCode.OK, delete(ownerSecret))
            assertTrue(store.top("endless", "all", now).isEmpty())
            assertEquals(HttpStatusCode.OK, delete(ownerSecret))
        }
    }

    @Test fun legacyPlayerIsClaimedByFirstVerifiedSecretScore() = testApplication {
        SqliteStore("jdbc:sqlite::memory:", PublicPlayerIds(publicKey)).use { store ->
            val victim = score(UUID.randomUUID().toString(), "Stary")
            store.save(victim, VerifiedReplay("endless", victim.score, 900), now, "old")
            assertFalse(store.isOwner(victim.player, installSecretHash(ownerSecret)))
            assertTrue(store.mayClaim(victim.player, installSecretHash(ownerSecret)))
            application {
                leaderboardModule(store, hmacKey, { now }, verifyReplay = { input, _, _ ->
                    VerifiedReplay("endless", input.score, 900)
                })
            }
            val body = Json.encodeToString(victim.copy(nick = "Nowy", score = victim.score + 10))
            val nonce = UUID.randomUUID().toString()
            val response = client.post("/v1/score") {
                header("X-CW-Timestamp", (now / 1000L).toString())
                header("X-CW-Nonce", nonce)
                header("X-CW-Player-Secret", ownerSecret)
                header("X-CW-Signature", sign("POST", "/v1/score", body, nonce, ownerSecret))
                setBody(body)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(store.isOwner(victim.player, installSecretHash(ownerSecret)))
            assertFalse(store.mayClaim(victim.player, installSecretHash(strangerSecret)))
        }
    }

    @Test fun reportUsesPublicIdAndAppearsInCli() = testApplication {
        SqliteStore("jdbc:sqlite::memory:", PublicPlayerIds(publicKey)).use { store ->
            val victim = score(UUID.randomUUID().toString(), "ZlyNick")
            store.save(victim, VerifiedReplay("endless", victim.score, 900), now, "saved")
            application { leaderboardModule(store, hmacKey, { now },
                limits = RateLimits(reportIp = 2)) }
            val id = store.publicIdFor(victim.player)!!
            suspend fun report(id: String): HttpStatusCode =
                client.post("/v1/report") {
                    setBody(Json.encodeToString(NickReportRequest(id, "offensive")))
                }.status
            assertEquals(HttpStatusCode.Accepted, report(id))
            assertEquals(HttpStatusCode.Accepted, report(id))
            assertEquals(HttpStatusCode.TooManyRequests, report(id))
            val cli = StringBuilder()
            assertEquals(0, runAdmin(listOf("reports"), store, cli, StringBuilder()))
            assertTrue(cli.contains(id))
            assertTrue(cli.contains(victim.player))
            assertTrue(cli.contains("offensive"))
            assertEquals(2, store.recentReports().size)
        }
    }

    @Test fun proxyHeadersCannotBeForgedFromUntrustedPeer() {
        val resolver = ClientAddress(setOf("127.0.0.1"))
        assertEquals("203.0.113.4", resolver.resolve("127.0.0.1", "203.0.113.4"))
        assertEquals("203.0.113.5", resolver.resolve("203.0.113.5", "198.51.100.9"))
        assertEquals("127.0.0.1", resolver.resolve("127.0.0.1", "bad-header"))
        val limiter = RateLimiter({ now })
        assertTrue(limiter.allow("post-ip:" + resolver.resolve("127.0.0.1", "203.0.113.4"), 1))
        assertTrue(limiter.allow("post-ip:" + resolver.resolve("127.0.0.1", "203.0.113.6"), 1))
        assertFalse(limiter.allow("post-ip:" + resolver.resolve("127.0.0.1", "203.0.113.4"), 1))
    }

    @Test fun boundedVerificationQueueRejectsSeventeenthWaiter() = runBlocking {
        withTimeout(5_000L) {
            val gate = VerificationGate(parallel = 1, waiting = 1, timeoutMillis = 4_000L)
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val active = async(start = CoroutineStart.UNDISPATCHED) {
                gate.run { started.complete(Unit); release.await(); 1 }
            }
            started.await()
            val waiting = async(start = CoroutineStart.UNDISPATCHED) { gate.run { 2 } }
            assertNull(gate.run { 3 })
            release.complete(Unit)
            assertEquals(1, active.await()!!.getOrThrow())
            assertEquals(2, waiting.await()!!.getOrThrow())
        }
    }
}
