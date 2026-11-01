package com.centurywar.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dwadzieścia seedów na doktrynę, na każdym z zamianą stron.
 * Obie armie mają te same badania, epokę, złoto i osobowość AI.
 * Zapis liczbowy pojawia się w system-out raportu JUnit.
 */
class Doctrine69BattleSimTest {
    private val tested = arrayOf(Doctrine.BANNER, Doctrine.SUPPLY,
        Doctrine.SIEGE_MASTERS, Doctrine.VIGILANCE)

    private fun duel(doctrine: Doctrine, buffedSide: Int, seed: Long): Int {
        val b = Battle(BattleConfig(Mode.SKIRMISH, seed, 1f,
            startEra = 2, maxEra = 2, aiPersonality = AiPersonality.BALANCED,
            modifiers = setOf(Modifier.FRAGILE_BASES, Modifier.RICH_START),
            variantsEnabled = false))
        b.playerAi = Ai(b, 0, AiPersonality.BALANCED)
        for (s in b.sides) {
            s.baseHp = s.baseMaxHp * 0.20f
            s.gold += 600f * Defs.costScale(s.era)
        }
        b.applyDoctrine(b.sides[buffedSide], doctrine)
        var n = 0
        while (!b.over && n < 60 * 600) {
            b.step()
            b.fx.count = 0
            n++
        }
        return if (b.over) b.winner else -1
    }

    @Test fun mirroredAiMatchesRemainBelowSixtyPercent() {
        val problems = StringBuilder()
        for (d in tested) {
            var wins = 0
            var losses = 0
            var unfinished = 0
            for (i in 0 until 20) {
                val seed = 69_000L + i
                for (buffedSide in 0..1) {
                    when (duel(d, buffedSide, seed)) {
                        buffedSide -> wins++
                        -1 -> unfinished++
                        else -> losses++
                    }
                }
            }
            val resolved = wins + losses
            println("DOKTRYNA_69 ${d.name}: seedy=20 mecze=40 wygrane=$wins przegrane=$losses nierozstrzygniete=$unfinished skutecznosc=${if (resolved > 0) wins * 100.0 / resolved else 0.0}%")
            if (resolved < 24) problems.append("${d.name}: zbyt mało rozstrzygnięć ($resolved); ")
            if (resolved > 0 && wins * 100 > 60 * resolved)
                problems.append("${d.name}: przewaga ponad 60% ($wins/$resolved); ")
        }
        assertTrue(problems.toString(), problems.isEmpty())
    }

    @Test fun sameSeedAndSideGiveSameOutcome() {
        assertEquals(duel(Doctrine.BANNER, 1, 69012L),
            duel(Doctrine.BANNER, 1, 69012L))
    }
}
