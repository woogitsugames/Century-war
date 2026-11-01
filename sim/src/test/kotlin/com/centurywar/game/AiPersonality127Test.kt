package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Decyzje dowódców bez zmiany statystyk, RNG bitwy ani formatu stanu. */
class AiPersonality127Test {
    @Test fun siegeInvestsInTurretsBeforeBalancedAtEqualGold() {
        fun setup(personality: AiPersonality): Battle {
            val battle = Battle(BattleConfig(
                Mode.SKIRMISH, 12_701L, 1f, startEra = 2, maxEra = 2,
                aiPersonality = personality,
            ))
            battle.time = 26f
            battle.enemy.gold = 3000f
            battle.ai!!.update(1f)
            return battle
        }
        val siege = setup(AiPersonality.SIEGER)
        val balanced = setup(AiPersonality.BALANCED)
        assertTrue("Oblężnik powinien kupić wieżę", siege.enemy.turretsBuilt > 0)
        assertEquals("Taktyk jeszcze czeka na budowę", 0, balanced.enemy.turretsBuilt)
        assertTrue("Kupno wieży ma kosztować złoto", siege.enemy.gold < 3000f)
    }

    @Test fun economistCollectsLargerWaveAndReleasesIt() {
        val b = Battle(BattleConfig(
            Mode.SKIRMISH, 12_702L, 1f, startEra = 2, maxEra = 3,
            aiPersonality = AiPersonality.ECONOMIST,
        ))
        b.time = 110f
        repeat(3) { assertTrue(b.spawn(b.enemy, Defs.unit(2, Role.MELEE))) }
        b.ai!!.update(1f)
        assertTrue("Gospodarz powinien grupować oddziały", b.enemy.hold)
        repeat(AiPersonality.ECONOMIST.pushArmySize - 3) {
            assertTrue(b.spawn(b.enemy, Defs.unit(2, Role.MELEE)))
        }
        b.ai!!.update(1f)
        assertFalse("Gotowa fala nie może pozostawać w obronie", b.enemy.hold)
    }

    @Test fun siegeBarrageProtectsTheAdvanceWithinLegalRange() {
        val b = Battle(BattleConfig(
            Mode.SKIRMISH, 12_703L, 1f, startEra = 2, maxEra = 2,
            aiPersonality = AiPersonality.SIEGER,
        ))
        repeat(3) { assertTrue(b.spawn(b.enemy, Defs.unit(2, Role.MELEE))) }
        repeat(2) { assertTrue(b.spawn(b.player, Defs.unit(2, Role.RANGED))) }
        b.enemy.front = b.enemy.baseFront - 700f
        b.units[3].x = 1500f
        b.units[4].x = 2100f
        b.ai!!.update(1f)
        assertEquals(1, b.enemy.barragesUsed)
        var projectiles = 0
        for (p in b.projs) {
            if (!p.active) continue
            projectiles++
            val distance = (p.tx - b.enemy.baseFront) * b.enemy.dir
            assertTrue("Ostrzał poza zasięgiem", distance >= 220f && distance <= 1370f)
            assertTrue("Ostrzał powinien celować w wysunięte pozycje", p.tx < 1700f)
        }
        assertTrue(projectiles > 0)
    }

    @Test fun balancedRespondsToRangedUnitsAcrossSeeds() {
        fun counters(personality: AiPersonality): Int {
            var count = 0
            repeat(80) { seed ->
                val b = Battle(BattleConfig(
                    Mode.SKIRMISH, 70_000L + seed * 97L, 1f, startEra = 2,
                    maxEra = 2, aiPersonality = personality,
                ))
                assertTrue(b.spawn(b.enemy, Defs.unit(2, Role.MELEE)))
                repeat(3) { assertTrue(b.spawn(b.player, Defs.unit(2, Role.RANGED))) }
                b.enemy.gold = 10_000f
                b.ai!!.update(1f)
                if (b.enemy.queueSize > 0 &&
                    Defs.allUnits[b.enemy.queue[0]].role == Role.MELEE
                ) count++
            }
            return count
        }
        val balanced = counters(AiPersonality.BALANCED)
        val economist = counters(AiPersonality.ECONOMIST)
        assertTrue("Taktyk powinien częściej kontratakować strzelców: " +
            balanced + " vs " + economist, balanced > economist)
    }
}
