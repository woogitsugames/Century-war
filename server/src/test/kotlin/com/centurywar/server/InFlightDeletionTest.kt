package com.centurywar.server

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** A156-05: zgłoszenie w toku nie może odtworzyć gracza usuniętego w trakcie weryfikacji. */
class InFlightDeletionTest {
    private val now = 1_800_000_000_000L
    private val hmacKey = "test-secret-at-least-32-characters-long!".toByteArray()
    private val ownerSecret = "a1".repeat(32)
    private val ownerHash = installSecretHash(ownerSecret)

    private fun submission(player: String = UUID.randomUUID().toString()) =
        ReplayVerifierTest.validEndless(player = player, seed = 42L, steps = 120).copy(nick = "Audit156")

    private fun verified(input: ScoreSubmission) = ReplayVerifier.verify(input, now, input.gameVersion)

    private fun saveClaimed(store: SqliteStore, input: ScoreSubmission, replay: VerifiedReplay) =
        store.beginClaim(input.player, ownerHash)!!.use { claim ->
            store.save(input, replay, now, "hash-" + UUID.randomUUID(), ownerHash, claim)
        }

    /** Reprodukcja z audytu: weryfikacja → usunięcie → dokończenie zapisu; teraz 0 wyników. */
    @Test fun scoreVerifiedBeforeDeletionIsNotRestored() {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            val input = submission()
            saveClaimed(store, input, verified(input))
            assertEquals(1L, store.countScores())

            val executor = Executors.newSingleThreadExecutor()
            try {
                val verifiedLatch = CountDownLatch(1)
                val release = CountDownLatch(1)
                val inFlight = executor.submit<Throwable?> {
                    store.beginClaim(input.player, ownerHash)!!.use { claim ->
                        val replay = verified(input)
                        verifiedLatch.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        runCatching { store.save(input, replay, now, "late", ownerHash, claim) }.exceptionOrNull()
                    }
                }
                assertTrue(verifiedLatch.await(10, TimeUnit.SECONDS))
                assertTrue(store.deleteOwnedPlayer(input.player, ownerHash))
                assertEquals(0L, store.countScores())
                assertNull(store.publicIdFor(input.player))
                release.countDown()
                assertTrue(inFlight.get(10, TimeUnit.SECONDS) is PlayerDeletedException)
            } finally {
                executor.shutdownNow()
            }
            assertEquals(0L, store.countScores())
            assertNull(store.publicIdFor(input.player))
            assertTrue(store.recentScores().isEmpty())
            assertEquals(0, store.trackedDeletions())
        }
    }

    /** Nowy gracz: dwa zgłoszenia przed pierwszym zapisem, usunięcie między zapisami. */
    @Test fun secondFirstSubmissionCannotRecreateDeletedPlayer() {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            val input = submission()
            val replay = VerifiedReplay("endless", input.score, 120)
            val first = store.beginClaim(input.player, ownerHash)!!
            val second = store.beginClaim(input.player, ownerHash)!!
            store.save(input, replay, now, "first", ownerHash, first)
            first.close()
            assertTrue(store.deleteOwnedPlayer(input.player, ownerHash))
            assertThrows(PlayerDeletedException::class.java) {
                store.save(input, replay, now, "second", ownerHash, second)
            }
            second.close()
            assertEquals(0L, store.countScores())
            assertEquals(0, store.trackedDeletions())
        }
    }

    @Test fun newSubmissionAfterDeletionIsAcceptedAndClaimsAreChecked() {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            val input = submission()
            val replay = VerifiedReplay("endless", input.score, 120)
            saveClaimed(store, input, replay)
            assertTrue(store.deleteOwnedPlayer(input.player, ownerHash))
            // Świadome zgłoszenie rozpoczęte po usunięciu jest nowym wpisem, nie wskrzeszeniem.
            saveClaimed(store, input, replay)
            assertEquals(1L, store.countScores())

            val other = submission()
            val claim = store.beginClaim(other.player, ownerHash)!!
            // Bilet innego gracza lub zamknięty bilet nie pozwala na zapis.
            assertThrows(IllegalStateException::class.java) {
                store.save(input, replay, now, "x", ownerHash, claim)
            }
            claim.close()
            assertThrows(IllegalStateException::class.java) {
                store.save(other, replay, now, "y", ownerHash, claim)
            }
            assertNull(store.beginClaim(input.player, installSecretHash("b2".repeat(32))))
        }
    }

    private fun sign(path: String, body: String, nonce: String): String {
        val base = listOf((now / 1000L).toString(), nonce, "POST", path, body).joinToString("\n") +
            "\n" + ownerSecret
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKey, "HmacSHA256"))
        return mac.doFinal(base.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    /** Równoległe endpointy: /v1/score czeka po weryfikacji, w tym czasie /v1/player/delete. */
    @Test fun concurrentDeleteEndpointWinsOverInFlightScoreEndpoint() = testApplication {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            val input = submission()
            saveClaimed(store, input, verified(input))
            val verifiedLatch = CountDownLatch(1)
            val release = CountDownLatch(1)
            application {
                leaderboardModule(store, hmacKey, { now }, verifyReplay = { submitted, at, version, budget ->
                    ReplayVerifier.verify(submitted, at, version, budget).also {
                        verifiedLatch.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                    }
                })
            }
            suspend fun post(path: String, body: String) = client.post(path) {
                val nonce = UUID.randomUUID().toString()
                header("X-CW-Timestamp", (now / 1000L).toString())
                header("X-CW-Nonce", nonce)
                header("X-CW-Player-Secret", ownerSecret)
                header("X-CW-Signature", sign(path, body, nonce))
                setBody(body)
            }
            coroutineScope {
                val score = async { post("/v1/score", Json.encodeToString(input.copy(nick = "Wskrzeszony"))) }
                assertTrue(withContext(Dispatchers.IO) { verifiedLatch.await(10, TimeUnit.SECONDS) })
                val deleted = post("/v1/player/delete", Json.encodeToString(DeletePlayerRequest(input.player)))
                assertEquals(HttpStatusCode.OK, deleted.status)
                assertEquals(0L, store.countScores())
                release.countDown()
                val response = score.await()
                assertEquals(HttpStatusCode.Conflict, response.status)
                assertTrue(response.bodyAsText().contains("player_deleted"))
            }
            assertEquals(0L, store.countScores())
            assertNull(store.publicIdFor(input.player))
            assertEquals(0, store.trackedDeletions())

            // Kolejne zgłoszenie po usunięciu jest przyjmowane jako nowy wpis.
            assertEquals(HttpStatusCode.OK, post("/v1/score", Json.encodeToString(input)).status)
            assertEquals(1L, store.countScores())
        }
    }
}
