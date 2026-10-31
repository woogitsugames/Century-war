package com.centurywar.game

import kotlin.math.abs

/**
 * Dowódca podejmuje decyzje na podstawie własnych zasobów i widocznych jednostek.
 * Mnożnik trudności jest wyłącznie jawny w BattleConfig.aiPower.
 */
class Ai(
    private val b: Battle,
    sideIndex: Int,
    selectedPersonality: AiPersonality? = null,
) {
    private val me = b.sides[sideIndex]
    val personality: AiPersonality =
        selectedPersonality ?: b.cfg.aiPersonality ?: AiPersonality.entries[b.rng.nextInt(AiPersonality.entries.size)]

    private var think = 0.4f
    private var plannedRole: Role? = null
    private var saveEra = -1
    private var saveUntil = 0f
    private var evolveReadyAt = -1f
    private var holdUntil = 0f
    private var nextHoldAt = 0f
    private var enemyEngaged = 0
    private var enemyNear = 0
    private var enemyRanged = 0
    private var enemyMelee = 0
    private var enemyHeavy = 0
    private var enemyElite = 0
    private var ownMelee = 0
    private var ownRanged = 0
    private var ownHeavy = 0
    private var ownPower = 0f
    private var enemyPower = 0f

    fun update(dt: Float) {
        think -= dt
        if (think > 0f || b.over) return
        think = 0.45f + b.rng.nextFloat() * 0.3f

        observeBattlefield()
        if (b.canEvolveSide(me)) {
            if (evolveReadyAt < 0f) evolveReadyAt = b.time
            val delay = personality == AiPersonality.ECONOMIST &&
                enemyNear < 2 && b.time - evolveReadyAt < 7f
            if (!delay && b.evolve(me)) {
                plannedRole = null
                evolveReadyAt = -1f
            }
        } else {
            evolveReadyAt = -1f
        }
        managePosture()
        useAbilities()

        val saving = savingForNextEra()
        manageTurrets(saving)
        trainUnits(saving)
    }

    /** Zasięg jest lustrzanym odbiciem mgły gracza: 650 jednostek od frontu. */
    internal fun canSeeEnemy(u: Fighter): Boolean =
        u.alive && u.side != me.index &&
            (!b.fog || (u.x - me.front) * me.dir <= 650f)

    private fun observeBattlefield() {
        enemyNear = 0; enemyEngaged = 0; enemyRanged = 0; enemyMelee = 0
        enemyHeavy = 0; enemyElite = 0
        ownMelee = 0; ownRanged = 0; ownHeavy = 0
        ownPower = 0f; enemyPower = 0f

        for (u in b.units) {
            if (!u.alive) continue
            val power = u.hp + u.dmg * 4f
            if (u.side == me.index) {
                ownPower += power
                when (u.def.role) {
                    Role.MELEE -> ownMelee++
                    Role.RANGED -> ownRanged++
                    Role.HEAVY, Role.ELITE -> ownHeavy++
                }
                continue
            }
            if (!canSeeEnemy(u)) continue
            enemyPower += power
            val dist = (u.x - me.baseFront) * me.dir
            if (dist <= 700f) enemyNear++
            if (abs(u.x - me.front) <= 330f) enemyEngaged++
            when (u.def.role) {
                Role.MELEE -> enemyMelee++
                Role.RANGED -> enemyRanged++
                Role.HEAVY -> enemyHeavy++
                Role.ELITE -> enemyElite++
            }
        }
    }

    private fun managePosture() {
        if (me.hold) {
            if (b.time >= holdUntil || enemyNear <= 1 || ownPower >= enemyPower) {
                me.hold = false
                nextHoldAt = b.time + 22f
            }
        } else if (b.time >= nextHoldAt && enemyNear >= 3 &&
            ownPower > 0f && ownPower < enemyPower * 0.65f
        ) {
            me.hold = true
            holdUntil = b.time + 3.5f
        }
    }

    private fun useAbilities() {
        if (b.abilityReady(me, 0)) {
            val minimum = if (personality == AiPersonality.SIEGER) 1 else 2
            val threatened = enemyNear >= 2 && me.baseHp < me.baseMaxHp * 0.65f
            val target = biggestVisibleCluster(minimum, threatened)
            if (target.isFinite()) b.castBarrage(me, target)
        }

        val required = if (personality == AiPersonality.ASSAULT) 3 else 5
        if (b.abilityReady(me, 1) && me.aliveCount >= required &&
            (enemyEngaged > 0 || pushing())
        ) {
            b.castRally(me)
        }
    }

    /** Celujemy w największą widoczną grupę, a nie w ukrytą część mapy. */
    private fun biggestVisibleCluster(minimum: Int, threatened: Boolean): Float {
        var bestCount = 0
        var bestDist = Float.MAX_VALUE
        var bestX = Float.NaN
        for (candidate in b.units) {
            if (!canSeeEnemy(candidate)) continue
            val d = (candidate.x - me.baseFront) * me.dir
            if (d < 220f || d > 1370f) continue
            var count = 0
            for (other in b.units) {
                if (!canSeeEnemy(other)) continue
                val otherDist = (other.x - me.baseFront) * me.dir
                if (otherDist < 220f || otherDist > 1370f) continue
                if (abs(other.x - candidate.x) <= 170f) count++
            }
            if (count > bestCount || (count == bestCount && d < bestDist)) {
                bestCount = count
                bestDist = d
                bestX = candidate.x
            }
        }
        return if (bestCount >= minimum || (threatened && bestCount >= 1)) bestX else Float.NaN
    }

    private fun pushing(): Boolean = abs(me.front - me.baseFront) > Defs.LANE * 0.45f

    /** Przed awansem odkładamy złoto na armię następnej epoki, o ile front jest bezpieczny. */
    private fun savingForNextEra(): Boolean {
        if (me.era >= b.cfg.maxEra || me.xp < Defs.evolveXp[me.era] * 0.86f) return false
        if (saveEra != me.era) {
            saveEra = me.era
            saveUntil = b.time + 6f
        }
        return b.time < saveUntil && enemyNear == 0 && me.aliveCount >= 2
    }

    private fun manageTurrets(saving: Boolean) {
        if (me.slots == 0 || saving) return
        val earliest = when (personality) {
            AiPersonality.ENGINEER -> 18f
            AiPersonality.SIEGER -> 30f
            AiPersonality.ECONOMIST -> 65f
            else -> 40f
        }
        if (b.time < earliest) return

        val meleeCost = Defs.unit(me.era, Role.MELEE).cost
        if (enemyNear > 0 && me.gold < meleeCost && me.era >= 2) {
            for (i in 0 until me.slots) {
                val oldEra = me.turretEra[i]
                if (oldEra >= 0 && oldEra <= me.era - 2 && b.upgradeCost(me, i) > me.gold) {
                    if (b.sellTurret(me, i)) return
                }
            }
        }

        val reserve = if (enemyNear >= 2) meleeCost * 3 else Defs.unit(me.era, Role.HEAVY).cost
        if (personality == AiPersonality.ENGINEER && me.armory < 3 && b.time > 45f &&
            me.gold >= b.armoryCost(me) + reserve
        ) {
            if (b.buyArmory(me)) return
        }

        for (i in 0 until me.slots) {
            val cost = b.upgradeCost(me, i)
            if (cost >= 0 && me.gold >= cost + reserve) {
                if (b.upgradeTurret(me, i)) return
            }
        }
        val slot = b.freeSlot(me)
        if (slot >= 0 && me.gold >= me.turretCost() + reserve) b.buyTurret(me, slot)
    }

    private fun trainUnits(saving: Boolean) {
        if (me.aliveCount + me.queueSize >= me.cap) {
            val reserve = Defs.unit(me.era, Role.HEAVY).cost
            if (!saving && me.gold >= b.armoryCost(me) + reserve) b.buyArmory(me)
            return
        }
        if (me.queueSize >= 2) return
        if (saving) return

        val role = plannedRole ?: pickRole().also { plannedRole = it }
        if (b.queueUnit(me, role)) {
            plannedRole = null
        } else if (enemyNear >= 2 && role != Role.MELEE && b.queueUnit(me, Role.MELEE)) {
            plannedRole = null
        } else if (me.gold < Defs.unit(me.era, role).cost * 0.5f) {
            plannedRole = null
        }
    }

    /** Wagi osobowości uzupełniają kontry na rzeczywiście widoczne jednostki. */
    private fun pickRole(): Role {
        var melee = personality.meleeWeight
        var ranged = personality.rangedWeight
        var heavy = personality.heavyWeight
        var elite = personality.eliteWeight

        if (enemyRanged >= 2 && enemyRanged > enemyMelee) {
            heavy += 65
            melee += 25
        }
        if (enemyHeavy >= 2 && enemyHeavy > enemyRanged) ranged += 48
        if (enemyMelee >= 3 && enemyMelee > enemyRanged) ranged += 35
        if (enemyElite >= 2) { heavy += 20; ranged += 25 }
        if (ownMelee == 0) melee += 35
        if (ownRanged == 0) ranged += 12
        if (ownHeavy == 0) heavy += 15
        if (b.rangedHorde && me.index == 1) ranged += 65

        val eliteCost = Defs.unit(me.era, Role.ELITE).cost
        if (!me.eliteUnlocked || me.gold < eliteCost * 0.55f) elite = 0

        val roll = b.rng.nextInt(melee + ranged + heavy + elite)
        return when {
            roll < melee -> Role.MELEE
            roll < melee + ranged -> Role.RANGED
            roll < melee + ranged + heavy -> Role.HEAVY
            else -> Role.ELITE
        }
    }

    fun chooseDoctrine(options: Array<Doctrine>): Doctrine {
        var best = options[0]
        var bestScore = Int.MAX_VALUE
        for (option in options) {
            val score = doctrinePriority(option) * 3 + b.rng.nextInt(3)
            if (score < bestScore) {
                bestScore = score
                best = option
            }
        }
        return best
    }

    private fun doctrinePriority(d: Doctrine): Int = when (personality) {
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

/** Sprawdzenie awansu AI niezależne od wyboru doktryny gracza. */
fun Battle.canEvolveSide(side: Side): Boolean =
    !over && side.era < cfg.maxEra && side.xp >= Defs.evolveXp[side.era] &&
        (side.index == 1 || pendingChoice == null)
