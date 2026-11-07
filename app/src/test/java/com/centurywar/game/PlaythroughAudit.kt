package com.centurywar.game

import com.centurywar.meta.LostBattle
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Audyt 71: symulacja pełnego przejścia kampanii (etapy 1–36 po kolei) przez trzy profile graczy,
 * a po kampanii kawałek Endless, potyczki i Rozkazów. Tylko pomiar — nic nie zmienia w grze.
 *
 * Każdy „gracz” gra etapy po kolei, powtarza etap do wygranej (najwyżej [maxAttempts] prób; potem
 * liczony jako „utknął” i idzie dalej), zbiera medale jak w grze (wygrana: 20 + 6·etap + 5·★ +
 * 12·nowe ★; porażka: [LostBattle.lossReward]) plus medale „z czasu gry” (misje, logowanie:
 * [timeMedals] na 45 min, jak `medale-czas` w BalanceReport) i od razu wydaje je na najtańszy
 * następny poziom badań. Umiejętność gracza = czas reakcji: AI gracza myśli co 0,45–0,75 s,
 * profil mnoży tę przerwę przez [reaction] (początkujący wolniej reaguje i spóźnia decyzje).
 *
 * PLAYTHROUGH_RUN=true ./gradlew :app:testDebugUnitTest --tests '*PlaythroughAudit*'
 * Zmienne: PLAYTHROUGH_PLAYERS (domyślnie 8), PLAYTHROUGH_PROFILES (np. "dobry"),
 * PLAYTHROUGH_STAGE_TO (domyślnie 36), PLAYTHROUGH_EXTRAS=false (bez Endless/potyczki/Rozkazów), PT_REACT_BEGINNER/MID/GOOD (mnożniki reakcji).
 * Wyniki: build/playthrough/.
 */
class PlaythroughAudit {
    @Before fun onlyOnRequest() = assumeTrue(System.getenv("PLAYTHROUGH_RUN") == "true")

    private class Skill(val name: String, val reaction: Float, val timeMedals: Double, val maxAttempts: Int)

    private val skills = listOf(
        Skill("poczatkujacy", envF("PT_REACT_BEGINNER", 2.5f), 100.0, 12),
        Skill("sredni", envF("PT_REACT_MID", 1.6f), 140.0, 12),
        Skill("dobry", envF("PT_REACT_GOOD", 1.0f), 140.0, 12),
    )

    private fun envF(name: String, def: Float) = System.getenv(name)?.toFloatOrNull() ?: def

    private class Outcome(val won: Boolean, val minutes: Double, val stars: Int, val timeout: Boolean,
                          val battle: Battle)

    /** Bitwa z AI gracza o spowolnionej reakcji. */
    private fun play(cfg: BattleConfig, reaction: Float, maxMinutes: Float = 30f, maxWave: Int = Int.MAX_VALUE): Outcome {
        val battle = Battle(cfg)
        val ai = Ai(battle, 0)
        battle.playerAi = ai
        val maxSteps = (maxMinutes * 60f / Battle.STEP).toInt()
        var steps = 0
        var prev = ai.thinkTimer
        while (!battle.over && steps < maxSteps && battle.wave < maxWave) {
            battle.step()
            battle.fx.count = 0
            val cur = ai.thinkTimer
            if (reaction != 1f && cur > prev + 1e-4f) ai.thinkTimer = cur * reaction
            prev = ai.thinkTimer
            steps++
        }
        val won = battle.over && battle.winner == 0
        return Outcome(won, battle.time / 60.0, if (won) battle.stars() else 0, !battle.over, battle)
    }

    private class Player(val levels: IntArray = IntArray(Research.defs.size), var medals: Double = 0.0,
                         var minutes: Double = 0.0) {
        fun spend() {
            val defs = Research.defs
            while (true) {
                val idx = defs.indices.filter { levels[it] < defs[it].max }
                    .minWithOrNull(compareBy<Int> { defs[it].cost(levels[it]) }.thenBy { it }) ?: return
                val cost = defs[idx].cost(levels[idx])
                if (cost > medals) return
                medals -= cost
                levels[idx]++
            }
        }
    }

    private class StageRow(val skill: String, val stage: Int) {
        val attempts = ArrayList<Outcome>()
        val passAttempts = ArrayList<Int>()
        val stageMinutes = ArrayList<Double>()
        val cumulative = ArrayList<Double>()
        val levels = ArrayList<Int>()
        val passStars = ArrayList<Int>()
        var stuck = 0
        val hard = ArrayList<Outcome>()
        val styleOn = ArrayList<Boolean>()
        val styleOff = ArrayList<Boolean>()
    }

