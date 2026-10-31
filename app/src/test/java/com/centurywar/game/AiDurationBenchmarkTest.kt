package com.centurywar.game

import org.junit.Test

/** Stały zestaw bitew do porównywania czasu symulacji między gałęziami. */
class AiDurationBenchmarkTest {
    @Test
    fun fixedBattleSample() {
        val configs = arrayOf(
            BattleConfig(Mode.SKIRMISH, 301L, 1.0f, 0, 0,
                setOf(Modifier.FRAGILE_BASES, Modifier.RICH_START)),
            BattleConfig(Mode.SKIRMISH, 302L, 1.0f, 1, 1,
                setOf(Modifier.FRAGILE_BASES, Modifier.RICH_START)),
            BattleConfig(Mode.SKIRMISH, 303L, 1.0f, 2, 2,
                setOf(Modifier.FRAGILE_BASES, Modifier.RICH_START)),
            BattleConfig(Mode.SKIRMISH, 304L, 1.0f, 3, 3,
                setOf(Modifier.FRAGILE_BASES, Modifier.RICH_START)),
            BattleConfig(Mode.SKIRMISH, 305L, 1.0f, 3, 4,
                setOf(Modifier.FRAGILE_BASES, Modifier.RICH_START)),
            BattleConfig(Mode.SKIRMISH, 306L, 1.0f, 5, 5,
                setOf(Modifier.FRAGILE_BASES, Modifier.RICH_START)),
        )
        val maxSteps = (15f * 60f / Battle.STEP).toInt()
        for (cfg in configs) {
            val b = Battle(cfg)
            b.playerAi = Ai(b, 0)
            var steps = 0
            while (!b.over && steps < maxSteps) {
                b.step()
                b.fx.count = 0
                steps++
            }
            println("BENCHMARK seed=${cfg.seed} seconds=${b.time} completed=${b.over}")
        }
    }
}
