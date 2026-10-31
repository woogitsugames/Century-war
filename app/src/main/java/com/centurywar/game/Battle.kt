package com.centurywar.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Jednostka na polu bitwy. Obiekty pochodzą z puli, więc w trakcie gry nic nie jest alokowane. */
class Fighter {
    var active = false
    var side = 0
    var def: UnitDef = Defs.allUnits[0]
    var x = 0f
    var hp = 0f
    var maxHp = 0f
    var dmg = 0f
    var cd = 0f
    var anim = 0f
    var attackAnim = 0f
    var flash = 0f
    var moving = false
    /** < 0 znaczy, że żyje; >= 0 to czas od śmierci (animacja znikania). */
    var dying = -1f
    var serial = 0
    val radius get() = 16f * def.size
    val alive get() = active && dying < 0f
}

class Projectile {
    var active = false
    var side = 0
    var type = Proj.STONE
    var x = 0f
    var h = 0f
    var vx = 0f
    var vh = 0f
    var g = 0f
    var tx = 0f
    var t = 0f
    var dur = 0f
    var delay = 0f
    var dmg = 0f
    var splash = 0f
    var siegeMul = 1f
    var era = 0
}

class Side(val index: Int) {
    val dir = if (index == 0) 1f else -1f
    val baseFront = if (index == 0) Defs.BASE_FRONT else Defs.LANE - Defs.BASE_FRONT
    var era = 0
    var gold = 0f
    var xp = 0f
    var baseHp = 0f
    var baseMaxHp = 0f
    val queue = IntArray(Battle.QUEUE_CAP)
    var queueSize = 0
    var queueMax = 5
    var trainT = 0f
    val turretEra = IntArray(Defs.MAX_TURRET_SLOTS) { -1 }
    val turretCd = FloatArray(Defs.MAX_TURRET_SLOTS)
    val turretFire = FloatArray(Defs.MAX_TURRET_SLOTS)
    var slots = 2
    val abilityCd = FloatArray(2)
    var rallyT = 0f
    var hold = false
    var aliveCount = 0
    var front = 0f
    var baseFlash = 0f

    var incomeMul = 1f
    var hpMul = 1f
    var dmgMul = 1f
    var baseHpMul = 1f
    var turretDmgMul = 1f
    var trainMul = 1f
    var xpMul = 1f
    var abilityCdMul = 1f
    var siegeMul = 1f
    var killGoldMul = 1f
    var speedMul = 1f
    var turretCostMul = 1f
    var cap = 12
    var eliteUnlocked = true
    var rallyUnlocked = true
    val doctrines = ArrayList<Doctrine>()
    /** Rangi weteranów: nadwyżka XP w ostatniej dostępnej epoce daje +6% obrażeń i HP za rangę. */
    var rank = 0
    /** Poziom zbrojowni (kupowany złotem w bitwie): +8% obrażeń i HP dla nowych jednostek. */
    var armory = 0

    var kills = 0
    var killValue = 0
    var spawned = 0
    val spawnedByRole = IntArray(Role.entries.size)
    var goldEarned = 0f

    fun turretCost(era: Int = this.era): Int = (Defs.eras[era].turret.cost * turretCostMul).toInt()
}

/** Kolejka zdarzeń wizualnych dla renderera. Symulacja nie zna Canvas ani Androida. */
class FxBuffer {
    companion object {
        const val CAP = 256
        const val HIT = 0
        const val DEATH = 1
        const val EXPLOSION = 2
        const val MUZZLE = 3
        const val BASE_HIT = 4
        const val EVOLVE = 5
        const val GOLD = 6
        const val BARRAGE = 7
    }

    val type = IntArray(CAP)
    val x = FloatArray(CAP)
    val h = FloatArray(CAP)
    val side = IntArray(CAP)
    val value = FloatArray(CAP)
    var count = 0

    fun add(t: Int, px: Float, ph: Float, s: Int, v: Float = 0f) {
        if (count >= CAP) return
        type[count] = t; x[count] = px; h[count] = ph; side[count] = s; value[count] = v
        count++
    }
}

/**
 * Cała symulacja bitwy: czysty Kotlin bez zależności od Androida, deterministyczna
 * dla danego seeda. Dzięki temu da się ją testować i balansować w testach JVM
 * (AI kontra AI), a w przyszłości weryfikować wyniki rankingu na serwerze.
 */
