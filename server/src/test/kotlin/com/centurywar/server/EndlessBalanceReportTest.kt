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
        var scoreTotal = 0L
        var wagerRuns = 0
        var wagerBonus = 0L
        val cardRuns = IntArray(com.centurywar.game.EndlessCard.entries.size)
        val cardStacks = IntArray(com.centurywar.game.EndlessCard.entries.size)
        val maxSteps = 90_000
        // Dzień 58: próba kontrolna bez edycji testu (ENDLESS100_FIRST_SEED=2001).
        val firstSeed = System.getenv("ENDLESS100_FIRST_SEED")?.toLongOrNull() ?: 1001L
        println("ENDLESS100_SEEDS=$firstSeed..${firstSeed + 99}")
        for (seed in firstSeed until firstSeed + 100L) {
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
            scoreTotal += b.score()
            // Zlecenie 184: ile punktów dał sam Zakład (różnica stawki za falę).
            if (b.player.cardStacks[com.centurywar.game.EndlessCard.WAGER.ordinal] > 0) {
                wagerRuns++
                wagerBonus += b.wave * (com.centurywar.game.EndlessCards.WAGER_WAVE_SCORE - 250L)
            }
            if (b.over) defeats.add(b.wave)
            // Dzień 58: jak często AI bierze nowe karty (przebiegi z kartą i suma stosów).
            for (card in CARDS_206) {
                val n = b.player.cardStacks[card.ordinal]
                if (n > 0) cardRuns[card.ordinal]++
                cardStacks[card.ordinal] += n
            }
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
        println("ENDLESS100_SCORE_MEAN=${scoreTotal / 100.0}")
        println("ENDLESS100_WAGER_RUNS=$wagerRuns/100")
        println("ENDLESS100_SCORE_MEAN_WITHOUT_WAGER_BONUS=${(scoreTotal - wagerBonus) / 100.0}")
        println("ENDLESS100_FAILURES=${defeats.size}/100")
        println("ENDLESS100_DEFEAT_MEDIAN=$median")
        println("ENDLESS100_DEFEAT_WAVES=${ordered.joinToString(",")}")
        for (i in thresholds.indices) {
            println("ENDLESS100_SURVIVE_${thresholds[i]}=${survived[i]}/100")
        }
        println("ENDLESS100_DISTRIBUTION=${buckets.joinToString(",")}")
        for (card in CARDS_206) {
            println("ENDLESS100_CARD206 ${card.name} RUNS=${cardRuns[card.ordinal]}/100 " +
                "STACKS=${cardStacks[card.ordinal]}")
        }
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

    /**
     * Zlecenie 184: każdy zestaw tygodnia na tych samych 8 tygodniach (seed, epoki),
     * więc mediany fal są porównywalne między zestawami i między wersjami gry.
     */
    @Test fun weeklyRuleSets184() {
        val first = com.centurywar.game.Weekly.firstRuleSetWeek()
        val base = (0 until 8).map { com.centurywar.game.Weekly.config(first + 1L + 3L * it) }
        for (index in 0 until com.centurywar.game.ChallengeRuleSets.COUNT) {
            val set = com.centurywar.game.ChallengeRuleSets.forWeek(index.toLong())
            val waves = ArrayList<Int>(8)
            for (cfg in base) {
                val config = BattleConfig(
                    mode = cfg.mode, seed = cfg.seed, aiPower = cfg.aiPower,
                    startEra = cfg.startEra, maxEra = cfg.maxEra,
                    modifiers = linkedSetOf(set.first, set.second), playerBonuses = cfg.playerBonuses,
                    weekly = cfg.weekly, weeklyCard = set.weeklyCard,
                )
                val battle = Battle(config)
                battle.playerAi = Ai(battle, 0)
                var steps = 0
                while (!battle.over && battle.wave < 60 && steps < 90_000) {
                    battle.step()
                    battle.fx.count = 0
                    steps++
                }
                assertTrue(steps < 90_000, "Timeout zestawu ${set.key}")
                waves.add(battle.wave)
            }
            val sorted = waves.sorted()
            println("WEEKLY184_SET KEY=${set.key} MEDIAN=${(sorted[3] + sorted[4]) / 2.0} " +
                "MEAN=${waves.average()} WAVE20=${waves.count { it >= 20 }}/8 WAVES=${waves.joinToString(",")}")
        }
    }

    /**
     * Zlecenie 184: ten sam seed z Zakładem branym przy pierwszej ofercie (fala 5) i bez niego.
     * Mierzy, o ile Zakład zmienia średni wynik i falę (próg strojenia: +10% wyniku).
     */
    @Test fun wagerScoreEffect184() {
        val bonus = veteranBonuses()
        var scoreWith = 0L
        var scoreWithout = 0L
        var waveWith = 0
        var waveWithout = 0
        var survivedWith = 0
        var survivedWithout = 0
        val seeds = 1001L..1100L
        for (seed in seeds) {
            for (wager in listOf(false, true)) {
                val b = Battle(BattleConfig(Mode.ENDLESS, seed, 1f, playerBonuses = bonus))
                b.playerAi = Ai(b, 0)
                var steps = 0
                var placed = false
                while (!b.over && b.wave < 60 && steps < 90_000) {
                    b.step()
                    b.fx.count = 0
                    steps++
                    if (wager && !placed && b.wave >= 5) {
                        b.applyEndlessCard(b.player, com.centurywar.game.EndlessCard.WAGER)
                        placed = true
                    }
                }
                assertTrue(steps < 90_000, "Timeout pomiaru Zakładu: seed=$seed")
                if (wager) {
                    scoreWith += b.score(); waveWith += b.wave
                    if (!b.over) survivedWith++
                } else {
                    scoreWithout += b.score(); waveWithout += b.wave
                    if (!b.over) survivedWithout++
                }
            }
        }
        val n = (seeds.last - seeds.first + 1).toDouble()
        println("WAGER184 N=${n.toInt()} SCORE_WITH=${scoreWith / n} SCORE_WITHOUT=${scoreWithout / n} " +
            "GAIN=${"%.1f".format(java.util.Locale.US, (scoreWith.toDouble() / scoreWithout - 1.0) * 100.0)}% " +
            "WAVE_WITH=${waveWith / n} WAVE_WITHOUT=${waveWithout / n} " +
            "SURVIVE60_WITH=$survivedWith SURVIVE60_WITHOUT=$survivedWithout")
    }

    /**
     * Dzień 58 (paczka 206): siła pojedynczej karty. Ten sam seed z kartą dokładaną
     * trzykrotnie przy pierwszej ofercie (fala 5) i bez niej; dla porównania starsze karty
     * o podobnej roli. Karta nie jest dominująca, gdy jej przyrost średniej fali i przeżyć
     * fali 60 mieści się w rozrzucie kart porównawczych.
     */
    @Test fun newCardsEffect206() {
        val bonus = veteranBonuses()
        val variants: List<com.centurywar.game.EndlessCard?> = listOf(null) + CARDS_206 + listOf(
            com.centurywar.game.EndlessCard.SHIELD_WALL, com.centurywar.game.EndlessCard.SAPPERS,
            com.centurywar.game.EndlessCard.FRONTLINE_PLATE, com.centurywar.game.EndlessCard.WAR_CHEST,
            com.centurywar.game.EndlessCard.WAR_DRUMS,
        )
        val first = System.getenv("ENDLESS100_FIRST_SEED")?.toLongOrNull() ?: 1001L
        val seeds = first until first + 50L
        for (card in variants) {
            var waves = 0
            var survived = 0
            var score = 0L
            for (seed in seeds) {
                val b = Battle(BattleConfig(Mode.ENDLESS, seed, 1f, playerBonuses = bonus))
                b.playerAi = Ai(b, 0)
                var steps = 0
                var placed = card == null
                while (!b.over && b.wave < 60 && steps < 90_000) {
                    b.step()
                    b.fx.count = 0
                    steps++
                    if (!placed && b.wave >= 5) {
                        repeat(3) { b.applyEndlessCard(b.player, card!!) }
                        placed = true
                    }
                }
                assertTrue(steps < 90_000, "Timeout pomiaru karty: seed=$seed")
                waves += b.wave
                score += b.score()
                if (!b.over) survived++
            }
            val n = (seeds.last - seeds.first + 1).toDouble()
            println("CARD206 CARD=${card?.name ?: "BRAK"} N=${n.toInt()} WAVE_MEAN=${waves / n} " +
                "SURVIVE60=$survived SCORE_MEAN=${score / n}")
        }
    }

    private companion object {
        val CARDS_206 = listOf(
            com.centurywar.game.EndlessCard.PIKE_SQUARE, com.centurywar.game.EndlessCard.VANGUARD,
            com.centurywar.game.EndlessCard.BOSS_BOUNTY, com.centurywar.game.EndlessCard.WAR_HORNS,
        )
    }
}
