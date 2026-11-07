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

    /**
     * Tylko do pomiarów balansu (BalanceReport): null = zwykłe zasady trybu,
     * true = przywołuje dowódcę także z własnej inicjatywy w kampanii, false = nigdy.
     * Nie jest zapisywane w snapshotach ani powtórkach; gra zawsze używa null.
     */
    var commanderOverride: Boolean? = null

    private var danger = 0
    private var barrageTargets = 0
    private var closeContact = 0
    private var enemyMelee = 0
    private var enemyRanged = 0
    private var enemyHeavy = 0
    private var ownPower = 0f
    private var enemyPower = 0f

    /**
     * Dzień 68: tylko przeciwnik w potyczce losuje ze wszystkich stylów; pozostałe tryby i AI
     * gracza używają zamrożonej piątki [AiPersonality.LEGACY], więc te same seedy dają tego
     * samego dowódcę co przed dodaniem nowych stylów (wyzwania dnia, Endless, tydzień, Rozkazy).
     */
    private fun personalityForSeed(seed: Long, index: Int): AiPersonality {
        val n = seed xor (seed ushr 32) xor (index.toLong() * 0x5DEECE66DL)
        val pool = if (b.cfg.mode == Mode.SKIRMISH && index == 1) AiPersonality.entries
            else AiPersonality.LEGACY
        val choice = ((n and Long.MAX_VALUE) % pool.size).toInt()
        return pool[choice]
    }

    // Rozkazy AI gracza przechodzą przez rejestrator powtórki; przeciwnik działa bezpośrednio.
    private val commands: BattleController? get() = if (me.index == 0) b.commandController else null

    private fun evolve() = commands?.evolve() ?: b.evolve(me)
    private fun barrage(targetX: Float) = commands?.barrage(targetX) ?: b.castBarrage(me, targetX)
    private fun rally() = commands?.rally() ?: b.castRally(me)
    private fun stance(hold: Boolean) { commands?.stance(hold) ?: run { me.hold = hold } }
    private fun upgrade(slot: Int) = commands?.upgradeTurret(slot) ?: b.upgradeTurret(me, slot)
    private fun buyTurret(slot: Int) = commands?.buyTurret(slot) ?: b.buyTurret(me, slot)
    private fun tierTurret(slot: Int) = commands?.upgradeTurretTier(slot) ?: b.upgradeTurretTier(me, slot)
    private fun sellTurret(slot: Int) = commands?.sellTurret(slot) ?: b.sellTurret(me, slot)
    private fun armory() = commands?.armory() ?: b.buyArmory(me)
    private fun queue(role: Role) = commands?.queue(role) ?: b.queueUnit(me, role)
    private fun upgradeUnit(role: Role) = commands?.upgradeUnit(role) ?: b.upgradeUnit(me, role)
    private fun summonCommander() = commands?.summonCommander() ?: b.summonCommander(me)

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
        // AI gracza wydaje ten sam rejestrowany rozkaz co użytkownik.
        // W kampanii AI reaguje na dowódcę przeciwnika: zachowuje dotychczasowy balans
        // przy braku tego zakupu, ale odpowiada na nową jednostkę na takich samych zasadach.
        val commanderCounterplay = commanderOverride ?: (b.cfg.mode != Mode.CAMPAIGN ||
            b.sides[1 - me.index].commanderCd < 0f)
        if (commanderCounterplay && b.canSummonCommander(me) &&
            (danger > 0 || me.aliveCount >= 2 || b.time >= 35f)) {
            summonCommander()
        }
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
        // Gospodarz inwestuje nadwyżki w ulepszenia, ale nie zaniedbuje obrony.
        val safeInvestment = if (personality == AiPersonality.ECONOMIST && danger == 0) 0.8f else 1f
        val reserve = meleeCost * (if (full) 2f else 5f) *
            personality.upgradeReserve * safeInvestment
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
            (!b.fog || me.fogSight || (u.x - me.front) * me.dir <= 650f)

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
        if (personality == AiPersonality.RAIDS) {
            raidPosture()
            return
        }
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
        // Gospodarz opóźnia natarcie, by zebrać silniejszą falę już przed końcową epoką.
        if (personality == AiPersonality.ECONOMIST &&
            me.era >= b.cfg.maxEra - 1 && b.time >= 105f
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

    /** Własne jednostki za linią postoju, czyli te, które już wyszły do uderzenia. */
    private fun unitsOut(): Int {
        val line = me.baseFront + me.dir * (Battle.HOLD_DIST + RAID_MARGIN)
        var out = 0
        for (u in b.units) if (u.alive && u.side == me.index && (u.x - line) * me.dir > 0f) out++
        return out
    }

    /**
     * Dzień 68, „Krótkie uderzenia”: postój zbiera przy bazie grupę [AiPersonality.pushArmySize]
     * jednostek, która rusza razem na [RAID_STRIKE] s; potem znowu postój (ocalali stają tam,
     * gdzie są, i walczą dalej), a przy bazie zbiera się następna grupa. Wróg blisko bazy
     * zwalnia postój, a po [RAID_MAX_WAIT] s wychodzą choćby dwie jednostki.
     * Stan: istniejące [holdUntil] i [nextHoldAt] (zapisywane w snapshocie), bez nowych pól.
     */
    private fun raidPosture() {
        if (me.hold) {
            val out = unitsOut()
            val gathered = me.aliveCount - out
            // Pełny limit armii: nikt więcej nie dojdzie, więc grupa rusza od razu.
            if (gathered >= personality.pushArmySize || danger >= 3 || me.aliveCount + me.queueSize >= me.cap ||
                (b.time >= holdUntil && (gathered >= 2 || out > 0))
            ) {
                stance(false)
                nextHoldAt = b.time + RAID_STRIKE
            }
        } else if (b.time >= nextHoldAt && danger < 2) {
            stance(true)
            holdUntil = b.time + RAID_MAX_WAIT
        }
    }

    private fun useAbilities() {
        val threatened = danger >= 2 && me.baseHp < me.baseMaxHp * 0.6f
        // Oblężnik osłania wysuniętą armię ostrzałem w kierunku bazy w granicach zasięgu.
        val siegeAdvance = personality == AiPersonality.SIEGER &&
            (me.front - me.baseFront) * me.dir >= 450f &&
            me.aliveCount >= 3 && barrageTargets > 0
        if (b.abilityReady(me, 0) &&
            (danger >= personality.barrageDanger || threatened ||
                barrageTargets >= personality.barrageTargets || siegeAdvance)
        ) {
            val forward = if (siegeAdvance) forwardVisibleTarget() else Float.NaN
            val target = if (forward.isFinite()) forward else biggestVisibleCluster()
            if (target.isFinite()) barrage(target)
        }
        // Dzień 68, „Zryw w kontakcie”: Zryw tylko przy zetknięciu z widocznym wrogiem
        // (albo w obronie zagrożonej bazy), nigdy na marszu.
        val rallyNow = if (personality == AiPersonality.CONTACT_RALLY)
            closeContact >= CONTACT_MIN || (threatened && me.aliveCount >= 2)
        else personality.rallyOnMarch || danger > 0 || pushing() || closeContact > 0
        if (b.abilityReady(me, 1) && me.aliveCount >= personality.rallyMin && rallyNow) {
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

    /** Najdalszy widoczny cel w legalnym zasięgu ostrzału. */
    private fun forwardVisibleTarget(): Float {
        var farthest = -1f
        for (u in b.units) {
            if (!canSeeEnemy(u)) continue
            val dist = (u.x - me.baseFront) * me.dir
            if (dist >= 220f && dist <= 1370f && dist > farthest) farthest = dist
        }
        return if (farthest < 0f) Float.NaN else me.baseFront + me.dir * farthest
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
        // W Endless bot najpierw inwestuje w armię, stopnie rozważa dopiero pod koniec próby.
        if ((!b.endless || b.wave >= 55) &&
            (personality == AiPersonality.ENGINEER || personality == AiPersonality.SIEGER)) {
            for (i in 0 until me.slots) {
                val cost = b.turretTierCost(me, i)
                if (cost >= 0 && cost <= budget && me.gold > cost + reserve && tierTurret(i)) return
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
        if (b.cfg.dailyEvent == DailyEvent.MARKSMEN) return Role.RANGED
        val restricted = b.allowedMask(me) != OrderRules.ALL
        if (melee == 0 && me.queueSize == 0 && (!restricted || b.roleAllowed(me, Role.MELEE))) return Role.MELEE
        val profile = personality
        var wm = profile.meleeWeight
        var wr = profile.rangedWeight
        var wh = profile.heavyWeight
        var we = 0

        // Kontry: wręcz > dystans, dystans > ciężcy, ciężcy > wręcz.
        // Zrównoważony dowódca obserwuje role wroga i mocniej reaguje kontrami.
        val counterWeight = if (profile == AiPersonality.BALANCED) 45 else 35
        if (enemyRanged >= 2 && enemyRanged > enemyMelee) wm += counterWeight
        if (enemyHeavy >= 2 && enemyHeavy > enemyRanged) wr += counterWeight
        if (enemyMelee >= 3) wh += if (profile == AiPersonality.BALANCED) 35 else 25
        if (danger >= 3) wm += 15
        if (melee > ranged + heavy + 3) wm = wm * 2 / 3
        if (ranged < melee) wr += 10
        if (heavy * 3 < melee + ranged) wh += 20
        // Dzień 68, „Osłona i strzała”: najpierw osłona z dwóch wręcz, potem strzelcy za nią.
        if (profile == AiPersonality.SCREEN) {
            var front = melee
            for (i in 0 until me.queueSize) if (Defs.allUnits[me.queue[i]].role == Role.MELEE) front++
            if (front < SCREEN_FRONT) {
                wm += 70
                wr /= 2
            } else {
                wr += 45
                wm /= 3
            }
        }

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
        if (b.rangedHorde && me.index == 1 && (!restricted || b.roleAllowed(me, Role.RANGED)) &&
            b.rng.nextAiInt(me.index, 100) < 55) return Role.RANGED
        // Dzień 62 (Rozkazy sztabu): tylko role dozwolone rozkazem; poza Rozkazami bez zmian.
        if (restricted) {
            if (!b.roleAllowed(me, Role.MELEE)) wm = 0
            if (!b.roleAllowed(me, Role.RANGED)) wr = 0
            if (!b.roleAllowed(me, Role.HEAVY)) wh = 0
            if (!b.roleAllowed(me, Role.ELITE)) we = 0
            if (wm + wr + wh + we <= 0) {
                return Role.entries.firstOrNull { b.roleAllowed(me, it) && it != Role.ELITE }
                    ?: Role.ELITE
            }
        }

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
        if (variants.isEmpty()) return if (Defs.spec(era).recruit != null) chooseRecruit(era) else -1
        val armored = variants.indexOfFirst {
            it.art == EraArt.COMBUSTION || it.art == EraArt.BIOTECH
        }.let { if (it < 0) 0 else it }
        var weight = when (personality) {
            AiPersonality.SIEGER -> 72
            AiPersonality.ENGINEER -> 70
            AiPersonality.ECONOMIST -> 60
            AiPersonality.ASSAULT -> 35
            AiPersonality.BALANCED -> 50
            AiPersonality.SCREEN -> 55
            AiPersonality.RAIDS -> 40
            AiPersonality.CONTACT_RALLY -> 50
        }
        val opponent = b.sides[1 - me.index]
        if (opponent.era == era && opponent.eraVariant >= 0) {
            if (opponent.eraVariant == armored) weight -= 8 else weight += 8
        }
        return if (b.rng.nextAiInt(me.index, 100) < weight) armored else 1 - armored
    }

    /**
     * Dzień 69: karta 0 = podstawowy skład, karta 1 = nowa jednostka wariantu jednej roli.
     * Jedno losowanie ze strumienia AI tej strony, jak przy pełnych wariantach; osobowość
     * przesuwa szansę tylko o kilka punktów, żeby obie karty pojawiały się w grze.
     */
    private fun chooseRecruit(era: Int): Int {
        val role = Defs.spec(era).recruit?.role ?: return 0
        var weight = 50
        if (personality == AiPersonality.SIEGER && role == Role.ELITE) weight += 10
        if (personality == AiPersonality.ASSAULT && role == Role.HEAVY) weight += 6
        if (personality == AiPersonality.ENGINEER && role == Role.RANGED) weight += 6
        return if (b.rng.nextAiInt(me.index, 100) < weight) 1 else 0
    }

    /** Marginalny zysk z redukcji odnowienia, z uwzględnieniem twardego minimum. */
    private fun cooldownValue(reduction: Float): Float {
        val full = me.abilityCdMul * reduction
        if (full <= 0f) return 0f
        val next = maxOf(Battle.MIN_ABILITY_CD_MUL, me.abilityCdMul * (1f - reduction))
        return ((me.abilityCdMul - next) / full).coerceIn(0f, 1f)
    }

    /** Doktryna może być legalna w ofercie, ale praktycznie bezwartościowa przy limicie. */
    private fun doctrineValue(d: Doctrine, power: Float): Float = when (d) {
        Doctrine.TACTICS -> cooldownValue(0.25f * power)
        Doctrine.ARSENAL -> if (me.slots in 1 until Defs.MAX_TURRET_SLOTS &&
            Modifier.NO_TURRETS !in b.cfg.modifiers) 1f else 0f
        Doctrine.BANNER -> if (me.rallyUnlocked) 1f else 0f
        Doctrine.VIGILANCE -> if (me.slots > 0 || (b.fog && !me.fogSight)) 1f else 0f
        else -> 1f
    }

    private fun doctrinePreference(d: Doctrine): Float = 100f - doctrineRank(d) * 8f

    private companion object {
        /** Obniżka wagi Zakładu dla profili innych niż szturmowy (strojenie: docs/BALANCE.md). */
        const val WAGER_CAUTION = 30f
        /** Dzień 68: tyle wręcz (żywych i w kolejce) stanowi osłonę stylu „Osłona i strzała”. */
        const val SCREEN_FRONT = 2
        /** Dzień 68: jednostka dalej niż linia postoju + margines jest już „w polu”. */
        const val RAID_MARGIN = 60f
        /** Dzień 68: czas uderzenia grupy, zanim wróci postój i zbiórka następnej [s]. */
        const val RAID_STRIKE = 20f
        /** Dzień 68: najdłuższe zbieranie grupy, zanim wyjdą choćby dwie jednostki [s]. */
        const val RAID_MAX_WAIT = 10f
        /** Dzień 68: tylu widocznych wrogów przy froncie oznacza kontakt dla Zrywu. */
        const val CONTACT_MIN = 2
    }

    /** Wartość dodatkowej karty w zależności od profilu; bez modyfikacji jej efektu. */
    private fun cardPreference(card: EndlessCard): Float {
        val doctrine = card.doctrine
        if (doctrine != null) return doctrinePreference(doctrine)
        var value = 72f
        value += when (personality) {
            AiPersonality.ASSAULT -> when (card) {
                EndlessCard.BERSERK, EndlessCard.MARCH, EndlessCard.WAR_DRUMS,
                EndlessCard.LAST_STAND, EndlessCard.DRILL_SERGEANT,
                EndlessCard.FINAL_SPARK, EndlessCard.WAR_HORNS,
                EndlessCard.VANGUARD -> 24f
                else -> 0f
            }
            AiPersonality.ENGINEER -> when (card) {
                EndlessCard.BURNING_TOWERS, EndlessCard.RAPID_TOWERS,
                EndlessCard.TOWER_NETWORK, EndlessCard.IRON_CURTAIN,
                EndlessCard.EMBER_FORGE, EndlessCard.REPAIR_CREW,
                EndlessCard.FIRST_GUARD, EndlessCard.MASTER_MASONS,
                EndlessCard.SHIELD_WALL -> 24f
                else -> 0f
            }
            AiPersonality.ECONOMIST -> when (card) {
                EndlessCard.EXTRA_GOLD, EndlessCard.BLOOD_GOLD,
                EndlessCard.SCAVENGER, EndlessCard.SUPPLY_LINE,
                EndlessCard.MOBILIZATION, EndlessCard.FIELD_MEDICS,
                EndlessCard.QUARTERMASTER, EndlessCard.WAR_CHEST,
                EndlessCard.BOSS_BOUNTY -> 24f
                else -> 0f
            }
            AiPersonality.SIEGER -> when (card) {
                EndlessCard.FRONTLINE_PLATE, EndlessCard.SHARPSHOOTERS,
                EndlessCard.ELITE_SCHOOL, EndlessCard.FORTRESS,
                EndlessCard.FREE_ELITE, EndlessCard.MUSTER_BANNER,
                EndlessCard.BOSS_RECALL, EndlessCard.SAPPERS -> 24f
                else -> 0f
            }
            AiPersonality.BALANCED -> when (card) {
                EndlessCard.FRONTLINE_PLATE, EndlessCard.DRILL_SERGEANT,
                EndlessCard.FORTRESS, EndlessCard.SUPPLY_LINE,
                EndlessCard.FIELD_MEDICS, EndlessCard.SHIELD_WALL,
                EndlessCard.PIKE_SQUARE -> 12f
                else -> 0f
            }
            // Dzień 68: nowe style nie są losowane w Endless; preferencje tylko dla kompletności.
            AiPersonality.SCREEN -> when (card) {
                EndlessCard.SHARPSHOOTERS, EndlessCard.FRONTLINE_PLATE,
                EndlessCard.SHIELD_WALL, EndlessCard.PIKE_SQUARE -> 18f
                else -> 0f
            }
            AiPersonality.RAIDS -> when (card) {
                EndlessCard.MARCH, EndlessCard.WAR_DRUMS, EndlessCard.MOBILIZATION,
                EndlessCard.VANGUARD -> 18f
                else -> 0f
            }
            AiPersonality.CONTACT_RALLY -> when (card) {
                EndlessCard.RALLY_MASTER, EndlessCard.WAR_HORNS,
                EndlessCard.BERSERK, EndlessCard.LAST_STAND -> 18f
                else -> 0f
            }
        }
        // Zlecenie 184: Zakład to ryzyko; poza profilem szturmowym AI bierze go rzadko.
        if (card == EndlessCard.WAGER && personality != AiPersonality.ASSAULT)
            value -= WAGER_CAUTION
        value += when (card) {
            EndlessCard.SUPPLY_LINE -> {
                var total = 0
                for (economic in EndlessCards.economicSynergyCards) total += me.cardStacks[economic.ordinal]
                if (total > 0) 12f else 0f
            }
            EndlessCard.WAR_DRUMS ->
                if (me.cardStacks[EndlessCard.MARCH.ordinal] > 0 ||
                    me.cardStacks[EndlessCard.SWIFT.ordinal] > 0) 12f else 0f
            EndlessCard.TOWER_NETWORK ->
                if (me.cardStacks[EndlessCard.TOWER_SLOT.ordinal] > 0 ||
                    me.cardStacks[EndlessCard.ARSENAL.ordinal] > 0) 12f else 0f
            EndlessCard.MUSTER_BANNER ->
                if (me.cardStacks[EndlessCard.MOBILIZATION.ordinal] > 0) 12f else 0f
            else -> 0f
        }
        return value
    }

    /** Udział rzeczywistego efektu następnej karty w jej pełnej mocy. */
    private fun cardValue(card: EndlessCard): Float {
        val stacks = me.cardStacks[card.ordinal]
        val power = if (EndlessCards.persistent(card))
            EndlessCards.stackPower(stacks + 1) else 1f
        val marginal = when (card) {
            EndlessCard.TACTICS -> doctrineValue(Doctrine.TACTICS, power)
            EndlessCard.RALLY_MASTER -> cooldownValue(0.2f * power)
            EndlessCard.CLONING -> ((0.6f - me.cloneChance) / 0.1f).coerceIn(0f, 1f)
            EndlessCard.FREE_ELITE -> if (me.freeElitesPerWave < 4) 1f else 0f
            EndlessCard.AUTO_BARRAGE -> if (me.autoBarrageInterval <= 0f) 1f
                else ((me.autoBarrageInterval - maxOf(15f, me.autoBarrageInterval - 5f)) / 5f)
                    .coerceIn(0f, 1f)
            EndlessCard.MEDIC -> if (me.baseMaxHp <= 0f) 0f else
                ((me.baseMaxHp - me.baseHp) / (0.25f * me.baseMaxHp)).coerceIn(0f, 1f)
            EndlessCard.MOBILIZATION ->
                ((Battle.MAX_UNITS - me.cap) / 3f).coerceIn(0f, 1f)
            EndlessCard.MUSTER_BANNER ->
                if (me.cap < Battle.MAX_UNITS) 1f else 0f
            EndlessCard.REPAIR_CREW -> ((0.3f - me.repairPerWave) / 0.06f).coerceIn(0f, 1f)
            EndlessCard.CITADEL_HEART -> {
                val defenses = me.cardStacks[EndlessCard.BULWARK.ordinal] +
                    me.cardStacks[EndlessCard.FORTRESS.ordinal] +
                    me.cardStacks[EndlessCard.REPAIR_CREW.ordinal]
                val full = 0.01f * (1 + defenses)
                ((0.3f - me.repairPerWave) / full).coerceIn(0f, 1f)
            }
            // Spowolnienie tej karty jest liniowe do trzeciego stosu, bez wygaszania.
            EndlessCard.IRON_CURTAIN -> {
                val before = minOf(0.45f, 0.15f * stacks)
                val after = minOf(0.45f, 0.15f * (stacks + 1))
                ((after - before) / 0.15f).coerceIn(0f, 1f)
            }
            else -> card.doctrine?.let { doctrineValue(it, power) } ?: 1f
        }
        val stackValue = if (card == EndlessCard.IRON_CURTAIN) 1f else power
        return cardPreference(card) * stackValue * marginal
    }

    /** Wybór największego realnego przyrostu; oferty puste lub wyczerpane są pomijane. */
    fun chooseCard(options: Array<EndlessCard>): Int {
        var best = 0
        var bestScore = -1f
        for (i in options.indices) {
            val card = options[i]
            // Losowanie przed warunkiem: stała liczba odczytów strumienia AI na ofertę.
            val jitter = b.rng.nextAiInt(me.index, 3)
            if (!EndlessCards.canOffer(card, me, b.fog)) continue
            val score = cardValue(card) + jitter
            if (score > bestScore) {
                bestScore = score
                best = i
            }
        }
        return best
    }

    fun chooseDoctrine(options: Array<Doctrine>): Doctrine {
        var best = options[0]
        var bestScore = -1f
        for (option in options) {
            val score = doctrinePreference(option) * doctrineValue(option, 1f) +
                b.rng.nextAiInt(me.index, 3)
            if (score > bestScore) {
                bestScore = score
                best = option
            }
        }
        return best
    }

    private fun doctrineRank(d: Doctrine): Int = when (personality) {
        AiPersonality.ASSAULT -> when (d) {
            Doctrine.FURY -> 0; Doctrine.SWIFT -> 1; Doctrine.PLUNDER -> 2
            Doctrine.TACTICS -> 3; Doctrine.BANNER -> 4; else -> 5
        }
        AiPersonality.ENGINEER -> when (d) {
            Doctrine.VIGILANCE -> 0; Doctrine.ARSENAL -> 1; Doctrine.BULWARK -> 2
            Doctrine.ECONOMY -> 3; Doctrine.SIEGE -> 4; else -> 5
        }
        AiPersonality.ECONOMIST -> when (d) {
            Doctrine.ECONOMY -> 0; Doctrine.VETERAN -> 1; Doctrine.PLUNDER -> 2
            Doctrine.SWIFT -> 3; Doctrine.SUPPLY -> 4; else -> 5
        }
        AiPersonality.SIEGER -> when (d) {
            Doctrine.SIEGE -> 0; Doctrine.TACTICS -> 1; Doctrine.ARSENAL -> 2
            Doctrine.FURY -> 3; Doctrine.SIEGE_MASTERS -> 4; else -> 5
        }
        AiPersonality.BALANCED -> when (d) {
            Doctrine.FURY -> 0; Doctrine.ECONOMY -> 1; Doctrine.BULWARK -> 2
            Doctrine.SWIFT -> 3; Doctrine.BANNER -> 4; else -> 5
        }
        AiPersonality.SCREEN -> when (d) {
            Doctrine.BULWARK -> 0; Doctrine.FURY -> 1; Doctrine.ECONOMY -> 2
            Doctrine.TACTICS -> 3; Doctrine.SWIFT -> 4; else -> 5
        }
        AiPersonality.RAIDS -> when (d) {
            Doctrine.SWIFT -> 0; Doctrine.FURY -> 1; Doctrine.ECONOMY -> 2
            Doctrine.PLUNDER -> 3; Doctrine.SUPPLY -> 4; else -> 5
        }
        AiPersonality.CONTACT_RALLY -> when (d) {
            Doctrine.BANNER -> 0; Doctrine.TACTICS -> 1; Doctrine.FURY -> 2
            Doctrine.BULWARK -> 3; Doctrine.ECONOMY -> 4; else -> 5
        }
    }
}

/** AI uwzględnia wyłącznie własny próg XP i stan wyboru doktryny gracza. */
fun Battle.canEvolveSide(side: Side): Boolean =
    !over && side.era < cfg.maxEra && side.xp >= Defs.evolveXp[side.era] &&
        (side.index == 1 || (pendingVariant == null && pendingChoice == null))