class Battle(val cfg: BattleConfig) {
    companion object {
        const val STEP = 1f / 60f
        const val MAX_UNITS = 72
        const val MAX_PROJ = 160
        const val QUEUE_CAP = 32
        const val HOLD_DIST = 430f
        const val BARRAGE_CD = 55f
        const val RALLY_CD = 42f
        const val RALLY_TIME = 8f
        const val ENDLESS_CHOICE_EVERY = 5
    }

    val rng = Rng(cfg.seed)
    val sides = arrayOf(Side(0), Side(1))
    val units = Array(MAX_UNITS) { Fighter() }
    val projs = Array(MAX_PROJ) { Projectile() }
    val fx = FxBuffer()
    var time = 0f
    var over = false
    var winner = -1
    var wave = 0
    var waveTimer = 12f
    var serialCounter = 0
    val endless = cfg.mode == Mode.ENDLESS
    val fog = Modifier.FOG_OF_WAR in cfg.modifiers
    val rangedHorde = Modifier.RANGED_HORDE in cfg.modifiers

    /** Gdy nie jest null, gracz wybiera doktrynę, a symulacja stoi. */
    var pendingChoice: Array<Doctrine>? = null

    var ai: Ai? = if (endless) null else Ai(this, 1)
    /** Tylko testy/autoplay: AI sterujące graczem. */
    var playerAi: Ai? = null

    init {
        val mods = cfg.modifiers
        for (s in sides) {
            s.era = cfg.startEra
            if (Modifier.DOUBLE_INCOME in mods) s.incomeMul *= 2f
            if (Modifier.FRAGILE_BASES in mods) s.baseHpMul *= 0.5f
            if (Modifier.FAST_UNITS in mods) s.speedMul *= 1.5f
            if (Modifier.NO_TURRETS in mods) s.slots = 0
            if (Modifier.GLASS_CANNON in mods) { s.dmgMul *= 1.5f; s.hpMul *= 0.7f }
            if (Modifier.SLOW_XP in mods) s.xpMul *= 0.6f
            s.gold = 175f * Defs.costScale(cfg.startEra)
            if (Modifier.RICH_START in mods) s.gold += 400f * Defs.costScale(cfg.startEra)
        }
        val p = sides[0]
        val b = cfg.playerBonuses
        p.gold += b.startGold * Defs.costScale(cfg.startEra)
        p.incomeMul *= b.incomeMul
        p.hpMul *= b.unitHpMul
        p.dmgMul *= b.unitDmgMul
        p.baseHpMul *= b.baseHpMul
        p.turretDmgMul *= b.turretDmgMul
        p.trainMul *= b.trainMul
        p.xpMul *= b.xpMul
        p.cap = b.popCap
        p.abilityCdMul *= b.abilityCdMul
        if (p.slots > 0) p.slots = b.turretSlots
        p.eliteUnlocked = b.eliteUnlocked
        p.rallyUnlocked = b.rallyUnlocked

        val e = sides[1]
        e.incomeMul *= cfg.aiPower
        e.xpMul *= cfg.aiPower
        if (Modifier.ARMORED_FOE in mods) e.hpMul *= 1.3f
        // Przeciwnik ma ten sam limit armii i slotów co strona bez badań.
        if (endless) {
            e.queueMax = QUEUE_CAP
            e.cap = 30
            e.slots = 0
            e.trainMul = 2.5f
        }
        for (s in sides) {
            s.baseMaxHp = Defs.baseHp(s.era) * s.baseHpMul
            s.baseHp = s.baseMaxHp
            s.front = s.baseFront
        }
    }

    val player get() = sides[0]
    val enemy get() = sides[1]

    // ---------------------------------------------------------------- komendy gracza / AI

    fun canQueue(side: Side, role: Role): Boolean {
        val def = Defs.unit(side.era, role)
        if (role == Role.ELITE && !side.eliteUnlocked) return false
        return side.queueSize < side.queueMax && side.gold >= def.cost
    }

    fun queueUnit(side: Side, role: Role): Boolean {
        if (over || !canQueue(side, role)) return false
        val def = Defs.unit(side.era, role)
        side.gold -= def.cost
        side.queue[side.queueSize++] = def.id
        return true
    }

    /** Anuluje ostatnią jednostkę w kolejce i zwraca całe złoto. */
    fun cancelLast(side: Side): Boolean {
        if (side.queueSize == 0) return false
        val id = side.queue[--side.queueSize]
        side.gold += Defs.allUnits[id].cost
        if (side.queueSize == 0) side.trainT = 0f
        return true
    }

