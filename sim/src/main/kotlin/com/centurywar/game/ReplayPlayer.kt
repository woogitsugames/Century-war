package com.centurywar.game

/**
 * Deterministyczny odtwarzacz: ten sam kontroler i porządek rozkazów co Replay.verify.
 * Przy przewijaniu kopiuje stan co 1800 kroków, bez ponownej symulacji od początku.
 */
class ReplayPlayer(val cfg: BattleConfig, val log: Replay.Log) {
    private data class Keyframe(val step: Int, val commandIndex: Int, val bytes: ByteArray)

    var battle: Battle = Battle(cfg)
        private set
    private var controller = BattleController(battle)
    private val keyframes = ArrayList<Keyframe>()
    private var commandIndex = 0
    private var target = -1
    var broken = false
        private set

    val step: Int get() = battle.stepCount
    val totalSteps: Int get() = log.steps
    val seeking: Boolean get() = target >= 0
    val progress: Float get() = if (totalSteps == 0) 1f else
        (step.toFloat() / totalSteps).coerceIn(0f, 1f)
    val finished: Boolean get() = battle.over && !broken

    init {
        battle.playerAi = null
        try { Replay.encode(log) } catch (_: Exception) { broken = true }
    }

    /** W normalnym tempie zachowuje FX do dźwięku; przewijanie usuwa je po każdym kroku. */
    fun advance(steps: Int, keepEffects: Boolean = false): Int {
        if (broken || steps < 0 || seeking) return 0
        return runSteps(steps, keepEffects)
    }

    private fun runSteps(count: Int, keepEffects: Boolean): Int {
        var advanced = 0
        try {
            while (!broken && !battle.over && advanced < count) {
                val now = battle.stepCount
                while (commandIndex < log.commands.size && log.commands[commandIndex].step == now && !battle.over) {
                    val cmd = log.commands[commandIndex]
                    if (!Replay.applyCommand(controller, cmd)) {
                        broken = true
                        break
                    }
                    commandIndex++
                }
                if (broken || battle.over) break
                if (now >= log.steps || (commandIndex < log.commands.size &&
                        log.commands[commandIndex].step < now)) {
                    broken = true
                    break
                }
                battle.step()
                if (battle.stepCount != now + 1) {
                    broken = true
                    break
                }
                advanced++
                if (!keepEffects) battle.fx.count = 0
                if (battle.stepCount % KEYFRAME_STEPS == 0 &&
                    keyframes.none { it.step == battle.stepCount }) {
                    keyframes.add(Keyframe(battle.stepCount, commandIndex, BattleSnapshot.encode(battle)))
                }
            }
            // Kapitulacja może być zapisana dokładnie przy ostatnim kroku bez step().
            if (!broken && battle.stepCount == log.steps && !battle.over) {
                while (commandIndex < log.commands.size &&
                    log.commands[commandIndex].step == battle.stepCount && !battle.over) {
                    if (!Replay.applyCommand(controller, log.commands[commandIndex])) {
                        broken = true
                        break
                    }
                    commandIndex++
                }
                if (!battle.over) broken = true
            }
            if (battle.over && (battle.stepCount != log.steps || commandIndex != log.commands.size)) {
                broken = true
            }
        } catch (_: Exception) {
            broken = true
        }
        if (broken || !keepEffects) battle.fx.count = 0
        return advanced
    }

    /** Skok synchroniczny do testów; UI powinno używać requestSeek + seekStep. */
    fun seek(step: Int): Boolean {
        requestSeek(step)
        while (seeking && !broken) seekStep(100_000_000L)
        return !broken && this.step == step.coerceIn(0, totalSteps)
    }

    /** Zmienia cel przewijania; wstecz przywraca najbliższy istniejący keyframe. */
    fun requestSeek(step: Int) {
        if (broken) return
        val wanted = step.coerceIn(0, totalSteps)
        if (wanted < battle.stepCount || (battle.over && wanted != battle.stepCount)) {
            val frame = keyframes.lastOrNull { it.step <= wanted }
            if (frame == null) {
                battle = Battle(cfg)
                battle.playerAi = null
                controller = BattleController(battle)
                commandIndex = 0
            } else {
                val restored = BattleSnapshot.decode(frame.bytes)?.battle
                if (restored == null || restored.commandController == null || restored.playerAi != null) {
                    broken = true
                    return
                }
                battle = restored
                controller = restored.commandController!!
                commandIndex = frame.commandIndex
            }
        }
        battle.fx.count = 0
        target = wanted
        if (battle.stepCount == target && target != log.steps) target = -1
    }

    /**
     * Porcja przewijania: budżet czasu przekazany przez UI jest zamieniany na ograniczenie
     * liczby kroków. Bez zegara w sim i bez blokowania całej klatki na długiej powtórce.
     */
    fun seekStep(budgetNanos: Long = 2_000_000L): Boolean {
        if (broken || target < 0) return true
        val chunk = (budgetNanos.coerceAtLeast(1L) / 12_000L).coerceIn(1L, 256L).toInt()
        runSteps(minOf(chunk, target - battle.stepCount), false)
        if (broken || battle.stepCount >= target) target = -1
        return !seeking
    }

    /** Wynik identyczny jak Replay.verify, dostępny po zakończeniu. */
    fun result(): Replay.Result? {
        if (!finished) return null
        return Replay.Result(battle.score(), battle.winner, battle.stepCount,
            battle.player.kills, battle.enemy.kills, battle.time, battle.wave)
    }

    companion object {
        const val KEYFRAME_STEPS = 30 * 60
    }
}
