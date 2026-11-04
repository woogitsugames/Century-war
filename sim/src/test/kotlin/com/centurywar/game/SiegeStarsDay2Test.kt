package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dzień 2: gwiazdki z HP bazy gracza bez obrażeń z presji oblężniczej; przebieg bitwy bez zmian. */
class SiegeStarsDay2Test {
    private fun campaign(index: Int, seed: Long) =
        Battle(Campaign.config(Campaign.stages[index], Bonuses(), seed)).also { it.playerAi = Ai(it, 0) }

    @Test
    fun siegeDamageIsIgnoredAndCappedAtFullHp() {
        val b = campaign(3, 5L)
        b.over = true
        b.winner = 0
        val max = b.player.baseMaxHp
        b.player.baseHp = max * 0.3f
        assertEquals(1, b.stars())
        // Wraca 60% obrażeń z przyspieszonej presji.
        b.player.siegeDamageTaken = max * 0.15f
        assertEquals(1, b.stars())
        b.player.siegeDamageTaken = max * 0.2f
        assertEquals(2, b.stars())
        b.player.siegeDamageTaken = max * 0.7f
        assertEquals(2, b.stars())
        b.player.siegeDamageTaken = max * 0.8f
        assertEquals(3, b.stars())
        // Naprawy bazy nie wypychają wyniku ponad pełne HP.
        b.player.siegeDamageTaken = max * 5f
        assertEquals(3, b.stars())
        b.winner = 1
        assertEquals(0, b.stars())
    }

    @Test
    fun noPressureMeansNoSiegeDamage() {
        // Przed startem presji (4 min w etapach 2–7) licznik stoi w miejscu.
        val b = campaign(2, 9L)
        repeat(((b.siegeStart - 1f) / Battle.STEP).toInt()) { if (!b.over) b.step() }
        assertEquals(0f, b.player.siegeDamageTaken, 0f)
        assertEquals(0f, b.enemy.siegeDamageTaken, 0f)
    }

    @Test
    fun standardPressureStagesKeepOldStars() {
        // Etap 1 i etapy od 19. mają standardową presję: licznik zostaje 0, gwiazdki jak dawniej.
        for (index in intArrayOf(0, 18, 20)) {
            val b = campaign(index, 77L + index)
            var steps = 0
            while (!b.over && steps < (30 * 60 / Battle.STEP).toInt()) { b.step(); b.fx.count = 0; steps++ }
            assertTrue("etap ${index + 1}: bitwa po starcie presji", b.time > b.siegeStart)
            assertEquals("etap ${index + 1}", 0f, b.player.siegeDamageTaken, 0f)
            assertEquals("etap ${index + 1}", 0f, b.enemy.siegeDamageTaken, 0f)
        }
    }

    @Test
    fun starsNeverLowerThanPlainHp() {
        var wins = 0
        for (seed in 1L..6L) {
            val b = campaign(4, seed * 31L)
            var steps = 0
            while (!b.over && steps < (30 * 60 / Battle.STEP).toInt()) { b.step(); b.fx.count = 0; steps++ }
            assertTrue(b.player.siegeDamageTaken >= 0f && b.player.siegeDamageTaken.isFinite())
            if (b.winner != 0) continue
            wins++
            val plain = (b.player.baseHp / b.player.baseMaxHp).let { r -> if (r >= 0.75f) 3 else if (r >= 0.4f) 2 else 1 }
            assertTrue("seed $seed: ${b.stars()} < $plain", b.stars() >= plain)
            assertTrue("bitwa etapu 5 kończy się po starcie presji", b.player.siegeDamageTaken > 0f)
        }
        assertTrue("AI gracza powinno wygrać część bitew", wins > 0)
    }
}
