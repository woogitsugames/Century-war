package com.centurywar.game

import kotlin.math.abs

/**
 * Przeciwnik komputerowy. Gra według tych samych zasad co gracz (złoto, XP, kolejka,
 * limit jednostek). Poziom trudności zmienia tylko jawny mnożnik przychodu/XP
 * ([BattleConfig.aiPower]), który gracz widzi przed bitwą. AI nie ma ukrytych bonusów.
 */
class Ai(private val b: Battle, sideIndex: Int) {
    private val me = b.sides[sideIndex]
    private val foe = b.sides[1 - sideIndex]
    private var think = 0.4f
    private var plannedRole: Role? = null

    /** Stan automatu decyzji przywracany razem z bitwą. */
    var thinkTimer: Float
        get() = think
        set(v) { think = v }
    var plannedRoleState: Role?
        get() = plannedRole
        set(v) { plannedRole = v }

    // Komendy gracza przechodzą przez rejestrator, przeciwnik działa bezpośrednio.
    private val commands: BattleController? get() = if (me.index == 0) b.commandController else null

    private fun evolve() = commands?.evolve() ?: b.evolve(me)
    private fun barrage() = commands?.barrage() ?: b.castBarrage(me)
    private fun rally() = commands?.rally() ?: b.castRally(me)
    private fun upgrade(slot: Int) = commands?.upgradeTurret(slot) ?: b.upgradeTurret(me, slot)
    private fun buyTurret(slot: Int) = commands?.buyTurret(slot) ?: b.buyTurret(me, slot)
    private fun armory() = commands?.armory() ?: b.buyArmory(me)
    private fun queue(role: Role) = commands?.queue(role) ?: b.queueUnit(me, role)

    fun update(dt: Float) {
        think -= dt
        if (think > 0f) return
        think = 0.45f + b.rng.nextAiFloat(me.index) * 0.3f

        if (b.canEvolveSide(me)) evolve()

        val danger = countFoesNear(700f)
        val inBarrageZone = countFoesNear(1350f)
        if (b.abilityReady(me, 0) && (inBarrageZone >= 4 || (danger >= 2 && me.baseHp < me.baseMaxHp * 0.6f))) {
            barrage()
        }
        if (b.abilityReady(me, 1) && me.aliveCount >= 5 && (danger > 0 || pushing())) {
            rally()
        }

        manageTurrets()
        trainUnits(danger)
    }

    private fun pushing(): Boolean = abs(me.front - me.baseFront) > Defs.LANE * 0.45f

    private fun countFoesNear(dist: Float): Int {
        var n = 0
        for (u in b.units) {
            if (!u.alive || u.side == me.index) continue
            if (abs(u.x - me.baseFront) < dist) n++
        }
        return n
    }

    private fun manageTurrets() {
        if (me.slots == 0 || b.time < 35f) return
        val unitReserve = Defs.unit(me.era, Role.HEAVY).cost
        for (i in 0 until me.slots) {
            val cost = b.upgradeCost(me, i)
            if (cost >= 0 && me.gold > cost + unitReserve * 2) { upgrade(i); return }
        }
        val slot = b.freeSlot(me)
        if (slot >= 0 && me.gold > me.turretCost() + unitReserve) buyTurret(slot)
    }

    private fun trainUnits(danger: Int) {
        if (me.aliveCount + me.queueSize >= me.cap) {
            // Armia pełna: nadwyżkę złota zamień w ulepszenia, zamiast ją trzymać.
            val reserve = Defs.unit(me.era, Role.HEAVY).cost * 2
            if (me.gold > b.armoryCost(me) + reserve) armory()
            return
        }
        if (me.queueSize >= 2) return
        var melee = 0; var ranged = 0; var heavy = 0
        for (u in b.units) {
            if (!u.alive || u.side != me.index) continue
            when (u.def.role) {
                Role.MELEE -> melee++
                Role.RANGED -> ranged++
                Role.HEAVY, Role.ELITE -> heavy++
            }
        }
        val role = plannedRole ?: pickRole(melee, ranged, heavy, danger).also { plannedRole = it }
        if (queue(role)) {
            plannedRole = null
        } else if (danger >= 2 && queue(Role.MELEE)) {
            plannedRole = null
        } else if (me.gold > Defs.unit(me.era, role).cost * 0.5f && b.rng.nextAiInt(me.index, 10) == 0) {
            plannedRole = null // zmień plan, jeśli długo nie stać nas na wybraną jednostkę
        }
    }

    private fun pickRole(melee: Int, ranged: Int, heavy: Int, danger: Int): Role {
        val r = b.rng.nextAiInt(me.index, 100)
        val eliteCost = Defs.unit(me.era, Role.ELITE).cost
        if (me.eliteUnlocked && me.gold >= eliteCost * 1.2f && r < 35) return Role.ELITE
        if (b.rangedHorde && me.index == 1 && r < 55) return Role.RANGED
        if (melee == 0 || (danger >= 3 && r < 50)) return Role.MELEE
        if (heavy * 3 < melee + ranged && r < 45) return Role.HEAVY
        return if (ranged < melee) Role.RANGED else Role.MELEE
    }

    fun chooseDoctrine(options: Array<Doctrine>): Doctrine {
        val pref = arrayOf(Doctrine.FURY, Doctrine.ECONOMY, Doctrine.BULWARK, Doctrine.SWIFT, Doctrine.SIEGE,
            Doctrine.VETERAN, Doctrine.PLUNDER, Doctrine.ARSENAL, Doctrine.TACTICS)
        return options.minByOrNull { pref.indexOf(it) + b.rng.nextAiInt(me.index, 3) } ?: options[0]
    }
}

/** Wersja canEvolve dla AI, która ignoruje oczekujący wybór doktryny gracza. */
fun Battle.canEvolveSide(side: Side): Boolean =
    !over && side.era < cfg.maxEra && side.xp >= Defs.evolveXp[side.era] && (side.index == 1 || pendingChoice == null)
