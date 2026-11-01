package com.centurywar.game

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32

/** Bitwa odtworzona ze zrzutu razem z danymi ekranu, które nie należą do symulacji. */
class RestoredBattle(val battle: Battle, val rankedDaily: Boolean, val camX: Float)

/**
 * Zrzut całego stanu bitwy do zwartego formatu binarnego (Android potrafi zabić aplikację
 * w tle, a bitwa trwa kilkanaście minut). Odtworzenie jest bit w bit deterministyczne:
 * po [decode] dalsza symulacja daje dokładnie ten sam przebieg co oryginał.
 *
 * Format: nagłówek (magic, [VERSION], długość, CRC32) + dane. Enumy zapisujemy po ordinal,
 * więc zmiana kolejności Role/Doctrine/Modifier/Mode wymaga podbicia [VERSION]. Dodatkowo
 * zapisujemy odcisk danych balansu ([Defs]); po aktualizacji balansu stary zrzut jest
 * odrzucany, bo dalsza symulacja nie byłaby już tą samą bitwą.
 *
 * Bufor efektów ([FxBuffer]) i akumulator klatek ekranu nie są zapisywane: nie wpływają
 * na symulację.
 */
object BattleSnapshot {
    const val VERSION = 13
    private const val MAGIC = 0x43575342 // "CWSB"
    private const val MAX_BYTES = 1 shl 20

    /** Odcisk danych balansu: zmienia się, gdy zmienia się cokolwiek w statystykach. */
    val defsFingerprint: Int by lazy {
        var h = 17
        fun mix(v: Int) { h = h * 31 + v }
        fun mixF(v: Float) = mix(v.toRawBits())
        mix(Defs.ERA_COUNT); mixF(Defs.LANE); mixF(Defs.BASE_FRONT); mix(Defs.MAX_TURRET_SLOTS)
        mix(Defs.MAX_UNIT_TIER); mixF(Defs.tierStatMul(2)); mixF(Defs.tierStatMul(3))
        mixF(Battle.BALANCE_BASE_HP_MUL); mixF(Battle.SIEGE_START); mixF(Battle.SIEGE_ATTRITION)
        // Reguły kart i doktryn wpływają na odtwarzalność tak samo jak parametry jednostek.
        mixF(Battle.MIN_ABILITY_CD_MUL); mixF(Battle.SUPPLY_INSTANT_GOLD)
        mix(EndlessCards.MAX_PERSISTENT_STACKS)
        for (stack in 1..EndlessCards.MAX_PERSISTENT_STACKS) mixF(EndlessCards.stackPower(stack))
        for (u in Defs.allUnits) {
            mix(u.id); mix(u.era); mix(u.variant); mix(u.art.ordinal); mix(u.role.ordinal); mix(u.cost); mixF(u.hp); mixF(u.dmg); mixF(u.range)
            mixF(u.cooldown); mixF(u.speed); mixF(u.trainTime); mixF(u.size); mixF(u.splash); mix(u.projectile); mixF(u.siegeMul)
            mix(Defs.unitUpgradeCost(u.era, u.role, 2)); mix(Defs.unitUpgradeCost(u.era, u.role, 3))
            mixF(Defs.unitRange(u, 3)); mixF(Defs.unitSplash(u, 3))
            mixF(Defs.unitSpeed(u, 3)); mixF(Defs.unitTrainRate(u, 3))
        }
        for (e in Defs.eras) {
            val t = e.turret
            mix(e.evolveXp); mix(t.cost); mixF(t.dmg); mixF(t.range); mixF(t.cooldown); mixF(t.splash); mix(t.projectile)
            mixF(Defs.baseHp(e.index)); mixF(Defs.income(e.index)); mixF(Defs.abilityDmg(e.index)); mixF(Defs.costScale(e.index))
        }
        for (era in 0 until Defs.ERA_COUNT) {
            for (variant in -1 until Defs.spec(era).variants.size) {
                val t = Defs.turret(era, variant)
                val a = Defs.barrage(era, variant)
                mix(t.art.ordinal); mix(t.projectile); mixF(t.dmg); mixF(t.cooldown)
                mix(a.projectile); mix(a.count); mixF(a.splash)
            }
        }
        // Zmiana macierzy kontr również unieważnia stare zrzuty bitwy.
        for (attacker in Role.values()) for (defender in Role.values()) mixF(Defs.counterMultiplier(attacker, defender))
        mixF(Defs.TURRET_VS_LATE_HEAVY)
        h
    }

