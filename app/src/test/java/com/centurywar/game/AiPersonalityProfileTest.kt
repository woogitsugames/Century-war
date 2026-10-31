package com.centurywar.game

import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

/** Porównania na identycznych seedach, z graczem prowadzonym przez BALANCED. */
class AiPersonalityProfileTest {
    private companion object {
        const val SEEDS = 20
        const val LIMIT_SECONDS = 720f
        const val SAMPLE_STEPS = 300 // 5 sekund przy stałym kroku 60 Hz
    }

    private class Result(val name: AiPersonality, val startEra: Int) {
        var games = 0
        var wins = 0
        var finished = 0
        var evolveTime = 0.0
        var evolves = 0.0
        val roles = LongArray(Role.entries.size)
        var meleeEarly = 0.0
        var turrets = 0.0
        var barrages = 0.0
        var rallies = 0.0
        var upgrades = 0.0
        var gold = 0.0
        var alive = 0.0
        var holdSamples = 0
        var sampleCount = 0
        var pushingAlive = 0.0
        var pushingSamples = 0
        var duration = 0.0

        fun run(seed: Long) {
            val b = Battle(BattleConfig(Mode.SKIRMISH, seed, 1f,
                startEra = startEra, maxEra = if (startEra == 0) 3 else 2,
                aiPersonality = name))
            b.playerAi = Ai(b, 0, AiPersonality.BALANCED)
            BattleController(b) // Rozkazy gracza są zapisywane w powtórce.
            var firstEvolve = -1f
            var early = 0
            var steps = 0
            val limit = (LIMIT_SECONDS / Battle.STEP).toInt()
            while (!b.over && steps < limit) {
                b.step()
                b.fx.count = 0
                steps++
                if (b.time <= 90f) early = b.enemy.spawnedByRole[Role.MELEE.ordinal]
                if (firstEvolve < 0f && b.enemy.era > startEra) firstEvolve = b.time
                if (steps % SAMPLE_STEPS == 0) {
                    sampleCount++
                    gold += b.enemy.gold
                    alive += b.enemy.aliveCount
                    if (b.enemy.hold) holdSamples++
                    if (!b.enemy.hold && (b.enemy.front - b.enemy.baseFront) * b.enemy.dir > 600f) {
                        pushingSamples++
                        pushingAlive += b.enemy.aliveCount
                    }
                }
            }
            games++
            if (b.over) finished++
            if (b.winner == 1 && b.over) wins++
            if (startEra == 0) evolveTime += if (firstEvolve >= 0f) firstEvolve.toDouble() else LIMIT_SECONDS.toDouble()
            evolves += b.enemy.erasAdvanced
            for (r in roles.indices) roles[r] += b.enemy.spawnedByRole[r].toLong()
            meleeEarly += early
            turrets += b.enemy.turretsBuilt
            barrages += b.enemy.barragesUsed
            rallies += b.enemy.ralliesUsed
            for (tier in b.enemy.unitTier) upgrades += tier - 1
            duration += b.time
        }

        private fun avg(v: Double) = v / games
        fun metric(index: Int): Double = when (index) {
            0 -> avg(evolveTime)
            1 -> avg(evolves)
            2, 3, 4, 5 -> {
                val total = roles.sum()
                if (total == 0L) 0.0 else roles[index - 2].toDouble() / total
            }
            6 -> avg(meleeEarly)
            7 -> avg(turrets)
            8 -> avg(barrages)
            9 -> avg(rallies)
            10 -> if (sampleCount == 0) 0.0 else alive / sampleCount
            11 -> if (sampleCount == 0) 0.0 else gold / sampleCount
            12 -> if (pushingSamples == 0) 0.0 else pushingAlive / pushingSamples
            13 -> if (sampleCount == 0) 0.0 else holdSamples.toDouble() / sampleCount
            14 -> avg(duration)
            15 -> wins.toDouble() / games
            16 -> avg(upgrades)
            else -> error("Miara poza zakresem")
        }

        fun line(): String {
            val values = (0..16).joinToString("\t") { i ->
                if (i == 0 && startEra != 0) "-" else String.format(Locale.US, "%.3f", metric(i))
            }
            return (if (startEra == 0) "0-3" else "2-2") + "\t" + name.name + "\t" +
                games + "\t" + finished + "\t" + values
        }
    }

    @Test
    fun measureAndReport() {
        assertTrue(SEEDS >= 20)
        val header = "era\tosobowosc\tproby\tkoniec\tawans_s\tawansy\twrecz\tzasieg\tciezkie\telita\t" +
            "wrecz_90s\twieze\tostrzaly\tzrywy\tzywi\tzloto\tarmia_w_natarciu\tpostoj\tczas_s\twygrane\tulepszenia"
        val lines = StringBuilder(header).append('\n')
        for (start in intArrayOf(0, 2)) {
            for (p in AiPersonality.entries) {
                val r = Result(p, start)
                for (i in 0 until SEEDS) r.run(30001L + 7919L * i + start)
                lines.append(r.line()).append('\n')
            }
        }
        println(lines)
        val out = File("build/ai-personality-profile.tsv")
        out.parentFile.mkdirs()
        out.writeText(lines.toString())
    }
}
