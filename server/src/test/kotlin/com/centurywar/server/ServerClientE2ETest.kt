package com.centurywar.server

import com.centurywar.game.Ai
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleController
import com.centurywar.game.Bonuses
import com.centurywar.game.Campaign
import com.centurywar.game.Daily
import com.centurywar.game.Mode
import com.centurywar.game.Replay
import com.centurywar.game.Research
import com.centurywar.game.Weekly
import com.centurywar.meta.ScoreQueue
import com.centurywar.meta.ScoreWire
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pełna ścieżka: symulacja + rejestracja rozkazów, kodowanie i HMAC klienta,
 * testowy transport Ktor, weryfikacja serwera, SQLite oraz odczyt rankingu.
 * Transport HTTP jest w pamięci; test nie zastępuje osobnego testu TLS/Androida.
 */
class ServerClientE2ETest {
    private val now = 1_800_000_000_000L
    private val key = "test-secret-at-least-32-characters-long!".toByteArray(Charsets.UTF_8)
    private val installation = "a1".repeat(32)
    private val levels = IntArray(Research.defs.size)
    private val json = Json

    private data class Run(val config: BattleConfig, val log: Replay.Log, val score: Long)

    /** Bot używa tej samej ścieżki BattleController co gra na urządzeniu. */
    private fun play(config: BattleConfig): Run {
        val battle = Battle(config)
        val controller = BattleController(battle)
        battle.playerAi = Ai(battle, 0)
        while (!battle.over && battle.stepCount < 60 * 180) {
            val before = battle.stepCount
            battle.step()
            assertTrue(battle.over || battle.stepCount > before, "Bot utknął na wyborze")
            battle.fx.count = 0
            if (!battle.over && battle.stepCount >= 900 && battle.score() > 0L) {
                assertTrue(controller.surrender())
            }
        }
        if (!battle.over) assertTrue(controller.surrender())
        val log = controller.log()
        assertTrue(log.steps >= 60, "Za krótka bitwa rankingowa")
        assertTrue(log.commands.isNotEmpty(), "Bot nie wydał żadnego rozkazu")
        assertTrue(battle.score() > 0L, "Bot nie osiągnął dodatniego wyniku")
        assertEquals(battle.score(), Replay.verify(config, log).score)
        return Run(config, log, battle.score())
    }

    private fun payload(run: Run, player: String = UUID.randomUUID().toString()): String =
        ScoreWire.submission(run.config, run.log, run.score, "Dowodca", player, levels, 0)

    private fun HttpRequestBuilder.signed(
        method: String, uri: String, body: String = "", install: String? = null,
        nonce: String = UUID.randomUUID().toString(),
    ) {
        val auth = ScoreWire.headers(
            key, (now / 1_000L).toString(), nonce, method, uri, body, install,
        )
        header("X-CW-Timestamp", auth.timestamp)
        header("X-CW-Nonce", auth.nonce)
        if (auth.installationSecret != null) header("X-CW-Player-Secret", auth.installationSecret)
        header("X-CW-Signature", auth.signature)
    }

    private suspend fun HttpClient.sendScore(
        body: String, nonce: String = UUID.randomUUID().toString(),
    ): HttpResponse = post("/v1/score") {
        signed("POST", "/v1/score", body, installation, nonce)
        setBody(body)
    }

    private suspend fun HttpClient.top(board: String): RankingResponse {
        val uri = "/v1/top?board=" + board + "&period=all"
        val response = get(uri) { signed("GET", uri) }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString<RankingResponse>(response.bodyAsText())
    }

    private suspend fun HttpClient.around(board: String, player: String): RankingResponse {
        val uri = "/v1/around?board=" + board + "&period=all&player=" + player
        val response = get(uri) { signed("GET", uri, install = installation) }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString<RankingResponse>(response.bodyAsText())
    }

