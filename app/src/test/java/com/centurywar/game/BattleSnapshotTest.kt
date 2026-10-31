package com.centurywar.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Zrzut bitwy musi być bit w bit deterministyczny: bitwa odtworzona ze zrzutu i oryginał
 * po kolejnych M krokach muszą mieć identyczny stan.
 */
class BattleSnapshotTest {

    @get:Rule val tmp = TemporaryFolder()

    /** Prosty, deterministyczny "gracz" bez AI (żeby wybór doktryny czekał na gracza). */
    private fun scripted(b: Battle) {
        val p = b.player
        if (b.pendingChoice != null) return
        if (b.canEvolve(p)) b.evolve(p)
        val role = Role.values()[(b.serialCounter + p.queueSize) % 3]
        b.queueUnit(p, role)
        if (b.abilityReady(p, 0) && p.aliveCount < 3) b.castBarrage(p)
        val slot = b.freeSlot(p)
        if (slot >= 0 && p.gold > p.turretCost() * 2) b.buyTurret(p, slot)
    }

    private fun steps(b: Battle, n: Int, script: Boolean = false) {
        var i = 0
        while (i < n && !b.over) {
            if (script) scripted(b)
            if (b.pendingChoice != null) b.chooseDoctrine((b.wave + b.player.era) % 3)
            b.step(); b.fx.count = 0; i++
        }
    }

    private fun roundTrip(b: Battle, ranked: Boolean = false): Battle {
        val bytes = BattleSnapshot.encode(b, ranked, 123.5f)
        val r = BattleSnapshot.decode(bytes)
        assertNotNull("zrzut powinien się wczytać", r)
        assertEquals(ranked, r!!.rankedDaily)
        assertEquals(123.5f, r.camX)
        // zrzut odtworzonej bitwy jest identyczny z oryginałem
        assertArrayEquals(bytes, BattleSnapshot.encode(r.battle, ranked, 123.5f))
        return r.battle
    }

    private fun assertSame(a: Battle, b: Battle) {
        assertEquals(a.time, b.time)
        assertEquals(a.over, b.over)
        assertEquals(a.winner, b.winner)
        assertEquals(a.wave, b.wave)
        assertEquals(a.serialCounter, b.serialCounter)
        for (k in 0..1) {
            val x = a.sides[k]; val y = b.sides[k]
            assertEquals(x.baseHp, y.baseHp)
            assertEquals(x.gold, y.gold)
            assertEquals(x.xp, y.xp)
            assertEquals(x.era, y.era)
            assertEquals(x.kills, y.kills)
            assertEquals(x.spawned, y.spawned)
            assertEquals(x.doctrines, y.doctrines)
        }
        for (k in a.units.indices) {
            val u = a.units[k]; val v = b.units[k]
            assertEquals(u.active, v.active)
            if (!u.active) continue
            assertEquals(u.x, v.x); assertEquals(u.hp, v.hp); assertEquals(u.serial, v.serial); assertEquals(u.def.id, v.def.id)
        }
        for (k in a.projs.indices) {
            assertEquals(a.projs[k].active, b.projs[k].active)
            if (a.projs[k].active) assertEquals(a.projs[k].x, b.projs[k].x)
        }
        assertArrayEquals(BattleSnapshot.encode(a), BattleSnapshot.encode(b))
    }

    /** Uruchamia N kroków, robi zrzut, odtwarza i porównuje obie bitwy po M kolejnych krokach. */
    private fun check(cfg: BattleConfig, n: Int, m: Int, playerAi: Boolean = true, ranked: Boolean = false): Pair<Battle, Battle> {
        val a = Battle(cfg)
        if (playerAi) a.playerAi = Ai(a, 0)
        steps(a, n, !playerAi)
        val b = roundTrip(a, ranked)
        assertEquals(playerAi, b.playerAi != null)
        steps(a, m, !playerAi)
        steps(b, m, !playerAi)
        assertSame(a, b)
        return a to b
    }

    @Test
    fun skirmishResumesExactly() {
        val (a, _) = check(BattleConfig(Mode.SKIRMISH, 42L, 1.05f), 60 * 150, 60 * 240)
        assertTrue(a.player.spawned > 0 && a.enemy.spawned > 0)
    }

    @Test
    fun skirmishUntilTheEnd() {
        val (a, b) = check(BattleConfig(Mode.SKIRMISH, 99L, 1.1f), 60 * 300, 60 * 60 * 40)
        assertTrue(a.over && b.over)
        assertEquals(a.winner, b.winner)
        assertEquals(a.score(), b.score())
    }

    @Test
    fun endlessResumesExactly() {
        val (a, _) = check(BattleConfig(Mode.ENDLESS, 7L, 1f), 60 * 200, 60 * 300)
        assertTrue(a.wave >= 5)
    }

    @Test
    fun endlessWithPlayerChoices() {
        // bez AI gracza: co 5 fal gra czeka na wybór doktryny
        check(BattleConfig(Mode.ENDLESS, 11L, 1f), 60 * 170, 60 * 200, playerAi = false)
    }

    @Test
    fun dailyResumesExactlyAndKeepsRankedFlag() {
        val cfg = Daily.config(20000L, Bonuses())
        val a = Battle(cfg); a.playerAi = Ai(a, 0)
        steps(a, 60 * 120)
        val r = BattleSnapshot.decode(BattleSnapshot.encode(a, true))!!
        assertTrue(r.rankedDaily)
        assertEquals(Mode.DAILY, r.battle.cfg.mode)
        assertEquals(cfg.modifiers, r.battle.cfg.modifiers)
        assertEquals(cfg.seed, r.battle.cfg.seed)
        check(cfg, 60 * 90, 60 * 200, ranked = true)
    }

