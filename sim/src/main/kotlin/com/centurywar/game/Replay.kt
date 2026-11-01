package com.centurywar.game

import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * Powtórka bitwy: zapisuje wyłącznie zaakceptowane komendy gracza i ich krok.
 * Komendy oznaczone numerem N wykonuje się PRZED wywołaniem step() dla kroku N.
 */
object Replay {
    const val VERSION = 5
    const val MAX_STEPS = 432_000 // Maksymalnie 120 minut symulacji.
    const val MAX_COMMANDS = 32_768
    private const val MAX_ENCODED_LENGTH = 600_000

    // Numery i kolejność typów są częścią wersjonowanego formatu.
    enum class Kind {
        QUEUE, CANCEL, EVOLVE, BUY_TURRET, UPGRADE_TURRET, SELL_TURRET,
        ARMORY, BARRAGE, RALLY, STANCE, DOCTRINE, SURRENDER, TUTORIAL_GOLD,
        BARRAGE_AT, CARD, UNIT_UPGRADE, VARIANT,
    }

    data class Command(val step: Int, val kind: Kind, val argument: Int = 0)
    data class Log(val steps: Int, val commands: List<Command>, val version: Int = VERSION,
                   val dailyEvent: DailyEvent? = null)

    data class Result(
        val score: Long,
        val winner: Int,
        val steps: Int,
        val kills: Int,
        val enemyKills: Int,
        val time: Float,
        val wave: Int,
    )

