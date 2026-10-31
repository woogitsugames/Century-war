package com.centurywar.game

import kotlin.math.abs

/**
 * Dowódca wykorzystuje te same zasoby i reguły co gracz.
 * Osobowość wpływa na wybory, a nie na statystyki lub ukryty przychód.
 */
class Ai(
    private val b: Battle,
    sideIndex: Int,
    selectedPersonality: AiPersonality? = null,
) {
    private val me = b.sides[sideIndex]
    var personality: AiPersonality =
        selectedPersonality ?: b.cfg.aiPersonality
            ?: if (b.cfg.tutorial) AiPersonality.BALANCED else personalityForSeed(b.cfg.seed, sideIndex)
        internal set

    private var think = 0.4f
    private var plannedRole: Role? = null
    private var saveEra = -1
    private var saveUntil = 0f
    private var evolveReadyAt = -1f
    private var holdUntil = 0f
    private var nextHoldAt = 0f

    /** Zmienne planowania muszą przeżyć zapis i wznowienie bitwy. */
    var thinkTimer: Float
        get() = think
        set(v) { think = v }
    var plannedRoleState: Role?
        get() = plannedRole
        set(v) { plannedRole = v }
    internal var saveEraState: Int
        get() = saveEra
        set(v) { saveEra = v }
    internal var saveUntilState: Float
        get() = saveUntil
        set(v) { saveUntil = v }
    internal var evolveReadyAtState: Float
        get() = evolveReadyAt
        set(v) { evolveReadyAt = v }
    internal var holdUntilState: Float
        get() = holdUntil
        set(v) { holdUntil = v }
    internal var nextHoldAtState: Float
        get() = nextHoldAt
        set(v) { nextHoldAt = v }

    private var danger = 0
    private var barrageTargets = 0
    private var closeContact = 0
    private var enemyMelee = 0
    private var enemyRanged = 0
    private var enemyHeavy = 0
    private var ownPower = 0f
    private var enemyPower = 0f

    private fun personalityForSeed(seed: Long, index: Int): AiPersonality {
        val n = seed xor (seed ushr 32) xor (index.toLong() * 0x5DEECE66DL)
        val choice = ((n and Long.MAX_VALUE) % AiPersonality.entries.size).toInt()
        return AiPersonality.entries[choice]
    }

    // Rozkazy AI gracza przechodzą przez rejestrator powtórki; przeciwnik działa bezpośrednio.
    private val commands: BattleController? get() = if (me.index == 0) b.commandController else null

    private fun evolve() = commands?.evolve() ?: b.evolve(me)
    private fun barrage(targetX: Float) = commands?.barrage(targetX) ?: b.castBarrage(me, targetX)
    private fun rally() = commands?.rally() ?: b.castRally(me)
    private fun stance(hold: Boolean) { commands?.stance(hold) ?: run { me.hold = hold } }
    private fun upgrade(slot: Int) = commands?.upgradeTurret(slot) ?: b.upgradeTurret(me, slot)
    private fun buyTurret(slot: Int) = commands?.buyTurret(slot) ?: b.buyTurret(me, slot)
    private fun sellTurret(slot: Int) = commands?.sellTurret(slot) ?: b.sellTurret(me, slot)
    private fun armory() = commands?.armory() ?: b.buyArmory(me)
    private fun queue(role: Role) = commands?.queue(role) ?: b.queueUnit(me, role)

    fun update(dt: Float) {
        think -= dt
        if (think > 0f || b.over) return
        think = 0.45f + b.rng.nextAiFloat(me.index) * 0.3f

        observe()
        if (b.canEvolveSide(me)) {
            if (evolveReadyAt < 0f) evolveReadyAt = b.time
            val wait = personality == AiPersonality.ECONOMIST &&
                danger < 2 && b.time - evolveReadyAt < 7f
            if (!wait && evolve()) {
                plannedRole = null
                evolveReadyAt = -1f
            }
        } else evolveReadyAt = -1f

        managePosture()
        useAbilities()
        val saving = savingForNextEra()
        manageTurrets(saving)
        trainUnits(saving)
    }

    /** Dokładnie ten sam próg mgły, lecz mierzony od frontu sterowanej strony. */
    internal fun canSeeEnemy(u: Fighter): Boolean =
        u.alive && u.side != me.index &&
            (!b.fog || (u.x - me.front) * me.dir <= 650f)

    private fun observe() {
        danger = 0; barrageTargets = 0; closeContact = 0
        enemyMelee = 0; enemyRanged = 0; enemyHeavy = 0
        ownPower = 0f; enemyPower = 0f
        for (u in b.units) {
            if (!u.alive) continue
            val power = u.hp + u.dmg * 4f
            if (u.side == me.index) {
                ownPower += power
                continue
            }
            if (!canSeeEnemy(u)) continue
            enemyPower += power
            val dist = (u.x - me.baseFront) * me.dir
            if (dist < 700f) danger++
            if (dist < 1350f) barrageTargets++
            if (abs(u.x - me.front) < 260f) closeContact++
            when (u.def.role) {
                Role.MELEE -> enemyMelee++
                Role.RANGED -> enemyRanged++
                Role.HEAVY, Role.ELITE -> enemyHeavy++
            }
        }
    }

    /** Krótka obrona przed przewagą przeciwnika; nie tworzy trwałego impasu. */
    private fun managePosture() {
        if (me.hold) {
            if (b.time >= holdUntil || danger <= 1 || ownPower >= enemyPower) {
                stance(false)
                nextHoldAt = b.time + 24f
            }
        } else if (b.time >= nextHoldAt && danger >= 3 && ownPower > 0f &&
            me.baseHp < me.baseMaxHp * 0.7f && ownPower < enemyPower * 0.6f
        ) {
            stance(true)
            holdUntil = b.time + 2.5f
        }
    }

    private fun useAbilities() {
        val threatened = danger >= 2 && me.baseHp < me.baseMaxHp * 0.6f
        val siegeStrike = personality == AiPersonality.SIEGER && danger >= 2
        // Ostrzał jest narzędziem obrony bazy, nie niszczy wzajemnie obu fal na środku.
        if (b.abilityReady(me, 0) && (danger >= 4 || threatened || siegeStrike)) {
            val target = biggestVisibleCluster()
            if (target.isFinite()) barrage(target)
        }
        val required = if (personality == AiPersonality.ASSAULT) 3 else 5
        if (b.abilityReady(me, 1) && me.aliveCount >= required &&
            (danger > 0 || pushing() || closeContact > 0)
        ) {
            rally()
        }
    }

    private fun biggestVisibleCluster(): Float {
        var best = 0
        var bestD = Float.MAX_VALUE
        for (candidate in b.units) {
            if (!canSeeEnemy(candidate)) continue
            val dist = (candidate.x - me.baseFront) * me.dir
            if (dist < 0f || dist > 1370f) continue
            var count = 0
            for (u in b.units) {
                if (canSeeEnemy(u) && abs(u.x - candidate.x) <= 170f) count++
            }
            if (count > best) {
                best = count
                bestD = dist
            }
        }
        if (best == 0) return Float.NaN
        return me.baseFront + me.dir * bestD.coerceIn(220f, 1370f)
    }

    private fun pushing(): Boolean = abs(me.front - me.baseFront) > Defs.LANE * 0.45f

    private fun savingForNextEra(): Boolean {
        if (me.era >= b.cfg.maxEra || danger > 0 || me.aliveCount < 2) return false
        if (me.xp < Defs.evolveXp[me.era] * 0.86f) return false
        if (saveEra != me.era) {
            saveEra = me.era
            saveUntil = b.time + 6f
        }
        return b.time < saveUntil
    }

    private fun manageTurrets(saving: Boolean) {
        if (me.slots == 0) return
        if (danger >= 2 && me.gold < Defs.unit(me.era, Role.MELEE).cost && me.era >= 2) {
            for (i in 0 until me.slots) {
                if (me.turretEra[i] >= 0 && me.turretEra[i] <= me.era - 2 &&
                    b.upgradeCost(me, i) > me.gold
                ) {
                    if (sellTurret(i)) return
                }
            }
        }
        val delay = if (personality == AiPersonality.ENGINEER) 28f else 35f
        if (saving || b.time < delay) return

        val reserve = Defs.unit(me.era, Role.HEAVY).cost
        for (i in 0 until me.slots) {
            val cost = b.upgradeCost(me, i)
            if (cost >= 0 && me.gold > cost + reserve * 2) {
                if (upgrade(i)) return
            }
        }
        val slot = b.freeSlot(me)
        if (slot >= 0 && me.gold > me.turretCost() + reserve) buyTurret(slot)
    }

    private fun trainUnits(saving: Boolean) {
        if (me.aliveCount + me.queueSize >= me.cap) {
            val reserve = Defs.unit(me.era, Role.HEAVY).cost * 2
            if (!saving && me.gold > b.armoryCost(me) + reserve) armory()
            return
        }
        if (me.queueSize >= 2 || saving) return

        var melee = 0; var ranged = 0; var heavy = 0
        for (u in b.units) {
            if (!u.alive || u.side != me.index) continue
            when (u.def.role) {
                Role.MELEE -> melee++
                Role.RANGED -> ranged++
                Role.HEAVY, Role.ELITE -> heavy++
            }
        }
        val role = plannedRole ?: pickRole(melee, ranged, heavy).also { plannedRole = it }
        if (queue(role)) {
            plannedRole = null
        } else if (danger >= 2 && queue(Role.MELEE)) {
            plannedRole = null
        } else if (me.gold > Defs.unit(me.era, role).cost * 0.5f && b.rng.nextAiInt(me.index, 10) == 0) {
            plannedRole = null
        }
    }

    /** Trzon doboru jest oszczędny; dowódcy modyfikują tylko część decyzji. */
    private fun pickRole(melee: Int, ranged: Int, heavy: Int): Role {
        val r = b.rng.nextAiInt(me.index, 100)
        // W ostatniej epoce przełamuj pat inwestycją w ciężkie siły oblężnicze.
        // Nie dodajemy złota: dowódca czeka na standardowy koszt szkolenia.
        if (b.time > 175f && me.era == b.cfg.maxEra && danger < 2 &&
            me.eliteUnlocked && heavy < 3 &&
            r < (if (personality == AiPersonality.SIEGER) 70 else 45)
        ) return Role.ELITE
        val eliteCost = Defs.unit(me.era, Role.ELITE).cost
        val eliteChance = when (personality) {
            AiPersonality.SIEGER -> 35
            AiPersonality.ECONOMIST -> 48
            AiPersonality.ASSAULT -> 32
            AiPersonality.ENGINEER -> 32
            AiPersonality.BALANCED -> 35
        }
        if (me.eliteUnlocked && me.gold >= eliteCost * 1.2f && r < eliteChance) return Role.ELITE
        if (b.rangedHorde && me.index == 1 && r < 55) return Role.RANGED

        // Kontry zgodne z macierzą [Defs.counterMultiplier] i liczone tylko z widocznych
        // jednostek: wręcz > dystans, dystans > ciężcy, ciężcy > wręcz.
        if (enemyRanged >= 2 && enemyRanged > enemyMelee && r < 65) return Role.MELEE
        if (enemyHeavy >= 2 && enemyHeavy > enemyRanged && r < 35) return Role.RANGED
        if (enemyMelee >= 3 && r < 25 && me.gold >= Defs.unit(me.era, Role.HEAVY).cost) return Role.HEAVY

        if (personality == AiPersonality.ASSAULT && b.time < 90f && r in 35..60) return Role.MELEE
        if (personality == AiPersonality.ENGINEER && r in 35..57 &&
            heavy * 3 < melee + ranged && me.gold >= Defs.unit(me.era, Role.HEAVY).cost
        ) return Role.HEAVY

        if (melee == 0 || (danger >= 3 && r < 50)) return Role.MELEE
        if (heavy * 3 < melee + ranged && r < 45) return Role.HEAVY
        return if (ranged < melee) Role.RANGED else Role.MELEE
    }

    fun chooseDoctrine(options: Array<Doctrine>): Doctrine {
        var best = options[0]
        var score = Int.MAX_VALUE
        for (option in options) {
            val v = doctrineRank(option) * 3 + b.rng.nextAiInt(me.index, 3)
            if (v < score) { score = v; best = option }
        }
        return best
    }

    private fun doctrineRank(d: Doctrine): Int = when (personality) {
        AiPersonality.ASSAULT -> when (d) {
            Doctrine.FURY -> 0; Doctrine.SWIFT -> 1; Doctrine.PLUNDER -> 2
            Doctrine.TACTICS -> 3; else -> 5
        }
        AiPersonality.ENGINEER -> when (d) {
            Doctrine.ARSENAL -> 0; Doctrine.BULWARK -> 1; Doctrine.ECONOMY -> 2
            Doctrine.SIEGE -> 3; else -> 5
        }
        AiPersonality.ECONOMIST -> when (d) {
            Doctrine.ECONOMY -> 0; Doctrine.VETERAN -> 1; Doctrine.PLUNDER -> 2
            Doctrine.SWIFT -> 3; else -> 5
        }
        AiPersonality.SIEGER -> when (d) {
            Doctrine.SIEGE -> 0; Doctrine.TACTICS -> 1; Doctrine.ARSENAL -> 2
            Doctrine.FURY -> 3; else -> 5
        }
        AiPersonality.BALANCED -> when (d) {
            Doctrine.FURY -> 0; Doctrine.ECONOMY -> 1; Doctrine.BULWARK -> 2
            Doctrine.SWIFT -> 3; else -> 5
        }
    }
}

/** AI uwzględnia wyłącznie własny próg XP i stan wyboru doktryny gracza. */
fun Battle.canEvolveSide(side: Side): Boolean =
    !over && side.era < cfg.maxEra && side.xp >= Defs.evolveXp[side.era] &&
        (side.index == 1 || pendingChoice == null)
