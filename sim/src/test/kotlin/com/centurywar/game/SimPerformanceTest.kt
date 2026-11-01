package com.centurywar.game

import org.junit.Assert.assertTrue
import org.junit.Test

/** Przepustowość w krokach symulacji na sekundę, bez Androida i rysowania. */
class SimPerformanceTest {
    private fun measure(make: () -> Battle, indexed: Boolean, steps: Int): Double {
        fun fresh() = make().apply { useIndexedScan = indexed }
        var battle = fresh()
        repeat(1000) {
            if (battle.over) battle = fresh()
            battle.step()
            battle.fx.count = 0
        }
        val begin = System.nanoTime()
        repeat(steps) {
            if (battle.over) battle = fresh()
            battle.step()
            battle.fx.count = 0
        }
        return steps * 1_000_000_000.0 / (System.nanoTime() - begin).coerceAtLeast(1L)
    }

    private fun compare(name: String, make: () -> Battle, steps: Int = 6500) {
        // JIT pracuje na tej samej VM; nie porównujemy różnych maszyn ani buildów.
        val oldRate = measure(make, false, steps)
        val newRate = measure(make, true, steps)
        if (System.getenv("PERF_REPORT") == "true") {
            println("%-20s stare=%9.0f kroki/s  nowe=%9.0f kroki/s  ratio=%5.2fx"
                .format(name, oldRate, newRate, newRate / oldRate))
        }
        // 600 kroków/s = dwa razy więcej niż ×5 przy 60 FPS; duży margines na CI.
        assertTrue("$name: tylko ${newRate.toInt()} kroków/s", newRate >= 600.0)
    }

    @Test fun campaignStage36BothSidesAi() {
        compare("kampania-36", {
            val b = Battle(Campaign.config(Campaign.stages.last(), Bonuses(), 27001L))
            b.playerAi = Ai(b, 0)
            b
        })
    }

    @Test fun endlessWave40FullArmies() {
        compare("endless-40", {
            val b = Battle(BattleConfig(Mode.ENDLESS, 27002L, 1f, startEra = 8, maxEra = 8))
            b.wave = 40
            b.player.cap = 36; b.enemy.cap = 36
            repeat(30) {
                b.spawn(b.player, Defs.unit(8, Role.HEAVY))
                b.spawn(b.enemy, Defs.unit(8, Role.RANGED))
            }
            b.playerAi = Ai(b, 0)
            b
        })
    }

    @Test fun skirmishAtMinute30() {
        compare("potyczka-30min", {
            val b = Battle(BattleConfig(Mode.SKIRMISH, 27003L, 1f, startEra = 8, maxEra = 8))
            b.stepCount = 30 * 60 * 60
            b.time = b.stepCount * Battle.STEP
            b.playerAi = Ai(b, 0)
            b
        })
    }
}
