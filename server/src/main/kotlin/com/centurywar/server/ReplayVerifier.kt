package com.centurywar.server

import com.centurywar.game.BattleConfig
import com.centurywar.game.Campaign
import com.centurywar.game.Daily
import com.centurywar.game.Mode
import com.centurywar.game.Replay
import com.centurywar.game.Research
import com.centurywar.game.Skirmish
import com.centurywar.game.Weekly

data class VerifiedReplay(val board: String, val score: Long, val steps: Int, val researchSum: Int = 0)

/** Odtwarza kod BattleController/Replay z symulacji bez udziału rendererów. */
object ReplayVerifier {
    private const val MAX_BATTLE_STEPS = 90_000
    private const val MAX_ENDLESS_STEPS = 216_000

    fun verify(input: ScoreSubmission, nowMillis: Long, supportedVersion: String): VerifiedReplay {
        require(input.gameVersion == supportedVersion) { "unsupported_version" }
        validateSubmission(input)
        require(input.score > 0L) { "invalid_score" }

        val board = submittedBoard(input.board, input.stage, input.day)
        val log = try {
            Replay.decode(input.replay)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("invalid_replay", e)
        }
        val stepsLimit = if (board == "endless" || board.startsWith("weekly_")) MAX_ENDLESS_STEPS else MAX_BATTLE_STEPS
        require(log.steps in 60..stepsLimit) { "invalid_ticks" }
        require(log.commands.none { it.kind == Replay.Kind.SURRENDER && it.step < 60 }) { "early_surrender" }
        require(board == "campaign_0" && input.tutorial ||
            log.commands.none { it.kind == Replay.Kind.TUTORIAL_GOLD }) { "illegal_command" }

        val bonuses = Research.bonuses(input.research.toIntArray())
        val cfg = when (input.board) {
            "daily" -> {
                val day = requireNotNull(input.day) { "invalid_day" }
                require(day == todayUtc(nowMillis) && input.seed == day) { "invalid_day_or_seed" }
                Daily.config(day, bonuses)
            }
            "weekly" -> {
                val week = requireNotNull(input.day) { "invalid_week" }
                val current = Weekly.weekNumber(todayUtc(nowMillis))
                require(week == current || week == current - 1L) { "invalid_week" }
                Weekly.config(week).also { require(input.seed == it.seed) { "invalid_week_seed" } }
            }
            "endless" -> {
                require(input.day == null) { "invalid_day" }
                BattleConfig(Mode.ENDLESS, input.seed, 1f, playerBonuses = bonuses)
            }
            "skirmish" -> {
                require(input.day == null) { "invalid_day" }
                BattleConfig(Mode.SKIRMISH, input.seed, Skirmish.power(input.difficulty!!), playerBonuses = bonuses)
            }
            "campaign" -> {
                require(input.day == null) { "invalid_day" }
                val stage = requireNotNull(input.stage) { "invalid_stage" }
                Campaign.config(Campaign.stages[stage], bonuses, input.seed, tutorial = input.tutorial)
            }
            else -> throw IllegalArgumentException("invalid_board")
        }

        val result = try {
            Replay.verify(cfg, log)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("illegal_replay", e)
        } catch (e: IndexOutOfBoundsException) {
            throw IllegalArgumentException("illegal_replay", e)
        }
        require(result.score == input.score) { "score_mismatch" }
        return VerifiedReplay(board, result.score, result.steps, input.research.sum())
    }
}
