package com.centurywar.game

import com.centurywar.meta.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Powtarzalny pomiar wszystkich etapów kampanii × 3 profile badań × N seedów.
 * Długi (dziesiątki minut), więc działa tylko na żądanie:
 * BALANCE_RUN=true ./gradlew :app:testDebugUnitTest --tests '*BalanceReport*'
 * Opcjonalne zmienne: BALANCE_STAGE_FROM, BALANCE_STAGE_TO, BALANCE_PROFILE,
 * BALANCE_RUNS (domyślnie 20), BALANCE_STRICT=true (asercje progów).
 */
class BalanceReport {
    @Before fun onlyOnRequest() = assumeTrue(System.getenv("BALANCE_RUN") == "true")

    private data class ResearchProfile(val name: String, val progress: Float)
    private data class Result(
        val winner: Int,
        val minutes: Double,
        val timeout: Boolean,
        val playerEra: Int,
        val enemyEra: Int,
    )
    private data class Summary(
        val stage: Stage,
        val profile: String,
        val wins: Int,
        val timeouts: Int,
        val count: Int,
        val medianMinutes: Double,
        val avgPlayerEra: Double,
        val avgEnemyEra: Double,
    ) {
        val winPct get() = wins * 100.0 / count
        val timeoutPct get() = timeouts * 100.0 / count
        val avgEra get() = (avgPlayerEra + avgEnemyEra) / 2.0
    }

    private val profiles = listOf(
        ResearchProfile("nowy", 0f),
        ResearchProfile("poczatkujacy", 0.1f),
        ResearchProfile("sredni", 0.4f),
        ResearchProfile("weteran", 0.8f),
    )

    /**
     * Odwzorowanie poziomów badań z Profile.research; odblokowania 1-poziomowe
     * kupujemy najpierw, a pozostałe poziomy rozdzielamy możliwie równomiernie.
     * Łączna liczba poziomów = round(110 × postęp).
     */
    private fun bonuses(progress: Float): Bonuses {
        val defs = Profile.research
        val total = defs.sumOf { it.max }
        val desired = (total * progress).roundToInt()
        val levels = IntArray(defs.size) { floor(defs[it].max * progress).toInt() }
        if (desired >= 2) {
            for (i in defs.indices) {
                if (defs[i].max == 1) levels[i] = 1
            }
        }
        while (levels.sum() < desired) {
            val idx = defs.indices
                .filter { levels[it] < defs[it].max }
                .maxWithOrNull(compareBy<Int> { defs[it].max * progress - levels[it] }.thenBy { -it })
                ?: break
            levels[idx]++
        }
        while (levels.sum() > desired) {
            val idx = defs.indices
                .filter { levels[it] > 0 && !(desired >= 2 && defs[it].max == 1) }
                .minWithOrNull(compareBy<Int> { defs[it].max * progress - levels[it] }.thenBy { it })
                ?: break
            levels[idx]--
        }
        assertEquals(desired, levels.sum())
        val profile = Profile(File("nie-zapisuj-profilu-balansu.json"))
        for (i in defs.indices) profile.levels[defs[i].key] = levels[i]
        return profile.bonuses()
    }

    /** BALANCE_POWER_SCALE: mnożnik siły AI etapu (przegląd wartości przy strojeniu). */
    private val powerScale = System.getenv("BALANCE_POWER_SCALE")?.toFloatOrNull() ?: 1f

    private fun run(stage: Stage, bonus: Bonuses, seed: Long): Result {
        val base = Campaign.config(stage, bonus, seed)
        val battle = Battle(if (powerScale == 1f) base else BattleConfig(base.mode, base.seed, base.aiPower * powerScale,
            base.startEra, base.maxEra, base.modifiers, base.playerBonuses, base.stageIndex, base.tutorial, base.aiPersonality))
        battle.playerAi = Ai(battle, 0)
        val maxSteps = (30f * 60f / Battle.STEP).toInt()
        var steps = 0
        while (!battle.over && steps < maxSteps) {
            battle.step()
            battle.fx.count = 0
            steps++
        }
        return Result(
            winner = if (battle.over) battle.winner else -1,
            minutes = battle.time.toDouble() / 60.0,
            timeout = !battle.over,
            playerEra = battle.player.era,
            enemyEra = battle.enemy.era,
        )
    }