    /** Osobny plik SQLite, zamykany i usuwany po teście. */
    private fun withStore(block: (SqliteStore) -> Unit) {
        val db = Files.createTempFile("cw-client-e2e-", ".sqlite")
        try {
            SqliteStore("jdbc:sqlite:" + db, PublicPlayerIds(ByteArray(32) { 7 })).use(block)
        } finally {
            Files.deleteIfExists(db)
            Files.deleteIfExists(db.resolveSibling(db.fileName.toString() + "-wal"))
            Files.deleteIfExists(db.resolveSibling(db.fileName.toString() + "-shm"))
        }
    }

    @Test
    fun fourRealBattlesTravelThroughClientAndServerToRanking() = withStore { store ->
        testApplication {
            application { leaderboardModule(store, key, { now }) }
            val today = todayUtc(now)
            val week = Weekly.weekNumber(today)
            val runs = listOf(
                play(Campaign.config(Campaign.stages[0], Research.bonuses(levels), 12901L)),
                play(BattleConfig(Mode.ENDLESS, 12902L, 1f, playerBonuses = Research.bonuses(levels))),
                play(Daily.config(today, Bonuses())),
                play(Weekly.config(week)),
            )
            val expectedBoards = listOf("campaign_0", "endless", "daily", "weekly_" + week)
            val bodies = ArrayList<String>()

            for ((index, run) in runs.withIndex()) {
                val player = UUID.randomUUID().toString()
                val body = payload(run, player)
                bodies.add(body)
                val decoded = json.decodeFromString<ScoreSubmission>(body)
                assertEquals(run.config.seed, decoded.seed)
                assertEquals(run.score, decoded.score)
                assertEquals(run.log, Replay.decode(decoded.replay))
                assertEquals(List(levels.size) { 0 }, decoded.research)

                val response = client.sendScore(body)
                assertEquals(HttpStatusCode.OK, response.status, expectedBoards[index])
                val receipt = json.decodeFromString<ScoreReceipt>(response.bodyAsText())
                assertEquals(expectedBoards[index], receipt.board)
                assertEquals(run.score, receipt.verifiedScore)
                assertEquals(run.score, receipt.bestScore)
                assertTrue(receipt.accepted)

                val top = client.top(receipt.board)
                assertEquals(1, top.entries.size)
                assertEquals(run.score, top.entries.single().score)
                assertFalse(top.entries.single().player == player)
                assertEquals(24, top.entries.single().player.length)

                val around = client.around(receipt.board, player)
                assertEquals(1, around.entries.size)
                assertEquals(top.entries.single(), around.entries.single())
                assertEquals(top.entries.single().player, around.self)
            }

            // W identycznym żądaniu z nowym nonce wynik nie może się zdublować.
            val repeat = client.sendScore(bodies[1])
            assertEquals(HttpStatusCode.OK, repeat.status)
            assertFalse(json.decodeFromString<ScoreReceipt>(repeat.bodyAsText()).accepted)
            assertEquals(1, client.top("endless").entries.size)

            // Daily przyjmuje tylko pierwszą zweryfikowaną próbę.
            val secondDaily = client.sendScore(bodies[2])
            assertEquals(HttpStatusCode.Conflict, secondDaily.status)
            assertTrue(secondDaily.bodyAsText().contains("daily_already_submitted"))

            val original = json.decodeFromString<ScoreSubmission>(bodies[1])
            assertEquals(HttpStatusCode.UnprocessableEntity,
                client.sendScore(json.encodeToString(original.copy(score = original.score + 1))).status)
            assertEquals(HttpStatusCode.BadRequest,
                client.sendScore(json.encodeToString(original.copy(gameVersion = "9.9.9"))).status)

            // W daily seed jest obowiązkowo równy numerowi dnia.
            val daily = json.decodeFromString<ScoreSubmission>(bodies[2])
            assertEquals(HttpStatusCode.UnprocessableEntity,
                client.sendScore(json.encodeToString(daily.copy(seed = daily.seed + 1))).status)

            // Ponowne użycie nonce to 401, także gdy body i podpis są poprawne.
            val nonce = UUID.randomUUID().toString()
            assertEquals(HttpStatusCode.OK, client.sendScore(bodies[1], nonce).status)
            assertEquals(HttpStatusCode.Unauthorized, client.sendScore(bodies[1], nonce).status)
            assertEquals(4, store.countScores())
        }
    }