    // ------------------------------------------------------------------ zapis

    fun encode(b: Battle, rankedDaily: Boolean = false, camX: Float = 0f): ByteArray {
        val bo = ByteArrayOutputStream(8192)
        val o = DataOutputStream(bo)
        o.writeInt(defsFingerprint)
        o.writeBoolean(rankedDaily)
        o.writeFloat(camX)
        writeConfig(o, b.cfg)
        o.writeLong(b.rng.state)
        o.writeLong(b.rng.aiState(0)); o.writeLong(b.rng.aiState(1))
        o.writeFloat(b.time)
        o.writeInt(b.stepCount)
        o.writeBoolean(b.over)
        o.writeInt(b.winner)
        o.writeInt(b.wave)
        o.writeFloat(b.waveTimer)
        o.writeInt(b.serialCounter)
        o.writeBoolean(b.achievementsRecorded)
        val pc = b.pendingChoice
        o.writeByte(pc?.size ?: -1)
        pc?.forEach { o.writeByte(it.ordinal) }
        o.writeByte(b.pendingVariant ?: -1)
        val cards = b.pendingCards
        o.writeByte(cards?.size ?: -1)
        cards?.forEach { o.writeByte(it.ordinal) }
        o.writeByte(b.currentWaveTheme.ordinal)
        o.writeByte(b.nextWaveTheme.ordinal)
        o.writeInt(b.bossesSpawned)
        o.writeInt(b.bossesDefeated)
        o.writeInt(b.nextBossToSpawn)
        o.writeInt(b.bossRewardsPending)
        for (s in b.sides) writeSide(o, s)
        writeAi(o, b.ai)
        writeAi(o, b.playerAi)
        o.writeBoolean(b.commandController != null)
        b.commandController?.writeSnapshot(o)

        o.writeShort(b.units.count { it.active })
        for ((i, u) in b.units.withIndex()) {
            if (!u.active) continue
            o.writeShort(i)
            o.writeByte(u.side); o.writeShort(u.def.id); o.writeByte(u.tier)
            o.writeFloat(u.x); o.writeFloat(u.hp); o.writeFloat(u.maxHp); o.writeFloat(u.dmg); o.writeFloat(u.waveSpeedMul); o.writeFloat(u.cd)
            o.writeFloat(u.anim); o.writeFloat(u.attackAnim); o.writeFloat(u.flash)
            o.writeBoolean(u.moving); o.writeFloat(u.dying); o.writeInt(u.serial)
            o.writeByte(u.bossKind); o.writeByte(u.bossSecondaryKind)
            o.writeFloat(u.bossCd); o.writeFloat(u.bossSummonCd); o.writeBoolean(u.bossRageActive)
            o.writeFloat(u.burnDps); o.writeFloat(u.burnLeft); o.writeFloat(u.slowLeft)
        }
        o.writeShort(b.projs.count { it.active })
        for ((i, p) in b.projs.withIndex()) {
            if (!p.active) continue
            o.writeShort(i)
            o.writeByte(p.side); o.writeByte(p.type); o.writeByte(p.era)
            o.writeByte(p.shooterRole?.ordinal ?: -1); o.writeByte(p.turretEra); o.writeByte(p.variant)
            o.writeFloat(p.x); o.writeFloat(p.h); o.writeFloat(p.vx); o.writeFloat(p.vh); o.writeFloat(p.g)
            o.writeFloat(p.tx); o.writeFloat(p.t); o.writeFloat(p.dur); o.writeFloat(p.delay)
            o.writeFloat(p.dmg); o.writeFloat(p.splash); o.writeFloat(p.siegeMul); o.writeFloat(p.burnDps)
        }
        o.flush()
        val payload = bo.toByteArray()
        val crc = CRC32().apply { update(payload) }.value.toInt()
        val out = ByteArrayOutputStream(payload.size + 16)
        DataOutputStream(out).apply {
            writeInt(MAGIC); writeInt(VERSION); writeInt(payload.size); writeInt(crc); write(payload); flush()
        }
        return out.toByteArray()
    }