    private fun median(v: List<Double>): Double {
        if (v.isEmpty()) return Double.NaN
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }

    private fun f(v: Double, d: Int = 1) = String.format(Locale.US, "%.${d}f", v)

    @Test
    fun fullPlaythrough() {
        val players = (System.getenv("PLAYTHROUGH_PLAYERS")?.toIntOrNull() ?: 8).coerceAtLeast(1)
        val only = System.getenv("PLAYTHROUGH_PROFILES")?.split(',')?.map { it.trim() }?.toSet()
        val stageTo = (System.getenv("PLAYTHROUGH_STAGE_TO")?.toIntOrNull() ?: 36).coerceIn(1, 36)
        val extras = System.getenv("PLAYTHROUGH_EXTRAS") != "false"
        val out = File(System.getenv("PLAYTHROUGH_OUT") ?: "build/playthrough").also { it.mkdirs() }
        val tag = System.getenv("PLAYTHROUGH_PROFILES")?.let { "-" + it.replace(",", "-") } ?: ""
        val rows = LinkedHashMap<Pair<String, Int>, StageRow>()
        val extraCsv = StringBuilder("profile,player,mode,item,result,minutes,detail\n")
        val raw = StringBuilder("profile,player,stage,attempt,difficulty,won,minutes,stars,timeout,levels,medals_after\n")

        for (skill in skills) {
            if (only != null && skill.name !in only) continue
            val finals = ArrayList<Player>()
            for (p in 0 until players) {
                val pl = Player()
                for (si in 0 until stageTo) {
                    val stage = Campaign.stages[si]
                    val row = rows.getOrPut(skill.name to si + 1) { StageRow(skill.name, si + 1) }
                    row.levels.add(pl.levels.sum())
                    var attempt = 0
                    var spent = 0.0
                    var passed = false
                    // Etap 1 za pierwszym razem to samouczek (łagodniejszy), jak w grze.
                    while (attempt < skill.maxAttempts && !passed) {
                        val seed = 246_813_579L + p * 1_000_003L + si * 10_007L + attempt * 997L
                        val tutorial = si == 0 && attempt == 0
                        val cfg = Campaign.config(stage, Research.bonuses(pl.levels), seed, tutorial = tutorial)
                        val o = play(cfg, skill.reaction)
                        row.attempts.add(o)
                        val gain = if (o.won) (20 + si * 6 + o.stars * 5 + o.stars * 12).toDouble()
                            else LostBattle.lossReward(cfg, o.battle, false).toDouble()
                        val minutes = o.minutes + 1.5
                        pl.medals += gain + skill.timeMedals * minutes / 45.0
                        pl.minutes += minutes
                        spent += minutes
                        raw.append("${skill.name},$p,${si + 1},$attempt,NORMAL,${o.won},${f(o.minutes, 2)},${o.stars},${o.timeout},${pl.levels.sum()},${f(pl.medals, 0)}\n")
                        pl.spend()
                        attempt++
                        if (o.won) {
                            passed = true
                            row.passStars.add(o.stars)
                        }
                    }
                    if (!passed) row.stuck++
                    row.passAttempts.add(attempt)
                    row.stageMinutes.add(spent)
                    row.cumulative.add(pl.minutes)
                    // Trudny poziom: jedna próba z tymi samymi badaniami (bez wpływu na medale).
                    val hardSeed = 913_000_000L + p * 1_000_003L + si * 10_007L
                    val hard = play(Campaign.config(stage, Research.bonuses(pl.levels), hardSeed,
                        difficulty = CampaignDifficulty.HARD), skill.reaction)
                    row.hard.add(hard)
                    raw.append("${skill.name},$p,${si + 1},0,HARD,${hard.won},${f(hard.minutes, 2)},${hard.stars},${hard.timeout},${pl.levels.sum()},\n")
                    // D68: etap z nowym stylem przeciwnika vs ten sam etap z losowym (starym) stylem.
                    if (stage.personality != null) {
                        for (k in 0 until 4) {
                            val seed = 555_000_000L + p * 1_000_003L + si * 10_007L + k * 997L
                            val base = Campaign.config(stage, Research.bonuses(pl.levels), seed)
                            row.styleOn.add(play(base, skill.reaction).won)
                            val off = BattleConfig(base.mode, base.seed, base.aiPower, base.startEra, base.maxEra,
                                base.modifiers, base.playerBonuses, base.stageIndex, base.tutorial, null,
                                base.variantsEnabled, base.weather)
                            row.styleOff.add(play(off, skill.reaction).won)
                        }
                    }
                }
                finals.add(pl)
                println("PLAYTHROUGH profile=${skill.name} player=$p total=${f(pl.minutes)}min levels=${pl.levels.sum()}")

                if (extras) {
                    val bonus = Research.bonuses(pl.levels)
                    // Endless: do fali 60 (jak EndlessBalanceReportTest).
                    for (k in 0 until 2) {
                        val seed = 1001L + p * 2 + k
                        val o = play(BattleConfig(Mode.ENDLESS, seed, 1f, playerBonuses = bonus), skill.reaction,
                            maxMinutes = 90_000 * Battle.STEP / 60f, maxWave = 60)
                        extraCsv.append("${skill.name},$p,ENDLESS,$seed,${o.battle.wave},${f(o.minutes, 2)},alive=${!o.battle.over}\n")
                    }
                    // Potyczka rankingowa: cztery poziomy siły.
                    for (power in intArrayOf(80, 105, 130, 160)) {
                        val seed = 70_000L + p * 31L + power
                        val o = play(BattleConfig(Mode.SKIRMISH, seed, power / 100f, playerBonuses = bonus,
                            eraRecruits = true), skill.reaction)
                        extraCsv.append("${skill.name},$p,SKIRMISH,$power,${if (o.won) 1 else 0},${f(o.minutes, 2)},timeout=${o.timeout}\n")
                    }
                    // Rozkazy sztabu: wszystkie, po jednej próbie (bonusy zamrożone, badania bez znaczenia).
                    for (order in Orders.all) {
                        val o = play(Orders.config(order, 80_000L + p * 101L + order.index), skill.reaction, maxMinutes = 15f)
                        val st = Orders.stars(order, o.battle)
                        extraCsv.append("${skill.name},$p,ORDERS,${order.index + 1},$st,${f(o.minutes, 2)},goal=${order.goal}\n")
                    }
                }
            }
        }

        val csv = StringBuilder("profile,stage,ai_power,personality,players,attempts,attempt_win_pct,first_try_win_pct,median_battle_min,median_win_min,mean_pass_stars,mean_attempts,stuck,median_stage_min,median_cumulative_min,mean_levels,hard_win_pct,style_on_win_pct,style_off_win_pct,timeouts\n")
        for (r in rows.values) {
            val stage = Campaign.stages[r.stage - 1]
            // Wygrana za pierwszym razem = etap przejęty w jednej próbie.
            val firstTry = r.passAttempts.count { it == 1 }
            val wins = r.attempts.count { it.won }
            csv.append("${r.skill},${r.stage},${f(stage.aiPower.toDouble(), 3)},${stage.personality ?: ""},${r.passAttempts.size},${r.attempts.size}," +
                "${f(wins * 100.0 / r.attempts.size)},${f(firstTry * 100.0 / r.passAttempts.size)}," +
                "${f(median(r.attempts.map { it.minutes }), 2)},${f(median(r.attempts.filter { it.won }.map { it.minutes }), 2)}," +
                "${f(r.passStars.average(), 2)},${f(r.passAttempts.average(), 2)},${r.stuck}," +
                "${f(median(r.stageMinutes), 2)},${f(median(r.cumulative), 1)},${f(r.levels.average(), 1)}," +
                "${f(r.hard.count { it.won } * 100.0 / r.hard.size)}," +
                "${if (r.styleOn.isEmpty()) "" else f(r.styleOn.count { it } * 100.0 / r.styleOn.size)}," +
                "${if (r.styleOff.isEmpty()) "" else f(r.styleOff.count { it } * 100.0 / r.styleOff.size)}," +
                "${r.attempts.count { it.timeout }}\n")
        }
        File(out, "playthrough-stages$tag.csv").writeText(csv.toString())
        File(out, "playthrough-raw$tag.csv").writeText(raw.toString())
        File(out, "playthrough-extras$tag.csv").writeText(extraCsv.toString())
        println("PLAYTHROUGH_REPORT ${File(out, "playthrough-stages$tag.csv").absolutePath}")
    }
}
