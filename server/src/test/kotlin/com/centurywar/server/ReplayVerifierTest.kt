package com.centurywar.server

import com.centurywar.game.Battle
import com.centurywar.game.Ai
import com.centurywar.game.DailyEvent
import com.centurywar.game.BattleConfig
import com.centurywar.game.BattleController
import com.centurywar.game.Bonuses
import com.centurywar.game.Campaign
import com.centurywar.game.Daily
import com.centurywar.game.Weather
import com.centurywar.game.Mode
import com.centurywar.game.Replay
import com.centurywar.game.Research
import com.centurywar.game.SimVersion
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
                gameVersion = SimVersion.PROTOCOL,
                seed = seed,
                replay = Replay.encode(controller.log()),
                score = battle.score(),
                research = List(Research.defs.size) { 0 },
            )
        }

        fun replayOf(input: ScoreSubmission): Replay.Log = Replay.decode(input.replay)
        fun withLog(input: ScoreSubmission, log: Replay.Log) = input.copy(replay = Replay.encode(log))
    }

    private fun weatherSubmission(
        cfg: com.centurywar.game.BattleConfig,
        board: String,
        stage: Int? = null,
        day: Long? = null,
    ): ScoreSubmission {
        val battle = Battle(cfg)
        val controller = BattleController(battle)
        while (!battle.over && battle.stepCount < 60 * 180) {
            if (battle.pendingVariant != null) controller.variant(0)
            if (battle.pendingChoice != null) controller.doctrine(0)
            if (battle.pendingCards != null) controller.card(0)
            if (battle.stepCount % 90 == 0 && battle.canQueue(battle.player, Role.MELEE)) {
                controller.queue(Role.MELEE)
            }
            battle.step()
            battle.fx.count = 0
        }
        if (!battle.over) controller.surrender()
        return ScoreSubmission(
            board = board,
            player = UUID.randomUUID().toString(),
            nick = "Dowodca",
            gameVersion = "0.1.0",
            day = day,
            seed = cfg.seed,
            stage = stage,
            replay = Replay.encode(controller.log()),
            score = battle.score(),
            research = List(Research.defs.size) { 0 },
        )
    }

    @Test fun verifiesCampaignWithRain() {
        val stage = Campaign.stages[4]
        val cfg = Campaign.config(stage, Research.bonuses(IntArray(Research.defs.size)), 9991L)
        assertEquals(Weather.RAIN, cfg.weather)
        val submission = weatherSubmission(cfg, "campaign", stage = 4)
        val verified = ReplayVerifier.verify(submission, now, "0.1.0")
        assertEquals(submission.score, verified.score)
    }

    @Test fun verifiesDailyWithIndependentWeather() {
        val day = (Daily.RELEASE_DAY until Daily.RELEASE_DAY + 20L).first {
            Daily.config(it, Bonuses()).weather != Weather.CLEAR
        }
        val cfg = Daily.config(day, Research.bonuses(IntArray(Research.defs.size)))
        val submission = weatherSubmission(cfg, "daily", day = day)
        val verified = ReplayVerifier.verify(submission, day * 86_400_000L + 1_000L, "0.1.0")
        assertEquals(submission.score, verified.score)
    }


    @Test fun serverVerifiesSpecialDayAndRejectsForgedEvent() {
        val day = (Daily.RELEASE_DAY + 3L..Daily.RELEASE_DAY + 720L step 4L)
            .first { Daily.config(it, Bonuses()).dailyEvent == DailyEvent.FREE_BARRAGE }
        val cfg = Daily.config(day, Research.bonuses(IntArray(Research.defs.size)))
        val battle = Battle(cfg)
        val controller = BattleController(battle)
        battle.playerAi = Ai(battle, 0)
        while (!battle.over && battle.stepCount < 60 * 240) {
            battle.step()
            battle.fx.count = 0
        }
        if (!battle.over) controller.surrender()
        org.junit.jupiter.api.Assertions.assertTrue(battle.score() > 0L)
        val log = controller.log()
        assertEquals(DailyEvent.FREE_BARRAGE, log.dailyEvent)
        val input = ScoreSubmission(
            board = "daily", player = UUID.randomUUID().toString(), nick = "Dowodca",
            gameVersion = "0.1.0", day = day, seed = day,
            replay = Replay.encode(log), score = battle.score(),
            research = List(Research.defs.size) { 0 },
        )
        val verified = ReplayVerifier.verify(input, day * 86_400_000L + 1_000L, "0.1.0")
        assertEquals(input.score, verified.score)

        val forged = input.copy(replay = Replay.encode(log.copy(dailyEvent = null)))
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(forged, day * 86_400_000L + 1_000L, "0.1.0")
        }
    }

    @Test fun verifiesRealScoreFromSim() {
        val input = validEndless()
        val result = ReplayVerifier.verify(input, now, "0.5.0")
        assertEquals("endless", result.board)
        assertEquals(input.score, result.score)
    }

    @Test fun rejectsForgedScore() {
        val input = validEndless()
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(input.copy(score = input.score + 1000L), now, "0.5.0")
        }
    }

    @Test fun rejectsIllegalCommand() {
        val input = validEndless()
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(
                withLog(input, replayOf(input).let { log ->
                    log.copy(commands = listOf(Replay.Command(0, Replay.Kind.BUY_TURRET, 3)) + log.commands)
                }),
                now, "0.5.0",
            )
        }
    }

    @Test fun rejectsUnfinishedAndOverlongReplay() {
        val input = validEndless()
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(withLog(input, replayOf(input).copy(commands = emptyList())), now, "0.5.0")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Replay.encode(replayOf(input).copy(steps = Replay.MAX_STEPS + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(input.copy(replay = "!!nie-base64!!"), now, "0.5.0")
        }
    }

    @Test fun dailyAcceptsPreviousDayUntilTwoHoursUtc() {
        val day = 21_000L
        val cfg = Daily.config(day, Bonuses())
        val input = weatherSubmission(cfg, "daily", day = day)
        val midnight = (day + 1L) * 86_400_000L
        assertEquals(input.score, ReplayVerifier.verify(input, midnight, "0.1.0").score)
        assertEquals(input.score,
            ReplayVerifier.verify(input, midnight + 2L * 60L * 60L * 1000L - 1L, "0.1.0").score)
        val error = assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(input, midnight + 2L * 60L * 60L * 1000L, "0.1.0")
        }
        assertEquals("invalid_day_or_seed", error.message)
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(input, midnight + 86_400_000L, "0.1.0")
        }
    }

    @Test fun tutorialIsNeverEligibleForRanking() {
        val input = validEndless().copy(board = "campaign", stage = 0, tutorial = true)
        val error = assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(input, now, SimVersion.PROTOCOL)
        }
        assertEquals("invalid_tutorial", error.message)
        val full = validEndless()
        val gold = replayOf(full).copy(commands = listOf(
            Replay.Command(0, Replay.Kind.TUTORIAL_GOLD, 100),
        ) + replayOf(full).commands)
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(withLog(full, gold), now, SimVersion.PROTOCOL)
        }
    }

    @Test fun replayStepLimitIsSharedWithSimulation() {
        val sample = validEndless()
        val log = replayOf(sample)
        assertEquals(432_000, Replay.MAX_STEPS)
        assertEquals(216_001, Replay.decode(Replay.encode(log.copy(steps = 216_001))).steps)
        assertEquals(Replay.MAX_STEPS, Replay.decode(
            Replay.encode(log.copy(steps = Replay.MAX_STEPS))).steps)
        assertThrows(IllegalArgumentException::class.java) {
            Replay.encode(log.copy(steps = Replay.MAX_STEPS + 1))
        }
    }

    @Test fun dailyRequiresServerDaySeed() {
        val input = validEndless()
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(
                input.copy(board = "daily", day = todayUtc(now), seed = todayUtc(now) + 1),
                now, "0.5.0",
            )
        }
    }

    @Test fun rejectsChangedSimulationVersion() {
        val input = validEndless()
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(input.copy(gameVersion = "9.9.9"), now, "0.5.0")
        }
    }

    @Test fun rejectsEarlySurrenderAndTutorialGoldOutsideTutorial() {
        val input = validEndless(steps = 30)
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(input, now, "0.5.0")
        }
        val full = validEndless()
        val log = replayOf(full)
        assertThrows(IllegalArgumentException::class.java) {
            ReplayVerifier.verify(
                withLog(full, log.copy(commands = listOf(Replay.Command(0, Replay.Kind.TUTORIAL_GOLD, 15)) + log.commands)),
                now, "0.5.0",
            )
        }
    }
}
