package com.centurywar.server

import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Bonuses
import com.centurywar.game.Campaign
import com.centurywar.game.Daily
import com.centurywar.game.Mode
import com.centurywar.game.Role

data class VerifiedReplay(val board: String, val score: Long)

/** Powtórkę odtwarza zawsze kod produkcyjnej symulacji JVM. */
object ReplayVerifier {
    const val PROTOCOL_VERSION = 1
    const val MAX_COMMANDS = 20_000

    fun verify(input: ScoreSubmission, nowMillis: Long, supportedVersion: String): VerifiedReplay {
        require(input.gameVersion == supportedVersion) { "unsupported_version" }
        require(input.replayVersion == PROTOCOL_VERSION) { "unsupported_replay" }
        require(input.score > 0L) { "invalid_score" }
        require(input.commands.size <= MAX_COMMANDS) { "too_many_commands" }

        val board = submittedBoard(input.board, input.stage)
        val ticksLimit = if (board == "endless") 216_000 else 90_000
        require(input.ticks in 60..ticksLimit) { "invalid_ticks" }

        val cfg = when (input.board) {
            "daily" -> {
                val day = requireNotNull(input.day) { "invalid_day" }
                require(day == todayUtc(nowMillis) && input.seed == day) { "invalid_day_or_seed" }
                Daily.config(day, Bonuses())
            }
            "endless" -> {
                require(input.day == null) { "invalid_day" }
                BattleConfig(Mode.ENDLESS, input.seed, 1f)
            }
            "skirmish" -> {
                require(input.day == null) { "invalid_day" }
                BattleConfig(Mode.SKIRMISH, input.seed, 1f)
            }
            "campaign" -> {
                require(input.day == null) { "invalid_day" }
                val stage = requireNotNull(input.stage) { "invalid_stage" }
                Campaign.config(Campaign.stages[stage], Bonuses(), input.seed)
            }
            else -> throw IllegalArgumentException("invalid_board")
        }

        val battle = Battle(cfg)
        var index = 0
        var lastTick = -1
        var actionsThisTick = 0
        val deadline = System.nanoTime() + 12_000_000_000L
        for (tick in 0..input.ticks) {
            while (index < input.commands.size && input.commands[index].tick == tick) {
                val command = input.commands[index++]
                if (lastTick != tick) {
                    lastTick = tick
                    actionsThisTick = 0
                }
                require(++actionsThisTick <= 32) { "too_many_actions_per_tick" }
                require(!battle.over) { "command_after_end" }
                if (battle.pendingChoice != null) {
                    require(command.action == "CHOOSE_DOCTRINE" || command.action == "SURRENDER") {
                        "missing_doctrine"
                    }
                }
                require(applyCommand(battle, command)) { "illegal_command" }
            }
            if (tick < input.ticks) {
                require(!battle.over) { "extra_ticks" }
                require(battle.pendingChoice == null) { "missing_doctrine" }
                battle.step()
                // Efekty wizualne nie są częścią stanu rankingu.
                battle.fx.count = 0
                if (battle.over && tick + 1 < input.ticks) {
                    throw IllegalArgumentException("extra_ticks")
                }
            }
            if (tick % 512 == 0) {
                require(System.nanoTime() <= deadline) { "replay_timeout" }
            }
        }

        require(index == input.commands.size) { "unordered_commands" }
        require(battle.over && battle.pendingChoice == null) { "unfinished_battle" }
        val computed = battle.score()
        require(computed == input.score) { "score_mismatch" }
        return VerifiedReplay(board, computed)
    }

    private fun applyCommand(battle: Battle, c: ReplayCommand): Boolean {
        val player = battle.player
        return when (c.action) {
            "QUEUE_UNIT" -> {
                if (c.arg !in 0..3) false else battle.queueUnit(player, Role.entries[c.arg])
            }
            "CANCEL_LAST" -> battle.cancelLast(player)
            "EVOLVE" -> battle.evolve(player)
            "CHOOSE_DOCTRINE" -> {
                if (c.arg !in 0..2 || battle.pendingChoice == null) false
                else { battle.chooseDoctrine(c.arg); true }
            }
            "BUY_TURRET" -> if (c.arg !in 0..3) false else battle.buyTurret(player, c.arg)
            "UPGRADE_TURRET" -> if (c.arg !in 0..3) false else battle.upgradeTurret(player, c.arg)
            "SELL_TURRET" -> if (c.arg !in 0..3) false else battle.sellTurret(player, c.arg)
            "ARMORY" -> battle.buyArmory(player)
            "BARRAGE" -> battle.castBarrage(player)
            "RALLY" -> battle.castRally(player)
            "HOLD" -> {
                if (c.arg !in 0..1) false else { player.hold = c.arg == 1; true }
            }
            "SURRENDER" -> {
                // Wcześniejsze poddanie nie daje wiarygodnego wyniku do rankingu.
                if (c.tick < 60) false else { battle.surrender(); true }
            }
            else -> false
        }
    }
}