    fun canEvolve(side: Side): Boolean =
        !over && side.era < cfg.maxEra && side.xp >= Defs.evolveXp[side.era] && pendingChoice == null

    fun evolve(side: Side): Boolean {
        if (!canEvolve(side)) return false
        side.xp -= Defs.evolveXp[side.era]
        val ratio = side.baseHp / side.baseMaxHp
        side.era++
        side.baseMaxHp = Defs.baseHp(side.era) * side.baseHpMul
        side.baseHp = min(side.baseMaxHp, side.baseMaxHp * ratio + side.baseMaxHp * 0.1f)
        fx.add(FxBuffer.EVOLVE, side.baseFront, 0f, side.index)
        offerDoctrines(side)
        return true
    }

    private fun offerDoctrines(side: Side) {
        val pool = Doctrine.values().filter {
            !(it == Doctrine.ARSENAL && (side.slots >= Defs.MAX_TURRET_SLOTS || side.slots == 0))
        }.toMutableList()
        val picks = Array(3) {
            val i = rng.nextInt(pool.size)
            pool.removeAt(i)
        }
        if (side.index == 0 && playerAi == null) {
            pendingChoice = picks
        } else {
            val brain = if (side.index == 0) playerAi else ai
            applyDoctrine(side, brain?.chooseDoctrine(picks) ?: picks[0])
        }
    }

    fun chooseDoctrine(index: Int) {
        val c = pendingChoice ?: return
        pendingChoice = null
        applyDoctrine(player, c[index.coerceIn(0, c.size - 1)])
    }

    fun applyDoctrine(side: Side, d: Doctrine) {
        side.doctrines.add(d)
        when (d) {
            Doctrine.ECONOMY -> side.incomeMul *= 1.25f
            Doctrine.SIEGE -> { side.siegeMul *= 1.3f; side.turretDmgMul *= 1.2f }
            Doctrine.BULWARK -> {
                side.baseHpMul *= 1.2f
                val add = side.baseMaxHp * 0.2f
                side.baseMaxHp += add; side.baseHp += add
                side.hpMul *= 1.1f
            }
            Doctrine.SWIFT -> { side.trainMul *= 1.25f; side.speedMul *= 1.1f }
            Doctrine.VETERAN -> side.xpMul *= 1.3f
            Doctrine.FURY -> side.dmgMul *= 1.12f
            Doctrine.ARSENAL -> { side.slots = min(Defs.MAX_TURRET_SLOTS, side.slots + 1); side.turretCostMul *= 0.8f }
            Doctrine.TACTICS -> side.abilityCdMul *= 0.75f
            Doctrine.PLUNDER -> side.killGoldMul *= 1.35f
        }
    }

    fun armoryCost(side: Side): Int = (140f * Defs.costScale(side.era) * 1.45f.pow(side.armory)).toInt()

    fun buyArmory(side: Side): Boolean {
        if (over || side.armory >= 15) return false
        val c = armoryCost(side)
        if (side.gold < c) return false
        side.gold -= c
        side.armory++
        side.dmgMul *= 1.08f
        side.hpMul *= 1.08f
        return true
    }

    fun freeSlot(side: Side): Int {
        for (i in 0 until side.slots) if (side.turretEra[i] < 0) return i
        return -1
    }

    fun buyTurret(side: Side, slot: Int): Boolean {
        if (over || slot !in 0 until side.slots || side.turretEra[slot] >= 0) return false
        val cost = side.turretCost()
        if (side.gold < cost) return false
        side.gold -= cost
        side.turretEra[slot] = side.era
        side.turretCd[slot] = 0.5f
        return true
    }

    fun upgradeCost(side: Side, slot: Int): Int {
        val te = side.turretEra[slot]
        if (te < 0 || te >= side.era) return -1
        return max(0, side.turretCost() - side.turretCost(te) / 2)
    }

    fun upgradeTurret(side: Side, slot: Int): Boolean {
        val cost = upgradeCost(side, slot)
        if (over || cost < 0 || side.gold < cost) return false
        side.gold -= cost
        side.turretEra[slot] = side.era
        return true
    }