    @Test
    fun quota429KeepsClientQueueAndReturnsRetryAfter() = withStore { store ->
        testApplication {
            application {
                leaderboardModule(store, key, { now }, limits = RateLimits(
                    postIp = 100, postPlayer = 100, postInstallDaily = 1, postIpDaily = 100,
                ))
            }
            val sample = ReplayVerifierTest.validEndless()
            val cfg = BattleConfig(Mode.ENDLESS, sample.seed, 1f, playerBonuses = Research.bonuses(levels))
            val body = ScoreWire.submission(cfg, Replay.decode(sample.replay), sample.score,
                sample.nick, sample.player, levels, 0)
            val queue = ScoreQueue()
            queue.add(ScoreQueue.Entry(now, body), now)
            assertEquals(HttpStatusCode.OK, client.sendScore(queue.entries.first().payload).status)
            assertNull(queue.recordResponse(200))
            assertTrue(queue.entries.isEmpty())

            queue.add(ScoreQueue.Entry(now, body), now)
            val limited = client.sendScore(queue.entries.first().payload)
            assertEquals(HttpStatusCode.TooManyRequests, limited.status)
            assertTrue(limited.bodyAsText().contains("daily_quota_exceeded"))
            assertNotNull(limited.headers["Retry-After"])
            assertTrue(limited.headers["Retry-After"]!!.toLong() > 0L)
            assertEquals(4_000L, queue.recordResponse(limited.status.value))
            assertEquals(body, queue.entries.single().payload)
            assertEquals(1, queue.entries.single().attempts)
        }
    }

    @Test
    fun server503IsRetriedByClientQueueAfterBackoff() = withStore { store ->
        val verifications = AtomicInteger()
        testApplication {
            application {
                leaderboardModule(store, key, { now },
                    verifyReplay = { input, time, version, budget ->
                        if (verifications.incrementAndGet() == 1) throw VerificationBudgetExceeded()
                        ReplayVerifier.verify(input, time, version, budget)
                    })
            }
            val sample = ReplayVerifierTest.validEndless()
            val cfg = BattleConfig(Mode.ENDLESS, sample.seed, 1f, playerBonuses = Research.bonuses(levels))
            val body = ScoreWire.submission(cfg, Replay.decode(sample.replay), sample.score,
                sample.nick, sample.player, levels, 0)
            val queue = ScoreQueue()
            queue.add(ScoreQueue.Entry(now, body), now)

            val unavailable = client.sendScore(queue.entries.single().payload)
            assertEquals(HttpStatusCode.ServiceUnavailable, unavailable.status)
            assertEquals("30", unavailable.headers["Retry-After"])
            val delay = queue.recordResponse(unavailable.status.value)
            assertEquals(4_000L, delay)
            assertEquals(1, queue.entries.single().attempts)
            val nextAttemptAt = now + delay!!
            var testClock = now
            assertTrue(testClock < nextAttemptAt, "Próba nie może być natychmiastowa")
            testClock = nextAttemptAt

            // Symulacja upływu czasu bez sleep; żądanie ma nowy podpis i nonce.
            assertTrue(testClock >= nextAttemptAt)
            val accepted = client.sendScore(queue.entries.single().payload)
            assertEquals(HttpStatusCode.OK, accepted.status)
            assertNull(queue.recordResponse(accepted.status.value))
            assertTrue(queue.entries.isEmpty())
            assertEquals(2, verifications.get())
            assertEquals(1, store.countScores())
        }
    }
}