    fun writeConfig(o: DataOutputStream, c: BattleConfig) {
        o.writeByte(c.mode.ordinal)
        o.writeLong(c.seed)
        o.writeFloat(c.aiPower)
        o.writeByte(c.startEra)
        o.writeByte(c.maxEra)
        var mask = 0
        for (m in c.modifiers) mask = mask or (1 shl m.ordinal)
        o.writeInt(mask)
        val b = c.playerBonuses
        o.writeFloat(b.startGold); o.writeFloat(b.incomeMul); o.writeFloat(b.unitHpMul); o.writeFloat(b.unitDmgMul)
        o.writeFloat(b.baseHpMul); o.writeFloat(b.turretDmgMul); o.writeFloat(b.trainMul); o.writeFloat(b.xpMul)
        o.writeInt(b.popCap); o.writeFloat(b.abilityCdMul); o.writeInt(b.turretSlots)
        o.writeBoolean(b.eliteUnlocked); o.writeBoolean(b.rallyUnlocked)
        o.writeInt(c.stageIndex)
        o.writeBoolean(c.tutorial)
        o.writeByte(c.aiPersonality?.ordinal ?: -1)
        o.writeBoolean(c.variantsEnabled)
        o.writeByte(c.weather.ordinal)
        o.writeLong(c.weekly)
        o.writeByte(c.weeklyCard?.ordinal ?: -1)
        o.writeByte(c.dailyEvent?.ordinal ?: -1)
    }

    private fun writeSide(o: DataOutputStream, s: Side) {
        o.writeByte(s.era); o.writeByte(s.eraVariant); o.writeFloat(s.gold); o.writeFloat(s.xp); o.writeFloat(s.baseHp); o.writeFloat(s.baseMaxHp)
        o.writeByte(s.queueSize); o.writeByte(s.queueMax)
        for (i in 0 until s.queueSize) o.writeShort(s.queue[i])
        for (i in 0 until s.queueSize) o.writeByte(s.queueWaveTheme[i])
        o.writeFloat(s.trainT)
        for (i in 0 until Defs.MAX_TURRET_SLOTS) { o.writeByte(s.turretEra[i]); o.writeByte(s.turretVariant[i]); o.writeFloat(s.turretCd[i]); o.writeFloat(s.turretFire[i]) }
        o.writeByte(s.slots)
        o.writeFloat(s.abilityCd[0]); o.writeFloat(s.abilityCd[1])
        o.writeFloat(s.rallyT); o.writeBoolean(s.hold); o.writeShort(s.aliveCount); o.writeFloat(s.front); o.writeFloat(s.baseFlash)
        o.writeFloat(s.incomeMul); o.writeFloat(s.hpMul); o.writeFloat(s.dmgMul); o.writeFloat(s.baseHpMul)
        o.writeFloat(s.turretDmgMul); o.writeFloat(s.trainMul); o.writeFloat(s.xpMul); o.writeFloat(s.abilityCdMul)
        o.writeFloat(s.siegeMul); o.writeFloat(s.killGoldMul); o.writeFloat(s.speedMul); o.writeFloat(s.turretCostMul)
        o.writeFloat(s.turretRateMul); o.writeFloat(s.turretBurnRatio); o.writeFloat(s.rangedDmgMul)
        o.writeFloat(s.eliteDmgMul); o.writeFloat(s.lastStandMul); o.writeFloat(s.cloneChance)
        o.writeFloat(s.repairPerWave); o.writeInt(s.freeElitesPerWave); o.writeInt(s.freeEliteCredits)
        o.writeFloat(s.autoBarrageInterval); o.writeFloat(s.autoBarrageTimer)
        for (v in s.cardStacks) o.writeInt(v)
        o.writeShort(s.cap); o.writeBoolean(s.eliteUnlocked); o.writeBoolean(s.rallyUnlocked)
        o.writeShort(s.doctrines.size)
        for (d in s.doctrines) o.writeByte(d.ordinal)
        o.writeShort(s.rank); o.writeShort(s.armory)
        for (tier in s.unitTier) o.writeByte(tier)
        o.writeInt(s.kills); o.writeInt(s.killValue); o.writeInt(s.spawned); o.writeFloat(s.goldEarned)
        for (v in s.spawnedByRole) o.writeInt(v)
        // liczniki misji i osiągnięć (v4)
        o.writeInt(s.turretsBuilt); o.writeInt(s.barragesUsed); o.writeInt(s.ralliesUsed)
        o.writeInt(s.elitesLost); o.writeInt(s.erasAdvanced); o.writeFloat(s.baseDamageTaken)
        o.writeFloat(s.rallyArmorMul); o.writeFloat(s.supplyUntil)
        o.writeFloat(s.heavyBaseDmgMul); o.writeFloat(s.turretRangeMul); o.writeBoolean(s.fogSight)
    }

