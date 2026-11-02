package com.centurywar.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresje A156-01/02: zrzut bitwy musi przyjmować każdy stan, który gra potrafi wytworzyć
 * zwykłymi rozkazami (kontroler) i zwykłą symulacją, a wznowiona bitwa ma dalej przebiegać
 * bit w bit tak samo jak ciągła.
 */
class Snapshot156Test {

    private fun step(b: Battle) { b.step(); b.fx.count = 0 }

    /** Encode → decode; zrzut odtworzonej bitwy musi być identyczny z oryginałem. */
    private fun roundTrip(b: Battle): Battle {
        val bytes = BattleSnapshot.encode(b)
        val restored = BattleSnapshot.decode(bytes)
        assertNotNull("zrzut z kroku ${b.stepCount} powinien się wczytać", restored)
        assertArrayEquals(bytes, BattleSnapshot.encode(restored!!.battle))
        return restored.battle
    }

    /** Dalszy przebieg z tymi samymi rozkazami; porównanie stanu, logu i wyniku. */
    private fun continueBoth(a: Battle, b: Battle, steps: Int, script: (Battle) -> Unit = {}) {
        repeat(steps) {
            if (!a.over) { script(a); step(a) }
            if (!b.over) { script(b); step(b) }
        }
        val ca = a.commandController; val cb = b.commandController
        if (ca != null && cb != null) {
            if (!a.over) assertTrue(ca.surrender())
            if (!b.over) assertTrue(cb.surrender())
            assertEquals(ca.log(), cb.log())
        }
        assertEquals(a.score(), b.score())
        assertEquals(a.winner, b.winner)
        assertArrayEquals(BattleSnapshot.encode(a), BattleSnapshot.encode(b))
    }

    private fun turretCfg() = BattleConfig(Mode.SKIRMISH, 42L, 1f,
        playerBonuses = Bonuses(startGold = 200f, turretSlots = 4))

    /** Kupno i ulepszenie stopnia wieży w danym slocie wyłącznie przez kontroler. */
    private fun buyAndTier(b: Battle, c: BattleController, slot: Int) {
        var n = 0
        while (!c.buyTurret(slot)) { assertTrue(n++ < 20_000 && !b.over); step(b) }
        while (!c.upgradeTurretTier(slot)) { assertTrue(n++ < 20_000 && !b.over); step(b) }
        assertEquals(2, b.player.turretTier[slot])
    }

    @Test
    fun a156_01_turretTierOnEverySlotResumesLikeContinuousBattle() {
        for (slot in 0 until Defs.MAX_TURRET_SLOTS) {
            val a = Battle(turretCfg())
            val c = BattleController(a)
            buyAndTier(a, c, slot)
            assertTrue(c.log().commands.any { it.kind == Replay.Kind.TURRET_TIER && it.argument == slot })
            val b = roundTrip(a)
            assertEquals(c.log(), b.commandController!!.log())
            // Dalsze ulepszenie tego samego slotu po wznowieniu też trafia do logu.
            continueBoth(a, b, 1_500) { battle -> battle.commandController!!.upgradeTurretTier(slot) }
            assertEquals(3, a.player.turretTier[slot])
        }
    }

    @Test
    fun a156_01_replaySeekBackwardsWithTurretTierOnSlots1to3() {
        val cfg = BattleConfig(Mode.SKIRMISH, 238L, aiPower = 0.12f,
            playerBonuses = Bonuses(baseHpMul = 30f, startGold = 500f, turretSlots = 4))
        for (slot in 1 until Defs.MAX_TURRET_SLOTS) {
            val battle = Battle(cfg)
            val c = BattleController(battle)
            buyAndTier(battle, c, slot)
            assertTrue("Ulepszenie musi trafić do pierwszego punktu kontrolnego",
                battle.stepCount < ReplayPlayer.KEYFRAME_STEPS)
            while (!battle.over && battle.stepCount < 3_000) step(battle)
            if (!battle.over) assertTrue(c.surrender())
            val log = c.log()
            assertTrue(log.steps > 2_100)
            assertEquals(battle.score(), Replay.verify(cfg, log).score)

            val jumping = ReplayPlayer(cfg, log)
            assertTrue(jumping.seek(2_100))
            assertTrue("Cofnięcie z 2100 do 1900 (slot $slot)", jumping.seek(1_900))
            val linear = ReplayPlayer(cfg, log)
            assertEquals(1_900, linear.advance(1_900))
            assertArrayEquals(BattleSnapshot.encode(linear.battle), BattleSnapshot.encode(jumping.battle))
            assertTrue(jumping.seek(log.steps))
            assertEquals(Replay.verify(cfg, log), jumping.result())
        }
    }

