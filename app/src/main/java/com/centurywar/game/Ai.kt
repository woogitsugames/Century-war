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
        selectedPersonality ?: b.cfg.aiPersonality ?: personalityForSeed(b.cfg.seed, sideIndex)
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

    fun update(dt: Float) {
        think -= dt
        if (think > 0f || b.over) return
        think = 0.45f + b.rng.nextFloat() * 0.3f

        observe()
        if (b.canEvolveSide(me)) {
            if (evolveReadyAt < 0f) evolveReadyAt = b.time
            val wait = personality == AiPersonality.ECONOMIST &&
                danger < 2 && b.time - evolveReadyAt < 7f
            if (!wait && b.evolve(me)) {
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
                me.hold = false
                nextHoldAt = b.time + 24f
            }
        } else if (b.time >= nextHoldAt && danger >= 3 && ownPower > 0f &&
            me.baseHp < me.baseMaxHp * 0.7f && ownPower < enemyPower * 0.6f
        ) {
            me.hold = true
            holdUntil = b.time + 2.5f
        }
    }

    private fun useAbilities() {
        val threatened = danger >= 2 && me.baseHp < me.baseMaxHp * 0.6f
        val siegeStrike = personality == AiPersonality.SIEGER && barrageTargets >= 2
        if (b.abilityReady(me, 0) && (barrageTargets >= 4 || threatened || siegeStrike)) {
            val target = biggestVisibleCluster()
            if (target.isFinite()) b.castBarrage(me, target)
        }
        val required = if (personality == AiPersonality.ASSAULT) 3 else 5
        if (b.abilityReady(me, 1) && me.aliveCount >= required &&
            (danger > 0 || pushing() || (personality == AiPersonality.ASSAULT && closeContact > 0))
        ) {
            b.castRally(me)
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
                    if (b.sellTurret(me, i)) return
                }
            }
        }
        val delay = if (personality == AiPersonality.ENGINEER) 28f else 35f
        if (saving || b.time < delay) return

        val reserve = Defs.unit(me.era, Role.HEAVY).cost
        for (i in 0 until me.slots) {
            val cost = b.upgradeCost(me, i)
            if (cost >= 0 && me.gold > cost + reserve * 2) {
                if (b.upgradeTurret(me, i)) return
            }
        }
        val slot = b.freeSlot(me)
        if (slot >= 0 && me.gold > me.turretCost() + reserve) b.buyTurret(me, slot)
    }

    private fun trainUnits(saving: Boolean) {
        if (me.aliveCount + me.queueSize >= me.cap) {
            val reserve = Defs.unit(me.era, Role.HEAVY).cost * 2
            if (!saving && me.gold > b.armoryCost(me) + reserve) b.buyArmory(me)
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
        if (b.queueUnit(me, role)) {
            plannedRole = null
        } else if (danger >= 2 && b.queueUnit(me, Role.MELEE)) {
            plannedRole = null
        } else if (me.gold > Defs.unit(me.era, role).cost * 0.5f && b.rng.nextInt(10) == 0) {
            plannedRole = null
        }
    }

    /** Wagi dowódcy + kontratak na widoczny skład, bez zmiany statystyk. */
    private fun pickRole(melee: Int, ranged: Int, heavy: Int): Role {
        if (b.rangedHorde && me.index == 1 && b.rng.nextInt(100) < 55) return Role.RANGED

        var meleeW = personality.meleeWeight
        var rangedW = personality.rangedWeight
        var heavyW = personality.heavyWeight
        var eliteW = if (me.eliteUnlocked) personality.eliteWeight else 0

        if (enemyRanged >= 2 && enemyRanged > enemyMelee) {
            meleeW += 22
            heavyW += 25
        }
        if (enemyHeavy >= 2 && enemyHeavy > enemyRanged) rangedW += 32
        if (enemyMelee >= 3) rangedW += 18
        if (melee == 0) meleeW += 45
        if (heavy * 3 < melee + ranged) heavyW += 16
        if (danger >= 3) meleeW += 30

        // Po dłuższym patowym starciu inwestuj w prawdziwe jednostki oblężnicze.
        // Kupno nadal przechodzi przez standardową kolejkę i wymaga złota.
        if (personality == AiPersonality.ECONOMIST && b.time < 75f) eliteW = 0
        if (me.era == b.cfg.maxEra && danger < 2 && b.time > 120f && me.eliteUnlocked) {
            eliteW += 26
            if (b.time > 220f) eliteW += 38
        }
        val roll = b.rng.nextInt(meleeW + rangedW + heavyW + eliteW)
        val role = when {
            roll < meleeW -> Role.MELEE
            roll < meleeW + rangedW -> Role.RANGED
            roll < meleeW + rangedW + heavyW -> Role.HEAVY
            else -> Role.ELITE
        }
        // W obronie nie blokuj produkcji na długo, oszczędzając na elitę.
        if (danger >= 2 && role == Role.ELITE &&
            me.gold < Defs.unit(me.era, Role.ELITE).cost) return Role.MELEE
        return role
    }

    fun chooseDoctrine(options: Array<Doctrine>): Doctrine {
        var best = options[0]
        var score = Int.MAX_VALUE
        for (option in options) {
            val v = doctrineRank(option) * 3 + b.rng.nextInt(3)
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