    private fun writeAi(o: DataOutputStream, ai: Ai?) {
        o.writeBoolean(ai != null)
        if (ai == null) return
        o.writeFloat(ai.thinkTimer)
        o.writeByte(ai.plannedRoleState?.ordinal ?: -1)
        o.writeByte(ai.personality.ordinal)
        o.writeInt(ai.saveEraState)
        o.writeFloat(ai.saveUntilState)
        o.writeFloat(ai.evolveReadyAtState)
        o.writeFloat(ai.holdUntilState)
        o.writeFloat(ai.nextHoldAtState)
    }

    // ------------------------------------------------------------------ odczyt

    /** Zwraca null, gdy zrzut jest uszkodzony, z innej wersji albo z innym balansem. */
    fun decode(bytes: ByteArray): RestoredBattle? {
        return try {
            if (bytes.size < 16 || bytes.size > MAX_BYTES) return null
            val h = DataInputStream(ByteArrayInputStream(bytes))
            if (h.readInt() != MAGIC || h.readInt() != VERSION) return null
            val len = h.readInt()
            val crc = h.readInt()
            if (len != bytes.size - 16) return null
            if (CRC32().apply { update(bytes, 16, len) }.value.toInt() != crc) return null
            read(DataInputStream(ByteArrayInputStream(bytes, 16, len)))
        } catch (_: Exception) {
            null
        }
    }