    /** Ta sama funkcja zakresu argumentów dla powtórki i dziennika w zrzucie. */
    @Test
    fun replayAndSnapshotShareCommandArgumentRules() {
        for (kind in Replay.Kind.entries) for (arg in -2..Defs.LANE.toInt() + 2) {
            val log = Replay.Log(10, listOf(Replay.Command(0, kind, arg)))
            val decoded = runCatching { Replay.decode(Replay.encode(log)) }.isSuccess
            assertEquals("$kind/$arg", Replay.argumentValid(kind, arg), decoded)
        }
        for (slot in 0 until Defs.MAX_TURRET_SLOTS) for (kind in listOf(Replay.Kind.BUY_TURRET,
            Replay.Kind.UPGRADE_TURRET, Replay.Kind.SELL_TURRET, Replay.Kind.TURRET_TIER)) {
            assertTrue("$kind/$slot", Replay.argumentValid(kind, slot))
        }
        assertFalse(Replay.argumentValid(Replay.Kind.TURRET_TIER, Defs.MAX_TURRET_SLOTS))
    }

    @Test
    fun a156_02_waveSpeedMultipliersComeFromTheThemes() {
        for (theme in EndlessWaveTheme.entries) {
            assertTrue(EndlessWaveTheme.isLegalUnitSpeedMul(theme.unitSpeedMul))
        }
        assertEquals(0.85f, EndlessWaveTheme.SIEGE_TRAIN.unitSpeedMul)
        assertEquals(1.15f, EndlessWaveTheme.TWIN_RAID.unitSpeedMul)
        assertFalse(EndlessWaveTheme.isLegalUnitSpeedMul(1.3f))
    }

    @Test
    fun a156_02_twinRaidReproductionFromAudit() {
        val a = Battle(BattleConfig(Mode.ENDLESS, 2L, 1f))
        var n = 0
        while (a.units.none { it.alive && it.side == 1 && it.waveSpeedMul == 1.15f }) {
            assertTrue(n++ < 5_000 && !a.over); step(a)
        }
        val b = roundTrip(a)
        continueBoth(a, b, 600)
    }

    /**
     * Każdy temat fali: bitwa Endless z AI gracza (przez kontroler) do faktycznego
     * pojawienia się jednostki z kolejki tego tematu, roundtrip i porównanie dalszego przebiegu.
     */
    @Test
    fun a156_02_everyEndlessWaveThemeResumesAfterItsUnitsSpawn() {
        val missing = EndlessWaveTheme.entries.toMutableSet()
        var seed = 1L
        while (missing.isNotEmpty() && seed < 60L) {
            val cfg = BattleConfig(Mode.ENDLESS, seed++, 1f)
            var a = Battle(cfg)
            BattleController(a)
            a.playerAi = Ai(a, 0)
            var n = 0
            while (!a.over && n++ < 30_000 && missing.isNotEmpty()) {
                val e = a.enemy
                val head = if (e.queueSize > 0) EndlessWaveTheme.entries[e.queueWaveTheme[0]] else null
                val spawned = e.spawned
                step(a)
                if (head == null || e.spawned == spawned || head !in missing) continue
                val unit = a.units.filter { it.alive && it.side == 1 && !it.isBoss && !it.isCommander }
                    .maxByOrNull { it.serial } ?: continue
                assertEquals(head.unitSpeedMul, unit.waveSpeedMul)
                missing.remove(head)
                val b = roundTrip(a)
                assertEquals(a.commandController!!.log(), b.commandController!!.log())
                // Kopia ciągłej bitwy przez zrzut — żeby szukać kolejnych tematów dalej.
                val continuous = roundTrip(a)
                continueBoth(a, b, 900)
                a = continuous
            }
        }
        assertTrue("Nie wystąpiły tematy: $missing", missing.isEmpty())
    }