    private fun measure(stage: Stage, name: String, bonus: Bonuses, runs: Int): Summary {
        val results = ArrayList<Result>(runs)
        for (trial in 0 until runs) {
            val seed = 246_813_579L + stage.index * 10_007L + trial * 997L
            results.add(run(stage, bonus, seed))
        }
        val durations = results.map { it.minutes }.sorted()
        val median = if (runs % 2 == 1) durations[runs / 2]
            else (durations[runs / 2 - 1] + durations[runs / 2]) / 2.0
        return Summary(
            stage = stage,
            profile = name,
            wins = results.count { it.winner == 0 },
            timeouts = results.count { it.timeout },
            count = runs,
            medianMinutes = median,
            avgPlayerEra = results.sumOf { it.playerEra.toDouble() } / runs,
            avgEnemyEra = results.sumOf { it.enemyEra.toDouble() } / runs,
        )
    }

    /** Oczekiwany postęp badań na danym etapie (zgodnie ze stageRamp w Modes.kt). */
    private fun targetProfile(index: Int): String {
        val n = Campaign.stages.size
        return when {
            index < 6 -> "nowy"
            index < n / 2 -> "poczatkujacy"
            index < n * 3 / 4 -> "sredni"
            else -> "weteran"
        }
    }

    private fun fmt(value: Double) = String.format(Locale.US, "%.2f", value)

    @Test
    fun campaignReport() {
        val stages = Campaign.stages.size
        val from = (System.getenv("BALANCE_STAGE_FROM")?.toIntOrNull() ?: 1).coerceIn(1, stages)
        val to = (System.getenv("BALANCE_STAGE_TO")?.toIntOrNull() ?: stages).coerceIn(from, stages)
        val runs = (System.getenv("BALANCE_RUNS")?.toIntOrNull() ?: 20).coerceAtLeast(1)
        val filter = System.getenv("BALANCE_PROFILE")
        // BALANCE_PROFILE=target: tylko profil docelowy każdego etapu.
        val selected = profiles.filter { filter == null || filter == "target" || it.name == filter }
        require(selected.isNotEmpty()) { "Nieznany profil: $filter" }

        val dir = File(System.getenv("BALANCE_OUT") ?: "build/balance")
        dir.mkdirs()
        val stats = ArrayList<Summary>()
        // BALANCE_STAGES="1,7,13": tylko wybrane etapy (szybkie przybliżenie przy strojeniu).
        val only = System.getenv("BALANCE_STAGES")?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.toSet()
        for (index in from - 1 until to) {
            if (only != null && index + 1 !in only) continue
            val stage = Campaign.stages[index]
            for (profile in selected) {
                if (filter == "target" && profile.name != targetProfile(index)) continue
                val result = measure(stage, profile.name, bonuses(profile.progress), runs)
                stats.add(result)
                println("BALANCE stage=${stage.index + 1} profile=${profile.name}" +
                    " win=${fmt(result.winPct)}% median=${fmt(result.medianMinutes)}m" +
                    " timeout=${fmt(result.timeoutPct)}% era=${fmt(result.avgEra)}")
            }
        }

        val name = "${from}-$to" + (System.getenv("BALANCE_TAG")?.let { "-$it" } ?: "")
        val csv = StringBuilder("stage,profile,ai_power,start_era,max_era,modifiers,n,wins,win_rate_pct,median_minutes,timeouts,timeout_pct,avg_era,avg_player_era,avg_enemy_era\n")
        val md = StringBuilder("# Raport balansu: $name\n\n")
            .append("${to - from + 1} etapów, 3 profile badań (0%, 40%, 80%), domyślnie N=20 seedów na kombinację. ")
            .append("Czas do 30 min; timeout to niedokończona bitwa; epoka = średnia dwóch stron, indeksowana od 0.\n\n")
            .append("| Etap | Profil | AI | Epoki | Wygrane | Mediana [min] | Timeout | Śr. epoka |\n")
            .append("|---:|---|---:|---|---:|---:|---:|---:|\n")
        for (r in stats) {
            val s = r.stage
            val mods = s.modifiers.joinToString("+") { it.name }
            csv.append("${s.index + 1},${r.profile},${fmt(s.aiPower.toDouble())},${s.startEra},${s.maxEra},$mods,${r.count},${r.wins},${fmt(r.winPct)},${fmt(r.medianMinutes)},${r.timeouts},${fmt(r.timeoutPct)},${fmt(r.avgEra)},${fmt(r.avgPlayerEra)},${fmt(r.avgEnemyEra)}\n")
            md.append("| ${s.index + 1} | ${r.profile} | ${fmt(s.aiPower.toDouble())} | ${s.startEra}–${s.maxEra} | ${fmt(r.winPct)}% | ${fmt(r.medianMinutes)} | ${fmt(r.timeoutPct)}% | ${fmt(r.avgEra)} |\n")
        }
        File(dir, "report-$name.csv").writeText(csv.toString())
        File(dir, "report-$name.md").writeText(md.toString())
        println("BALANCE_REPORT ${File(dir, "report-$name.csv").absolutePath}")
        if (System.getenv("BALANCE_STRICT") == "true") {
            for (r in stats) {
                val target = targetProfile(r.stage.index)
                if (r.profile != target) continue
                val minRate = when (target) {
                    "nowy" -> 70.0
                    "poczatkujacy" -> 55.0
                    else -> 45.0
                }
                assertTrue("Za mało zwycięstw na etapie ${r.stage.index + 1}: ${r.winPct}%",
                    r.winPct >= minRate)
                assertTrue("Nieprawidłowa mediana etapu ${r.stage.index + 1}: ${r.medianMinutes} min",
                    r.medianMinutes in 4.0..12.0)
                assertTrue("Pat na etapie ${r.stage.index + 1}", r.timeoutPct < 5.0)
            }
            assertTrue("Timeouty w raporcie przekraczają limit 5%",
                stats.sumOf { it.timeouts } * 20 < stats.sumOf { it.count })
        }
    }