    @Test
    fun pendingDoctrineChoiceSurvives() {
        val a = Battle(BattleConfig(Mode.SKIRMISH, 5L, 0.8f, startEra = 0, maxEra = 3))
        var guard = 0
        while (a.pendingChoice == null && !a.over && guard < 60 * 60 * 20) { scripted(a); a.step(); a.fx.count = 0; guard++ }
        val choice = a.pendingChoice
        assertNotNull("gracz powinien dostać wybór doktryny", choice)
        val b = roundTrip(a)
        assertArrayEquals(choice, b.pendingChoice)
        // w trakcie wyboru symulacja stoi
        val t = b.time; b.step(); assertEquals(t, b.time)
        a.chooseDoctrine(2); b.chooseDoctrine(2)
        assertEquals(a.player.doctrines, b.player.doctrines)
        steps(a, 60 * 180, true); steps(b, 60 * 180, true)
        assertSame(a, b)
    }

    @Test
    fun modifiersAndBonusesSurvive() {
        val mods = setOf(Modifier.FOG_OF_WAR, Modifier.RANGED_HORDE, Modifier.GLASS_CANNON, Modifier.RICH_START,
            Modifier.FAST_UNITS, Modifier.ARMORED_FOE, Modifier.SLOW_XP)
        val bonuses = Bonuses(startGold = 80f, incomeMul = 1.3f, unitHpMul = 1.2f, unitDmgMul = 1.15f, baseHpMul = 1.4f,
            turretDmgMul = 1.3f, trainMul = 1.2f, xpMul = 1.25f, popCap = 15, abilityCdMul = 0.8f, turretSlots = 4,
            eliteUnlocked = false, rallyUnlocked = true)
        val cfg = BattleConfig(Mode.CAMPAIGN, 777L, 1.3f, 2, 5, mods, bonuses, 17)
        val (a, b) = check(cfg, 60 * 100, 60 * 300)
        val c = b.cfg
        assertEquals(mods, c.modifiers)
        assertEquals(17, c.stageIndex)
        assertEquals(2, c.startEra); assertEquals(5, c.maxEra)
        assertEquals(1.3f, c.aiPower)
        assertFalse(c.playerBonuses.eliteUnlocked)
        assertEquals(15, c.playerBonuses.popCap)
        assertEquals(a.fog, b.fog)
        assertTrue(b.fog && b.rangedHorde)
        // drugi zestaw: bez wież, kruche bazy, podwójny przychód
        check(BattleConfig(Mode.CAMPAIGN, 31L, 0.9f, 0, 2,
            setOf(Modifier.NO_TURRETS, Modifier.FRAGILE_BASES, Modifier.DOUBLE_INCOME), Bonuses(), 9), 60 * 80, 60 * 400)
    }

    @Test
    fun campaignStagesResume() {
        for (stage in Campaign.stages.filter { it.index % 5 == 0 }) {
            check(Campaign.config(stage, Bonuses(), 1000L + stage.index), 60 * 75, 60 * 120)
        }
    }

    @Test
    fun rejectsWrongVersionAndCorruption() {
        val a = Battle(BattleConfig(Mode.SKIRMISH, 3L, 1f)); a.playerAi = Ai(a, 0)
        steps(a, 60 * 60)
        val bytes = BattleSnapshot.encode(a)
        assertNotNull(BattleSnapshot.decode(bytes))
        val wrongVersion = bytes.copyOf().also { it[7] = (BattleSnapshot.VERSION + 1).toByte() }
        assertNull(BattleSnapshot.decode(wrongVersion))
        val corrupt = bytes.copyOf().also { it[bytes.size / 2] = (it[bytes.size / 2] + 1).toByte() }
        assertNull(BattleSnapshot.decode(corrupt))
        assertNull(BattleSnapshot.decode(bytes.copyOf(bytes.size - 3)))
        assertNull(BattleSnapshot.decode(ByteArray(0)))
        assertNull(BattleSnapshot.decode(ByteArray(64) { 7 }))
    }

    @Test
    fun snapshotIsCompact() {
        val a = Battle(BattleConfig(Mode.SKIRMISH, 8L, 1f)); a.playerAi = Ai(a, 0)
        steps(a, 60 * 240)
        val size = BattleSnapshot.encode(a).size
        println("snapshot size: $size B, units=${a.units.count { it.active }} projs=${a.projs.count { it.active }}")
        assertTrue(size < 16 * 1024)
    }

    @Test
    fun storeWritesAtomicallyAndOrdersWrites() {
        val f = tmp.newFolder().resolve("battle.snap")
        val store = SnapshotStore(f)
        assertNull(store.loadBattle())
        val a = Battle(BattleConfig(Mode.SKIRMISH, 4L, 1f)); a.playerAi = Ai(a, 0)
        steps(a, 60 * 30)
        val first = BattleSnapshot.encode(a)
        steps(a, 60 * 30)
        val second = BattleSnapshot.encode(a, true)
        assertTrue(store.saveNow(second))
        assertArrayEquals(second, store.load())
        assertFalse(f.resolveSibling("battle.snap.tmp").exists())
        // starszy zapis z kolejki w tle nie może nadpisać nowszego... (kolejność: async potem sync)
        store.saveAsync(first)
        store.saveNow(second)
        Thread.sleep(200)
        assertArrayEquals(second, store.load())
        val r = store.loadBattle()!!
        assertTrue(r.rankedDaily)
        assertEquals(a.time, r.battle.time)
        // ...ani wskrzesić usuniętego
        store.saveAsync(first)
        store.delete()
        Thread.sleep(200)
        assertFalse(f.exists())
        // uszkodzony plik jest odrzucany i usuwany
        f.writeBytes(byteArrayOf(1, 2, 3))
        assertNull(store.loadBattle())
        assertFalse(f.exists())
    }
}
