package com.centurywar.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nowe zasady bitwy: czas, rangi, wieże oraz darmowe jednostki. */
class NewModifiersBattleRulesTest {
    private fun nearStep(b: Battle, step: Int) {
        b.ai = null
        b.stepCount = step
        b.time = step * Battle.STEP
    }

    @Test fun shortDayEndsExactlyAtEightMinutesByBaseHpPercent() {
        for (winForPlayer in listOf(true, false)) {
            val cfg = BattleConfig(Mode.SKIRMISH, 701L, 1f,
                modifiers = setOf(Modifier.SHORT_DAY))
            val b = Battle(cfg)
            nearStep(b, Battle.SHORT_DAY_STEPS - 2)
            b.player.baseHp = b.player.baseMaxHp * (if (winForPlayer) 0.6f else 0.3f)
            b.enemy.baseHp = b.enemy.baseMaxHp * (if (winForPlayer) 0.4f else 0.7f)
            b.step()
            assertFalse(b.over)
            b.step()
            assertEquals(Battle.SHORT_DAY_STEPS, b.stepCount)
            assertTrue(b.over)
            assertEquals(if (winForPlayer) 0 else 1, b.winner)
            assertEquals(0f, b.sides[1 - b.winner].baseHp, 0f)
        }
    }

    @Test fun shortDayTieGoesToDefenderAndNormalBattleContinues() {
        val timed = Battle(BattleConfig(Mode.SKIRMISH, 702L, 1f,
            modifiers = setOf(Modifier.SHORT_DAY)))
        nearStep(timed, Battle.SHORT_DAY_STEPS - 1)
        timed.step()
        assertTrue(timed.over)
        assertEquals(1, timed.winner)

        val normal = Battle(BattleConfig(Mode.SKIRMISH, 702L, 1f))
        nearStep(normal, Battle.SHORT_DAY_STEPS - 1)
        normal.step()
        assertFalse(normal.over)
    }

    @Test fun veteransGiveBothSidesTwoRanksAndMatchingCombatBonus() {
        val normal = Battle(BattleConfig(Mode.SKIRMISH, 703L, 1f))
        val veteran = Battle(BattleConfig(Mode.SKIRMISH, 703L, 1f,
            modifiers = setOf(Modifier.VETERANS)))
        val factor = 1.06f * 1.06f
        for (index in 0..1) {
            val n = normal.sides[index]
            val v = veteran.sides[index]
            assertEquals(Battle.VETERAN_START_RANK, v.rank)
            assertEquals(n.hpMul * factor, v.hpMul, 0.00001f)
            assertEquals(n.dmgMul * factor, v.dmgMul, 0.00001f)
            assertEquals(n.turretDmgMul * factor, v.turretDmgMul, 0.00001f)
            assertTrue(normal.spawn(n, n.unit(Role.MELEE)))
            assertTrue(veteran.spawn(v, v.unit(Role.MELEE)))
        }
        val baseUnit = normal.units.first { it.active && it.side == 0 }
        val veteranUnit = veteran.units.first { it.active && it.side == 0 }
        assertEquals(baseUnit.maxHp * factor, veteranUnit.maxHp, 0.001f)
        assertEquals(baseUnit.dmg * factor, veteranUnit.dmg, 0.001f)
    }

    @Test fun expensiveTowersAffectCostAndDamageForBothSides() {
        val normal = Battle(BattleConfig(Mode.SKIRMISH, 704L, 1f))
        val heavy = Battle(BattleConfig(Mode.SKIRMISH, 704L, 1f,
            modifiers = setOf(Modifier.EXPENSIVE_TOWERS)))
        for (i in 0..1) {
            val base = normal.sides[i]
            val upgraded = heavy.sides[i]
            assertEquals((Defs.turret(upgraded.era, upgraded.eraVariant).cost * 1.6f).toInt(),
                upgraded.turretCost())
            assertEquals(base.turretDmgMul * 1.4f, upgraded.turretDmgMul, 0.00001f)
            assertTrue(upgraded.turretCost() > base.turretCost())
        }
    }

    @Test fun mercenariesAppearEveryMinuteWithoutSpendingGold() {
        val b = Battle(BattleConfig(Mode.SKIRMISH, 705L, 1f,
            modifiers = setOf(Modifier.MERCENARIES)))
        nearStep(b, Battle.MERCENARY_INTERVAL_STEPS - 1)
        val before = b.player.gold
        b.step()
        assertEquals(1, b.player.spawned)
        assertEquals(1, b.enemy.spawned)
        assertArrayEquals(b.player.spawnedByRole, b.enemy.spawnedByRole)
        assertTrue(b.player.gold >= before)
        b.step()
        assertEquals(1, b.player.spawned)
        assertEquals(1, b.enemy.spawned)
    }

    @Test fun mercenariesRespectArmyCapacity() {
        val b = Battle(BattleConfig(Mode.SKIRMISH, 706L, 1f,
            modifiers = setOf(Modifier.MERCENARIES)))
        nearStep(b, Battle.MERCENARY_INTERVAL_STEPS - 1)
        b.player.cap = 0
        b.enemy.cap = 0
        b.step()
        assertEquals(0, b.player.spawned)
        assertEquals(0, b.enemy.spawned)
    }

    @Test fun restoredBattleHasIdenticalMercenarySpawnsAndRng() {
        val cfg = BattleConfig(Mode.SKIRMISH, 707L, 1f,
            modifiers = setOf(Modifier.VETERANS, Modifier.EXPENSIVE_TOWERS, Modifier.MERCENARIES))
        val original = Battle(cfg)
        // Zrzut zawsze zawiera AI przeciwnika; usypiamy je zamiast usuwać.
        original.ai!!.thinkTimer = Float.MAX_VALUE
        original.stepCount = Battle.MERCENARY_INTERVAL_STEPS - 3
        original.time = original.stepCount * Battle.STEP
        val restored = BattleSnapshot.decode(BattleSnapshot.encode(original))
        assertNotNull(restored)
        val copy = restored!!.battle
        assertEquals(cfg.modifiers, copy.cfg.modifiers)
        repeat(12) {
            original.step()
            copy.step()
            original.fx.count = 0
            copy.fx.count = 0
        }
        assertEquals(1, original.player.spawned)
        assertEquals(1, copy.player.spawned)
        assertArrayEquals(BattleSnapshot.encode(original), BattleSnapshot.encode(copy))
    }

    @Test fun replayVerifierReproducesBattleWithNewModifiers() {
        val cfg = BattleConfig(Mode.SKIRMISH, 708L, 1f,
            modifiers = setOf(Modifier.VETERANS, Modifier.EXPENSIVE_TOWERS, Modifier.MERCENARIES),
            playerBonuses = Bonuses(baseHpMul = 100f))
        val battle = Battle(cfg)
        val controller = BattleController(battle)
        while (battle.stepCount <= Battle.MERCENARY_INTERVAL_STEPS + 2 && !battle.over) {
            battle.step()
            battle.fx.count = 0
        }
        assertFalse("Bitwa musi dotrwać do pierwszego zaciągu", battle.over)
        assertTrue(battle.player.spawned > 0)
        assertTrue(controller.surrender())
        val encoded = Replay.encode(controller.log())
        val verified = Replay.verify(cfg, encoded)
        assertEquals(battle.stepCount, verified.steps)
        assertEquals(battle.score(), verified.score)
    }
}
