package com.centurywar.server

import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
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
        ): ScoreSubmission {
            val ticks = 900
            val battle = Battle(BattleConfig(Mode.ENDLESS, seed, 1f))
            repeat(ticks) { battle.step(); battle.fx.count = 0 }
            battle.surrender()
            return ScoreSubmission(
                board = "endless",
                player = player,
                nick = "Dowodca",
                gameVersion = "0.1.0",
                seed = seed,
                ticks = ticks,
                commands = listOf(ReplayCommand(ticks, "SURRENDER")),
                score = battle.score(),
            )
        }
    }

    @Test fun verifiesRealScoreFromSim() {
        val input = validEndless()
        val result = ReplayVerifier.verify(input, now, "0.1.0")
        assertEquals("endless", result.board)
        assertEquals(input.score, result.score)
    }

    @Test fun rejectsForgedScore() {
        val input = validEndless()
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(input.copy(score = input.score + 1000L), now, "0.1.0")
        }
    }

    @Test fun rejectsIllegalCommand() {
        val input = validEndless()
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(
                input.copy(commands = listOf(ReplayCommand(0, "BUY_TURRET", 3), ReplayCommand(900, "SURRENDER"))),
                now, "0.1.0",
            )
        }
    }

    @Test fun rejectsUnfinishedAndOverlongReplay() {
        val input = validEndless()
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(input.copy(commands = emptyList()), now, "0.1.0")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(input.copy(ticks = 216_001), now, "0.1.0")
        }
    }

    @Test fun dailyRequiresServerDaySeed() {
        val input = validEndless()
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(
                input.copy(board = "daily", day = todayUtc(now), seed = todayUtc(now) + 1),
                now, "0.1.0",
            )
        }
    }

    @Test fun rejectsChangedSimulationVersion() {
        val input = validEndless()
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(input.copy(gameVersion = "9.9.9"), now, "0.1.0")
        }
    }
}