    /** Lustrzane pojedynki bez badań, z tą samą siłą i możliwością elit obu stron. */
    @Test
    fun mirrorReport() {
        val runs = (System.getenv("BALANCE_MIRROR_RUNS")?.toIntOrNull() ?: 20).coerceAtLeast(1)
        val dir = File(System.getenv("BALANCE_OUT") ?: "build/balance")
        dir.mkdirs()
        val out = StringBuilder("# Lustrzane bitwy AI vs AI\n\n")
            .append("| Epoka startowa | N | Timeouty | Mediana [min] |\n|---:|---:|---:|---:|\n")
        for (era in listOf(0, Defs.ERA_COUNT / 2, Defs.ERA_COUNT - 1)) {
            val results = (0 until runs).map { trial ->
                val config = BattleConfig(Mode.SKIRMISH, 1_000_003L + era * 10_007L + trial * 997L,
                    1f, era, era, playerBonuses = Bonuses())
                val b = Battle(config)
                b.playerAi = Ai(b, 0)
                val maxSteps = (30f * 60f / Battle.STEP).toInt()
                var step = 0
                while (!b.over && step < maxSteps) { b.step(); b.fx.count = 0; step++ }
                Pair(!b.over, b.time.toDouble() / 60.0)
            }
            val t = results.map { it.second }.sorted()
            val median = if (runs % 2 == 1) t[runs / 2]
                else (t[runs / 2 - 1] + t[runs / 2]) / 2.0
            out.append("| $era | $runs | ${results.count { it.first }} | ${fmt(median)} |\n")
            println("MIRROR era=$era timeouts=${results.count { it.first }}/$runs median=${fmt(median)}m")
            // Każda lustrzana bitwa musi skończyć się przed limitem 30 minut.
            assertEquals("Pat w epoce $era", 0, results.count { it.first })
        }
        File(dir, "mirror.md").writeText(out.toString())
    }
}
