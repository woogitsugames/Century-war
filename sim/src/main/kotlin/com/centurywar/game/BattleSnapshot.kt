package com.centurywar.game

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32

/**
 * Bitwa odtworzona ze zrzutu razem z danymi ekranu, które nie należą do symulacji.
 * [battleId] to trwały identyfikator rozliczenia (A156-10); 0 = zrzut sprzed wersji 17.
 */
class RestoredBattle(val battle: Battle, val rankedDaily: Boolean, val camX: Float, val battleId: Long = 0L)

/**
 * R10-01: krótki opis przerwanej bitwy zapisywany obok zrzutu (filesDir/battle.meta).
 * Format jest stały i niezależny od [BattleSnapshot.VERSION] oraz od odcisku balansu, więc
 * po aktualizacji gry, która odrzuca stary zrzut, nadal wiadomo, jaka bitwa przepadła
 * i jaką rekompensatę przyznać. Nie wpływa na symulację ani na wynik bitwy.
 * [consolation] to medale, jakie gra dałaby w chwili zapisu za porażkę.
 */
class BattleMeta(
    val battleId: Long, val mode: Mode, val stageIndex: Int, val weekly: Long,
    val dailyDay: Long, val rankedDaily: Boolean, val wave: Int, val timeS: Int,
    val kills: Int, val consolation: Int, val savedAtMillis: Long,
) {
    fun encode(): ByteArray {
        val bo = ByteArrayOutputStream(64)
        DataOutputStream(bo).apply {
            writeLong(battleId); writeUTF(mode.name); writeInt(stageIndex); writeLong(weekly)
            writeLong(dailyDay); writeBoolean(rankedDaily); writeInt(wave); writeInt(timeS)
            writeInt(kills); writeInt(consolation); writeLong(savedAtMillis); flush()
        }
        val payload = bo.toByteArray()
        val crc = CRC32().apply { update(payload) }.value.toInt()
        val out = ByteArrayOutputStream(payload.size + 16)
        DataOutputStream(out).apply {
            writeInt(MAGIC); writeInt(META_VERSION); writeInt(payload.size); writeInt(crc); write(payload); flush()
        }
        return out.toByteArray()
    }

    companion object {
        /** „CWBM”. */
        const val MAGIC = 0x4357424D
        /** Wersja tego pliku; nigdy nie zależy od wersji zrzutu bitwy. */
        const val META_VERSION = 1
        private const val MAX_BYTES = 256

        /** Null, gdy plik jest uszkodzony, nieznany albo opisuje nieznany tryb. */
        fun decode(bytes: ByteArray): BattleMeta? = try {
            if (bytes.size < 16 || bytes.size > MAX_BYTES) null else {
                val h = DataInputStream(ByteArrayInputStream(bytes))
                val magic = h.readInt()
                val version = h.readInt()
                val len = h.readInt()
                val crc = h.readInt()
                if (magic != MAGIC || version != META_VERSION || len != bytes.size - 16 ||
                    CRC32().apply { update(bytes, 16, len) }.value.toInt() != crc) null
                else {
                    val data = ByteArrayInputStream(bytes, 16, len)
                    val i = DataInputStream(data)
                    val battleId = i.readLong()
                    val modeName = i.readUTF()
                    val mode = Mode.entries.firstOrNull { it.name == modeName }
                    val meta = BattleMeta(battleId, mode ?: Mode.CAMPAIGN, i.readInt(), i.readLong(),
                        i.readLong(), i.readBoolean(), i.readInt(), i.readInt(), i.readInt(),
                        i.readInt(), i.readLong())
                    meta.takeIf { mode != null && data.available() == 0 && it.battleId != 0L &&
                        it.consolation >= 0 && it.wave >= 0 && it.timeS >= 0 && it.kills >= 0 }
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}

/** R10-01: dlaczego zrzutu nie da się wznowić ([BattleSnapshot.probe]). */
enum class SnapshotReject { OK, CORRUPT, OLDER_FORMAT, NEWER_FORMAT, BALANCE_CHANGED }

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
    /** 22: dzień 62 — Mode.ORDERS i pola Rozkazów sztabu w konfiguracji (tylko dla ORDERS). */
    /** 23: dzień 66 — [BattleConfig.orderFlags] (eskorta) w konfiguracji, tylko dla ORDERS. */
    /**
     * 25: dzień 69 — stan cech wariantów epok ([Fighter.traitUsed], [Fighter.traitT])
     * i licznik [Side.traitUses]. Numer 24 zostaje wolny dla równoległej zmiany formatu.
     */
    const val VERSION = 25
    /** Pierwsza wersja ze stanem cech jednostek wariantów epok (dzień 69). */
    private const val FIRST_WITH_TRAITS = 25
    /** Pierwsza wersja z bajtem [BattleConfig.orderFlags] w konfiguracji Rozkazów. */
    private const val FIRST_WITH_ORDER_FLAGS = 23
    /** Wersje 16–18 nie zapisywały [Side.siegeDamageTaken] (Dzień 2); brak pola = 0. */
    private const val FIRST_WITH_SIEGE_DAMAGE = 19
    /** Wersje 16–17 zapisywały karty Endless sprzed zlecenia 170 (bez dwóch ostatnich kart). */
    private const val CARDS_BEFORE_170 = 2
    /** Wersje 16–19 nie znały czterech kart zlecenia 184 (Saperzy, Mur tarcz, Skarbiec, Zakład). */
    private const val CARDS_BEFORE_184 = 4
    /** Wersje 16–20 nie znały czterech kart dnia 58 (Szyk pik, Straż przednia, Nagroda, Rogi). */
    private const val CARDS_BEFORE_206 = 4

    /** Liczba stosów kart zapisana w danej wersji formatu (nowe karty zawsze na końcu enuma). */
    private fun storedCards(version: Int): Int {
        val all = EndlessCard.entries.size
        return when {
            version >= 21 -> all
            version >= 20 -> all - CARDS_BEFORE_206
            version >= 18 -> all - CARDS_BEFORE_206 - CARDS_BEFORE_184
            else -> all - CARDS_BEFORE_206 - CARDS_BEFORE_184 - CARDS_BEFORE_170
        }
    }
    /**
     * Wersja 16 różni się tylko brakiem identyfikatora rozliczenia na końcu danych.
     * Taki zrzut nadal wznawiamy (aktualizacja gry nie kasuje przerwanej bitwy),
     * a ekran bitwy nadaje mu nowy identyfikator.
     */
    const val LEGACY_VERSION_WITHOUT_ID = 16
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
            mix(u.trait); mix(u.body)
        }
        // Dzień 69: parametry cech i karty wyboru wariantów jednej roli.
        mixF(Trait.SHIELD_TIME); mixF(Trait.SHIELD_MUL); mixF(Trait.DASH_MUL)
        mixF(Trait.RAM_MUL); mixF(Trait.BRACE_TIME); mixF(Trait.BRACE_MUL)
        for (era in 0 until Defs.ERA_COUNT) {
            mix(Defs.recruitUnit(era)?.id ?: -1)
            for (v in Defs.variantChoices(era)) mix(v)
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

    fun encode(b: Battle, rankedDaily: Boolean = false, camX: Float = 0f, battleId: Long = 0L): ByteArray =
        encode(b, rankedDaily, camX, battleId, VERSION)

    /** [formatVersion] < [VERSION] tylko w testach zgodności wstecz (zapis jak w starszej wersji gry). */
    internal fun encode(b: Battle, rankedDaily: Boolean, camX: Float, battleId: Long, formatVersion: Int): ByteArray {
        val bo = ByteArrayOutputStream(8192)
        val o = DataOutputStream(bo)
        o.writeInt(defsFingerprint)
        o.writeBoolean(rankedDaily)
        o.writeFloat(camX)
        writeConfig(o, b.cfg, formatVersion >= FIRST_WITH_ORDER_FLAGS, formatVersion >= FIRST_WITH_TRAITS)
        o.writeBoolean(b.turretTiersEnabled)
        o.writeBoolean(b.commanderEnabled)
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
        for (s in b.sides) writeSide(o, s, formatVersion)
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
            o.writeBoolean(u.isCommander); o.writeFloat(u.commanderSkillCd)
            if (formatVersion >= FIRST_WITH_TRAITS) { o.writeBoolean(u.traitUsed); o.writeFloat(u.traitT) }
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
        // v17: identyfikator rozliczenia nie wpływa na symulację, dlatego jest na końcu.
        if (formatVersion >= 17) o.writeLong(battleId)
        o.flush()
        val payload = bo.toByteArray()
        val crc = CRC32().apply { update(payload) }.value.toInt()
        val out = ByteArrayOutputStream(payload.size + 16)
        DataOutputStream(out).apply {
            writeInt(MAGIC); writeInt(formatVersion); writeInt(payload.size); writeInt(crc); write(payload); flush()
        }
        return out.toByteArray()
    }

    /** [orderFlags] = false tylko przy zapisie w starszym formacie (v22, testy zgodności). */
    fun writeConfig(o: DataOutputStream, c: BattleConfig, orderFlags: Boolean = true, recruits: Boolean = true) {
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
        // Dzień 62: pola Rozkazów sztabu tylko dla Mode.ORDERS, więc zapis pozostałych
        // trybów (zrzuty i pliki powtórek) jest bajt w bajt taki jak przed v22.
        if (c.mode == Mode.ORDERS) {
            o.writeShort(c.playerAllowed); o.writeShort(c.enemyAllowed)
            o.writeInt(c.timeLimitSteps); o.writeBoolean(c.holdWins)
            // Dzień 66 (v23): reguły rozkazu, np. eskorta dowódcy.
            if (orderFlags) o.writeByte(c.orderFlags)
        }
        // Dzień 69 (zrzut v25, powtórka v4): warianty jednej roli, tylko w zapisie potyczki.
        if (c.mode == Mode.SKIRMISH && recruits) o.writeBoolean(c.eraRecruits)
    }

    private fun writeSide(o: DataOutputStream, s: Side, formatVersion: Int) {
        o.writeByte(s.era); o.writeByte(s.eraVariant); o.writeFloat(s.gold); o.writeFloat(s.xp); o.writeFloat(s.baseHp); o.writeFloat(s.baseMaxHp)
        o.writeByte(s.queueSize); o.writeByte(s.queueMax)
        for (i in 0 until s.queueSize) o.writeShort(s.queue[i])
        for (i in 0 until s.queueSize) o.writeByte(s.queueWaveTheme[i])
        o.writeFloat(s.trainT)
        for (i in 0 until Defs.MAX_TURRET_SLOTS) { o.writeByte(s.turretEra[i]); o.writeByte(s.turretVariant[i]); o.writeByte(s.turretTier[i]); o.writeFloat(s.turretCd[i]); o.writeFloat(s.turretFire[i]) }
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
        val cards = storedCards(formatVersion)
        for (k in 0 until cards) o.writeInt(s.cardStacks[k])
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
        o.writeFloat(s.commanderCd)
        if (formatVersion >= FIRST_WITH_SIEGE_DAMAGE) o.writeFloat(s.siegeDamageTaken)
        if (formatVersion >= FIRST_WITH_TRAITS) o.writeInt(s.traitUses)
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
            if (h.readInt() != MAGIC) return null
            val version = h.readInt()
            if (!supported(version)) return null
            val len = h.readInt()
            val crc = h.readInt()
            if (len != bytes.size - 16) return null
            if (CRC32().apply { update(bytes, 16, len) }.value.toInt() != crc) return null
            val data = ByteArrayInputStream(bytes, 16, len)
            read(DataInputStream(data), version)?.takeIf { data.available() == 0 }
        } catch (_: Exception) {
            null
        }
    }

    private fun supported(version: Int): Boolean =
        version == VERSION || version == 23 || version == 22 || version == 21 || version == 20 || version == 19 || version == 18 || version == 17 ||
            version == LEGACY_VERSION_WITHOUT_ID

    /**
     * R10-01: powód odrzucenia zrzutu, ta sama kolejność sprawdzeń co [decode] i [read],
     * ale bez budowania bitwy. [SnapshotReject.OK] oznacza tylko zgodny nagłówek, wersję
     * i odcisk balansu; pełną poprawność danych sprawdza dopiero [decode].
     */
    fun probe(bytes: ByteArray): SnapshotReject = try {
        if (bytes.size < 16 || bytes.size > MAX_BYTES) SnapshotReject.CORRUPT else {
            val h = DataInputStream(ByteArrayInputStream(bytes))
            if (h.readInt() != MAGIC) SnapshotReject.CORRUPT else {
                val version = h.readInt()
                val len = h.readInt()
                val crc = h.readInt()
                when {
                    // Nowsza wersja mogła zmienić nawet układ nagłówka: nic więcej nie czytamy.
                    version > VERSION -> SnapshotReject.NEWER_FORMAT
                    len != bytes.size - 16 -> SnapshotReject.CORRUPT
                    CRC32().apply { update(bytes, 16, len) }.value.toInt() != crc -> SnapshotReject.CORRUPT
                    !supported(version) -> SnapshotReject.OLDER_FORMAT
                    len < 4 -> SnapshotReject.CORRUPT
                    h.readInt() != defsFingerprint -> SnapshotReject.BALANCE_CHANGED
                    else -> SnapshotReject.OK
                }
            }
        }
    } catch (_: Exception) {
        SnapshotReject.CORRUPT
    }

    /**
     * Identyfikator rozliczenia z końca danych zrzutu (wersje >= 17) bez odczytu bitwy;
     * null, gdy wersja go nie zapisywała albo nagłówek jest niepoprawny. Służy do
     * dopasowania opisu [BattleMeta] do zrzutu, którego nie da się już wczytać.
     */
    fun storedBattleId(bytes: ByteArray): Long? = try {
        if (bytes.size < 16 + 8 || bytes.size > MAX_BYTES) null else {
            val h = DataInputStream(ByteArrayInputStream(bytes))
            val magic = h.readInt()
            val version = h.readInt()
            val len = h.readInt()
            if (magic != MAGIC || version < 17 || version > VERSION || len != bytes.size - 16) null
            else DataInputStream(ByteArrayInputStream(bytes, bytes.size - 8, 8)).readLong()
        }
    } catch (_: Exception) {
        null
    }

    private fun read(i: DataInputStream, version: Int): RestoredBattle? {
        if (i.readInt() != defsFingerprint) return null
        val ranked = i.readBoolean()
        val camX = i.readFloat()
        val cfg = readConfig(i, hasOrderFlags = version >= FIRST_WITH_ORDER_FLAGS,
            hasRecruits = version >= FIRST_WITH_TRAITS) ?: return null
        val b = Battle(cfg, turretTiersEnabled = i.readBoolean(), commanderEnabled = i.readBoolean())
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
        for (s in b.sides) readSide(i, s, version)
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
            require(EndlessWaveTheme.isLegalUnitSpeedMul(u.waveSpeedMul))
            u.anim = i.readFloat(); u.attackAnim = i.readFloat(); u.flash = i.readFloat()
            u.moving = i.readBoolean(); u.dying = i.readFloat(); u.serial = i.readInt()
            u.bossKind = i.readByte().toInt().also { require(it in 0..EndlessBosses.MAX_KIND) }
            u.bossSecondaryKind = i.readByte().toInt().also { require(it in 0..EndlessBosses.MAX_KIND) }
            require(u.bossKind != EndlessBosses.NONE || u.bossSecondaryKind == EndlessBosses.NONE)
            u.bossCd = i.readFloat(); u.bossSummonCd = i.readFloat()
            u.bossRageActive = i.readBoolean()
            u.burnDps = i.readFloat(); u.burnLeft = i.readFloat()
            u.slowLeft = i.readFloat().also { require(it.isFinite() && it in 0f..2f) }
            u.isCommander = i.readBoolean()
            u.commanderSkillCd = i.readFloat().also {
                require(it.isFinite() && it in 0f..Battle.COMMANDER_SKILL_COOLDOWN)
            }
            // Starsze zrzuty nie znały cech: jednostki bez cechy mają zawsze stan zerowy.
            if (version >= FIRST_WITH_TRAITS) {
                u.traitUsed = i.readBoolean()
                u.traitT = i.readFloat().also { require(it.isFinite() && it >= 0f && it <= 1_000_000f) }
            } else {
                u.traitUsed = false; u.traitT = 0f
            }
        }
        for (side in b.sides) {
            val activeCommanders = b.units.count { it.alive && it.side == side.index && it.isCommander }
            require(activeCommanders <= 1 && (side.commanderCd < 0f) == (activeCommanders == 1))
            require(b.commanderAvailable || activeCommanders == 0)
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
        val battleId = if (version >= 17) i.readLong() else 0L
        return RestoredBattle(b, ranked, camX, battleId)
    }

    internal fun readConfig(i: DataInputStream, hasDailyEvent: Boolean = true, hasOrderFlags: Boolean = true,
                            hasRecruits: Boolean = true): BattleConfig? {
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
        val orders = mode == Mode.ORDERS
        val playerAllowed = if (orders) i.readUnsignedShort() else OrderRules.ALL
        val enemyAllowed = if (orders) i.readUnsignedShort() else OrderRules.ALL
        val timeLimit = if (orders) i.readInt() else 0
        val holdWins = if (orders) i.readBoolean() else false
        // Dzień 66: zrzuty v22 i powtórki v2 nie miały reguł rozkazu (= 0, jak partia 1).
        val orderFlags = if (orders && hasOrderFlags) i.readUnsignedByte() else 0
        // Dzień 69: starsze zapisy potyczek nie znały wariantów jednej roli (= wyłączone).
        val recruits = if (mode == Mode.SKIRMISH && hasRecruits) i.readBoolean() else false
        if (orderFlags and OrderFlags.ALL.inv() != 0) return null
        if (playerAllowed !in 0..OrderRules.ALL || enemyAllowed !in 0..OrderRules.ALL ||
            timeLimit !in 0..Replay.MAX_STEPS) return null
        if (startEra > maxEra || weekly < -1L ||
            (weekly >= 0L) != (cardId >= 0) || (weekly >= 0L && mode != Mode.ENDLESS) ||
            (eventId >= 0 && mode != Mode.DAILY)) return null
        val weeklyCard = if (cardId < 0) null else enumAt(EndlessCard.values(), cardId)
        val event = if (eventId < 0) null else enumAt(DailyEvent.values(), eventId)
        return BattleConfig(mode, seed, aiPower, startEra, maxEra, mods, bonuses, stageIndex, tutorial,
            personality, enabled, weather, weekly, weeklyCard, event,
            playerAllowed, enemyAllowed, timeLimit, holdWins, orderFlags, recruits)
    }

    private fun readSide(i: DataInputStream, s: Side, version: Int) {
        s.era = era(i.readByte().toInt())
        s.eraVariant = sideVariant(s.era, i.readByte().toInt()); s.gold = i.readFloat(); s.xp = i.readFloat(); s.baseHp = i.readFloat(); s.baseMaxHp = i.readFloat()
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
            s.turretTier[k] = i.readUnsignedByte().also { require(if (s.turretEra[k] < 0) it == 0 else it in 1..3) }
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
        for (k in 0 until storedCards(version)) s.cardStacks[k] = i.readInt()
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
        s.commanderCd = i.readFloat().also {
            require(it.isFinite() && it >= -1f && it <= Battle.COMMANDER_COOLDOWN)
        }
        require(s.rallyArmorMul in 0.01f..1f && s.supplyUntil >= 0f && s.supplyUntil.isFinite())
        require(s.heavyBaseDmgMul in 1f..10f && s.turretRangeMul in 1f..10f)
        require(s.turretsBuilt >= 0 && s.barragesUsed >= 0 && s.ralliesUsed >= 0)
        require(s.elitesLost >= 0 && s.erasAdvanced >= 0 && s.baseDamageTaken >= 0f)
        s.siegeDamageTaken = if (version >= FIRST_WITH_SIEGE_DAMAGE) i.readFloat().also {
            require(it.isFinite() && it >= 0f)
        } else 0f
        s.traitUses = if (version >= FIRST_WITH_TRAITS) i.readInt().also { require(it >= 0) } else 0
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
    /** Dzień 69: wybór strony obejmuje też wariant jednej roli (0 = nowa jednostka). */
    private fun sideVariant(era: Int, value: Int): Int {
        require(value in -1 until Defs.variantLimit(era))
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
    private val backup = File(file.parentFile, file.name + ".bak")
    private val backupTmp = File(file.parentFile, file.name + ".bak.tmp")
    /** R10-01: opis bitwy obok zrzutu (battle.snap -> battle.meta), poza kopią zapasową. */
    internal val metaFile = File(file.parentFile, file.name.substringBeforeLast('.') + ".meta")
    private val metaTmp = File(file.parentFile, metaFile.name + ".tmp")
    private val lock = Any()
    private var seq = 0L
    private var written = 0L
    /** Identyfikator bitwy ostatnio zleconego zapisu (lub wczytanej bitwy); 0 = brak. */
    private var owner = 0L
    /**
     * Wątek zapisu, tworzony leniwie przy pierwszym zapisie lub usunięciu (pod [lock]).
     * D41: [close] go zamyka; bez tego każde ponowne otwarcie gry w tym samym procesie
     * zostawiało kolejny bezczynny wątek „snapshot-io”.
     */
    private var executor: ExecutorService? = null
    private var closed = false
    /**
     * D41: zadania I/O wykonują się pod tym zamkiem, żeby zadanie wykonane po [close]
     * na wątku wołającym nie pisało pliku równocześnie z dokańczanym zadaniem z kolejki.
     * Kolejność zamków zawsze [ioMutex] → [lock].
     */
    private val ioMutex = Any()

    /** Wątek zapisu albo null po [close]. */
    private fun io(): ExecutorService? = synchronized(lock) {
        if (closed) null
        else executor ?: Executors.newSingleThreadExecutor { r ->
            Thread(r, "snapshot-io").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
        }.also { executor = it }
    }

    /**
     * Zadanie w tle w kolejności zlecenia. Po [close] (np. wywołanie zwrotne po onDestroy)
     * zadanie wykonuje się od razu na wątku wołającym, więc zlecony zapis lub usunięcie
     * nie przepada.
     */
    private fun runIo(task: () -> Unit) {
        val ex = io()
        if (ex != null) {
            try {
                ex.execute { synchronized(ioMutex) { task() } }
                return
            } catch (_: RejectedExecutionException) {
                // close() między io() a execute(): wykonaj na miejscu.
            }
        }
        synchronized(ioMutex) { task() }
    }

    /** Jak [runIo], ale czeka na wynik (narzędzia, start). */
    private fun <T> callIo(timeoutMs: Long, task: () -> T): T {
        val ex = io()
        if (ex != null) {
            try {
                return ex.submit<T> { synchronized(ioMutex) { task() } }.get(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (_: RejectedExecutionException) {
                // close() między io() a submit(): wykonaj na miejscu.
            }
        }
        return synchronized(ioMutex) { task() }
    }

    /**
     * D41: zamyka wątek zapisu (MainActivity.onDestroy). Zadania już zlecone kończą się
     * normalnie (shutdown, nie shutdownNow). Późniejsze wywołania nie rzucają wyjątków:
     * wykonują się synchronicznie na wątku wołającym. Bez wpływu na symulację.
     */
    fun close() {
        synchronized(lock) {
            closed = true
            executor?.shutdown()
        }
    }

    /** Testy D41: czy [close] został wywołany. */
    val isClosed: Boolean get() = synchronized(lock) { closed }

    /** Testy D41: czy wątek zapisu w ogóle powstał. */
    internal val ioStarted: Boolean get() = synchronized(lock) { executor != null }

    /**
     * R10-01: bitwa, której zrzutu nie dało się wznowić po aktualizacji gry (inna wersja
     * formatu albo balans), opisana przez [BattleMeta]. Ustawiane przez [loadBattle].
     */
    @Volatile var lostByUpdate: BattleMeta? = null
        private set

    /**
     * Zapis w tle. Zamek chroni tylko numerację, nigdy fsync. [meta] (R10-01) trafia na dysk
     * w tym samym zadaniu, dopiero po udanym zapisie zrzutu.
     */
    fun saveAsync(bytes: ByteArray, battleId: Long = 0L, meta: ByteArray? = null) {
        val my = synchronized(lock) { owner = battleId; ++seq }
        runIo {
            if (synchronized(lock) { my != seq || my <= written }) return@runIo
            if (writeAtomic(bytes, meta)) synchronized(lock) { written = my }
        }
    }

    /** Do narzędzi i testów; gra używa wyłącznie saveAsync. */
    fun saveNow(bytes: ByteArray, battleId: Long = 0L, meta: ByteArray? = null): Boolean {
        val my = synchronized(lock) { owner = battleId; ++seq }
        return try {
            callIo(10_000L) {
                if (synchronized(lock) { my != seq || my <= written }) false
                else writeAtomic(bytes, meta).also { if (it) synchronized(lock) { written = my } }
            }
        } catch (e: Exception) {
            SaveGuard.report("snapshot-save", e)
            false
        }
    }

    /** Usunięcie z wątku gry odbywa się także poza nim. */
    fun deleteAsync() {
        val my = synchronized(lock) { owner = 0L; ++seq }
        runIo {
            if (synchronized(lock) { my != seq }) return@runIo
            removeFiles()
            synchronized(lock) { written = my }
        }
    }

    /**
     * Usuwa zrzut rozliczonej bitwy [battleId] (A156-10), wywoływane dopiero po trwałym
     * zapisie profilu, więc zwykle z wątku I/O profilu. Jeśli w międzyczasie zlecono zapis
     * innej bitwy (nowa bitwa zdążyła się zapisać), jej zrzut zostaje nietknięty.
     * Zwraca false, gdy usunięcie pominięto.
     */
    fun deleteSettledAsync(battleId: Long): Boolean {
        if (battleId == 0L) return false
        val my = synchronized(lock) {
            if (owner != battleId) return false
            owner = 0L
            ++seq
        }
        runIo {
            if (synchronized(lock) { my != seq }) return@runIo
            removeFiles()
            synchronized(lock) { written = my }
        }
        return true
    }

    /** Ograniczona czasowo bariera używana przy przechodzeniu aplikacji w tło. */
    fun awaitWrites(timeoutMs: Long): Boolean {
        if (timeoutMs <= 0L) return false
        return try {
            val ex = io()
            if (ex != null) ex.submit { }.get(timeoutMs, TimeUnit.MILLISECONDS)
            else {
                // D41: po close() bariera = dokończenie zadań z kolejki.
                val old = synchronized(lock) { executor }
                if (old != null && !old.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) return false
            }
            true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (_: Exception) { false }
    }

    /** Synchroniczne usunięcie tylko dla narzędzi i odczytu przy starcie. */
    fun delete() {
        val my = synchronized(lock) { owner = 0L; ++seq }
        try {
            callIo(10_000L) {
                removeFiles()
                synchronized(lock) { written = my }
            }
        } catch (e: Exception) {
            SaveGuard.report("snapshot-delete", e)
        }
    }

    private fun removeFiles() {
        GameIoCounter.diskAccess()
        for (candidate in arrayOf(file, tmp, backup, backupTmp, metaFile, metaTmp))
            if (candidate.exists() && !candidate.delete()) SaveGuard.report("snapshot-delete")
    }

    fun load(): ByteArray? = synchronized(lock) {
        GameIoCounter.diskAccess()
        try {
            if (!file.isFile || file.length() > (1 shl 20)) null else file.readBytes()
        } catch (e: Exception) {
            SaveGuard.report("snapshot-read", e)
            null
        }
    }

    /**
     * Wczytuje poprawny zapis główny, .tmp albo ostatnią bezpieczną kopię.
     * [settledBattleId] to identyfikator ostatnio rozliczonej bitwy z profilu: taki zrzut
     * pozostał tylko dlatego, że proces zginął między zapisem profilu a usunięciem
     * zrzutu (A156-10). Jest usuwany, a nie oferowany do wznowienia, żeby nagroda
     * nie została naliczona drugi raz.
     */
    fun loadBattle(settledBattleId: Long = 0L): RestoredBattle? {
        GameIoCounter.diskAccess()
        lostByUpdate = null
        // R10-01: czy któryś kandydat był poprawnym zrzutem starszej wersji gry lub balansu.
        var lostByUpgrade = false
        var lostSnapshotId: Long? = null
        val originals = if (tmp.lastModified() >= file.lastModified()) arrayOf(tmp, file)
            else arrayOf(file, tmp)
        val backups = if (backupTmp.lastModified() >= backup.lastModified()) arrayOf(backupTmp, backup)
            else arrayOf(backup, backupTmp)
        for (candidate in originals + backups) {
            if (!candidate.exists()) continue
            val bytes = try {
                if (!candidate.isFile || candidate.length() !in 16L..(1 shl 20).toLong()) {
                    SaveGuard.report("snapshot-size")
                    null
                } else candidate.readBytes()
            } catch (e: Exception) {
                SaveGuard.report("snapshot-read", e)
                null
            } ?: continue
            // Nie nadpisuj zrzutu stworzonego przez nowszą wersję aplikacji.
            val header = DataInputStream(ByteArrayInputStream(bytes))
            if (header.readInt() == 0x43575342 && header.readInt() > BattleSnapshot.VERSION) {
                SaveGuard.report("snapshot-version")
                return null
            }
            val restored = BattleSnapshot.decode(bytes)
            if (restored == null) {
                SaveGuard.report("snapshot-format")
                val reason = BattleSnapshot.probe(bytes)
                if (!lostByUpgrade && (reason == SnapshotReject.OLDER_FORMAT ||
                        reason == SnapshotReject.BALANCE_CHANGED)) {
                    lostByUpgrade = true
                    lostSnapshotId = BattleSnapshot.storedBattleId(bytes)
                }
                continue
            }
            if (restored.battle.over ||
                (settledBattleId != 0L && restored.battleId == settledBattleId)) {
                delete()
                return null
            }
            if (candidate != file) {
                try {
                    SaveGuard.atomicWrite(file, bytes)
                } catch (e: Exception) {
                    SaveGuard.report("snapshot-recovery", e)
                }
            }
            synchronized(lock) { owner = restored.battleId }
            return restored
        }
        if (lostByUpgrade) {
            val meta = readMeta()
            if (meta != null && (lostSnapshotId == null || lostSnapshotId == meta.battleId)) {
                if (settledBattleId != 0L && meta.battleId == settledBattleId) {
                    // Rekompensata już w profilu (proces zginął przed usunięciem plików).
                    delete()
                    return null
                }
                lostByUpdate = meta
                synchronized(lock) { owner = meta.battleId }
                return null
            }
        }
        // Zapis niekompatybilny zostaje do diagnostyki / migracji, profil nie jest dotykany.
        return null
    }

    /** Opis bitwy z battle.meta albo null (brak, uszkodzony, nieznany format). */
    fun readMeta(): BattleMeta? = try {
        if (!metaFile.isFile || metaFile.length() > 4096) null
        else BattleMeta.decode(metaFile.readBytes())
    } catch (e: Exception) {
        SaveGuard.report("snapshot-meta-read", e)
        null
    }

    private fun writeAtomic(bytes: ByteArray, meta: ByteArray? = null): Boolean = try {
        GameIoCounter.diskAccess()
        SaveGuard.atomicWrite(file, bytes)
        try {
            SaveGuard.writeBackup(file, bytes)
        } catch (e: Exception) {
            SaveGuard.report("snapshot-backup", e)
        }
        // R10-01: opis zawsze po zrzucie; bez opisu stary plik nie może opisywać nowej bitwy.
        try {
            if (meta != null) SaveGuard.atomicWrite(metaFile, meta)
            else if (metaFile.exists() && !metaFile.delete()) SaveGuard.report("snapshot-meta-delete")
        } catch (e: Exception) {
            SaveGuard.report("snapshot-meta-write", e)
        }
        true
    } catch (e: Exception) {
        SaveGuard.report("snapshot-write", e)
        false
    }
}