    fun sellTurret(side: Side, slot: Int): Boolean {
        val te = side.turretEra[slot]
        if (over || te < 0) return false
        side.gold += side.turretCost(te) / 2
        side.turretEra[slot] = -1
        return true
    }

    fun abilityReady(side: Side, i: Int): Boolean =
        !over && side.abilityCd[i] <= 0f && (i == 0 || side.rallyUnlocked)

    /** Bez argumentu celu zachowuje dawny, losowy rozrzut ostrzału. */
    fun castBarrage(side: Side): Boolean = castBarrage(side, Float.NaN)

    /** Ostrzał skupiony wokół wskazanego miejsca, ograniczony do zasięgu zdolności. */
    fun castBarrage(side: Side, targetX: Float): Boolean {
        if (!abilityReady(side, 0)) return false
        side.abilityCd[0] = BARRAGE_CD * side.abilityCdMul
        val count = 16
        val dmg = Defs.abilityDmg(side.era) * side.dmgMul
        val type = barrageProj(side.era)
        val aimed = targetX.isFinite()
        val aimDistance = (targetX - side.baseFront) * side.dir
        for (i in 0 until count) {
            val p = allocProj() ?: break
            val dist = if (aimed) {
                (aimDistance + (rng.nextFloat() - 0.5f) * 240f).coerceIn(220f, 1370f)
            } else {
                220f + rng.nextFloat() * 1150f
            }
            val tx = side.baseFront + side.dir * dist
            p.side = side.index
            p.type = type
            p.era = side.era
            p.x = tx - side.dir * 260f
            p.h = 950f
            p.tx = tx
            p.dur = 0.85f
            p.vx = (tx - p.x) / p.dur
            p.g = 0f
            p.vh = -p.h / p.dur
            p.t = 0f
            p.delay = i * 0.11f + rng.nextFloat() * 0.1f
            p.dmg = dmg
            p.splash = 60f
            p.siegeMul = 0.25f
        }
        fx.add(FxBuffer.BARRAGE, side.baseFront, 0f, side.index)
        return true
    }

    private fun barrageProj(era: Int) = when (era) {
        0 -> Proj.BOULDER; 1 -> Proj.JAVELIN; 2 -> Proj.ARROW; 3 -> Proj.BALL
        4 -> Proj.SHELL; 5 -> Proj.SHELL; 6 -> Proj.ROCKET; else -> Proj.BEAM
    }

    fun castRally(side: Side): Boolean {
        if (!abilityReady(side, 1)) return false
        side.abilityCd[1] = RALLY_CD * side.abilityCdMul
        side.rallyT = RALLY_TIME
        return true
    }

    // ---------------------------------------------------------------- symulacja

    fun step() {
        if (over || pendingChoice != null) return
        val dt = STEP
        time += dt
        updateFronts()
        val control = ((sides[0].front + sides[1].front) * 0.5f / Defs.LANE).coerceIn(0f, 1f)
        for (s in sides) {
            if (s.baseFlash > 0f) s.baseFlash -= dt
            if (!(endless && s.index == 1)) {
                val territory = if (s.index == 0) control else 1f - control
                val inc = Defs.income(s.era) * s.incomeMul * (0.7f + 0.6f * territory) * dt
                s.gold += inc
                s.goldEarned += inc
                s.xp += 0.9f * Defs.costScale(s.era) * s.xpMul * dt
            }
            train(s, dt)
            turrets(s, dt)
            for (i in 0..1) if (s.abilityCd[i] > 0f) s.abilityCd[i] -= dt
            if (s.rallyT > 0f) s.rallyT -= dt
        }
        for (s in sides) promote(s)
        if (endless) director(dt)
        ai?.update(dt)
        playerAi?.update(dt)
        for (u in units) if (u.active) updateUnit(u, dt)
        for (p in projs) if (p.active) updateProj(p, dt)
        if (!over) {
            if (sides[0].baseHp <= 0f) finish(1)
            else if (!endless && sides[1].baseHp <= 0f) finish(0)
        }
    }

    /** Koszt XP kolejnej rangi weteranów (gdy nie ma już epoki do awansu). */
    fun rankXp(s: Side): Float = Defs.evolveXp[max(0, cfg.maxEra - 1)] * 0.35f * (1f + s.rank * 0.25f)