    private fun read(i: DataInputStream): RestoredBattle? {
        if (i.readInt() != defsFingerprint) return null
        val ranked = i.readBoolean()
        val camX = i.readFloat()
        val cfg = readConfig(i) ?: return null
        val b = Battle(cfg)
        b.rng.state = i.readLong()
        b.rng.setAiState(0, i.readLong()); b.rng.setAiState(1, i.readLong())
        b.time = i.readFloat()
        b.stepCount = i.readInt()
        require(b.stepCount >= 0 && b.time == b.stepCount * Battle.STEP)
        b.over = i.readBoolean()
        b.winner = i.readInt()
        b.wave = i.readInt()
        b.waveTimer = i.readFloat()
        b.serialCounter = i.readInt()
        b.achievementsRecorded = i.readBoolean()
        val pcn = i.readByte().toInt()
        b.pendingChoice = if (pcn < 0) null else Array(pcn) { enumAt(Doctrine.values(), i.readByte().toInt()) }
        b.pendingVariant = i.readByte().toInt().let { if (it < 0) null else era(it) }
        val cn = i.readByte().toInt()
        require(cn == -1 || cn == 3)
        b.pendingCards = if (cn < 0) null else Array(cn) { enumAt(EndlessCard.values(), i.readByte().toInt()) }
        b.currentWaveTheme = enumAt(EndlessWaveTheme.values(), i.readByte().toInt())
        b.nextWaveTheme = enumAt(EndlessWaveTheme.values(), i.readByte().toInt())
        b.bossesSpawned = i.readInt()
        b.bossesDefeated = i.readInt()
        b.nextBossToSpawn = i.readInt()
        b.bossRewardsPending = i.readInt()
        require(b.bossesSpawned >= 0 && b.bossesDefeated in 0..b.bossesSpawned &&
            b.nextBossToSpawn >= 10 && b.bossRewardsPending in 0..1000)
        for (s in b.sides) readSide(i, s)
        if (!readAi(i, b.ai)) return null
        if (i.readBoolean()) {
            b.restoringSnapshot = true
            val pa = Ai(b, 0)
            readAiState(i, pa)
            b.playerAi = pa
            b.restoringSnapshot = false
        }
        if (i.readBoolean()) BattleController(b).readSnapshot(i, b.stepCount)

        val un = i.readShort().toInt()
        require(un in 0..Battle.MAX_UNITS)
        repeat(un) {
            val u = b.units[i.readShort().toInt()]
            require(!u.active)
            u.active = true
            u.side = side(i.readByte().toInt())
            u.def = Defs.allUnits[i.readShort().toInt()]
            u.tier = i.readUnsignedByte().also { require(it in 1..Defs.MAX_UNIT_TIER) }
            u.x = i.readFloat(); u.hp = i.readFloat(); u.maxHp = i.readFloat(); u.dmg = i.readFloat(); u.waveSpeedMul = i.readFloat(); u.cd = i.readFloat()
            require(u.waveSpeedMul == 1f || u.waveSpeedMul == 0.85f)
            u.anim = i.readFloat(); u.attackAnim = i.readFloat(); u.flash = i.readFloat()
            u.moving = i.readBoolean(); u.dying = i.readFloat(); u.serial = i.readInt()
            u.bossKind = i.readByte().toInt().also { require(it in 0..EndlessBosses.MAX_KIND) }
            u.bossSecondaryKind = i.readByte().toInt().also { require(it in 0..EndlessBosses.MAX_KIND) }
            require(u.bossKind != EndlessBosses.NONE || u.bossSecondaryKind == EndlessBosses.NONE)
            u.bossCd = i.readFloat(); u.bossSummonCd = i.readFloat()
            u.bossRageActive = i.readBoolean()
            u.burnDps = i.readFloat(); u.burnLeft = i.readFloat()
            u.slowLeft = i.readFloat().also { require(it.isFinite() && it in 0f..2f) }
        }
        val pn = i.readShort().toInt()
        require(pn in 0..Battle.MAX_PROJ)
        repeat(pn) {
            val p = b.projs[i.readShort().toInt()]
            require(!p.active)
            p.active = true
            p.side = side(i.readByte().toInt()); p.type = i.readByte().toInt(); p.era = era(i.readByte().toInt())
            val role = i.readByte().toInt()
            p.shooterRole = if (role == -1) null else enumAt(Role.values(), role)
            p.turretEra = i.readByte().toInt().also { require(it in -1 until Defs.ERA_COUNT) }
            p.variant = i.readByte().toInt().also { require(it in -1 until Defs.spec(p.era).variants.size) }
            p.x = i.readFloat(); p.h = i.readFloat(); p.vx = i.readFloat(); p.vh = i.readFloat(); p.g = i.readFloat()
            p.tx = i.readFloat(); p.t = i.readFloat(); p.dur = i.readFloat(); p.delay = i.readFloat()
            p.dmg = i.readFloat(); p.splash = i.readFloat(); p.siegeMul = i.readFloat(); p.burnDps = i.readFloat()
        }
        return RestoredBattle(b, ranked, camX)
    }

