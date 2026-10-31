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
    private val upgradeOrder = arrayOf(Role.RANGED, Role.MELEE, Role.HEAVY, Role.ELITE)
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
    // Publiczne, bo testy zapisu w :app są poza modułem :sim (internal byłoby niewidoczne).
    var saveEraState: Int
        get() = saveEra
        set(v) { saveEra = v }
    var saveUntilState: Float
        get() = saveUntil
        set(v) { saveUntil = v }
    var evolveReadyAtState: Float
        get() = evolveReadyAt
        set(v) { evolveReadyAt = v }
    var holdUntilState: Float
        get() = holdUntil
        set(v) { holdUntil = v }
    var nextHoldAtState: Float
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
    private fun upgradeUnit(role: Role) = commands?.upgradeUnit(role) ?: b.upgradeUnit(me, role)

    fun update(dt: Float) {
        think -= dt
        if (think > 0f || b.over) return
        think = 0.45f + b.rng.nextAiFloat(me.index) * 0.3f

        observe()
        if (b.canEvolveSide(me)) {
            if (evolveReadyAt < 0f) evolveReadyAt = b.time
            val wait = danger < 2 && b.time - evolveReadyAt < personality.evolveDelay
            if (!wait && evolve()) {
                plannedRole = null
                evolveReadyAt = -1f
            }
        } else evolveReadyAt = -1f

        managePosture()
        useAbilities()
        val saving = savingForNextEra()
        manageTurrets(saving)
        considerUnitUpgrades()
        trainUnits(saving)
    }

    /** Ulepszenia mają priorytet u obrońcy i gospodarza, a niski u szturmowca. */
    private fun considerUnitUpgrades() {
        val full = me.aliveCount + me.queueSize >= me.cap
        if (!full && me.era < b.cfg.maxEra &&
            me.xp > Defs.evolveXp[me.era] * personality.upgradeXpLimit) return
        val meleeCost = me.unit(Role.MELEE).cost
        val reserve = meleeCost * (if (full) 2f else 5f) * personality.upgradeReserve
        for (role in upgradeOrder) {
            if (role == Role.ELITE && !me.eliteUnlocked) continue
            val cost = b.unitUpgradeCost(me, role)
            if (cost < 0 || me.gold < cost + reserve) continue
            var count = 0
            for (u in b.units) if (u.alive && u.side == me.index && u.def.role == role) count++
            for (i in 0 until me.queueSize) if (Defs.allUnits[me.queue[i]].role == role) count++
            if (count >= 2 || (full && count > 0)) {
                upgradeUnit(role)
                return
            }
        }
    }

    /** Dokładnie ten sam próg mgły, lecz mierzony od frontu sterowanej strony. */
    fun canSeeEnemy(u: Fighter): Boolean =
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

    /** Gromadzenie fali i obrona wież są odrębne od krótkiego odwrotu pozostałych. */
    private fun managePosture() {
        if (personality == AiPersonality.SIEGER) {
            val launch = me.aliveCount >= personality.pushArmySize ||
                (danger >= 3 && me.aliveCount >= personality.pushArmySize - 2 &&
                    ownPower < enemyPower * 1.1f)
            if (me.hold) {
                if (launch) {
                    stance(false)
                    nextHoldAt = b.time + 15f
                }
            } else if (b.time >= nextHoldAt && !launch && danger < 2) {
                stance(true)
            }
            return
        }
        // Gospodarz nie wysyła zebranej w końcowej epoce elity pojedynczo.
        if (personality == AiPersonality.ECONOMIST && me.era == b.cfg.maxEra &&
            b.time >= 90f
        ) {
            val ready = me.aliveCount >= personality.pushArmySize
            if (me.hold) {
                if (ready || danger >= 2) {
                    stance(false)
                    nextHoldAt = b.time + 18f
                }
            } else if (!ready && danger < 2 && b.time >= nextHoldAt) {
                stance(true)
            }
            return
        }
        if (personality == AiPersonality.ENGINEER) {
            if (me.hold) {
                if ((me.aliveCount >= personality.pushArmySize &&
                    ownPower >= enemyPower * personality.holdThreshold) ||
                    (b.time >= holdUntil && danger == 0 &&
                        me.aliveCount >= personality.pushArmySize - 2)
                ) {
                    stance(false)
                    nextHoldAt = b.time + 15f
                }
            } else if (b.time >= nextHoldAt && danger < 3 &&
                me.aliveCount < personality.pushArmySize
            ) {
                stance(true)
                holdUntil = b.time + 10f
            }
            return
        }
        val weaker = ownPower < enemyPower * personality.holdThreshold
        if (me.hold) {
            if (b.time >= holdUntil || danger <= 1 || !weaker) {
                stance(false)
                nextHoldAt = b.time + 24f
            }
        } else if (b.time >= nextHoldAt && danger >= 3 && ownPower > 0f &&
            me.baseHp < me.baseMaxHp * 0.7f && weaker
        ) {
            stance(true)
            holdUntil = b.time + 2.5f
        }
    }

    private fun useAbilities() {
        val threatened = danger >= 2 && me.baseHp < me.baseMaxHp * 0.6f
        if (b.abilityReady(me, 0) &&
            (danger >= personality.barrageDanger || threatened ||
                barrageTargets >= personality.barrageTargets)
        ) {
            val target = biggestVisibleCluster()
            if (target.isFinite()) barrage(target)
        }
        if (b.abilityReady(me, 1) && me.aliveCount >= personality.rallyMin &&
            (personality.rallyOnMarch || danger > 0 || pushing() || closeContact > 0)
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
        if (me.era >= b.cfg.maxEra || danger >= 2 || me.aliveCount < 2) return false
        if (me.xp < Defs.evolveXp[me.era] * personality.saveForEraBias) return false
        if (saveEra != me.era) {
            saveEra = me.era
            saveUntil = b.time + personality.saveDuration
        }
        return b.time < saveUntil
    }

    private fun manageTurrets(saving: Boolean) {
        if (me.slots == 0) return
        if (danger >= 2 && me.gold < me.unit(Role.MELEE).cost && me.era >= 2) {
            for (i in 0 until me.slots) {
                if (me.turretEra[i] >= 0 && me.turretEra[i] <= me.era - 2 &&
                    b.upgradeCost(me, i) > me.gold
                ) {
                    if (sellTurret(i)) return
                }
            }
        }
        if (saving || b.time < personality.turretDelay) return

        val budget = me.gold * personality.turretBudget
        val reserve = me.unit(Role.HEAVY).cost
        for (i in 0 until me.slots) {
            val cost = b.upgradeCost(me, i)
            if (cost >= 0 && cost <= budget && me.gold > cost + reserve * 0.5f) {
                if (upgrade(i)) return
            }
        }
        val slot = b.freeSlot(me)
        val price = me.turretCost()
        if (slot >= 0 && price <= budget && me.gold > price + reserve * 0.4f) {
            buyTurret(slot)
        }
    }

    private fun trainUnits(saving: Boolean) {
        if (me.aliveCount + me.queueSize >= me.cap) {
            val reserve = me.unit(Role.HEAVY).cost * 2f * personality.armoryBias
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
        } else if (me.gold > me.unit(role).cost * 0.5f && b.rng.nextAiInt(me.index, 10) == 0) {
            plannedRole = null
        }
    }

    /** Losowanie ważone kontrami, ekonomią i profilem dowódcy, bez tymczasowych tablic. */
    private fun pickRole(melee: Int, ranged: Int, heavy: Int): Role {
        if (melee == 0 && me.queueSize == 0) return Role.MELEE
        val profile = personality
        var wm = profile.meleeWeight
        var wr = profile.rangedWeight
        var wh = profile.heavyWeight
        var we = 0

        // Kontry: wręcz > dystans, dystans > ciężcy, ciężcy > wręcz.
        if (enemyRanged >= 2 && enemyRanged > enemyMelee) wm += 35
        if (enemyHeavy >= 2 && enemyHeavy > enemyRanged) wr += 35
        if (enemyMelee >= 3) wh += 25
        if (danger >= 3) wm += 15
        if (melee > ranged + heavy + 3) wm = wm * 2 / 3
        if (ranged < melee) wr += 10
        if (heavy * 3 < melee + ranged) wh += 20

        // Nie planuj drogich formacji zbyt wcześnie: oszczędzanie jest świadomą decyzją.
        if (me.gold < me.unit(Role.HEAVY).cost * 0.65f) wh = 0
        if (me.eliteUnlocked &&
            (profile.eliteBeforeLastEra || me.era == b.cfg.maxEra) &&
            me.gold >= me.unit(Role.ELITE).cost * profile.eliteCommitment
        ) {
            we = profile.eliteWeight
            if (b.time >= 175f && me.era == b.cfg.maxEra && heavy < 3) {
                we += profile.lateEliteBoost
            }
        }
        if (b.rangedHorde && me.index == 1 &&
            b.rng.nextAiInt(me.index, 100) < 55) return Role.RANGED

        val roll = b.rng.nextAiInt(me.index, wm + wr + wh + we)
        return when {
            roll < wm -> Role.MELEE
            roll < wm + wr -> Role.RANGED
            roll < wm + wr + wh -> Role.HEAVY
            else -> Role.ELITE
        }
    }

    /** Warianty nie pobierają losowań z głównego RNG ani nie zmieniają starych epok. */
    fun chooseVariant(era: Int): Int {
        val variants = Defs.spec(era).variants
        if (variants.isEmpty()) return -1
        val armored = variants.indexOfFirst {
            it.art == EraArt.COMBUSTION || it.art == EraArt.BIOTECH
        }.let { if (it < 0) 0 else it }
        var weight = when (personality) {
            AiPersonality.SIEGER -> 72
            AiPersonality.ENGINEER -> 70
            AiPersonality.ECONOMIST -> 60
            AiPersonality.ASSAULT -> 35
            AiPersonality.BALANCED -> 50
        }
        val opponent = b.sides[1 - me.index]
        if (opponent.era == era && opponent.eraVariant >= 0) {
            if (opponent.eraVariant == armored) weight -= 8 else weight += 8
        }
        return if (b.rng.nextAiInt(me.index, 100) < weight) armored else 1 - armored
    }

    /** Wybór karty Oblężenia bez końca (AI gracza w testach); własny strumień RNG. */
    fun chooseCard(options: Array<EndlessCard>): Int = b.rng.nextAiInt(me.index, options.size)

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
        (side.index == 1 || (pendingVariant == null && pendingChoice == null))