    private fun promote(s: Side) {
        if (s.era < cfg.maxEra || s.rank >= 20) return
        if (endless && s.index == 1) return
        val need = rankXp(s)
        if (s.xp < need) return
        s.xp -= need
        s.rank++
        s.dmgMul *= 1.06f
        s.hpMul *= 1.06f
        s.turretDmgMul *= 1.06f
        fx.add(FxBuffer.EVOLVE, s.baseFront, 0f, s.index, 1f)
    }

    /**
     * Presja oblężnicza: po 8 minutach obrażenia zadawane bazom rosną o 20% na minutę.
     * Dzięki temu bitwa zawsze kiedyś się kończy (koniec z wiecznym patem przy wieżach).
     */
    fun siegePressure(): Float = if (endless || time < 480f) 1f else 1f + (time - 480f) / 60f * 0.2f

    fun surrender() { if (!over) { pendingChoice = null; finish(1) } }

    private fun finish(w: Int) {
        over = true
        winner = w
        val loser = sides[1 - w]
        loser.baseHp = 0f
        fx.add(FxBuffer.EXPLOSION, loser.baseFront - loser.dir * 80f, 120f, loser.index, 3f)
    }

    private fun updateFronts() {
        for (s in sides) {
            var f = s.baseFront
            var n = 0
            for (u in units) {
                if (!u.alive || u.side != s.index) continue
                n++
                if ((u.x - f) * s.dir > 0f) f = u.x
            }
            s.front = f
            s.aliveCount = n
        }
    }

    /** Najbardziej wysunięta żywa jednostka strony [sideIdx] (ta, która blokuje przeciwnika). */
    fun frontUnit(sideIdx: Int): Fighter? {
        val s = sides[sideIdx]
        var best: Fighter? = null
        for (u in units) {
            if (!u.alive || u.side != sideIdx) continue
            if (best == null || (u.x - best.x) * s.dir > 0f) best = u
        }
        return best
    }

    private fun train(s: Side, dt: Float) {
        if (s.queueSize == 0) return
        if (s.aliveCount >= s.cap) return
        val def = Defs.allUnits[s.queue[0]]
        s.trainT += dt * s.trainMul
        if (s.trainT >= def.trainTime) {
            if (spawn(s, def)) {
                s.trainT = 0f
                for (i in 1 until s.queueSize) s.queue[i - 1] = s.queue[i]
                s.queueSize--
            }
        }
    }

    fun spawn(s: Side, def: UnitDef, extraHpMul: Float = 1f): Boolean {
        var f: Fighter? = null
        for (u in units) if (!u.active) { f = u; break }
        if (f == null) return false
        f.active = true
        f.side = s.index
        f.def = def
        f.x = s.baseFront - s.dir * 30f
        f.maxHp = def.hp * s.hpMul * extraHpMul
        f.hp = f.maxHp
        f.dmg = def.dmg * s.dmgMul
        f.cd = 0.3f
        f.anim = rng.nextFloat() * 6f
        f.attackAnim = 0f
        f.flash = 0f
        f.dying = -1f
        f.moving = false
        f.serial = ++serialCounter
        s.spawned++
        s.spawnedByRole[def.role.ordinal]++
        s.aliveCount++
        return true
    }

    private fun turrets(s: Side, dt: Float) {
        val enemyIdx = 1 - s.index
        val target = frontUnit(enemyIdx) ?: run {
            for (i in 0 until s.slots) if (s.turretCd[i] > 0f) s.turretCd[i] -= dt
            return
        }
        for (i in 0 until s.slots) {
            if (s.turretFire[i] > 0f) s.turretFire[i] -= dt
            val te = s.turretEra[i]
            if (te < 0) continue
            if (s.turretCd[i] > 0f) { s.turretCd[i] -= dt; continue }
            val td = Defs.eras[te].turret
            val dist = (target.x - s.baseFront) * s.dir
            if (dist > td.range) continue
            s.turretCd[i] = td.cooldown
            s.turretFire[i] = 0.15f
            val h = turretHeight(i)
            launch(s.index, s.baseFront - s.dir * 40f, h, target.x, td.projectile, td.dmg * s.turretDmgMul, td.splash, 0f, te)
        }
    }

    fun turretHeight(slot: Int) = 150f + slot * 55f

