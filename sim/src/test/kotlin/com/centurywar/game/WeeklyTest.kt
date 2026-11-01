package com.centurywar.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Zamrożone reguły tygodni muszą dawać ten sam wynik na każdym urządzeniu. */
class WeeklyTest {

    @Test fun weekChangesAtMondayMidnightUtc() {
        val monday = LocalDate.of(2026, 11, 2).toEpochDay()
        val previous = Weekly.weekNumber(monday - 1L)
        val current = Weekly.weekNumber(monday)
        assertEquals(previous + 1L, current)
        assertEquals(current, Weekly.weekNumber(monday + 6L))
        assertEquals(current + 1L, Weekly.weekNumber(monday + 7L))
        assertEquals(0L, Weekly.weekNumber(3L))
        assertEquals(1L, Weekly.weekNumber(4L))
    }

    @Test fun frozenHistoricalDraws() {
        fun assertWeek(week: Long, seed: Long, start: Int, max: Int,
                       first: Modifier, second: Modifier, card: EndlessCard) {
            val config = Weekly.config(week)
            assertEquals(Mode.ENDLESS, config.mode)
            assertEquals(week, config.weekly)
            assertEquals(seed, config.seed)
            assertEquals(start, config.startEra)
            assertEquals(max, config.maxEra)
            assertEquals(linkedSetOf(first, second), config.modifiers)
            assertEquals(card, config.weeklyCard)
            val again = Weekly.config(week)
            assertEquals(config.seed, again.seed)
            assertEquals(config.startEra, again.startEra)
            assertEquals(config.maxEra, again.maxEra)
            assertEquals(config.modifiers, again.modifiers)
            assertEquals(config.weeklyCard, again.weeklyCard)
        }
        // Wartości stanowią kontrakt także po dodaniu kolejnych modyfikatorów i kart.
        assertWeek(0L, 211L, 1, 4, Modifier.FRAGILE_BASES, Modifier.FAST_UNITS, EndlessCard.ECONOMY)
        assertWeek(1000L, 104729211L, 1, 4, Modifier.RANGED_HORDE, Modifier.FRAGILE_BASES, EndlessCard.SHARPSHOOTERS)
        assertWeek(2000L, 209458211L, 1, 4, Modifier.ARMORED_FOE, Modifier.RANGED_HORDE, EndlessCard.MARCH)
    }

    @Test fun noResearchAndStartingCardApplied() {
        val config = Weekly.config(0L)
        val b = config.playerBonuses
        assertEquals(0f, b.startGold)
        assertEquals(1f, b.unitHpMul)
        assertEquals(1f, b.unitDmgMul)
        assertEquals(1f, b.incomeMul)
        assertEquals(14, b.popCap)
        assertEquals(3, b.turretSlots)
        assertTrue(b.eliteUnlocked)
        assertTrue(b.rallyUnlocked)
        val battle = Battle(config)
        assertEquals(1, battle.player.cardStacks[EndlessCard.ECONOMY.ordinal])
        assertTrue(battle.player.doctrines.contains(Doctrine.ECONOMY))
        assertEquals(1.25f, battle.player.incomeMul / 1.0f, 0.001f)
        assertEquals(0, battle.wave)
        assertEquals(config.startEra, battle.player.era)
    }

    @Test fun deterministicSimulationAndReplay() {
        val cfg = Weekly.config(1000L)
        val a = Battle(cfg)
        val b = Battle(cfg)
        val ca = BattleController(a)
        val cb = BattleController(b)
        repeat(1500) { i ->
            if (!a.over && !b.over) {
                if (a.pendingCards != null) assertEquals(ca.card(0), cb.card(0))
                if (i == 30) assertEquals(ca.queue(Role.MELEE), cb.queue(Role.MELEE))
                a.step()
                b.step()
                a.fx.count = 0
                b.fx.count = 0
            }
        }
        assertArrayEquals(BattleSnapshot.encode(a), BattleSnapshot.encode(b))
        if (!a.over) {
            assertTrue(ca.surrender())
            assertTrue(cb.surrender())
        }
        assertArrayEquals(BattleSnapshot.encode(a), BattleSnapshot.encode(b))
        val log = ca.log()
        val encoded = Replay.encode(log)
        assertEquals(log, Replay.decode(encoded))
        val result = Replay.verify(cfg, encoded)
        assertEquals(a.score(), result.score)
        assertEquals(a.wave, result.wave)
        assertEquals(a.stepCount, result.steps)
    }
    @Test fun releaseWeekAddsOnlyModifiersValidForEndless() {
        val releaseWeek = Weekly.weekNumber(Daily.RELEASE_DAY)
        val newcomers = setOf(Modifier.VETERANS, Modifier.EXPENSIVE_TOWERS, Modifier.MERCENARIES)
        for (week in 0L until releaseWeek) {
            val cfg = Weekly.config(week)
            assertTrue("Tydzień historyczny $week", cfg.modifiers.none { it in newcomers })
        }
        val observed = mutableSetOf<Modifier>()
        for (week in releaseWeek until releaseWeek + 500L) {
            val cfg = Weekly.config(week)
            observed.addAll(cfg.modifiers.intersect(newcomers))
            assertFalse("Krótki dzień nie pasuje do Endless", Modifier.SHORT_DAY in cfg.modifiers)
            assertEquals(cfg.modifiers, Weekly.config(week).modifiers)
            assertEquals(cfg.weeklyCard, Weekly.config(week).weeklyCard)
        }
        assertEquals(newcomers, observed)
        assertEquals(releaseWeek, Weekly.weekNumber(Daily.RELEASE_DAY))
    }

}