    internal fun readConfig(i: DataInputStream, hasDailyEvent: Boolean = true): BattleConfig? {
        val mode = enumAt(Mode.values(), i.readByte().toInt())
        val seed = i.readLong()
        val aiPower = i.readFloat()
        val startEra = era(i.readByte().toInt())
        val maxEra = era(i.readByte().toInt())
        val mask = i.readInt()
        val mods = Modifier.values().filter { mask and (1 shl it.ordinal) != 0 }.toSet()
        val bonuses = Bonuses(
            startGold = i.readFloat(), incomeMul = i.readFloat(), unitHpMul = i.readFloat(), unitDmgMul = i.readFloat(),
            baseHpMul = i.readFloat(), turretDmgMul = i.readFloat(), trainMul = i.readFloat(), xpMul = i.readFloat(),
            popCap = i.readInt(), abilityCdMul = i.readFloat(), turretSlots = i.readInt(),
            eliteUnlocked = i.readBoolean(), rallyUnlocked = i.readBoolean(),
        )
        val stageIndex = i.readInt()
        val tutorial = i.readBoolean()
        val p = i.readByte().toInt()
        val personality = if (p < 0) null else enumAt(AiPersonality.values(), p)
        val enabled = i.readBoolean()
        val weather = enumAt(Weather.values(), i.readByte().toInt())
        val weekly = i.readLong()
        val cardId = i.readByte().toInt()
        val eventId = if (hasDailyEvent) i.readByte().toInt() else -1
        if (startEra > maxEra || weekly < -1L ||
            (weekly >= 0L) != (cardId >= 0) || (weekly >= 0L && mode != Mode.ENDLESS) ||
            (eventId >= 0 && mode != Mode.DAILY)) return null
        val weeklyCard = if (cardId < 0) null else enumAt(EndlessCard.values(), cardId)
        val event = if (eventId < 0) null else enumAt(DailyEvent.values(), eventId)
        return BattleConfig(mode, seed, aiPower, startEra, maxEra, mods, bonuses, stageIndex, tutorial,
            personality, enabled, weather, weekly, weeklyCard, event)
    }