    private fun updateUnit(u: Fighter, dt: Float) {
        if (u.dying >= 0f) {
            u.dying += dt
            if (u.dying > 0.8f) u.active = false
            return
        }
        val s = sides[u.side]
        val eIdx = 1 - u.side
        val e = sides[eIdx]
        if (u.flash > 0f) u.flash -= dt
        if (u.attackAnim > 0f) u.attackAnim -= dt
        val rally = if (s.rallyT > 0f) 1.4f else 1f
        if (u.cd > 0f) u.cd -= dt * rally

        val target = frontUnit(eIdx)
        val gap = if (target != null) (target.x - u.x) * s.dir - u.radius - target.radius
        else (e.baseFront - u.x) * s.dir - u.radius
        val def = u.def

        if (gap <= def.range) {
            u.moving = false
            if (u.cd <= 0f) {
                u.cd = def.cooldown
                u.attackAnim = 0.25f
                if (def.projectile == Proj.NONE) {
                    if (def.splash > 0f) {
                        val cx = if (target != null) target.x else e.baseFront
                        splashDamage(u.side, cx, def.splash, u.dmg, def.siegeMul, def.era)
                    } else if (target != null) {
                        damageUnit(target, u.dmg, u.side)
                        fx.add(FxBuffer.HIT, target.x, 40f * target.def.size, eIdx)
                    } else {
                        damageBase(e, u.dmg * def.siegeMul, u.side)
                    }
                } else {
                    val tx = target?.x ?: (e.baseFront + e.dir * 30f)
                    val h = 38f * def.size
                    launch(u.side, u.x + s.dir * u.radius, h, tx, def.projectile, u.dmg, def.splash, def.siegeMul, def.era)
                    fx.add(FxBuffer.MUZZLE, u.x + s.dir * u.radius * 1.3f, h, u.side, def.projectile.toFloat())
                }
            }
            return
        }

        // Ruch: zatrzymaj się za sojusznikiem albo na linii obrony.
        var blocked = false
        for (a in units) {
            if (a === u || !a.alive || a.side != u.side) continue
            val ahead = (a.x - u.x) * s.dir
            if (ahead > 0f && ahead < (u.radius + a.radius) * 0.9f + 6f) { blocked = true; break }
        }
        if (!blocked && s.hold) {
            val holdX = s.baseFront + s.dir * HOLD_DIST
            if ((u.x - holdX) * s.dir >= 0f) blocked = true
        }
        if (!blocked) {
            val v = def.speed * s.speedMul * rally
            u.x += s.dir * v * dt
            u.anim += v * dt * 0.09f
            u.moving = true
        } else {
            u.moving = false
        }
    }

    private fun allocProj(): Projectile? {
        for (p in projs) if (!p.active) { p.active = true; return p }
        return null
    }

    private fun launch(side: Int, x: Float, h: Float, tx: Float, type: Int, dmg: Float, splash: Float, siegeMul: Float, era: Int) {
        val p = allocProj() ?: return
        p.side = side; p.type = type; p.x = x; p.h = h; p.tx = tx; p.era = era
        p.dmg = dmg; p.splash = splash; p.siegeMul = if (siegeMul <= 0f) 0.6f else siegeMul
        p.delay = 0f; p.t = 0f
        val dist = abs(tx - x)
        val straight = type == Proj.BULLET || type == Proj.BEAM || type == Proj.PLASMA || type == Proj.ROCKET || type == Proj.BOLT
        val speed = when (type) {
            Proj.BULLET -> 1500f; Proj.BEAM -> 2600f; Proj.PLASMA -> 900f; Proj.ROCKET -> 700f; Proj.BOLT -> 1000f
            else -> 620f
        }
        p.dur = (dist / speed).coerceIn(0.12f, 1.3f)
        p.vx = (tx - x) / p.dur
        val targetH = 30f
        if (straight) {
            p.g = 0f
            p.vh = (targetH - h) / p.dur
        } else {
            p.g = 900f
            p.vh = (targetH - h) / p.dur + 0.5f * p.g * p.dur
        }
    }

    private fun updateProj(p: Projectile, dt: Float) {
        if (p.delay > 0f) { p.delay -= dt; return }
        p.t += dt
        p.x += p.vx * dt
        p.vh -= p.g * dt
        p.h += p.vh * dt
        if (p.t < p.dur) return
        p.active = false
        val e = sides[1 - p.side]
        if (p.splash > 0f) {
            splashDamage(p.side, p.tx, p.splash, p.dmg, p.siegeMul, p.era)
            return
        }
        var best: Fighter? = null
        var bestD = 42f
        for (u in units) {
            if (!u.alive || u.side == p.side) continue
            val d = abs(u.x - p.tx) - u.radius
            if (d < bestD) { bestD = d; best = u }
        }
        if (best != null) {
            damageUnit(best, p.dmg, p.side)
            fx.add(FxBuffer.HIT, best.x, 40f * best.def.size, best.side)
        } else if ((p.tx - e.baseFront) * -e.dir >= -50f) {
            damageBase(e, p.dmg * p.siegeMul, p.side)
        }
    }