    /** Bajt wersji, varint liczby kroków i komend, następnie delta kroku + typ + argument. */
    fun encode(log: Log): String {
        validate(log)
        val out = ByteArrayOutputStream(log.commands.size * 4 + 12)
        out.write(VERSION)
        out.write(log.dailyEvent?.ordinal ?: 255)
        writeVarint(out, log.steps)
        writeVarint(out, log.commands.size)
        var previousStep = 0
        for (command in log.commands) {
            writeVarint(out, command.step - previousStep)
            out.write(command.kind.ordinal)
            if (hasArgument(command.kind)) writeVarint(out, command.argument)
            previousStep = command.step
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    /** Dekoder odrzuca nieznane wersje, nadmiarowe bajty i przepełnienia varint. */
    fun decode(encoded: String): Log {
        require(encoded.length <= MAX_ENCODED_LENGTH) { "Za długi zapis powtórki" }
        val raw = try {
            Base64.getUrlDecoder().decode(encoded)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Nieprawidłowy zapis Base64", e)
        }
        val input = Reader(raw)
        val version = input.byte()
        require(version == VERSION) { "Nieobsługiwana wersja powtórki: $version" }
        val eventCode = input.byte()
        require(eventCode == 255 || eventCode in DailyEvent.entries.indices) { "Nieznane zdarzenie dnia" }
        val dailyEvent = if (eventCode == 255) null else DailyEvent.entries[eventCode]
        val steps = input.varint()
        val count = input.varint()
        require(steps in 0..MAX_STEPS && count in 0..MAX_COMMANDS) { "Przekroczono limity powtórki" }
        val commands = ArrayList<Command>(count)
        val kinds = Kind.values()
        var step = 0
        repeat(count) {
            val delta = input.varint()
            require(delta <= steps - step) { "Przepełniony numer kroku" }
            step += delta
            val kindCode = input.byte()
            require(kindCode in kinds.indices) { "Nieznany typ komendy" }
            val kind = kinds[kindCode]
            val argument = if (hasArgument(kind)) input.varint() else 0
            commands.add(Command(step, kind, argument))
        }
        require(input.done()) { "Nadmiarowe dane w powtórce" }
        return Log(steps, commands, version, dailyEvent).also(::validate)
    }

    fun verify(cfg: BattleConfig, encoded: String): Result = verify(cfg, decode(encoded))

    /**
     * Weryfikacja bez renderera i AI gracza. Każda niedozwolona komenda
     * lub rozbieżność liczby kroków powoduje odrzucenie powtórki.
     */
    fun verify(cfg: BattleConfig, log: Log): Result {
        validate(log)
        require(cfg.dailyEvent == log.dailyEvent) { "Nieprawidłowe zdarzenie dnia" }
        val battle = Battle(cfg)
        battle.playerAi = null
        val controller = BattleController(battle, record = false)
        var index = 0
        while (!battle.over) {
            val step = battle.stepCount
            while (index < log.commands.size && log.commands[index].step == step && !battle.over) {
                val command = log.commands[index]
                val accepted = applyCommand(controller, command)
                require(accepted) { "Niedozwolona komenda nr $index w kroku $step" }
                index++
            }
            if (battle.over) break
            require(step < log.steps) { "Bitwa nie zakończyła się w zadanym kroku" }
            battle.step()
            require(battle.stepCount == step + 1) { "Brak decyzji wariantu, doktryny lub karty w kroku $step" }
            battle.fx.count = 0
        }
        require(index == log.commands.size && battle.over && battle.stepCount == log.steps) {
            "Niekompletna lub przedłużona bitwa"
        }
        return Result(
            score = battle.score(),
            winner = battle.winner,
            steps = battle.stepCount,
            kills = battle.player.kills,
            enemyKills = battle.enemy.kills,
            time = battle.time,
            wave = battle.wave,
        )
    }

    internal fun applyCommand(controller: BattleController, command: Command): Boolean = when (command.kind) {
        Kind.QUEUE -> controller.queue(Role.values()[command.argument])
        Kind.CANCEL -> controller.cancel()
        Kind.EVOLVE -> controller.evolve()
        Kind.BUY_TURRET -> controller.buyTurret(command.argument)
        Kind.UPGRADE_TURRET -> controller.upgradeTurret(command.argument)
        Kind.SELL_TURRET -> controller.sellTurret(command.argument)
        Kind.ARMORY -> controller.armory()
        Kind.BARRAGE -> controller.barrage()
        Kind.RALLY -> controller.rally()
        Kind.STANCE -> controller.stance(command.argument == 1)
        Kind.DOCTRINE -> controller.doctrine(command.argument)
        Kind.SURRENDER -> controller.surrender()
        Kind.TUTORIAL_GOLD -> controller.tutorialGold(command.argument)
        Kind.BARRAGE_AT -> controller.barrage(command.argument.toFloat())
        Kind.CARD -> controller.card(command.argument)
        Kind.UNIT_UPGRADE -> controller.upgradeUnit(Role.values()[command.argument])
        Kind.VARIANT -> controller.variant(command.argument)
    }

    private fun hasArgument(kind: Kind): Boolean = when (kind) {
        Kind.QUEUE, Kind.BUY_TURRET, Kind.UPGRADE_TURRET, Kind.SELL_TURRET,
        Kind.STANCE, Kind.DOCTRINE, Kind.TUTORIAL_GOLD, Kind.BARRAGE_AT, Kind.CARD, Kind.UNIT_UPGRADE, Kind.VARIANT -> true
        else -> false
    }

    private fun validate(log: Log) {
        require(log.version == VERSION) { "Nieobsługiwana wersja powtórki" }
        require(log.steps in 0..MAX_STEPS) { "Nieprawidłowa liczba kroków" }
        require(log.commands.size <= MAX_COMMANDS) { "Przekroczony limit komend" }
        var previousStep = 0
        for (command in log.commands) {
            require(command.step >= previousStep && command.step <= log.steps) { "Komenda spoza zakresu kroków" }
            val arg = command.argument
            val correct = when (command.kind) {
                Kind.QUEUE, Kind.UNIT_UPGRADE -> arg in 0 until Role.values().size
                Kind.BUY_TURRET, Kind.UPGRADE_TURRET, Kind.SELL_TURRET ->
                    arg in 0 until Defs.MAX_TURRET_SLOTS
                Kind.STANCE -> arg == 0 || arg == 1
                Kind.DOCTRINE -> arg in 0..2
                Kind.TUTORIAL_GOLD -> arg in 1..1_000_000
                Kind.BARRAGE_AT -> arg in 0..Defs.LANE.toInt()
                Kind.CARD -> arg in 0..2
                Kind.VARIANT -> arg in 0..1
                else -> arg == 0
            }
            require(correct) { "Niedozwolony argument komendy" }
            previousStep = command.step
        }
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Int) {
        var number = value
        while (number >= 128) {
            out.write((number and 127) or 128)
            number = number ushr 7
        }
        out.write(number)
    }

    private class Reader(private val data: ByteArray) {
        private var position = 0

        fun done(): Boolean = position == data.size

        fun byte(): Int {
            require(position < data.size) { "Ucięty zapis powtórki" }
            return data[position++].toInt() and 255
        }

        fun varint(): Int {
            var value = 0
            for (i in 0..4) {
                val b = byte()
                require(i != 4 || b <= 7) { "Przepełniony varint" }
                value = value or ((b and 127) shl (7 * i))
                if (b and 128 == 0) {
                    require(i == 0 || b != 0) { "Nadmiarowy varint" }
                    return value
                }
            }
            throw IllegalArgumentException("Za długi varint")
        }
    }
}