    private fun readSide(i: DataInputStream, s: Side) {
        s.era = era(i.readByte().toInt())
        s.eraVariant = variant(s.era, i.readByte().toInt()); s.gold = i.readFloat(); s.xp = i.readFloat(); s.baseHp = i.readFloat(); s.baseMaxHp = i.readFloat()
        s.queueSize = i.readByte().toInt(); s.queueMax = i.readByte().toInt()
        require(s.queueSize in 0..Battle.QUEUE_CAP && s.queueMax in 0..Battle.QUEUE_CAP)
        for (k in 0 until s.queueSize) s.queue[k] = i.readShort().toInt().also { require(it in Defs.allUnits.indices) }
        for (k in 0 until s.queueSize) s.queueWaveTheme[k] = i.readUnsignedByte().also { require(it in EndlessWaveTheme.entries.indices) }
        s.trainT = i.readFloat()
        for (k in 0 until Defs.MAX_TURRET_SLOTS) {
            s.turretEra[k] = i.readByte().toInt().also { require(it in -1 until Defs.ERA_COUNT) }
            val tv = i.readByte().toInt()
            s.turretVariant[k] = if (s.turretEra[k] < 0) {
                require(tv == -1); -1
            } else variant(s.turretEra[k], tv)
            s.turretCd[k] = i.readFloat(); s.turretFire[k] = i.readFloat()
        }
        s.slots = i.readByte().toInt().also { require(it in 0..Defs.MAX_TURRET_SLOTS) }
        s.abilityCd[0] = i.readFloat(); s.abilityCd[1] = i.readFloat()
        s.rallyT = i.readFloat(); s.hold = i.readBoolean(); s.aliveCount = i.readShort().toInt(); s.front = i.readFloat(); s.baseFlash = i.readFloat()
        s.incomeMul = i.readFloat(); s.hpMul = i.readFloat(); s.dmgMul = i.readFloat(); s.baseHpMul = i.readFloat()
        s.turretDmgMul = i.readFloat(); s.trainMul = i.readFloat(); s.xpMul = i.readFloat(); s.abilityCdMul = i.readFloat()
        s.siegeMul = i.readFloat(); s.killGoldMul = i.readFloat(); s.speedMul = i.readFloat(); s.turretCostMul = i.readFloat()
        s.turretRateMul = i.readFloat(); s.turretBurnRatio = i.readFloat(); s.rangedDmgMul = i.readFloat()
        s.eliteDmgMul = i.readFloat(); s.lastStandMul = i.readFloat(); s.cloneChance = i.readFloat()
        s.repairPerWave = i.readFloat(); s.freeElitesPerWave = i.readInt(); s.freeEliteCredits = i.readInt()
        s.autoBarrageInterval = i.readFloat(); s.autoBarrageTimer = i.readFloat()
        for (k in s.cardStacks.indices) s.cardStacks[k] = i.readInt()
        s.cap = i.readShort().toInt(); s.eliteUnlocked = i.readBoolean(); s.rallyUnlocked = i.readBoolean()
        val dn = i.readShort().toInt()
        require(dn in 0..1000)
        s.doctrines.clear()
        s.doctrines.clear() // karta startowa została już zastosowana w konstruktorze
        repeat(dn) { s.doctrines.add(enumAt(Doctrine.values(), i.readByte().toInt())) }
        s.rank = i.readShort().toInt(); s.armory = i.readShort().toInt()
        for (k in s.unitTier.indices) s.unitTier[k] = i.readUnsignedByte().also { require(it in 1..Defs.MAX_UNIT_TIER) }
        s.kills = i.readInt(); s.killValue = i.readInt(); s.spawned = i.readInt(); s.goldEarned = i.readFloat()
        for (k in s.spawnedByRole.indices) s.spawnedByRole[k] = i.readInt().also { require(it >= 0) }
        s.turretsBuilt = i.readInt(); s.barragesUsed = i.readInt(); s.ralliesUsed = i.readInt()
        s.elitesLost = i.readInt(); s.erasAdvanced = i.readInt(); s.baseDamageTaken = i.readFloat()
        s.rallyArmorMul = i.readFloat(); s.supplyUntil = i.readFloat()
        s.heavyBaseDmgMul = i.readFloat(); s.turretRangeMul = i.readFloat(); s.fogSight = i.readBoolean()
        require(s.rallyArmorMul in 0.01f..1f && s.supplyUntil >= 0f && s.supplyUntil.isFinite())
        require(s.heavyBaseDmgMul in 1f..10f && s.turretRangeMul in 1f..10f)
        require(s.turretsBuilt >= 0 && s.barragesUsed >= 0 && s.ralliesUsed >= 0)
        require(s.elitesLost >= 0 && s.erasAdvanced >= 0 && s.baseDamageTaken >= 0f)
    }

    /** false = niezgodność (zrzut ma AI wroga, a bitwa nie, albo odwrotnie). */
    private fun readAi(i: DataInputStream, ai: Ai?): Boolean {
        val present = i.readBoolean()
        if (present != (ai != null)) return false
        if (ai == null) return true
        readAiState(i, ai)
        return true
    }

    private fun readAiState(i: DataInputStream, ai: Ai) {
        ai.thinkTimer = i.readFloat()
        val r = i.readByte().toInt()
        ai.plannedRoleState = if (r < 0) null else enumAt(Role.values(), r)
        ai.personality = enumAt(AiPersonality.values(), i.readByte().toInt())
        ai.saveEraState = i.readInt().also { require(it in -1 until Defs.ERA_COUNT) }
        ai.saveUntilState = i.readFloat()
        ai.evolveReadyAtState = i.readFloat()
        ai.holdUntilState = i.readFloat()
        ai.nextHoldAtState = i.readFloat()
    }

    private fun <T> enumAt(values: Array<T>, ordinal: Int): T {
        require(ordinal in values.indices)
        return values[ordinal]
    }

    private fun variant(era: Int, value: Int): Int {
        require(value in -1 until Defs.spec(era).variants.size)
        return value
    }
    private fun era(v: Int): Int { require(v in 0 until Defs.ERA_COUNT); return v }
    private fun side(v: Int): Int { require(v == 0 || v == 1); return v }
}

