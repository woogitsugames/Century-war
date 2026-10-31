package com.centurywar.game

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
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
    const val VERSION = 3
    private const val MAGIC = 0x43575342 // "CWSB"
    private const val MAX_BYTES = 1 shl 20

    /** Odcisk danych balansu: zmienia się, gdy zmienia się cokolwiek w statystykach. */
    val defsFingerprint: Int by lazy {
        var h = 17
        fun mix(v: Int) { h = h * 31 + v }
        fun mixF(v: Float) = mix(v.toRawBits())
        mix(Defs.ERA_COUNT); mixF(Defs.LANE); mixF(Defs.BASE_FRONT); mix(Defs.MAX_TURRET_SLOTS)
        for (u in Defs.allUnits) {
            mix(u.id); mix(u.era); mix(u.role.ordinal); mix(u.cost); mixF(u.hp); mixF(u.dmg); mixF(u.range)
            mixF(u.cooldown); mixF(u.speed); mixF(u.trainTime); mixF(u.size); mixF(u.splash); mix(u.projectile); mixF(u.siegeMul)
        }
        for (e in Defs.eras) {
            val t = e.turret
            mix(e.evolveXp); mix(t.cost); mixF(t.dmg); mixF(t.range); mixF(t.cooldown); mixF(t.splash); mix(t.projectile)
            mixF(Defs.baseHp(e.index)); mixF(Defs.income(e.index)); mixF(Defs.abilityDmg(e.index)); mixF(Defs.costScale(e.index))
        }
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
        o.writeFloat(b.time)
        o.writeBoolean(b.over)
        o.writeInt(b.winner)
        o.writeInt(b.wave)
        o.writeFloat(b.waveTimer)
        o.writeInt(b.serialCounter)
        o.writeBoolean(b.achievementsRecorded)
        val pc = b.pendingChoice
        o.writeByte(pc?.size ?: -1)
        pc?.forEach { o.writeByte(it.ordinal) }
        for (s in b.sides) writeSide(o, s)
        writeAi(o, b.ai)
        writeAi(o, b.playerAi)

        o.writeShort(b.units.count { it.active })
        for ((i, u) in b.units.withIndex()) {
            if (!u.active) continue
            o.writeShort(i)
            o.writeByte(u.side); o.writeShort(u.def.id)
            o.writeFloat(u.x); o.writeFloat(u.hp); o.writeFloat(u.maxHp); o.writeFloat(u.dmg); o.writeFloat(u.cd)
            o.writeFloat(u.anim); o.writeFloat(u.attackAnim); o.writeFloat(u.flash)
            o.writeBoolean(u.moving); o.writeFloat(u.dying); o.writeInt(u.serial)
        }
        o.writeShort(b.projs.count { it.active })
        for ((i, p) in b.projs.withIndex()) {
            if (!p.active) continue
            o.writeShort(i)
            o.writeByte(p.side); o.writeByte(p.type); o.writeByte(p.era)
            o.writeFloat(p.x); o.writeFloat(p.h); o.writeFloat(p.vx); o.writeFloat(p.vh); o.writeFloat(p.g)
            o.writeFloat(p.tx); o.writeFloat(p.t); o.writeFloat(p.dur); o.writeFloat(p.delay)
            o.writeFloat(p.dmg); o.writeFloat(p.splash); o.writeFloat(p.siegeMul)
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

    private fun writeConfig(o: DataOutputStream, c: BattleConfig) {
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
    }

    private fun writeSide(o: DataOutputStream, s: Side) {
        o.writeByte(s.era); o.writeFloat(s.gold); o.writeFloat(s.xp); o.writeFloat(s.baseHp); o.writeFloat(s.baseMaxHp)
        o.writeByte(s.queueSize); o.writeByte(s.queueMax)
        for (i in 0 until s.queueSize) o.writeShort(s.queue[i])
        o.writeFloat(s.trainT)
        for (i in 0 until Defs.MAX_TURRET_SLOTS) { o.writeByte(s.turretEra[i]); o.writeFloat(s.turretCd[i]); o.writeFloat(s.turretFire[i]) }
        o.writeByte(s.slots)
        o.writeFloat(s.abilityCd[0]); o.writeFloat(s.abilityCd[1])
        o.writeFloat(s.rallyT); o.writeBoolean(s.hold); o.writeShort(s.aliveCount); o.writeFloat(s.front); o.writeFloat(s.baseFlash)
        o.writeFloat(s.incomeMul); o.writeFloat(s.hpMul); o.writeFloat(s.dmgMul); o.writeFloat(s.baseHpMul)
        o.writeFloat(s.turretDmgMul); o.writeFloat(s.trainMul); o.writeFloat(s.xpMul); o.writeFloat(s.abilityCdMul)
        o.writeFloat(s.siegeMul); o.writeFloat(s.killGoldMul); o.writeFloat(s.speedMul); o.writeFloat(s.turretCostMul)
        o.writeShort(s.cap); o.writeBoolean(s.eliteUnlocked); o.writeBoolean(s.rallyUnlocked)
        o.writeShort(s.doctrines.size)
        for (d in s.doctrines) o.writeByte(d.ordinal)
        o.writeShort(s.rank); o.writeShort(s.armory)
        o.writeInt(s.kills); o.writeInt(s.killValue); o.writeInt(s.spawned); o.writeFloat(s.goldEarned)
        // liczniki misji i osiągnięć (v3)
        for (count in s.trainedByRole) o.writeInt(count)
        o.writeInt(s.turretsBuilt); o.writeInt(s.barragesUsed); o.writeInt(s.ralliesUsed)
        o.writeInt(s.elitesLost); o.writeInt(s.erasAdvanced); o.writeFloat(s.baseDamageTaken)
    }

    private fun writeAi(o: DataOutputStream, ai: Ai?) {
        o.writeBoolean(ai != null)
        if (ai == null) return
        o.writeFloat(ai.thinkTimer)
        o.writeByte(ai.plannedRoleState?.ordinal ?: -1)
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
        b.time = i.readFloat()
        b.over = i.readBoolean()
        b.winner = i.readInt()
        b.wave = i.readInt()
        b.waveTimer = i.readFloat()
        b.serialCounter = i.readInt()
        b.achievementsRecorded = i.readBoolean()
        val pcn = i.readByte().toInt()
        b.pendingChoice = if (pcn < 0) null else Array(pcn) { enumAt(Doctrine.values(), i.readByte().toInt()) }
        for (s in b.sides) readSide(i, s)
        if (!readAi(i, b.ai)) return null
        if (i.readBoolean()) {
            val pa = Ai(b, 0)
            pa.thinkTimer = i.readFloat()
            val r = i.readByte().toInt()
            pa.plannedRoleState = if (r < 0) null else enumAt(Role.values(), r)
            b.playerAi = pa
        }

        val un = i.readShort().toInt()
        require(un in 0..Battle.MAX_UNITS)
        repeat(un) {
            val u = b.units[i.readShort().toInt()]
            require(!u.active)
            u.active = true
            u.side = side(i.readByte().toInt())
            u.def = Defs.allUnits[i.readShort().toInt()]
            u.x = i.readFloat(); u.hp = i.readFloat(); u.maxHp = i.readFloat(); u.dmg = i.readFloat(); u.cd = i.readFloat()
            u.anim = i.readFloat(); u.attackAnim = i.readFloat(); u.flash = i.readFloat()
            u.moving = i.readBoolean(); u.dying = i.readFloat(); u.serial = i.readInt()
        }
        val pn = i.readShort().toInt()
        require(pn in 0..Battle.MAX_PROJ)
        repeat(pn) {
            val p = b.projs[i.readShort().toInt()]
            require(!p.active)
            p.active = true
            p.side = side(i.readByte().toInt()); p.type = i.readByte().toInt(); p.era = era(i.readByte().toInt())
            p.x = i.readFloat(); p.h = i.readFloat(); p.vx = i.readFloat(); p.vh = i.readFloat(); p.g = i.readFloat()
            p.tx = i.readFloat(); p.t = i.readFloat(); p.dur = i.readFloat(); p.delay = i.readFloat()
            p.dmg = i.readFloat(); p.splash = i.readFloat(); p.siegeMul = i.readFloat()
        }
        return RestoredBattle(b, ranked, camX)
    }

    private fun readConfig(i: DataInputStream): BattleConfig? {
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
        if (startEra > maxEra) return null
        return BattleConfig(mode, seed, aiPower, startEra, maxEra, mods, bonuses, stageIndex, tutorial)
    }

    private fun readSide(i: DataInputStream, s: Side) {
        s.era = era(i.readByte().toInt()); s.gold = i.readFloat(); s.xp = i.readFloat(); s.baseHp = i.readFloat(); s.baseMaxHp = i.readFloat()
        s.queueSize = i.readByte().toInt(); s.queueMax = i.readByte().toInt()
        require(s.queueSize in 0..Battle.QUEUE_CAP && s.queueMax in 0..Battle.QUEUE_CAP)
        for (k in 0 until s.queueSize) s.queue[k] = i.readShort().toInt().also { require(it in Defs.allUnits.indices) }
        s.trainT = i.readFloat()
        for (k in 0 until Defs.MAX_TURRET_SLOTS) {
            s.turretEra[k] = i.readByte().toInt().also { require(it in -1 until Defs.ERA_COUNT) }
            s.turretCd[k] = i.readFloat(); s.turretFire[k] = i.readFloat()
        }
        s.slots = i.readByte().toInt().also { require(it in 0..Defs.MAX_TURRET_SLOTS) }
        s.abilityCd[0] = i.readFloat(); s.abilityCd[1] = i.readFloat()
        s.rallyT = i.readFloat(); s.hold = i.readBoolean(); s.aliveCount = i.readShort().toInt(); s.front = i.readFloat(); s.baseFlash = i.readFloat()
        s.incomeMul = i.readFloat(); s.hpMul = i.readFloat(); s.dmgMul = i.readFloat(); s.baseHpMul = i.readFloat()
        s.turretDmgMul = i.readFloat(); s.trainMul = i.readFloat(); s.xpMul = i.readFloat(); s.abilityCdMul = i.readFloat()
        s.siegeMul = i.readFloat(); s.killGoldMul = i.readFloat(); s.speedMul = i.readFloat(); s.turretCostMul = i.readFloat()
        s.cap = i.readShort().toInt(); s.eliteUnlocked = i.readBoolean(); s.rallyUnlocked = i.readBoolean()
        val dn = i.readShort().toInt()
        require(dn in 0..1000)
        s.doctrines.clear()
        repeat(dn) { s.doctrines.add(enumAt(Doctrine.values(), i.readByte().toInt())) }
        s.rank = i.readShort().toInt(); s.armory = i.readShort().toInt()
        s.kills = i.readInt(); s.killValue = i.readInt(); s.spawned = i.readInt(); s.goldEarned = i.readFloat()
        for (k in s.trainedByRole.indices) s.trainedByRole[k] = i.readInt().also { require(it >= 0) }
        s.turretsBuilt = i.readInt(); s.barragesUsed = i.readInt(); s.ralliesUsed = i.readInt()
        s.elitesLost = i.readInt(); s.erasAdvanced = i.readInt(); s.baseDamageTaken = i.readFloat()
        require(s.turretsBuilt >= 0 && s.barragesUsed >= 0 && s.ralliesUsed >= 0)
        require(s.elitesLost >= 0 && s.erasAdvanced >= 0 && s.baseDamageTaken >= 0f)
    }

    /** false = niezgodność (zrzut ma AI wroga, a bitwa nie, albo odwrotnie). */
    private fun readAi(i: DataInputStream, ai: Ai?): Boolean {
        val present = i.readBoolean()
        if (present != (ai != null)) return false
        if (ai == null) return true
        ai.thinkTimer = i.readFloat()
        val r = i.readByte().toInt()
        ai.plannedRoleState = if (r < 0) null else enumAt(Role.values(), r)
        return true
    }

    private fun <T> enumAt(values: Array<T>, ordinal: Int): T {
        require(ordinal in values.indices)
        return values[ordinal]
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

    /** Zapis w tle. Bajty muszą być już gotowe (wyprodukowane na wątku gry). */
    fun saveAsync(bytes: ByteArray) {
        val my = synchronized(lock) { ++seq }
        io.execute {
            synchronized(lock) {
                if (my > written) { writeAtomic(bytes); written = my }
            }
        }
    }

    /** Zapis synchroniczny (przy pauzie aplikacji). */
    fun saveNow(bytes: ByteArray): Boolean = synchronized(lock) {
        val my = ++seq
        val ok = writeAtomic(bytes)
        written = my
        ok
    }

    fun delete() {
        synchronized(lock) {
            written = ++seq
            file.delete()
            tmp.delete()
        }
    }

    fun load(): ByteArray? = synchronized(lock) {
        try {
            if (!file.isFile || file.length() > (1 shl 20)) null else file.readBytes()
        } catch (_: Exception) {
            null
        }
    }

    /** Wczytuje i dekoduje zrzut; uszkodzony albo niezgodny plik jest usuwany. */
    fun loadBattle(): RestoredBattle? {
        val bytes = load() ?: return null
        val r = BattleSnapshot.decode(bytes)
        if (r == null || r.battle.over) delete()
        return r?.takeIf { !it.battle.over }
    }

    private fun writeAtomic(bytes: ByteArray): Boolean = try {
        file.parentFile?.mkdirs()
        FileOutputStream(tmp).use { it.write(bytes); it.flush(); it.fd.sync() }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        } else true
    } catch (_: Exception) {
        tmp.delete()
        false
    }
}
