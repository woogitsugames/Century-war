package com.centurywar.game

import com.centurywar.meta.Profile
import com.centurywar.game.CampaignDifficulty
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
 * BALANCE_RUNS (domyślnie 20), BALANCE_STRICT=true (asercje progów),
 * BALANCE_COMMANDER=on|off (AI gracza przywołuje dowódcę z własnej inicjatywy / nigdy;
 * domyślnie zwykłe zasady trybu — w kampanii tylko w odpowiedzi na dowódcę wroga).
 * BALANCE_PROFILE=medale|medale-elita: realistyczny nowy gracz z medalami tylko z kampanii
 * (tura 8, docs/BALANCE.md).
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
        val playerCommander: Boolean = false,
        val enemyCommander: Boolean = false,
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
        val playerCommanders: Int = 0,
        val enemyCommanders: Int = 0,
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

    /**
     * Realistyczny nowy gracz (tura 8): medale wyłącznie z kampanii, pierwsza wygrana każdego
     * etapu na 2 gwiazdki, od etapu 7 jedna porażka przed wygraną; medale wydawane na najtańszy
     * następny poziom. [eliteFirst]: pierwszym zakupem jest `r_elite`.
     */
    private fun medalLevels(stageIndex: Int, eliteFirst: Boolean): IntArray {
        val defs = Research.defs
        var medals = 0
        for (i in 0 until stageIndex) {
            medals += 20 + 6 * i + 2 * 5 + 2 * 12
            if (i >= 6) medals += 5 + 2 * i + 10
        }
        val levels = IntArray(defs.size)
        if (eliteFirst) {
            if (medals < defs[0].cost(0)) return levels
            medals -= defs[0].cost(0)
            levels[0] = 1
        }
        while (true) {
            val idx = defs.indices.filter { levels[it] < defs[it].max }
                .minWithOrNull(compareBy<Int> { defs[it].cost(levels[it]) }.thenBy { it }) ?: break
            val cost = defs[idx].cost(levels[idx])
            if (cost > medals) break
            medals -= cost
            levels[idx]++
        }
        return levels
    }

    private fun bonusFor(profile: ResearchProfile, stageIndex: Int): Bonuses = when (profile.name) {
        "medale" -> Research.bonuses(medalLevels(stageIndex, false))
        "medale-elita" -> Research.bonuses(medalLevels(stageIndex, true))
        else -> bonuses(profile.progress)
    }

    /** BALANCE_COMMANDER: on = AI gracza przywołuje dowódcę samo, off = nigdy, brak = zasady trybu. */
    private val commanderOverride: Boolean? = when (System.getenv("BALANCE_COMMANDER")) {
        "on" -> true
        "off" -> false
        else -> null
    }

    /** BALANCE_POWER_SCALE: mnożnik siły AI etapu (przegląd wartości przy strojeniu). */
    private val powerScale = System.getenv("BALANCE_POWER_SCALE")?.toFloatOrNull() ?: 1f

    private fun run(
        stage: Stage, bonus: Bonuses, seed: Long,
        difficulty: CampaignDifficulty = CampaignDifficulty.NORMAL,
    ): Result {
        val base = Campaign.config(stage, bonus, seed, difficulty = difficulty)
        val battle = Battle(if (powerScale == 1f) base else BattleConfig(base.mode, base.seed, base.aiPower * powerScale,
            base.startEra, base.maxEra, base.modifiers, base.playerBonuses, base.stageIndex, base.tutorial, base.aiPersonality, base.variantsEnabled, base.weather))
        battle.playerAi = Ai(battle, 0).also { it.commanderOverride = commanderOverride }
        val maxSteps = (30f * 60f / Battle.STEP).toInt()
        var steps = 0
        var playerCommander = false
        var enemyCommander = false
        while (!battle.over && steps < maxSteps) {
            battle.step()
            battle.fx.count = 0
            if (battle.player.commanderCd != 0f) playerCommander = true
            if (battle.enemy.commanderCd != 0f) enemyCommander = true
            steps++
        }
        return Result(
            winner = if (battle.over) battle.winner else -1,
            minutes = battle.time.toDouble() / 60.0,
            timeout = !battle.over,
            playerEra = battle.player.era,
            enemyEra = battle.enemy.era,
            playerCommander = playerCommander,
            enemyCommander = enemyCommander,
        )
    }

    private fun measure(
        stage: Stage, name: String, bonus: Bonuses, runs: Int,
        difficulty: CampaignDifficulty = CampaignDifficulty.NORMAL,
    ): Summary {
        val results = ArrayList<Result>(runs)
        for (trial in 0 until runs) {
            val seed = 246_813_579L + stage.index * 10_007L + trial * 997L
            results.add(run(stage, bonus, seed, difficulty))
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
            playerCommanders = results.count { it.playerCommander },
            enemyCommanders = results.count { it.enemyCommander },
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

    /**
     * Dokładnie 36 etapów × 20 seedów × trzy poziomy, z tym samym profilem badań
     * dla danego etapu. Zakres można podzielić na grupy BALANCE_STAGES.
     */
    @Test
    fun campaignDifficultyReport() {
        val runs = (System.getenv("BALANCE_RUNS")?.toIntOrNull() ?: 20).coerceAtLeast(1)
        val only = System.getenv("BALANCE_STAGES")?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }?.toSet()
        val out = File(System.getenv("BALANCE_OUT") ?: "build/balance")
        out.mkdirs()
        val csv = StringBuilder("stage,difficulty,profile,ai_power,n,wins,win_pct,median_minutes,timeouts,timeout_pct\n")
        val md = StringBuilder("# Poziomy trudności kampanii (126)\n\n")
            .append("Każdy etap: ten sam profil badań i $runs seedów na każdy z trzech poziomów. ")
            .append("Sterowanie graczem: Ai, symulacja do 30 minut.\n\n")
            .append("| Etap | Poziom | Profil | Siła AI | Wygrane | Odsetek | Mediana (min) | Paty |\n")
            .append("|---:|---|---|---:|---:|---:|---:|---:|\n")
        for (stage in Campaign.stages) {
            if (only != null && stage.index + 1 !in only) continue
            val profile = profiles.first { it.name == targetProfile(stage.index) }
            val bonus = bonuses(profile.progress)
            for (difficulty in CampaignDifficulty.entries) {
                val s = measure(stage, profile.name, bonus, runs, difficulty)
                val power = stage.aiPower * difficulty.powerMultiplier
                println("DIFFICULTY_126 stage=${stage.index + 1} difficulty=${difficulty.name}" +
                    " profile=${profile.name} wins=${s.wins}/${s.count}" +
                    " median=${fmt(s.medianMinutes)}m timeouts=${s.timeouts}")
                csv.append("${stage.index + 1},${difficulty.name},${profile.name},${fmt(power.toDouble())},$runs," +
                    "${s.wins},${fmt(s.winPct)},${fmt(s.medianMinutes)},${s.timeouts},${fmt(s.timeoutPct)}\n")
                md.append("| ${stage.index + 1} | ${difficulty.name} | ${profile.name} | ${fmt(power.toDouble())} | " +
                    "${s.wins}/${s.count} | ${fmt(s.winPct)}% | ${fmt(s.medianMinutes)} | ${s.timeouts} |\n")
            }
        }
        File(out, "campaign-difficulty-126.csv").writeText(csv.toString())
        File(out, "campaign-difficulty-126.md").writeText(md.toString())
    }

    private fun fmt(value: Double) = String.format(Locale.US, "%.2f", value)

    /**
     * Dla każdej planszy z pogodą: ten sam zestaw seedów, profil badań i AI
     * przeciw wersji CLEAR oraz dwóm sąsiednim etapom.
     * BALANCE_RUN=true BALANCE_PROFILE=target BALANCE_RUNS=20.
     */
    @Test
    fun weatherImpactReport() {
        val runs = (System.getenv("BALANCE_RUNS")?.toIntOrNull() ?: 20).coerceAtLeast(1)
        val only = System.getenv("BALANCE_STAGES")?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.toSet()
        val out = File(System.getenv("BALANCE_OUT") ?: "build/balance")
        out.mkdirs()
        val csv = StringBuilder("stage,weather,profile,runs,weather_win_pct,clear_win_pct,delta_pp,prev_win_pct,next_win_pct\n")
        val md = StringBuilder("# Wpływ pogody na balans kampanii\n\n")
            .append("Te same seedy i profil badań dla pogody, CLEAR i sąsiadów; N=$runs. ")
            .append("Wyniki są porównywane w punktach procentowych.\n\n")
            .append("| Etap | Pogoda | Profil | N | Wygrane pogoda | CLEAR | Delta [pp] | Poprz. | Nast. |\n")
            .append("|---:|---|---|---:|---:|---:|---:|---:|---:|\n")
        for (stage in Campaign.stages) {
            if (stage.weather == Weather.CLEAR || (only != null && stage.index + 1 !in only)) continue
            val profile = profiles.first { it.name == targetProfile(stage.index) }
            val bonus = bonuses(profile.progress)
            val rainy = measure(stage, profile.name, bonus, runs)
            val clearStage = Stage(stage.index, stage.nameKey, stage.startEra, stage.maxEra,
                stage.aiPower, stage.modifiers, Weather.CLEAR)
            val baseline = measure(clearStage, profile.name, bonus, runs)
            val previous = if (stage.index > 0) measure(Campaign.stages[stage.index - 1], profile.name, bonus, runs).winPct else Double.NaN
            val next = if (stage.index + 1 < Campaign.stages.size)
                measure(Campaign.stages[stage.index + 1], profile.name, bonus, runs).winPct else Double.NaN
            val delta = rainy.winPct - baseline.winPct
            println("WEATHER_BALANCE stage=${stage.index + 1} weather=${stage.weather} profile=${profile.name}" +
                " win=${fmt(rainy.winPct)}% clear=${fmt(baseline.winPct)}% delta=${fmt(delta)}pp" +
                " neighbors=${fmt(previous)}%/${fmt(next)}%")
            csv.append("${stage.index + 1},${stage.weather},${profile.name},$runs,${fmt(rainy.winPct)},${fmt(baseline.winPct)},${fmt(delta)},${fmt(previous)},${fmt(next)}\n")
            md.append("| ${stage.index + 1} | ${stage.weather} | ${profile.name} | $runs | ${fmt(rainy.winPct)}% | ${fmt(baseline.winPct)}% | ${fmt(delta)} | ${fmt(previous)}% | ${fmt(next)}% |\n")
        }
        File(out, "weather-impact.csv").writeText(csv.toString())
        File(out, "weather-impact.md").writeText(md.toString())
        println("WEATHER_BALANCE_REPORT ${File(out, "weather-impact.csv").absolutePath}")
    }

    @Test
    fun campaignReport() {
        val stages = Campaign.stages.size
        val from = (System.getenv("BALANCE_STAGE_FROM")?.toIntOrNull() ?: 1).coerceIn(1, stages)
        val to = (System.getenv("BALANCE_STAGE_TO")?.toIntOrNull() ?: stages).coerceIn(from, stages)
        val runs = (System.getenv("BALANCE_RUNS")?.toIntOrNull() ?: 20).coerceAtLeast(1)
        val filter = System.getenv("BALANCE_PROFILE")
        // BALANCE_PROFILE=target: tylko profil docelowy każdego etapu.
        val medal = filter == "medale" || filter == "medale-elita"
        val selected = if (medal) listOf(ResearchProfile(filter!!, -1f))
            else profiles.filter { filter == null || filter == "target" || it.name == filter }
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
                val result = measure(stage, profile.name, bonusFor(profile, index), runs)
                stats.add(result)
                val levels = if (medal) " levels=${medalLevels(index, filter == "medale-elita").sum()}" else ""
                println("BALANCE stage=${stage.index + 1} profile=${profile.name}$levels" +
                    " commander=${System.getenv("BALANCE_COMMANDER") ?: "auto"}" +
                    " win=${fmt(result.winPct)}% median=${fmt(result.medianMinutes)}m" +
                    " timeout=${fmt(result.timeoutPct)}% era=${fmt(result.avgEra)}" +
                    " cmd=${result.playerCommanders}/${result.enemyCommanders}")
            }
        }

        val name = "${from}-$to" + (System.getenv("BALANCE_TAG")?.let { "-$it" } ?: "")
        val csv = StringBuilder("stage,profile,ai_power,start_era,max_era,modifiers,n,wins,win_rate_pct,median_minutes,timeouts,timeout_pct,avg_era,avg_player_era,avg_enemy_era,player_commander_battles,enemy_commander_battles\n")
        val md = StringBuilder("# Raport balansu: $name\n\n")
            .append("${to - from + 1} etapów, 3 profile badań (0%, 40%, 80%), domyślnie N=20 seedów na kombinację. ")
            .append("Czas do 30 min; timeout to niedokończona bitwa; epoka = średnia dwóch stron, indeksowana od 0.\n\n")
            .append("| Etap | Profil | AI | Epoki | Wygrane | Mediana [min] | Timeout | Śr. epoka |\n")
            .append("|---:|---|---:|---|---:|---:|---:|---:|\n")
        for (r in stats) {
            val s = r.stage
            val mods = s.modifiers.joinToString("+") { it.name }
            csv.append("${s.index + 1},${r.profile},${fmt(s.aiPower.toDouble())},${s.startEra},${s.maxEra},$mods,${r.count},${r.wins},${fmt(r.winPct)},${fmt(r.medianMinutes)},${r.timeouts},${fmt(r.timeoutPct)},${fmt(r.avgEra)},${fmt(r.avgPlayerEra)},${fmt(r.avgEnemyEra)},${r.playerCommanders},${r.enemyCommanders}\n")
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

    /**
     * Endless weterana (seedy 1001–…, jak EndlessBalanceReportTest) w trzech wariantach dowódcy:
     * `bez` — reguły bez dowódcy, `wrog` — dowódca tylko u wroga (AI gracza go nie przywołuje),
     * `z` — zwykłe zasady (obie strony). BALANCE_ENDLESS_SEEDS (domyślnie 100),
     * BALANCE_ENDLESS_VARIANTS="bez,z" (domyślnie wszystkie). Plik endless-commander.md.
     */
    @Test
    fun endlessCommanderReport() {
        val seeds = (System.getenv("BALANCE_ENDLESS_SEEDS")?.toIntOrNull() ?: 100).coerceAtLeast(1)
        val variants = System.getenv("BALANCE_ENDLESS_VARIANTS")?.split(',')?.map { it.trim() }
            ?: listOf("bez", "wrog", "z")
        val dir = File(System.getenv("BALANCE_OUT") ?: "build/balance")
        dir.mkdirs()
        val bonus = bonuses(0.8f)
        val md = StringBuilder("# Endless z dowódcą (weteran, $seeds seedów, do fali 60)\n\n")
            .append("| Wariant | Śr. fala | Mediana porażki | Fala 40 | Fala 50 | Fala 60 | Dowódca gracza [bitwy] |\n")
            .append("|---|---:|---:|---:|---:|---:|---:|\n")
        for (variant in variants) {
            val waves = ArrayList<Int>()
            val defeats = ArrayList<Int>()
            val survive = IntArray(3)
            var summoned = 0
            for (seed in 1001L until 1001L + seeds) {
                val b = Battle(BattleConfig(Mode.ENDLESS, seed, 1f, playerBonuses = bonus),
                    commanderEnabled = variant != "bez")
                b.playerAi = Ai(b, 0).also { if (variant == "wrog") it.commanderOverride = false }
                var steps = 0
                var used = false
                while (!b.over && b.wave < 60 && steps < 90_000) {
                    b.step()
                    b.fx.count = 0
                    if (b.player.commanderCd != 0f) used = true
                    steps++
                }
                if (used) summoned++
                waves.add(b.wave)
                if (b.over) defeats.add(b.wave)
                for ((i, t) in intArrayOf(40, 50, 60).withIndex()) {
                    if (b.wave > t || (b.wave == t && !b.over)) survive[i]++
                }
            }
            val d = defeats.sorted()
            val median = when {
                d.isEmpty() -> Double.NaN
                d.size % 2 == 1 -> d[d.size / 2].toDouble()
                else -> (d[d.size / 2 - 1] + d[d.size / 2]) / 2.0
            }
            val mean = waves.sum().toDouble() / seeds
            println("ENDLESS_COMMANDER variant=$variant mean=${fmt(mean)} median=${fmt(median)}" +
                " w40=${survive[0]} w50=${survive[1]} w60=${survive[2]} summoned=$summoned/$seeds")
            md.append("| $variant | ${fmt(mean)} | ${fmt(median)} | ${survive[0]}/$seeds | ${survive[1]}/$seeds | " +
                "${survive[2]}/$seeds | $summoned/$seeds |\n")
        }
        val tag = System.getenv("BALANCE_TAG")?.let { "-$it" } ?: ""
        File(dir, "endless-commander$tag.md").writeText(md.toString())
    }

    /** Jeden pojedynek wariantów: bez globalnej losowości przy wskazaniu strony. */
    private fun variantDuel(era: Int, seed: Long, first: Int, second: Int): Battle {
        val cfg = BattleConfig(Mode.SKIRMISH, seed, 1f, startEra = era, maxEra = era,
            playerBonuses = Bonuses(eliteUnlocked = true, rallyUnlocked = true))
        val b = Battle(cfg)
        check(b.chooseVariant(first))
        b.enemy.eraVariant = second
        // Lustro: przeciwnik nie losuje osobowości z seeda, obie strony grają tak samo.
        b.ai = Ai(b, 1, AiPersonality.BALANCED)
        b.playerAi = Ai(b, 0, AiPersonality.BALANCED)
        val limit = (30f * 60f / Battle.STEP).toInt()
        var ticks = 0
        while (!b.over && ticks < limit) {
            b.step()
            b.fx.count = 0
            ticks++
        }
        return b
    }

    /**
     * Raport A/B: 200 seedów × dwa kierunki, a potem dwa etapy kampanii × dwa warianty.
     * Uruchomienie tylko z BALANCE_RUN=true; plik build/balance/variants.md.
     */
    @Test
    fun variantReport() {
        val runs = (System.getenv("BALANCE_VARIANT_RUNS")?.toIntOrNull() ?: 200).coerceAtLeast(200)
        val dir = File(System.getenv("BALANCE_OUT") ?: "build/balance")
        dir.mkdirs()
        val out = StringBuilder("# Raport wariantów epok\n\n")
            .append("Seedów na epokę: $runs, z zamianą stron (łącznie ${runs * 2} bitew).\n\n")
            .append("| Epoka | Wygrane A | Wygrane B | Remisy/paty | Różnica A–B [pp] |\n")
            .append("|---|---:|---:|---:|---:|\n")
        // Sekcje kampanii po tabeli zbiorczej, żeby wiersze epok nie trafiały do cudzej tabeli.
        val campaign = StringBuilder()
        for (style in arrayOf(EraArt.STEAM, EraArt.SILICON)) {
            val era = Defs.eraOf(style)
            var winsA = 0
            var winsB = 0
            var stalemates = 0
            for (seed in 0 until runs) {
                for (swap in 0..1) {
                    val a = if (swap == 0) 0 else 1
                    val b = variantDuel(era, 200_003L + seed * 997L + era * 11_113L, a, 1 - a)
                    when {
                        !b.over -> stalemates++
                        b.winner == 0 && a == 0 -> winsA++
                        b.winner == 1 && a == 1 -> winsA++
                        else -> winsB++
                    }
                }
            }
            val matches = (runs * 2).toDouble()
            val diff = kotlin.math.abs(winsA - winsB) * 100.0 / matches
            out.append("| ${style.name} | $winsA | $winsB | $stalemates | ${fmt(diff)} |\n")
            println("VARIANT ${style.name}: A=$winsA B=$winsB pats=$stalemates diff=${fmt(diff)}pp")
            assertEquals("Remisy/paty w epoce ${style.name}", 0, stalemates)
            assertTrue("Warianty ${style.name}: różnica ${fmt(diff)} pp > 10 pp", diff <= 10.0)

            campaign.append("\n## Kampania ${style.name}\n\n")
                .append("| Etap | Wariant | N | Zwycięstwa | Odsetek [%] | Timeout |\n")
                .append("|---:|---:|---:|---:|---:|---:|\n")
            val stages = Campaign.stages.filter { it.startEra == era && it.maxEra >= era }.take(2)
            for (stage in stages) {
                for (variant in 0..1) {
                    var wins = 0
                    var timeout = 0
                    for (seed in 0 until runs) {
                        val cfg = Campaign.config(stage,
                            Bonuses(eliteUnlocked = true, rallyUnlocked = true),
                            800_003L + seed * 997L + stage.index * 11_113L)
                        val battle = Battle(cfg)
                        check(battle.chooseVariant(variant))
                        battle.playerAi = Ai(battle, 0, AiPersonality.BALANCED)
                        var ticks = 0
                        val limit = (30f * 60f / Battle.STEP).toInt()
                        while (!battle.over && ticks < limit) {
                            battle.step()
                            battle.fx.count = 0
                            ticks++
                        }
                        if (!battle.over) timeout++
                        if (battle.winner == 0 && battle.over) wins++
                    }
                    val pct = wins * 100.0 / runs
                    campaign.append("| ${stage.index + 1} | $variant | $runs | $wins | ${fmt(pct)} | $timeout |\n")
                    println("VARIANT CAMPAIGN stage=${stage.index + 1} v=$variant wins=${fmt(pct)}%")
                    assertEquals("Timeout etapu ${stage.index + 1} wariant $variant", 0, timeout)
                }
            }
        }
        File(dir, "variants.md").writeText(out.append(campaign).toString())
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