/**
 * Plik zrzutu bitwy (filesDir/battle.snap). Zapis jest atomowy (plik tymczasowy, fsync,
 * rename). Zapisy w tle idą przez jeden wątek; numer sekwencyjny gwarantuje, że starszy
 * zrzut z kolejki nigdy nie nadpisze nowszego ani nie wskrzesi usuniętego.
 */
class SnapshotStore(private val file: File) {
    private val tmp = File(file.parentFile, file.name + ".tmp")
    private val lock = Any()
    private var seq = 0L
    private var written = 0L
    private val io by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "snapshot-io").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
    }

    /** Zapis w tle. Zamek chroni wyłącznie numerację, nigdy fsync. */
    fun saveAsync(bytes: ByteArray) {
        val my = synchronized(lock) { ++seq }
        io.execute {
            if (synchronized(lock) { my != seq || my <= written }) return@execute
            if (writeAtomic(bytes)) synchronized(lock) { written = my }
        }
    }

    /** Do narzędzi i testów; gra używa wyłącznie saveAsync. */
    fun saveNow(bytes: ByteArray): Boolean {
        val my = synchronized(lock) { ++seq }
        return try {
            io.submit<Boolean> {
                if (synchronized(lock) { my != seq || my <= written }) false
                else writeAtomic(bytes).also { if (it) synchronized(lock) { written = my } }
            }.get(10, TimeUnit.SECONDS)
        } catch (_: Exception) { false }
    }

    /** Usunięcie z wątku gry odbywa się także poza nim. */
    fun deleteAsync() {
        val my = synchronized(lock) { ++seq }
        io.execute {
            if (synchronized(lock) { my != seq }) return@execute
            GameIoCounter.diskAccess()
            file.delete()
            tmp.delete()
            synchronized(lock) { written = my }
        }
    }

    /** Ograniczona czasowo bariera używana przy przechodzeniu aplikacji w tło. */
    fun awaitWrites(timeoutMs: Long): Boolean {
        if (timeoutMs <= 0L) return false
        return try {
            io.submit { }.get(timeoutMs, TimeUnit.MILLISECONDS)
            true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (_: Exception) { false }
    }

    /** Synchroniczne usunięcie tylko dla narzędzi i odczytu przy starcie. */
    fun delete() {
        val my = synchronized(lock) { ++seq }
        try {
            io.submit {
                GameIoCounter.diskAccess()
                file.delete()
                tmp.delete()
                synchronized(lock) { written = my }
            }.get(10, TimeUnit.SECONDS)
        } catch (_: Exception) { }
    }

    fun load(): ByteArray? = synchronized(lock) {
        GameIoCounter.diskAccess()
        try {
            if (!file.isFile || file.length() > (1 shl 20)) null else file.readBytes()
        } catch (_: Exception) {
            null
        }
    }

    /** Poprawny .tmp po przerwanym rename jest odzyskiwany bez utraty starego pliku. */
    fun loadBattle(): RestoredBattle? {
        GameIoCounter.diskAccess()
        val candidates = if (tmp.lastModified() >= file.lastModified()) arrayOf(tmp, file)
            else arrayOf(file, tmp)
        var found = false
        for (candidate in candidates) {
            val bytes = try {
                if (!candidate.isFile || candidate.length() > (1 shl 20)) null
                else candidate.readBytes()
            } catch (_: Exception) { null }
            if (bytes == null) continue
            found = true
            val restored = BattleSnapshot.decode(bytes)
            if (restored != null && !restored.battle.over) {
                if (candidate == tmp) {
                    try {
                        java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    } catch (_: Exception) { }
                }
                return restored
            }
        }
        if (found) delete()
        return null
    }

    private fun writeAtomic(bytes: ByteArray): Boolean = try {
        GameIoCounter.diskAccess()
        file.parentFile?.mkdirs()
        FileOutputStream(tmp).use { it.write(bytes); it.flush(); it.fd.sync() }
        if (tmp.renameTo(file)) true
        else try {
            java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (_: Exception) { false }
    } catch (_: Exception) {
        // Niedokończony .tmp może być odzyskany przy następnym starcie.
        false
    }
}
