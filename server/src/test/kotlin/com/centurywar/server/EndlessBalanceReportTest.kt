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
    /** Pomiar późnych fal na 100 seedach; osobno raportuje porażki i próbki ucięte na fali 60. */
    @Test fun veteranHundredSeeds() {
        val thresholds = intArrayOf(20, 30, 40, 50, 60)
        val survived = IntArray(thresholds.size)
        // Przedziały: <20, 20-29, 30-39, 40-44, 45-49, 50-54, 55-59, fala 60 bez porażki.
        val buckets = IntArray(8)
        val defeats = ArrayList<Int>(100)
        val bonus = veteranBonuses()
        var waveTotal = 0
        val maxSteps = 90_000
        for (seed in 1001L..1100L) {
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
            if (b.over) defeats.add(b.wave)
            for (i in thresholds.indices) {
                if (b.wave > thresholds[i] || (b.wave == thresholds[i] && !b.over)) survived[i]++
            }
            val bucket = when {
                !b.over -> 7
                b.wave < 20 -> 0
                b.wave < 30 -> 1
                b.wave < 40 -> 2
                b.wave < 45 -> 3
                b.wave < 50 -> 4
                b.wave < 55 -> 5
                else -> 6
            }
            buckets[bucket]++
            println("ENDLESS100_SEED=$seed WAVE=${b.wave} LOST=${b.over} STEPS=$steps")
        }
        val ordered = defeats.sorted()
        val median = when {
            ordered.isEmpty() -> "brak"
            ordered.size % 2 == 1 -> ordered[ordered.size / 2].toString()
            else -> ((ordered[ordered.size / 2 - 1] + ordered[ordered.size / 2]) / 2.0).toString()
        }
        println("ENDLESS100_MEAN=${waveTotal / 100.0}")
        println("ENDLESS100_FAILURES=${defeats.size}/100")
        println("ENDLESS100_DEFEAT_MEDIAN=$median")
        println("ENDLESS100_DEFEAT_WAVES=${ordered.joinToString(",")}")
        for (i in thresholds.indices) {
            println("ENDLESS100_SURVIVE_${thresholds[i]}=${survived[i]}/100")
        }
        println("ENDLESS100_DISTRIBUTION=${buckets.joinToString(",")}")
        // Stała próba seedów wykrywa regresję po kolejnych zmianach silnika i kart.
        // Cele strojenia (fala 60: 5–15%, mediana 45–55) tylko raportujemy, bo kolejne
        // zmiany kart i AI je przesuwają; twardo pilnujemy wczesnych fal i rzędu mediany.
        val inTarget60 = survived[4] in 5..15
        val medianValue = median.toDoubleOrNull()
        println("ENDLESS100_TARGET_SURVIVE_60=${if (inTarget60) "OK" else "POZA_CELEM"} (cel 5-15)")
        println("ENDLESS100_TARGET_MEDIAN=" +
            "${if (medianValue != null && medianValue in 45.0..55.0) "OK" else "POZA_CELEM"} (cel 45-55)")
        assertTrue(survived[0] >= 95, "Przeżycie fali 20: ${survived[0]}/100 (min. 95)")
        assertTrue(medianValue != null && medianValue in 38.0..60.0,
            "Mediana porażek: $median (dopuszczalny zakres 38–60, cel 45–55)")
    }


    /** Próbka tygodni po premierze: ten sam seed i karta z modyfikatorem oraz bez niego. */
    @Test fun weeklyModifierPairs122() {
        val firstWeek = com.centurywar.game.Weekly.weekNumber(com.centurywar.game.Daily.RELEASE_DAY)
        val pool = (firstWeek until firstWeek + 300L)
            .map { com.centurywar.game.Weekly.config(it) }
        for (modifier in listOf(
            com.centurywar.game.Modifier.ARMORED_FOE,
            com.centurywar.game.Modifier.GLASS_CANNON,
            com.centurywar.game.Modifier.VETERANS,
        )) {
            val sample = pool.filter { modifier in it.modifiers }.take(8)
            assertTrue(sample.size == 8, "Brak 8 tygodni dla $modifier")
            val with = ArrayList<Int>(8)
            val without = ArrayList<Int>(8)
            for (cfg in sample) {
                fun simulate(config: BattleConfig): Int {
                    val battle = Battle(config)
                    battle.playerAi = Ai(battle, 0)
                    var steps = 0
                    while (!battle.over && battle.wave < 60 && steps < 90_000) {
                        battle.step()
                        battle.fx.count = 0
                        steps++
                    }
                    assertTrue(steps < 90_000, "Timeout tygodnia ${config.weekly} ($modifier)")
                    return battle.wave
                }
                val control = BattleConfig(
                    mode = cfg.mode, seed = cfg.seed, aiPower = cfg.aiPower,
                    startEra = cfg.startEra, maxEra = cfg.maxEra,
                    modifiers = cfg.modifiers - modifier, playerBonuses = cfg.playerBonuses,
                    stageIndex = cfg.stageIndex, tutorial = cfg.tutorial,
                    aiPersonality = cfg.aiPersonality, variantsEnabled = cfg.variantsEnabled,
                    weather = cfg.weather, weekly = cfg.weekly, weeklyCard = cfg.weeklyCard,
                    dailyEvent = cfg.dailyEvent,
                )
                val currentWave = simulate(cfg)
                val controlWave = simulate(control)
                with.add(currentWave)
                without.add(controlWave)
                println("WEEKLY122_CASE MOD=${modifier.name} WEEK=${cfg.weekly} WITH=$currentWave WITHOUT=$controlWave")
            }
            fun median(values: List<Int>): Double {
                val v = values.sorted()
                return (v[3] + v[4]) / 2.0
            }
            println("WEEKLY122_SUMMARY MOD=${modifier.name} N=8 " +
                "WITH_MEAN=${with.average()} WITHOUT_MEAN=${without.average()} " +
                "WITH_MEDIAN=${median(with)} WITHOUT_MEDIAN=${median(without)} " +
                "WITH_WAVE20=${with.count { it >= 20 }}/8 " +
                "WITHOUT_WAVE20=${without.count { it >= 20 }}/8 " +
                "WITH_WAVE60=${with.count { it >= 60 }}/8 " +
                "WITHOUT_WAVE60=${without.count { it >= 60 }}/8")
        }
    }

}
