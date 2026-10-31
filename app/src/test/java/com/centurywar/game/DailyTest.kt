package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Granica wydania nie może zmienić historycznych wyzwań ani losowania nowej zawartości. */
class DailyTest {
    @Test fun daysBeforeReleaseKeepFrozenRules() {
        val days = longArrayOf(Daily.RELEASE_DAY - 400L, Daily.RELEASE_DAY - 30L, Daily.RELEASE_DAY - 1L)
        for (day in days) {
            val rng = Rng(day * 7919L + 17L)
            val modifiers = HashSet<Modifier>()
            val pool = Modifier.values()
            val count = 2 + rng.nextInt(2)
            while (modifiers.size < count) modifiers.add(pool[rng.nextInt(pool.size)])
            val rolledStart = rng.nextInt(3)
            val expectedStart = if (rolledStart >= 2) rolledStart + 1 else rolledStart
            val rolledMax = minOf(7, rolledStart + 3)
            val expectedMax = if (rolledMax >= 2) rolledMax + 1 else rolledMax
            val expectedPower = 1.1f + rng.nextFloat() * 0.25f

            val result = Daily.config(day, Bonuses())
            assertEquals(day, result.seed)
            assertEquals(modifiers, result.modifiers)
            assertEquals(expectedStart, result.startEra)
            assertEquals(expectedMax, result.maxEra)
            assertEquals(expectedPower, result.aiPower, 0f)
            assertEquals(Weather.CLEAR, result.weather)
            assertFalse(result.variantsEnabled)
        }
    }

    @Test fun releaseDayEnablesAllThreeFeaturesTogether() {
        var sampledIron = false
        var sampledBadWeather = false
        for (day in Daily.RELEASE_DAY..(Daily.RELEASE_DAY + 100L)) {
            val result = Daily.config(day, Bonuses())
            assertEquals(Mode.DAILY, result.mode)
            assertTrue("Dzień $day: warianty", result.variantsEnabled)
            assertTrue(result.startEra in 0..5)
            assertTrue(result.maxEra in result.startEra until Defs.ERA_COUNT)
            assertEquals(
                Weather.entries[Rng(day * 65537L + 109L).nextInt(Weather.entries.size)],
                result.weather,
            )
            sampledIron = sampledIron || result.startEra == 2
            sampledBadWeather = sampledBadWeather || result.weather != Weather.CLEAR
            val again = Daily.config(day, Bonuses())
            assertEquals(result.modifiers, again.modifiers)
            assertEquals(result.weather, again.weather)
            assertEquals(result.startEra, again.startEra)
            assertEquals(result.maxEra, again.maxEra)
            assertEquals(result.aiPower, again.aiPower, 0f)
        }
        assertTrue("Epoka żelaza ma pojawić się w losowaniu", sampledIron)
        assertTrue("Pogoda ma być różnorodna", sampledBadWeather)
    }

    @Test fun releaseBoundaryDoesNotSkipOrDuplicateDay() {
        val previous = Daily.config(Daily.RELEASE_DAY - 1L, Bonuses())
        val first = Daily.config(Daily.RELEASE_DAY, Bonuses())
        assertFalse(previous.variantsEnabled)
        assertEquals(Weather.CLEAR, previous.weather)
        assertTrue(first.variantsEnabled)
        assertEquals(Daily.RELEASE_DAY - 1L, previous.seed)
        assertEquals(Daily.RELEASE_DAY, first.seed)
    }
}