    /**
     * Mechaniki 125/140: dowódca (obu stron), stopnie wież, warianty epok i karty.
     * AI gracza wydaje rozkazy przez kontroler; zrzut co 20 s gry, każdy musi się wczytać
     * i dać ten sam dalszy przebieg.
     */
    @Test
    fun mechanics125And140SurviveSnapshotsTakenThroughoutTheBattle() {
        val kinds = HashSet<Replay.Kind>()
        var commanderSeen = false
        var enemyCommanderSeen = false
        var variantTurretSeen = false
        var tierSeen = false
        val configs = listOf(
            BattleConfig(Mode.SKIRMISH, 140L, 0.7f, playerBonuses = Bonuses(turretSlots = 4, baseHpMul = 3f)),
            BattleConfig(Mode.SKIRMISH, 125L, 0.8f, aiPersonality = AiPersonality.ENGINEER,
                playerBonuses = Bonuses(turretSlots = 4, startGold = 400f)),
            BattleConfig(Mode.ENDLESS, 7L, 1f, playerBonuses = Bonuses(turretSlots = 4)),
            // Epoka tuż przed pierwszą z wariantami: awans gracza daje wybór wariantu.
            BattleConfig(Mode.SKIRMISH, 141L, 0.7f,
                startEra = (0 until Defs.ERA_COUNT).first { Defs.spec(it).variants.isNotEmpty() } - 1,
                playerBonuses = Bonuses(turretSlots = 4, xpMul = 3f, baseHpMul = 3f, startGold = 600f)),
        )
        for (cfg in configs) {
            val a = Battle(cfg)
            val c = BattleController(a)
            a.playerAi = Ai(a, 0)
            var n = 0
            while (!a.over && n++ < 24_000) {
                step(a)
                // Gracz sam przywołuje dowódcę, gdy tylko może (AI nie musi tego robić).
                if (a.canSummonCommander(a.player)) c.summonCommander()
                // Wieża w wariancie epoki: dokupienie albo awans istniejącej po wyborze wariantu.
                if (a.player.eraVariant >= 0) {
                    val free = a.freeSlot(a.player)
                    if (free >= 0) c.buyTurret(free)
                    for (k in 0 until a.player.slots) c.upgradeTurret(k)
                }
                if (a.units.any { it.alive && it.isCommander && it.side == 0 }) commanderSeen = true
                if (a.units.any { it.alive && it.isCommander && it.side == 1 }) enemyCommanderSeen = true
                for (s in a.sides) for (k in 0 until s.slots) {
                    if (s.turretTier[k] > 1) tierSeen = true
                    if (s.turretVariant[k] >= 0) variantTurretSeen = true
                }
                if (a.stepCount % 1_200 == 0) {
                    val b = roundTrip(a)
                    assertEquals(c.log(), b.commandController!!.log())
                    // Porównanie krótkiego dalszego przebiegu na kopii, oryginał gra dalej.
                    val copy = roundTrip(a)
                    continueBoth(copy, b, 300)
                }
            }
            c.log().commands.mapTo(kinds) { it.kind }
        }
        val seen = "dowódca=$commanderSeen wróg=$enemyCommanderSeen stopień=$tierSeen wariant=$variantTurretSeen"
        assertTrue(seen, commanderSeen && enemyCommanderSeen && tierSeen && variantTurretSeen)
        assertTrue("Log: $kinds", Replay.Kind.SUMMON_COMMANDER in kinds && Replay.Kind.VARIANT in kinds)
    }
}
