package com.centurywar.game

import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.roundToInt

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
        !battle.over && battle.pendingVariant == null && battle.pendingChoice == null && battle.pendingCards == null &&
            battle.stepCount < Replay.MAX_STEPS && capacity(needed)

    private fun append(kind: Replay.Kind, argument: Int = 0) {
        if (!record) return
        events[count++] = (battle.stepCount.toLong() shl 40) or
            (kind.ordinal.toLong() shl 32) or (argument.toLong() and 0xffffffffL)
    }

    fun queue(role: Role): Boolean {
        if (!canAct() || !battle.queueUnit(battle.player, role)) return false
        append(Replay.Kind.QUEUE, role.ordinal)
        return true
    }

    /** Ulepszenie formacji (stopień jednostek danej roli w bieżącej epoce). */
    fun upgradeUnit(role: Role): Boolean {
        if (!canAct() || !battle.upgradeUnit(battle.player, role)) return false
        append(Replay.Kind.UNIT_UPGRADE, role.ordinal)
        return true
    }

    fun cancel(): Boolean {
        if (!canAct() || !battle.cancelLast(battle.player)) return false
        append(Replay.Kind.CANCEL)
        return true
    }

    fun evolve(): Boolean {
        // AI-gracz zapisuje awans, wariant i doktrynę w tym samym kroku.
        val next = battle.player.era + 1
        val hasVariant = next < Defs.ERA_COUNT && battle.cfg.variantsEnabled &&
            Defs.spec(next).variants.isNotEmpty()
        val needed = if (battle.playerAi != null) (if (hasVariant) 3 else 2) else 1
        if (!canAct(needed) || !battle.evolve(battle.player)) return false
        append(Replay.Kind.EVOLVE)
        if (battle.playerAi != null) {
            if (battle.pendingVariant != null)
                check(choosePendingVariant()) { "Nie udało się zapisać wyboru wariantu" }
            if (battle.pendingChoice != null)
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

    /**
     * Ostrzał celowany (AI gracza w testach powtórek). Cel jest zaokrąglany do całych
     * jednostek pasa przed wykonaniem, więc zapis odtwarza dokładnie ten sam strzał.
     * NaN = klasyczny rozrzut, jak przycisk na ekranie.
     */
    fun barrage(targetX: Float): Boolean {
        if (!targetX.isFinite()) return barrage()
        val x = targetX.roundToInt().coerceIn(0, Defs.LANE.toInt())
        if (!canAct() || !battle.castBarrage(battle.player, x.toFloat())) return false
        append(Replay.Kind.BARRAGE_AT, x)
        return true
    }

    fun rally(): Boolean {
        if (!canAct() || !battle.castRally(battle.player)) return false
        append(Replay.Kind.RALLY)
        return true
    }

    fun stance(hold: Boolean): Boolean {
        if (!canAct() || battle.player.hold == hold) return false
        battle.player.hold = hold
        append(Replay.Kind.STANCE, if (hold) 1 else 0)
        return true
    }

    /** Wybór jest odtwarzany identycznie na serwerze, także w kroku zerowym. */
    fun variant(index: Int): Boolean {
        val era = battle.pendingVariant ?: return false
        if (battle.over || battle.stepCount >= Replay.MAX_STEPS || !capacity() ||
            index !in Defs.spec(era).variants.indices) return false
        if (!battle.chooseVariant(index)) return false
        append(Replay.Kind.VARIANT, index)
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

    /** Wybór 1 z 3 kart Oblężenia bez końca. */
    fun card(index: Int): Boolean {
        val options = battle.pendingCards ?: return false
        if (battle.over || index !in options.indices ||
            battle.stepCount >= Replay.MAX_STEPS || !capacity()) return false
        battle.chooseEndlessCard(index)
        append(Replay.Kind.CARD, index)
        return true
    }

    /** Poddanie może zakończyć bitwę przed krokiem N, nawet przy wyborze doktryny. */
    fun surrender(): Boolean {
        if (battle.over || !capacity() || battle.stepCount > Replay.MAX_STEPS) return false
        append(Replay.Kind.SURRENDER)
        battle.surrender()
        return true
    }

    /** Deterministyczna pomoc samouczka; niedostępna poza samouczkiem. */
    fun tutorialGold(cost: Int): Boolean {
        if (!battle.cfg.tutorial || battle.cfg.mode != Mode.CAMPAIGN ||
            battle.cfg.stageIndex != 0 || !canAct() || cost <= 0 || cost > 1_000_000) return false
        val p = battle.player
        val valid = cost == Defs.unit(p.era, Role.MELEE).cost ||
            cost == Defs.unit(p.era, Role.RANGED).cost || cost == p.turretCost()
        if (!valid || p.gold >= cost) return false
        p.gold = cost.toFloat()
        append(Replay.Kind.TUTORIAL_GOLD, cost)
        return true
    }

    /** Automatyczny wybór po awansie lub nowej fali, bez ingerencji w RNG symulacji. */
    internal fun choosePendingVariant(): Boolean {
        val era = battle.pendingVariant ?: return false
        val brain = battle.playerAi ?: return false
        return variant(brain.chooseVariant(era))
    }

    internal fun choosePendingDoctrine(): Boolean {
        val options = battle.pendingChoice ?: return false
        val brain = battle.playerAi ?: return false
        val index = options.indexOf(brain.chooseDoctrine(options))
        return doctrine(index)
    }

    internal fun choosePendingCard(): Boolean {
        val options = battle.pendingCards ?: return false
        val brain = battle.playerAi ?: return false
        return card(brain.chooseCard(options))
    }

    /** Kopia na koniec bitwy; alokacje wyłącznie poza pętlą klatek. */
    fun log(): Replay.Log {
        val commands = ArrayList<Replay.Command>(count)
        val kinds = Replay.Kind.values()
        for (i in 0 until count) {
            val packed = events[i]
            commands.add(
                Replay.Command(
                    step = (packed ushr 40).toInt(),
                    kind = kinds[((packed ushr 32) and 255).toInt()],
                    argument = packed.toInt(),
                ),
            )
        }
        return Replay.Log(battle.stepCount, commands)
    }
    /** Zapis surowych wpisów jest możliwy także przed wykonaniem następnego kroku. */
    internal fun writeSnapshot(o: DataOutputStream) {
        check(record)
        o.writeInt(count)
        for (i in 0 until count) o.writeLong(events[i])
    }

    internal fun readSnapshot(i: DataInputStream, currentStep: Int) {
        check(record && count == 0)
        val n = i.readInt()
        require(n in 0..Replay.MAX_COMMANDS)
        val kinds = Replay.Kind.values()
        var previous = 0
        repeat(n) {
            val packed = i.readLong()
            val step = (packed ushr 40).toInt()
            val code = ((packed ushr 32) and 255).toInt()
            val argument = packed.toInt()
            require(step >= previous && step <= currentStep && code in kinds.indices)
            val valid = when (kinds[code]) {
                Replay.Kind.QUEUE, Replay.Kind.UNIT_UPGRADE -> argument in 0 until Role.values().size
                Replay.Kind.BUY_TURRET, Replay.Kind.UPGRADE_TURRET, Replay.Kind.SELL_TURRET ->
                    argument in 0 until Defs.MAX_TURRET_SLOTS
                Replay.Kind.STANCE -> argument == 0 || argument == 1
                Replay.Kind.DOCTRINE -> argument in 0..2
                Replay.Kind.TUTORIAL_GOLD -> argument in 1..1_000_000
                Replay.Kind.BARRAGE_AT -> argument in 0..Defs.LANE.toInt()
                Replay.Kind.CARD -> argument in 0..2
                Replay.Kind.VARIANT -> argument in 0..1
                else -> argument == 0
            }
            require(valid)
            events[count++] = packed
            previous = step
        }
    }
}
