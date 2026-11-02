package com.centurywar.server

import com.centurywar.game.Ai
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleController
import com.centurywar.game.Mode
import com.centurywar.game.Replay
import com.centurywar.game.Research
import com.centurywar.game.SimVersion
import com.centurywar.game.Skirmish
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** A156-04: bieżący ranking przyjmuje tylko format Replay o zasadach bieżącego protokołu. */
class RankedReplayFormatTest {
    private val now = 1_793_577_600_000L
    private val hmacKey = "test-secret-at-least-32-characters-long!".toByteArray()
    private val ownerSecret = "a1".repeat(32)

    /** Potyczka jak w audycie: przebieg bez stopni wież i dowódcy, oznaczony jako format [format]. */
    private fun legacySkirmish(format: Int, steps: Int = 3_000): ScoreSubmission {
        val bonuses = Research.bonuses(IntArray(Research.defs.size))
        val cfg = BattleConfig(Mode.SKIRMISH, 42L, Skirmish.power(1), playerBonuses = bonuses)
        val battle = Battle(cfg, turretTiersEnabled = format >= 6, commanderEnabled = format >= 7)
        val controller = BattleController(battle)
        battle.playerAi = Ai(battle, 0)
        while (battle.stepCount < steps && battle.winner == -1) {
            battle.step()
            battle.fx.count = 0
        }
        if (battle.winner == -1) controller.surrender()
        val log = controller.log().copy(version = format)
        return ScoreSubmission(
            board = "skirmish", player = UUID.randomUUID().toString(), nick = "Audit156",
            gameVersion = SimVersion.PROTOCOL, seed = 42L, replay = Replay.encode(log),
            score = Replay.verify(cfg, Replay.decode(Replay.encode(log))).score,
            research = List(Research.defs.size) { 0 }, difficulty = 1,
        )
    }

    @Test fun currentProtocolRanksOnlyCurrentReplayFormat() {
        assertEquals(setOf(Replay.VERSION), SimVersion.RANKED_REPLAY_FORMATS)
    }

    @Test fun oldFormatWithCurrentGameVersionIsRejectedWithClearCode() {
        for (format in Replay.MIN_VERSION until Replay.VERSION) {
            val old = legacySkirmish(format)
            // Bez powiązania formatu z wersją zasad dawny weryfikator przyjąłby ten wynik.
            assertTrue(ReplayVerifier.verify(old, now, SimVersion.PROTOCOL,
                rankedFormats = (Replay.MIN_VERSION..Replay.VERSION).toSet()).score > 0L)
            val error = assertThrows(IllegalArgumentException::class.java) {
                ReplayVerifier.verify(old, now, SimVersion.PROTOCOL)
            }
            assertEquals(UNSUPPORTED_REPLAY_FORMAT, error.message)
        }
        val current = legacySkirmish(Replay.VERSION)
        assertEquals(current.score, ReplayVerifier.verify(current, now, SimVersion.PROTOCOL).score)
    }

    private fun sign(body: String, nonce: String): String {
        val base = listOf((now / 1000L).toString(), nonce, "POST", "/v1/score", body).joinToString("\n") +
            "\n" + ownerSecret
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKey, "HmacSHA256"))
        return mac.doFinal(base.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    @Test fun apiReturnsUnsupportedReplayFormatForOldReplay() = testApplication {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            application { leaderboardModule(store, hmacKey, { now }) }
            suspend fun send(input: ScoreSubmission) = client.post("/v1/score") {
                val body = Json.encodeToString(input)
                val nonce = UUID.randomUUID().toString()
                header("X-CW-Timestamp", (now / 1000L).toString())
                header("X-CW-Nonce", nonce)
                header("X-CW-Player-Secret", ownerSecret)
                header("X-CW-Signature", sign(body, nonce))
                setBody(body)
            }
            val old = send(legacySkirmish(5))
            assertEquals(HttpStatusCode.UnprocessableEntity, old.status)
            assertTrue(old.bodyAsText().contains(UNSUPPORTED_REPLAY_FORMAT))
            assertEquals(0L, store.countScores())

            val current = send(legacySkirmish(Replay.VERSION))
            assertEquals(HttpStatusCode.OK, current.status)
            assertEquals(1L, store.countScores())
        }
    }
}
