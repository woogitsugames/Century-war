package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wydarzenia nie mogą zmieniać dawnego generatora ani zużywać RNG bitwy. */
class DailyEventsTest {
    @Test fun everyFourthReleasedDayHasDeterministicIndependentEvent() {
        val found = mutableSetOf<DailyEvent>()
        for (offset in -12L..720L) {
            val day = Daily.RELEASE_DAY + offset
            val cfg = Daily.config(day, Bonuses())
            val expected = if (offset >= 0L && offset % 4L == 3L)
                DailyEvent.entries[Rng(day * 131_071L + 907L).nextInt(DailyEvent.entries.size)]
            else null
            assertEquals("Zdarzenie dnia $day", expected, cfg.dailyEvent)
            assertEquals(expected, Daily.config(day, Bonuses()).dailyEvent)
            if (expected != null) found.add(expected)
            // Oddzielny RNG nie zmienia pogody, którą losowano już wcześniej.
            if (offset >= 0L) {
                val expectedWeather = Weather.entries[
                    Rng(day * 65537L + 109L).nextInt(Weather.entries.size)]
                assertEquals(expectedWeather, cfg.weather)
            } else {
                assertEquals(Weather.CLEAR, cfg.weather)
                assertFalse(cfg.variantsEnabled)
            }
        }
        assertEquals(DailyEvent.entries.toSet(), found)
    }

    @Test fun roleDayDiscountsBothSidesAndRefundsExactPaidAmount() {
        val cfg = BattleConfig(Mode.DAILY, 50L, 1f, startEra = 1,
            dailyEvent = DailyEvent.MARKSMEN)
        val b = Battle(cfg)
        for (side in b.sides) {
            assertFalse(b.canQueue(side, Role.MELEE))
            assertFalse(b.canQueue(side, Role.HEAVY))
            assertFalse(b.canQueue(side, Role.ELITE))
            assertTrue(b.canQueue(side, Role.RANGED))
            val cost = side.unit(Role.RANGED).cost * 7 / 10
            assertEquals(cost, b.unitCost(side, Role.RANGED))
            val gold = side.gold
            assertTrue(b.queueUnit(side, Role.RANGED))
            assertEquals(gold - cost, side.gold, 0f)
            assertTrue(b.cancelLast(side))
            assertEquals(gold, side.gold, 0f)
        }
        val ai = Ai(b, 0)
        b.playerAi = ai
        repeat(70) { if (!b.over) b.step() }
        assertEquals(0, b.player.spawnedByRole[Role.MELEE.ordinal])
        assertEquals(0, b.player.spawnedByRole[Role.HEAVY.ordinal])
    }

    @Test fun abilityDayHalvesRechargeTimeForBothSides() {
        val b = Battle(BattleConfig(Mode.DAILY, 51L, 1f,
            dailyEvent = DailyEvent.RAPID_ABILITIES))
        for (side in b.sides) {
            assertEquals(0.5f, side.abilityCdMul, 0f)
            assertTrue(b.castBarrage(side))
            assertEquals(Battle.BARRAGE_CD / 2f, side.abilityCd[0], 0f)
        }
    }

    @Test fun freeBarrageFiresAtThirtySecondsForBothSidesWithoutCooldown() {
        val b = Battle(BattleConfig(Mode.DAILY, 52L, 1f,
            dailyEvent = DailyEvent.FREE_BARRAGE))
        b.ai = null
        repeat(60 * 29) { b.step() }
        assertTrue(b.projs.none { it.active })
        repeat(61) { b.step() }
        assertTrue(b.projs.any { it.active && it.side == 0 })
        assertTrue(b.projs.any { it.active && it.side == 1 })
        for (side in b.sides) {
            assertEquals(0f, side.abilityCd[0], 0f)
            assertEquals(0, side.barragesUsed)
            assertTrue(side.autoBarrageTimer > 0f)
            assertTrue(side.autoBarrageTimer <= 30f)
        }
    }

    @Test fun basesAreDoubledAndGoldenStartUsesThirdEra() {
        val normalCfg = BattleConfig(Mode.DAILY, 53L, 1f, startEra = 2)
        val normal = Battle(normalCfg)
        val fortified = Battle(BattleConfig(Mode.DAILY, 53L, 1f, startEra = 2,
            dailyEvent = DailyEvent.FORTIFIED_BASES))
        for (i in 0..1) {
            assertEquals(normal.sides[i].baseMaxHp * 2f,
                fortified.sides[i].baseMaxHp, 0f)
        }

        val specialDay = (Daily.RELEASE_DAY + 3L..Daily.RELEASE_DAY + 500L step 4L)
            .first { Daily.config(it, Bonuses()).dailyEvent == DailyEvent.GOLD_RUSH }
        val cfg = Daily.config(specialDay, Bonuses())
        assertEquals(2, cfg.startEra)
        assertTrue(cfg.maxEra >= 2)
        val b = Battle(BattleConfig(Mode.DAILY, 54L, 1f, startEra = 2,
            dailyEvent = DailyEvent.GOLD_RUSH))
        val regular = Battle(BattleConfig(Mode.DAILY, 54L, 1f, startEra = 2))
        for (i in 0..1) {
            assertEquals(regular.sides[i].gold + 1000f * Defs.costScale(2),
                b.sides[i].gold, 0.01f)
        }
    }

    @Test fun mirrorDayUsesCommonPersonalityAndEqualLimits() {
        val day = (Daily.RELEASE_DAY + 3L..Daily.RELEASE_DAY + 500L step 4L)
            .first { Daily.config(it, Bonuses()).dailyEvent == DailyEvent.MIRROR_ARMY }
        val cfg = Daily.config(day, Bonuses())
        assertNotNull(cfg.aiPersonality)
        assertEquals(1f, cfg.aiPower, 0f)
        val b = Battle(cfg)
        assertEquals(b.player.cap, b.enemy.cap)
        assertEquals(b.player.slots, b.enemy.slots)
        assertEquals(cfg.aiPersonality, b.ai!!.personality)
        assertEquals(cfg.aiPersonality, Ai(b, 0).personality)
        assertNull(Daily.config(Daily.RELEASE_DAY - 1L, Bonuses()).dailyEvent)
    }
}
