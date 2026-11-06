package com.centurywar.game

import org.junit.Assume.assumeTrue
import org.junit.Test

/** Pomiar kontrolny; warianty ziaren nie są dodatkowymi oficjalnymi wyzwaniami. */
class DailyWeeklyBalance199Test {
    @Test fun measureDaysAndWeeklySets() {
        assumeTrue(System.getenv("BALANCE_RUN") == "true")
        val trials = 10
        for (offset in 0 until 28) {
            val day = Daily.RELEASE_DAY + offset
            val base = Daily.config(day, Bonuses())
            var wins = 0
            var timeouts = 0
            val minutes = mutableListOf<Double>()
            for (i in 0 until trials) {
                val cfg = BattleConfig(base.mode, base.seed + i * 997L, base.aiPower,
                    base.startEra, base.maxEra, base.modifiers, base.playerBonuses,
                    variantsEnabled = base.variantsEnabled, weather = base.weather,
                    dailyEvent = base.dailyEvent)
                val b = Battle(cfg)
                b.playerAi = Ai(b, 0)
                var steps = 0
                while (!b.over && steps < 108_000) { b.step(); b.fx.count = 0; steps++ }
                if (!b.over) timeouts++ else if (b.winner == 0) wins++
                minutes.add(b.time.toDouble() / 60.0)
            }
            val ordered = minutes.sorted()
            println("DAY199 day=$day offset=$offset event=${base.dailyEvent} mods=${base.modifiers.joinToString("+")} " +
                "n=$trials wins=$wins timeouts=$timeouts medianMinutes=${(ordered[4] + ordered[5]) / 2}")
        }
        val first = Weekly.firstRuleSetWeek()
        val baseWeeks = (0 until 8).map { Weekly.config(first + 1L + 3L * it) }
        for (set in ChallengeRuleSets.all) {
            val waves = mutableListOf<Int>()
            val minutes = mutableListOf<Double>()
            var timeouts = 0
            for (base in baseWeeks) {
                val cfg = BattleConfig(base.mode, base.seed, base.aiPower, base.startEra, base.maxEra,
                    linkedSetOf(set.first, set.second), base.playerBonuses,
                    weekly = base.weekly, weeklyCard = set.weeklyCard)
                val b = Battle(cfg)
                b.playerAi = Ai(b, 0)
                var steps = 0
                while (!b.over && b.wave < 60 && steps < 90_000) { b.step(); b.fx.count = 0; steps++ }
                if (!b.over && b.wave < 60) timeouts++
                waves.add(b.wave); minutes.add(b.time.toDouble() / 60.0)
            }
            val sorted = waves.sorted(); val sortedMinutes = minutes.sorted()
            println("WEEK199 key=${set.key} n=8 medianWave=${(sorted[3] + sorted[4]) / 2.0} " +
                "wave20=${waves.count { it >= 20 }} medianMinutes=${(sortedMinutes[3] + sortedMinutes[4]) / 2} timeouts=$timeouts")
        }
    }
}
