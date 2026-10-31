package com.centurywar.server

import com.centurywar.game.Ai
import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Bonuses
import com.centurywar.game.Mode
import com.centurywar.game.Research
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.floor
import kotlin.math.roundToInt

/** Pomiar tych samych 20 seedów; weteran ma dokładnie 80% poziomów badań. */
class EndlessBalanceReportTest {
    private fun veteranBonuses(): Bonuses {
        val defs = Research.defs
        val progress = 0.8f
        val desired = (defs.sumOf { it.max } * progress).roundToInt()
        val levels = IntArray(defs.size) { floor(defs[it].max * progress).toInt() }
        if (desired >= 2) {
            for (i in defs.indices) if (defs[i].max == 1) levels[i] = 1
        }
        while (levels.sum() < desired) {
            val idx = defs.indices.filter { levels[it] < defs[it].max }
                .maxWithOrNull(compareBy<Int> { defs[it].max * progress - levels[it] }.thenBy { -it })
                ?: break
            levels[idx]++
        }
        while (levels.sum() > desired) {
            val idx = defs.indices.filter { levels[it] > 0 && !(desired >= 2 && defs[it].max == 1) }
                .minWithOrNull(compareBy<Int> { defs[it].max * progress - levels[it] }.thenBy { it })
                ?: break
            levels[idx]--
        }
        check(levels.sum() == desired)
        return Research.bonuses(levels)
    }

    @Test fun veteranTwentySeeds() {
        val thresholds = intArrayOf(20, 30, 40, 50, 60)
        val survived = IntArray(thresholds.size)
        val failures = ArrayList<Int>()
        val bonus = veteranBonuses()
        var waveTotal = 0
        val maxSteps = 90_000
        for (seed in 1001L..1020L) {
            val b = Battle(BattleConfig(Mode.ENDLESS, seed, 1f, playerBonuses = bonus))
            b.playerAi = Ai(b, 0)
            var steps = 0
            while (!b.over && b.wave < 60 && steps < maxSteps) {
                b.step()
                b.fx.count = 0
                steps++
            }
            assertTrue(steps < maxSteps, "Timeout pomiaru Endless: seed=$seed")
            waveTotal += b.wave
            if (b.over) failures.add(b.wave)
            for (i in thresholds.indices) {
                // Śmierć w fali progowej nie zalicza jej przeżycia.
                if (b.wave > thresholds[i] || (b.wave == thresholds[i] && !b.over)) survived[i]++
            }
            println("ENDLESS_SEED=$seed WAVE=${b.wave} LOST=${b.over} STEPS=$steps")
        }
        println("ENDLESS_MEAN=${waveTotal / 20.0}")
        for (i in thresholds.indices) {
            println("ENDLESS_SURVIVE_${thresholds[i]}=${survived[i]}/20")
        }
        val median = failures.sorted().let {
            if (it.isEmpty()) "brak" else it[it.size / 2].toString()
        }
        println("ENDLESS_FAILURES=${failures.size}/20")
        println("ENDLESS_DEFEAT_WAVE_MEDIAN=$median")
        println("ENDLESS_DEFEAT_WAVES=${failures.joinToString(",")}")
    }
}
