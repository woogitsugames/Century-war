package com.centurywar.server

import com.centurywar.game.Battle
import com.centurywar.game.BattleController
import com.centurywar.game.Replay
import com.centurywar.game.Role
import com.centurywar.game.Weekly
import com.centurywar.game.Daily
import com.centurywar.game.Modifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class WeeklyReplayVerifierTest {
    private val now = 1_800_000_000_000L
    private val current = Weekly.weekNumber(todayUtc(now))

    private fun submission(week: Long): ScoreSubmission {
        val cfg = Weekly.config(week)
        val battle = Battle(cfg)
        val commands = BattleController(battle)
        while (battle.stepCount < 900 && !battle.over) {
            if (battle.pendingCards != null) commands.card(0)
            if (battle.stepCount == 30) commands.queue(Role.MELEE)
            battle.step()
            battle.fx.count = 0
        }
        if (!battle.over) commands.surrender()
        return ScoreSubmission(
            board = "weekly",
            player = UUID.randomUUID().toString(),
            nick = "Dowodca",
            gameVersion = "0.1.0",
            day = week,
            seed = cfg.seed,
            replay = Replay.encode(commands.log()),
            score = battle.score(),
            research = List(com.centurywar.game.Research.defs.size) { 0 },
        )
    }

    @Test fun acceptsCurrentAndPreviousWeek() {
        for (week in listOf(current, current - 1)) {
            val input = submission(week)
            val result = ReplayVerifier.verify(input, now, "0.1.0")
            assertEquals("weekly_$week", result.board)
            assertEquals(input.score, result.score)
        }
    }

    @Test fun rejectsOldWeekAndWrongSeed() {
        val old = submission(current - 2)
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(old, now, "0.1.0")
        }
        val currentInput = submission(current)
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(currentInput.copy(seed = currentInput.seed + 1L), now, "0.1.0")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(currentInput.copy(score = currentInput.score + 1L), now, "0.1.0")
        }
    }
    @Test fun verifiesWeeklyReplayWithPostReleaseModifiers() {
        val releaseWeek = Weekly.weekNumber(Daily.RELEASE_DAY)
        val newcomers = setOf(Modifier.VETERANS, Modifier.EXPENSIVE_TOWERS, Modifier.MERCENARIES)
        val week = (releaseWeek until releaseWeek + 200L).first {
            Weekly.config(it).modifiers.any { modifier -> modifier in newcomers }
        }
        val nowMillis = (week * 7L - 3L) * 86_400_000L
        val input = submission(week)
        val verified = ReplayVerifier.verify(input, nowMillis, "0.1.0")
        assertEquals("weekly_" + week, verified.board)
        assertEquals(input.score, verified.score)
    }

}
