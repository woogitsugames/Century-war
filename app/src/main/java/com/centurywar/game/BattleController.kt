package com.centurywar.game

/**
 * Jedyna ścieżka rejestrowanych rozkazów gracza. Stare metody Battle pozostają
 * dostępne dla AI przeciwnika i kodu, który nie zapisuje powtórki.
 */
class BattleController(
    val battle: Battle,
    private val record: Boolean = true,
) {
    // Tablica prealokowana: żaden rozkaz nie tworzy obiektu w pętli symulacji.
    private val events = if (record) LongArray(Replay.MAX_COMMANDS) else LongArray(0)
    private var count = 0

    init {
        require(battle.commandController == null) { "Bitwa ma już kontroler komend" }
        battle.commandController = this
    }

    private fun capacity(needed: Int = 1): Boolean =
        !record || count <= events.size - needed

    private fun canAct(needed: Int = 1): Boolean =
        !battle.over && battle.pendingChoice == null &&
            battle.stepCount < Replay.MAX_STEPS && capacity(needed)

    private fun append(kind: Replay.Kind, argument: Int = 0) {
        if (!record) return
        events[count++] = (battle.stepCount.toLong() shl 16) or
            (kind.ordinal.toLong() shl 8) or argument.toLong()
    }

    fun queue(role: Role): Boolean {
        if (!canAct() || !battle.queueUnit(battle.player, role)) return false
        append(Replay.Kind.QUEUE, role.ordinal)
        return true
    }

    fun cancel(): Boolean {
        if (!canAct() || !battle.cancelLast(battle.player)) return false
        append(Replay.Kind.CANCEL)
        return true
    }

    fun evolve(): Boolean {
        // AI wybierze doktrynę natychmiast, dlatego rezerwujemy dwa wpisy.
        val needed = if (battle.playerAi != null) 2 else 1
        if (!canAct(needed) || !battle.evolve(battle.player)) return false
        append(Replay.Kind.EVOLVE)
        if (battle.playerAi != null && battle.pendingChoice != null) {
            check(choosePendingDoctrine()) { "Nie udało się zapisać wyboru doktryny" }
        }
        return true
    }

    fun buyTurret(slot: Int): Boolean {
        if (!canAct() || !slotValid(slot) || !battle.buyTurret(battle.player, slot)) return false
        append(Replay.Kind.BUY_TURRET, slot)
        return true
    }

    fun upgradeTurret(slot: Int): Boolean {
        if (!canAct() || !slotValid(slot) || !battle.upgradeTurret(battle.player, slot)) return false
        append(Replay.Kind.UPGRADE_TURRET, slot)
        return true
    }

    fun sellTurret(slot: Int): Boolean {
        if (!canAct() || !slotValid(slot) || !battle.sellTurret(battle.player, slot)) return false
        append(Replay.Kind.SELL_TURRET, slot)
        return true
    }

    private fun slotValid(slot: Int) = slot >= 0 && slot < battle.player.slots &&
        slot < Defs.MAX_TURRET_SLOTS

    fun armory(): Boolean {
        if (!canAct() || !battle.buyArmory(battle.player)) return false
        append(Replay.Kind.ARMORY)
        return true
    }

    fun barrage(): Boolean {
        if (!canAct() || !battle.castBarrage(battle.player)) return false
        append(Replay.Kind.BARRAGE)
        return true
    }

    fun rally(): Boolean {
        if (!canAct() || !battle.castRally(battle.player)) return false
        append(Replay.Kind.RALLY)
        return true
    }

    fun stance(hold: Boolean): Boolean {
        if (!canAct()) return false
        battle.player.hold = hold
        append(Replay.Kind.STANCE, if (hold) 1 else 0)
        return true
    }

    fun doctrine(index: Int): Boolean {
        val options = battle.pendingChoice ?: return false
        if (battle.over || index !in options.indices ||
            battle.stepCount >= Replay.MAX_STEPS || !capacity()) return false
        battle.chooseDoctrine(index)
        append(Replay.Kind.DOCTRINE, index)
        return true
    }

    /** Automatyczny wybór po awansie lub nowej fali, bez ingerencji w RNG symulacji. */
    internal fun choosePendingDoctrine(): Boolean {
        val options = battle.pendingChoice ?: return false
        val brain = battle.playerAi ?: return false
        val index = options.indexOf(brain.chooseDoctrine(options))
        return doctrine(index)
    }

    /** Kopia na koniec bitwy; alokacje wyłącznie poza pętlą klatek. */
    fun log(): Replay.Log {
        val commands = ArrayList<Replay.Command>(count)
        val kinds = Replay.Kind.values()
        for (i in 0 until count) {
            val packed = events[i]
            commands.add(
                Replay.Command(
                    step = (packed ushr 16).toInt(),
                    kind = kinds[((packed ushr 8) and 255).toInt()],
                    argument = (packed and 255).toInt(),
                ),
            )
        }
        return Replay.Log(battle.stepCount, commands)
    }
}
