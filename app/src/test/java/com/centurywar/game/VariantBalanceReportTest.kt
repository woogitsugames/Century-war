package com.centurywar.game

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Raport balansu 1 na 1: ten sam seed, dwie orientacje stron, bez badań.
 * Warianty rywalizują ze sobą w 480 grach na epokę; remis liczy 0,5 pkt.
 */
class VariantBalanceReportTest {
    private fun duel(era: Int, left: Int, right: Int, seed: Long): Int {
        val battle = Battle(BattleConfig(Mode.SKIRMISH, seed, 1f, era, era))
        battle.chooseEraVariant(left)
        battle.enemy.eraVariant = right
        battle.playerAi = Ai(battle, 0)
        val limit = 60 * 60 * 15
        var steps = 0
        while (!battle.over && steps < limit) {
            battle.step()
            battle.fx.count = 0
            steps++
        }
        return battle.winner
    }

    @Test
    fun compareDieselWithTeslaAndCyberWithBio() {
        for (era in intArrayOf(4, 6)) {
            var firstPoints = 0f
            var secondPoints = 0f
            val seeds = 240
            var draws = 0
            for (n in 0 until seeds) {
                // Zamiana stron neutralizuje przewagę pierwszego gracza.
                val winnerA = duel(era, 0, 1, 6100L + n)
                val winnerB = duel(era, 1, 0, 6100L + n)
                firstPoints += if (winnerA == 0) 1f else if (winnerA < 0) 0.5f else 0f
                firstPoints += if (winnerB == 1) 1f else if (winnerB < 0) 0.5f else 0f
                secondPoints += if (winnerA == 1) 1f else if (winnerA < 0) 0.5f else 0f
                secondPoints += if (winnerB == 0) 1f else if (winnerB < 0) 0.5f else 0f
                if (winnerA < 0) draws++
                if (winnerB < 0) draws++
            }
            val total = 2f * seeds
            val firstRate = 100f * firstPoints / total
            val secondRate = 100f * secondPoints / total
            val gap = abs(firstRate - secondRate)
            println("era=$era variants=0/1 games=${2 * seeds} draws=$draws rate=${firstRate}%/${secondRate}% gap=${gap}pp")
            assertTrue("Epoka $era: rozrzut $gap pp przekracza 5 pp", gap <= 5.01f)
        }
    }
}
