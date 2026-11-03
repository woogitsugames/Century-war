package com.centurywar.game

import org.junit.Assert.assertTrue

/**
 * Lustrzany test skuteczności wszystkich doktryn: 100 seedów i zamiana stron.
 * Od dowódcy (125) AI przywołuje go w potyczce, co zwiększa rozrzut wyników; przy
 * 20 seedach ECONOMY dawała 70%, a przy 100 seedach 57%. Pomiar tury 9 z dowódcą:
 * 42% (ARSENAL) – 63,5% (PLUNDER); po wyrównaniu w turze 10: 46,5% (BANNER) – 59,5% (FURY),
 * szczegóły w docs/BALANCE.md.
 *
 * Noc 9: doktryny są podzielone na dwie klasy (parzyste i nieparzyste pozycje w
 * [Doctrine.entries]), żeby oba procesy testowe Gradle dostały połowę pracy; seedy, progi
 * i komunikaty są bez zmian. Klasy: [DoctrineBalance82Test], [DoctrineBalance82OddTest].
 */
abstract class DoctrineBalance82Base {
    private fun duel(doctrine: Doctrine, boostedSide: Int, seed: Long): Int {
        val b = Battle(BattleConfig(Mode.SKIRMISH, seed, 1f,
            startEra = 2, maxEra = 2, aiPersonality = AiPersonality.BALANCED,
            modifiers = setOf(Modifier.FRAGILE_BASES, Modifier.RICH_START),
            variantsEnabled = false))
        b.playerAi = Ai(b, 0, AiPersonality.BALANCED)
        for (s in b.sides) {
            s.baseHp = s.baseMaxHp * 0.20f
            s.gold += 600f * Defs.costScale(s.era)
        }
        b.applyDoctrine(b.sides[boostedSide], doctrine)
        var steps = 0
        while (!b.over && steps < 60 * 600) {
            b.step()
            b.fx.count = 0
            steps++
        }
        return if (b.over) b.winner else -1
    }

    /** Doktryny z pozycji o podanej parzystości w [Doctrine.entries]. */
    protected fun checkDoctrines(parity: Int) {
        val failures = StringBuilder()
        for (doctrine in Doctrine.entries.filterIndexed { index, _ -> index % 2 == parity }) {
            var wins = 0
            var losses = 0
            var unfinished = 0
            for (i in 0 until 100) {
                for (side in 0..1) {
                    when (duel(doctrine, side, 82_000L + i)) {
                        side -> wins++
                        -1 -> unfinished++
                        else -> losses++
                    }
                }
            }
            val resolved = wins + losses
            val percent = if (resolved == 0) 0.0 else wins * 100.0 / resolved
            println("DOCTRINE_82 name=${doctrine.name} wins=$wins losses=$losses " +
                "unfinished=$unfinished winRate=$percent")
            if (resolved < 120 || wins * 100 < 40 * resolved || wins * 100 > 65 * resolved) {
                failures.append("${doctrine.name}: $wins/$resolved ($percent%); ")
            }
        }
        assertTrue(failures.toString(), failures.isEmpty())
    }
}