    private fun splashDamage(attacker: Int, cx: Float, radius: Float, dmg: Float, siegeMul: Float, era: Int) {
        for (u in units) {
            if (!u.alive || u.side == attacker) continue
            if (abs(u.x - cx) <= radius + u.radius) damageUnit(u, dmg, attacker)
        }
        val e = sides[1 - attacker]
        if ((cx - e.baseFront) * -e.dir >= -radius) damageBase(e, dmg * siegeMul, attacker)
        fx.add(FxBuffer.EXPLOSION, cx, 20f, attacker, 0.6f + radius / 100f)
    }

    private fun damageUnit(u: Fighter, d: Float, attacker: Int) {
        if (!u.alive) return
        u.hp -= d
        u.flash = 0.12f
        if (u.hp <= 0f) {
            u.hp = 0f
            u.dying = 0f
            val k = sides[attacker]
            val v = sides[u.side]
            val cost = u.def.cost
            if (!(endless && attacker == 1)) {
                val g = cost * 1.1f * k.killGoldMul
                k.gold += g
                k.goldEarned += g
                if (attacker == 0) fx.add(FxBuffer.GOLD, u.x, 90f * u.def.size, 0, g)
            }
            k.xp += cost * 1.0f * k.xpMul
            v.xp += cost * 0.25f * v.xpMul
            k.kills++
            k.killValue += cost
            v.aliveCount--
            fx.add(FxBuffer.DEATH, u.x, 0f, u.side, u.def.size)
        }
    }

    private fun damageBase(s: Side, d: Float, attacker: Int) {
        if (endless && s.index == 1) return
        s.baseHp -= d * siegePressure()
        s.baseFlash = 0.15f
        val k = sides[attacker]
        k.xp += d * 0.12f * k.xpMul
        fx.add(FxBuffer.BASE_HIT, s.baseFront - s.dir * 30f, 80f, s.index)
    }

    // ---------------------------------------------------------------- tryb bez końca

    private fun director(dt: Float) {
        waveTimer -= dt
        if (waveTimer > 0f) return
        wave++
        waveTimer = max(16f, 28f - wave * 0.25f)
        val e = enemy
        e.era = min(cfg.maxEra, cfg.startEra + wave / 6)
        val inEra = wave % 6
        e.hpMul = (1f + inEra * 0.05f) * (1f + max(0, wave - 48) * 0.06f)
        e.dmgMul = 1f + inEra * 0.03f
        var budget = 45f * Defs.costScale(e.era) * (1f + inEra * 0.18f + wave * 0.02f)
        var n = 0
        while (budget > 0f && n < 16 && e.queueSize < e.queueMax) {
            val r = rng.nextInt(100)
            val role = when {
                wave >= 4 && r < 10 -> Role.ELITE
                r < 30 -> Role.HEAVY
                r < 60 -> Role.RANGED
                else -> Role.MELEE
            }
            val def = Defs.unit(e.era, role)
            e.queue[e.queueSize++] = def.id
            budget -= def.cost
            n++
        }
        if (wave % ENDLESS_CHOICE_EVERY == 0) offerDoctrines(player)
    }

    // ---------------------------------------------------------------- wynik

    fun stars(): Int {
        if (winner != 0) return 0
        val r = player.baseHp / player.baseMaxHp
        return when {
            r >= 0.75f -> 3
            r >= 0.4f -> 2
            else -> 1
        }
    }

    /** Wynik do rankingów. Liczony wyłącznie z przebiegu symulacji. */
    fun score(): Long = when (cfg.mode) {
        Mode.ENDLESS -> wave * 250L + player.kills * 12L + (time / 2f).toLong()
        else -> {
            if (winner != 0) player.killValue.toLong() / 4
            else {
                val hp = (player.baseHp / player.baseMaxHp * 5000f).toLong()
                val speed = max(0L, 6000L - (time * 4f).toLong())
                10_000L + hp + speed + player.kills * 10L
            }
        }
    }
}
