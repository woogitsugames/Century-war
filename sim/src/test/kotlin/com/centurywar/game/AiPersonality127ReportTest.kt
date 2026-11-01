package com.centurywar.game

import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Porównanie trzech dowódców na 50 identycznych seedach.
 * Test pomiarowy jest uruchamiany jawnie przez AI127_RUN=true.
 */
class AiPersonality127ReportTest {
    private companion object {
        const val SEEDS = 50
        const val MAX_SECONDS = 1200f
    }

    private class Totals(val personality: AiPersonality) {
        val roles = LongArray(Role.entries.size)
        var games = 0
        var completed = 0
        var wins = 0
        var contacts = 0
        var firstContactSum = 0.0
        var turretsFirst = 0
        var firstTurretSum = 0.0
        var evolvesFirst = 0
        var firstEvolveSum = 0.0
        var turrets = 0L
        var barrages = 0L
        var rallies = 0L
        var upgrades = 0L
        var heldSteps = 0L
        var steps = 0L

        fun run(seed: Long) {
            val b = Battle(BattleConfig(
                Mode.SKIRMISH, seed, 1f, startEra = 2, maxEra = 3,
                modifiers = setOf(Modifier.RICH_START, Modifier.FRAGILE_BASES),
                aiPersonality = personality,
            ))
            b.playerAi = Ai(b, 0, AiPersonality.BALANCED)
            BattleController(b)
            val limit = (MAX_SECONDS / Battle.STEP).toInt()
            var ticks = 0
            var firstContact = -1f
            var firstTurret = -1f
            var firstEvolve = -1f
            var previousEra = b.enemy.era
            val previousTiers = b.enemy.unitTier.copyOf()
            var usedUpgrades = 0

            while (!b.over && ticks < limit) {
                b.step()
                b.fx.count = 0
                ticks++
                if (b.enemy.hold) heldSteps++
                if (firstTurret < 0f && b.enemy.turretsBuilt > 0) firstTurret = b.time
                if (firstEvolve < 0f && b.enemy.era > 2) firstEvolve = b.time
                if (firstContact < 0f) {
                    // Pierwsze skuteczne trafienie gracza, a nie sama kolejka rekrutacji.
                    var hit = b.player.baseHp < b.player.baseMaxHp - 0.1f
                    if (!hit) for (u in b.units) {
                        if (u.alive && u.side == 0 && u.hp < u.maxHp - 0.1f) {
                            hit = true
                            break
                        }
                    }
                    if (hit) firstContact = b.time
                }
                if (b.enemy.era != previousEra) {
                    previousEra = b.enemy.era
                    for (i in previousTiers.indices) previousTiers[i] = 1
                }
                for (i in previousTiers.indices) {
                    val tier = b.enemy.unitTier[i]
                    if (tier > previousTiers[i]) usedUpgrades += tier - previousTiers[i]
                    previousTiers[i] = tier
                }
            }

            games++
            steps += ticks
            if (b.over) completed++
            if (b.over && b.winner == 1) wins++
            if (firstContact >= 0f) {
                contacts++
                firstContactSum += firstContact
            }
            if (firstTurret >= 0f) {
                turretsFirst++
                firstTurretSum += firstTurret
            }
            if (firstEvolve >= 0f) {
                evolvesFirst++
                firstEvolveSum += firstEvolve
            }
            for (i in roles.indices) roles[i] += b.enemy.spawnedByRole[i].toLong()
            turrets += b.enemy.turretsBuilt
            barrages += b.enemy.barragesUsed
            rallies += b.enemy.ralliesUsed
            upgrades += usedUpgrades
        }

        private fun mean(sum: Double, count: Int): String =
            if (count == 0) "-" else String.format(Locale.US, "%.2f", sum / count)
        private fun perGame(value: Long): String = mean(value.toDouble(), games)

        fun row(): String {
            val total = roles.sum().toDouble().coerceAtLeast(1.0)
            val columns = ArrayList<String>()
            columns.add(personality.name)
            columns.add(games.toString())
            columns.add(completed.toString())
            columns.add(mean(wins * 100.0, games))
            columns.add(contacts.toString())
            columns.add(mean(firstContactSum, contacts))
            columns.add(turretsFirst.toString())
            columns.add(mean(firstTurretSum, turretsFirst))
            columns.add(evolvesFirst.toString())
            columns.add(mean(firstEvolveSum, evolvesFirst))
            for (count in roles) columns.add(mean(count.toDouble() * 100.0, total.toInt()))
            columns.add(perGame(turrets))
            columns.add(perGame(barrages))
            columns.add(perGame(rallies))
            columns.add(perGame(upgrades))
            columns.add(mean(heldSteps.toDouble() * 100.0, steps.toInt()))
            return columns.joinToString(",")
        }
    }

    @Test fun compareThreeStylesOnFiftySeeds() {
        assumeTrue(System.getenv("AI127_RUN") == "true")
        assertEquals(50, SEEDS)
        val report = StringBuilder(
            "personality,seeds,finished,enemy_wins_pct,contacts,first_contact_s," +
                "turret_battles,first_turret_s,evolve_battles,first_evolve_s," +
                "melee_pct,ranged_pct,heavy_pct,elite_pct," +
                "turrets_per_game,barrages_per_game,rallies_per_game," +
                "unit_upgrades_per_game,hold_time_pct\n"
        )
        for (personality in arrayOf(
            AiPersonality.ECONOMIST, AiPersonality.SIEGER, AiPersonality.BALANCED,
        )) {
            val t = Totals(personality)
            repeat(SEEDS) { i -> t.run(50_021L + i * 9973L) }
            val line = t.row()
            println("AI127 " + line)
            report.append(line).append('\n')
        }
        val dir = File(System.getenv("AI127_OUT") ?: "build/ai127")
        dir.mkdirs()
        File(dir, "profiles.csv").writeText(report.toString())
    }
}
