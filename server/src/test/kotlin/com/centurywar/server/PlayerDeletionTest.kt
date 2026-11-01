package com.centurywar.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class PlayerDeletionTest {
    private val now = 1_800_000_000_000L
    private val secret = "test-secret-at-least-32-characters-long!".toByteArray()
    private val playerSecret = "a1".repeat(32)

    private fun input(player: String = UUID.randomUUID().toString()) = ScoreSubmission(
        board = "endless", player = player, nick = "Gracz", gameVersion = "0.4.0",
        seed = 42L, replay = "nieistotny", score = 100L,
        research = List(13) { 0 },
    )

    private fun sign(body: String, nonce: String): String {
        val data = listOf((now / 1000).toString(), nonce, "POST", "/v1/player/delete", body).joinToString("\n") +
            "\n" + playerSecret
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    @Test fun deletesAllBoardsIsIdempotentAndRejectsBadSignature() = testApplication {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            val first = input()
            val other = input()
            store.save(first, VerifiedReplay("endless", 100L, 900), now, "hash-a",
                installSecretHash(playerSecret))
            store.save(first, VerifiedReplay("campaign_7", 70L, 900), now, "hash-b",
                installSecretHash(playerSecret))
            store.save(other, VerifiedReplay("endless", 50L, 900), now, "hash-c",
                installSecretHash(playerSecret))
            application { leaderboardModule(store, secret, { now }) }

            val health = client.get("/healthz")
            assertEquals(HttpStatusCode.OK, health.status)
            assertEquals("ok\n", health.bodyAsText())

            val body = "{\"player\":\"" + first.player + "\"}"
            suspend fun delete(signatureValid: Boolean = true): HttpStatusCode {
                val nonce = UUID.randomUUID().toString()
                return client.post("/v1/player/delete") {
                    header("X-CW-Timestamp", (now / 1000).toString())
                    header("X-CW-Nonce", nonce)
                    header("X-CW-Player-Secret", playerSecret)
                    header("X-CW-Signature", if (signatureValid) sign(body, nonce) else "0".repeat(64))
                    setBody(body)
                }.status
            }
            assertEquals(HttpStatusCode.Unauthorized, delete(false))
            assertEquals(2, store.top("endless", "all", now).size)
            assertEquals(HttpStatusCode.OK, delete())
            assertTrue(store.top("campaign_7", "all", now).isEmpty())
            assertEquals(listOf(store.publicIdFor(other.player)), store.top("endless", "all", now).map { it.player })
            assertEquals(HttpStatusCode.OK, delete())
            assertEquals(1, store.top("endless", "all", now).size)
        }
    }

    @Test fun deleteKeepsBlockSoBlockedPlayerCannotUnblockThemselves() = testApplication {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            val blocked = input()
            store.save(blocked, VerifiedReplay("endless", 100L, 900), now, "hash-a",
                installSecretHash(playerSecret))
            assertTrue(store.blockPlayer(blocked.player))
            application { leaderboardModule(store, secret, { now }) }

            val body = "{\"player\":\"" + blocked.player + "\"}"
            val nonce = UUID.randomUUID().toString()
            val status = client.post("/v1/player/delete") {
                header("X-CW-Timestamp", (now / 1000).toString())
                header("X-CW-Nonce", nonce)
                header("X-CW-Player-Secret", playerSecret)
                header("X-CW-Signature", sign(body, nonce))
                setBody(body)
            }.status
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(store.top("endless", "all", now).isEmpty())
            assertTrue(store.isBlocked(blocked.player))
            // Nowy wynik po usunięciu nadal jest odrzucany.
            assertTrue(runCatching {
                store.save(blocked, VerifiedReplay("endless", 120L, 900), now, "hash-b")
            }.exceptionOrNull() is BlockedPlayerException)
        }
    }

    @Test fun migratesLegacyBoardsOnceWithoutCollisions() {
        val db = Files.createTempFile("cw-migration-", ".db").toFile()
        val jdbc = "jdbc:sqlite:" + db.absolutePath
        val first = input()
        try {
            SqliteStore(jdbc).use { store ->
                store.save(first, VerifiedReplay("campaign_8", 80L, 60), now, "8")
                store.save(first, VerifiedReplay("campaign_12", 120L, 60), now, "12")
                store.save(first, VerifiedReplay("campaign_31", 310L, 60), now, "31")
                store.save(first, VerifiedReplay("endless", 40L, 60), now, "other")
            }
            // Symulujemy bazę sprzed tabeli schema_version.
            DriverManager.getConnection(jdbc).use { c ->
                c.createStatement().use { it.execute("DROP TABLE schema_version") }
            }
            SqliteStore(jdbc).use { store ->
                assertTrue(store.top("campaign_8", "all", now).isEmpty())
                assertEquals(80L, store.top("campaign_12", "all", now).single().score)
                assertEquals(120L, store.top("campaign_16", "all", now).single().score)
                assertEquals(310L, store.top("campaign_35", "all", now).single().score)
                assertEquals(40L, store.top("endless", "all", now).single().score)
                store.save(first, VerifiedReplay("campaign_8", 808L, 60), now, "new")
            }
            SqliteStore(jdbc).use { store ->
                // Nowy etap 8 pozostaje: migracja nie może ruszyć drugi raz.
                assertEquals(808L, store.top("campaign_8", "all", now).single().score)
                assertEquals(80L, store.top("campaign_12", "all", now).single().score)
            }
            DriverManager.getConnection(jdbc).use { c ->
                c.createStatement().use { s ->
                    s.executeQuery("SELECT version FROM schema_version WHERE id=1").use { rs ->
                        assertTrue(rs.next())
                        assertEquals(2, rs.getInt(1))
                    }
                }
            }
        } finally {
            db.delete()
            File(db.path + "-wal").delete()
            File(db.path + "-shm").delete()
        }
    }

    @Test fun playerLimiterCanBeForgotten() {
        val limiter = RateLimiter({ now })
        assertTrue(limiter.allow("post-player:abc", 1))
        assertFalse(limiter.allow("post-player:abc", 1))
        limiter.forget("post-player:abc")
        assertTrue(limiter.allow("post-player:abc", 1))
    }
}
