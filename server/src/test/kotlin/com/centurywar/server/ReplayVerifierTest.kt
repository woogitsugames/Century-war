package com.centurywar.server

import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleController
import com.centurywar.game.Mode
import com.centurywar.game.Replay
import com.centurywar.game.Research
import com.centurywar.game.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class ReplayVerifierTest {
    private val now = 1_800_000_000_000L

    companion object {
        fun validEndless(
            player: String = UUID.randomUUID().toString(),
            seed: Long = 12345L,
            steps: Int = 900,
        ): ScoreSubmission {
            val battle = Battle(BattleConfig(Mode.ENDLESS, seed, 1f,
                playerBonuses = Research.bonuses(IntArray(Research.defs.size))))
            battle.playerAi = null
            val controller = BattleController(battle)
            while (battle.stepCount < steps) {
                if (battle.pendingChoice != null) controller.doctrine(0)
                if (battle.pendingCards != null) controller.card(0)
                if (battle.stepCount == 30) controller.queue(Role.MELEE)
                battle.step()
                battle.fx.count = 0
            }
            controller.surrender()
            return ScoreSubmission(
                board = "endless",
                player = player,
                nick = "Dowodca",
                gameVersion = "0.2.0",
                seed = seed,
                replay = Replay.encode(controller.log()),
                score = battle.score(),
                research = List(Research.defs.size) { 0 },
            )
        }

        fun replayOf(input: ScoreSubmission): Replay.Log = Replay.decode(input.replay)
        fun withLog(input: ScoreSubmission, log: Replay.Log) = input.copy(replay = Replay.encode(log))
    }

    @Test fun verifiesRealScoreFromSim() {
        val input = validEndless()
        val result = ReplayVerifier.verify(input, now, "0.2.0")
        assertEquals("endless", result.board)
        assertEquals(input.score, result.score)
    }

    @Test fun rejectsForgedScore() {
        val input = validEndless()
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(input.copy(score = input.score + 1000L), now, "0.2.0")
        }
    }

    @Test fun rejectsIllegalCommand() {
        val input = validEndless()
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(
                withLog(input, replayOf(input).let { log ->
                    log.copy(commands = listOf(Replay.Command(0, Replay.Kind.BUY_TURRET, 3)) + log.commands)
                }),
                now, "0.2.0",
            )
        }
    }

    @Test fun rejectsUnfinishedAndOverlongReplay() {
        val input = validEndless()
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(withLog(input, replayOf(input).copy(commands = emptyList())), now, "0.2.0")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(withLog(input, replayOf(input).copy(steps = 216_001)), now, "0.2.0")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(input.copy(replay = "!!nie-base64!!"), now, "0.2.0")
        }
    }

    @Test fun dailyRequiresServerDaySeed() {
        val input = validEndless()
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(
                input.copy(board = "daily", day = todayUtc(now), seed = todayUtc(now) + 1),
                now, "0.2.0",
            )
        }
    }

    @Test fun rejectsChangedSimulationVersion() {
        val input = validEndless()
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(input.copy(gameVersion = "9.9.9"), now, "0.2.0")
        }
    }

    @Test fun rejectsEarlySurrenderAndTutorialGoldOutsideTutorial() {
        val input = validEndless(steps = 30)
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(input, now, "0.2.0")
        }
        val full = validEndless()
        val log = replayOf(full)
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(
                withLog(full, log.copy(commands = listOf(Replay.Command(0, Replay.Kind.TUTORIAL_GOLD, 15)) + log.commands)),
                now, "0.2.0",
            )
        }
    }
}
