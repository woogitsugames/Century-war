package com.centurywar.server

import com.centurywar.game.Ai
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleController
import com.centurywar.game.BattleSnapshot
import com.centurywar.game.Campaign
import com.centurywar.game.Daily
import com.centurywar.game.Mode
import com.centurywar.game.Replay
import com.centurywar.game.Research
import com.centurywar.game.Skirmish
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ResearchSubmissionTest {
    private val now = 1_800_000_000_000L
    private val version = "0.4.0"
    private val json = Json { encodeDefaults = true }
    private val full = IntArray(Research.defs.size) { Research.defs[it].max }

    /** Bitwa z aplikacji: komendy przez kontroler, opcjonalny zrzut stanu, bez sieci. */
    private fun played(board: String, levels: IntArray, seed: Long = 123456L,
                       difficulty: Int = 0, tutorial: Boolean = false,
                       resume: Boolean = false): ScoreSubmission {
        val bonuses = Research.bonuses(levels)
        val cfg = when (board) {
            "endless" -> BattleConfig(Mode.ENDLESS, seed, 1f, playerBonuses = bonuses)
            "skirmish" -> BattleConfig(Mode.SKIRMISH, seed, Skirmish.power(difficulty), playerBonuses = bonuses)
            "campaign" -> Campaign.config(Campaign.stages[0], bonuses, seed, tutorial)
            "daily" -> Daily.config(todayUtc(now), bonuses)
            else -> error("board")
        }
        var battle = Battle(cfg)
        var commands = BattleController(battle)
        battle.playerAi = Ai(battle, 0)
        var didResume = false
        while (!battle.over && battle.stepCount < 32_000) {
            if (resume && !didResume && battle.stepCount >= 125) {
                val restored = BattleSnapshot.decode(BattleSnapshot.encode(battle, board == "daily"))
                assertNotNull(restored)
                battle = restored!!.battle
                commands = battle.commandController!!
                didResume = true
            }
            if (battle.stepCount >= 900 && (board == "endless" || battle.player.killValue > 0)) break
            battle.step()
            battle.fx.count = 0
        }
        if (!battle.over) assertTrue(commands.surrender())
        assertTrue(battle.score() > 0L, "Musi powstać wynik możliwy do zapisania")
        val log = commands.log()
        assertTrue(log.steps in 60..90_000)
        return ScoreSubmission(
            board = board,
            player = UUID.randomUUID().toString(),
            nick = "Testowy",
            gameVersion = version,
            day = if (board == "daily") todayUtc(now) else null,
            seed = if (board == "daily") todayUtc(now) else seed,
            stage = if (board == "campaign") 0 else null,
            replay = Replay.encode(log),
            score = battle.score(),
            research = if (board == "daily") List(Research.defs.size) { 0 } else levels.toList(),
            difficulty = if (board == "skirmish") difficulty else null,
            tutorial = tutorial,
        )
    }

    @Test fun verifierAcceptsClientReplayInAllFourModes() {
        for (board in listOf("endless", "skirmish", "campaign", "daily")) {
            val levels = if (board == "daily") IntArray(Research.defs.size) else full
            val input = played(board, levels, resume = board == "skirmish")
            val wire = json.decodeFromString<ScoreSubmission>(json.encodeToString(input))
            val result = ReplayVerifier.verify(wire, now, version)
            assertEquals(input.score, result.score, board)
            assertEquals(if (board == "campaign") "campaign_0" else board, result.board)
            assertEquals(levels.sum(), result.researchSum)
        }
    }

    @Test fun snapshotPreservesCompleteClientReplay() {
        val original = played("skirmish", full, seed = 889L)
        val restored = played("skirmish", full, seed = 889L, resume = true)
        assertEquals(original.replay, restored.replay)
        assertEquals(original.score, restored.score)
    }

    @Test fun modifiedResearchInvalidatesAuthenticatedReplay() {
        val research = IntArray(Research.defs.size)
        research[1] = 1 // Zryw odblokowany w badaniach.
        val cfg = BattleConfig(Mode.ENDLESS, 441L, 1f, playerBonuses = Research.bonuses(research))
        val battle = Battle(cfg)
        val controller = BattleController(battle)
        assertTrue(controller.rally())
        repeat(900) { battle.step(); battle.fx.count = 0 }
        assertTrue(controller.surrender())
        val input = ScoreSubmission("endless", UUID.randomUUID().toString(), "Dowodca", version,
            seed = 441L, replay = Replay.encode(controller.log()), score = battle.score(),
            research = research.toList())
        assertEquals(input.score, ReplayVerifier.verify(input, now, version).score)
        val changed = input.copy(research = List(research.size) { 0 })
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(changed, now, version)
        }
    }

    @Test fun invalidResearchAndDifficultyAreRejectedBeforeSimulation() {
        val input = ReplayVerifierTest.validEndless()
        assertThrows(IllegalArgumentException::class.java) {
            validateSubmission(input.copy(research = listOf(-1) + List(12) { 0 }))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateSubmission(input.copy(research = listOf(Research.defs[0].max + 1) + List(12) { 0 }))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateSubmission(input.copy(research = listOf(0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateSubmission(input.copy(board = "skirmish", difficulty = 4))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateSubmission(input.copy(tutorial = true))
        }
    }
}
