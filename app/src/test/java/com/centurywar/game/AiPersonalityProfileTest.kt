package com.centurywar.game

import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

/** Porównania na identycznych seedach, z graczem prowadzonym przez BALANCED. */
class AiPersonalityProfileTest {
    private companion object {
        const val SEEDS = 20
        /**
         * Bitwa gra do rozstrzygnięcia: presja oblężnicza od 480 s kończy ją zwykle przed 900 s.
         * Dawny limit 720 s ucinał większość partii i liczył remis jako porażkę przeciwnika,
         * przez co nawet lustrzany BALANCED wygrywał ~12%. Limit to tylko bezpiecznik.
         */
        const val LIMIT_SECONDS = 1800f
        const val SAMPLE_STEPS = 300 // 5 sekund przy stałym kroku 60 Hz
    }

    private class Result(val name: AiPersonality, val startEra: Int) {
        var games = 0
        var wins = 0
        var draws = 0
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
        var waveReleases = 0
        var waveReleaseUnits = 0.0
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
            var wasHolding = b.enemy.hold
            val limit = (LIMIT_SECONDS / Battle.STEP).toInt()
            while (!b.over && steps < limit) {
                b.step()
                b.fx.count = 0
                steps++
                if (wasHolding && !b.enemy.hold) {
                    waveReleases++
                    waveReleaseUnits += b.enemy.aliveCount
                }
                wasHolding = b.enemy.hold
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
            if (!b.over) draws++
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
            15 -> (wins + 0.5 * draws) / games
            16 -> avg(upgrades)
            17 -> if (waveReleases == 0) 0.0 else waveReleaseUnits / waveReleases
            else -> error("Miara poza zakresem")
        }

        fun line(): String {
            val values = (0..17).joinToString("\t") { i ->
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
            "wrecz_90s\twieze\tostrzaly\tzrywy\tzywi\tzloto\tarmia_w_natarciu\tpostoj\tczas_s\twygrane\tulepszenia\tfala_ruszajaca"
        val lines = StringBuilder(header).append('\n')
        val results = Array(2) { scenario ->
            val start = if (scenario == 0) 0 else 2
            Array(AiPersonality.entries.size) { i -> Result(AiPersonality.entries[i], start) }
        }
        for (scenario in results.indices) {
            val start = if (scenario == 0) 0 else 2
            for (r in results[scenario]) {
                for (i in 0 until SEEDS) r.run(30001L + 7919L * i + start)
                lines.append(r.line()).append('\n')
            }
        }
        println(lines)
        val out = File("build/ai-personality-profile.tsv")
        out.parentFile.mkdirs()
        out.writeText(lines.toString())

        val advancing = results[0]
        val static = results[1]
        val econ = AiPersonality.ECONOMIST.ordinal
        val engineer = AiPersonality.ENGINEER.ordinal
        val assault = AiPersonality.ASSAULT.ordinal
        val siege = AiPersonality.SIEGER.ordinal
        for (p in AiPersonality.entries) {
            val i = p.ordinal
            if (i != econ) {
                assertTrue("Ekonomista ma awansować średnio najwcześniej: " + lines,
                    advancing[econ].metric(0) <= advancing[i].metric(0))
            }
            if (i != engineer) {
                assertTrue("Inżynier powinien najwięcej budować: " + lines,
                    static[engineer].metric(7) >= static[i].metric(7))
            }
            if (i != assault) {
                assertTrue("Szturm powinien szkolić najwięcej wręcz na starcie: " + lines,
                    advancing[assault].metric(6) >= advancing[i].metric(6))
            }
            if (i != siege) {
                assertTrue("Oblężnik powinien ruszać największą falą: " + lines,
                    static[siege].metric(17) >= static[i].metric(17))
            }
            // Nierozstrzygnięta partia (tylko po bezpieczniku) liczy się jako pół wygranej.
            val winRate = (advancing[i].wins + static[i].wins +
                0.5 * (advancing[i].draws + static[i].draws)) / (SEEDS * 2)
            assertTrue(p.name + ": wygrane spoza 30-70% (" + winRate + ")\n" + lines,
                winRate in 0.30..0.70)
        }
        for (i in AiPersonality.entries.indices) {
            for (j in i + 1 until AiPersonality.entries.size) {
                var differences = 0
                for (k in intArrayOf(2, 3, 4, 5, 7, 8, 9, 10, 11, 13, 16, 17)) {
                    val x = static[i].metric(k)
                    val y = static[j].metric(k)
                    val scale = maxOf(kotlin.math.abs(x), kotlin.math.abs(y))
                    if (scale > 0.0001 && kotlin.math.abs(x - y) >= 0.15 * scale) differences++
                }
                assertTrue("Para " + AiPersonality.entries[i] + "/" +
                    AiPersonality.entries[j] + " różni się tylko w " + differences +
                    " miarach (minimum 2).\n" + lines, differences >= 2)
            }
        }
    }
}
