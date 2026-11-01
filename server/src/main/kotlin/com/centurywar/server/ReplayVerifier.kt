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
    private const val DAY_MS = 86_400_000L
    private const val DAILY_GRACE_MS = 2L * 60 * 60 * 1_000

    /** Zgłoszenie z wczoraj jest ważne do 01:59:59.999 UTC włącznie. */
    internal fun dailyDayAllowed(day: Long, nowMillis: Long): Boolean {
        val today = todayUtc(nowMillis)
        return day == today ||
            (day == today - 1L && Math.floorMod(nowMillis, DAY_MS) < DAILY_GRACE_MS)
    }

    fun verify(
        input: ScoreSubmission, nowMillis: Long, supportedVersion: String,
        budget: VerificationBudget? = null,
    ): VerifiedReplay {
        budget?.check()
        require(input.gameVersion == supportedVersion) { "unsupported_version" }
        validateSubmission(input)
        require(input.score > 0L) { "invalid_score" }

        val board = submittedBoard(input.board, input.stage, input.day)
        val log = try {
            Replay.decode(input.replay)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("invalid_replay", e)
        }
        budget?.check()
        require(log.steps in 60..Replay.MAX_STEPS) { "invalid_ticks" }
        require(budget == null || log.steps <= budget.maxReplaySteps) { "verification_step_limit" }
        require(log.commands.none { it.kind == Replay.Kind.SURRENDER && it.step < 60 }) { "early_surrender" }
        require(log.commands.none { it.kind == Replay.Kind.TUTORIAL_GOLD }) { "illegal_command" }

        val bonuses = Research.bonuses(input.research.toIntArray())
        val cfg = when (input.board) {
            "daily" -> {
                val day = requireNotNull(input.day) { "invalid_day" }
                require(dailyDayAllowed(day, nowMillis) && input.seed == day) { "invalid_day_or_seed" }
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
            Replay.verify(cfg, log, budget?.let { it::check })
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("illegal_replay", e)
        } catch (e: IndexOutOfBoundsException) {
            throw IllegalArgumentException("illegal_replay", e)
        }
        require(result.score == input.score) { "score_mismatch" }

        // Podane badania służą wyłącznie do sprawdzenia oryginalnej powtórki.
        // Wynik globalny przeliczamy drugi raz bez ulepszeń, niezależnie od deklaracji klienta.
        val normalizedScore = if (input.board in setOf("endless", "skirmish", "campaign") &&
            input.research.any { it != 0 }) {
            val zero = Research.bonuses(IntArray(Research.defs.size))
            val normalizedConfig = when (input.board) {
                "endless" -> BattleConfig(Mode.ENDLESS, input.seed, 1f, playerBonuses = zero)
                "skirmish" -> BattleConfig(Mode.SKIRMISH, input.seed,
                    Skirmish.power(input.difficulty!!), playerBonuses = zero)
                else -> Campaign.config(Campaign.stages[input.stage!!], zero, input.seed)
            }
            Replay.normalized(normalizedConfig, log, budget?.let { it::check }).score
        } else result.score
        budget?.check()
        return VerifiedReplay(board, normalizedScore, result.steps, input.research.sum())
    }
}
