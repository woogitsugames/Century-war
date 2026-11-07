package com.centurywar.meta

import com.centurywar.game.Ai
import com.centurywar.game.Battle
import com.centurywar.game.Campaign
import com.centurywar.game.CampaignDifficulty
import com.centurywar.game.Orders
import com.centurywar.game.Research
import com.centurywar.game.Weather
import com.centurywar.game.Weekly
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Audyt 73 (obszar C, ekonomia): krzywa medali i wydatków przeciętnego gracza w godzinach 1–20.
 * TYLKO LICZENIE — niczego w grze nie zmienia. Uruchamiany na żądanie:
 *
 * ECONOMY_REPORT=true ./gradlew :app:testDebugUnitTest --tests '*EconomyCurveReport.curve*'
 * ECONOMY_REPORT=true ./gradlew :app:testDebugUnitTest --tests '*EconomyCurveReport.researchFeel*'
 *
 * Wzory nagród są wzięte z kodu gry (BattleScreen.settleRewards, LostBattle.lossReward,
 * Missions, Weekly, Orders.medalsFor, Achievements.all, WelcomeBack). Zachowanie gracza
 * (czasy etapów, odsetki wygranych, ile misji odbiera) to ZAŁOŻENIA z raportów
 * (BALANCE.md „minutesNow”, d37 „realny gracz”, noc-5, d37 tydzień) — wypisane w raporcie.
 * Wynik: app/build/balance/economy-curve.md i economy-research.md.
 */
class EconomyCurveReport {
    private fun on() = assumeTrue(System.getenv("ECONOMY_REPORT") == "true")
    private fun f(v: Double, d: Int = 0) = String.format(Locale.ROOT, "%.${d}f", v)

    // ---------------------------------------------------------------- wzory z gry
    /** BattleScreen.settleRewards, gałąź wygranej kampanii. */
    private fun campaignWin(stage: Int, stars: Double, newStars: Double, mul: Float = 1f) =
        ((20 + stage * 6 + stars * 5 + newStars * 12) * mul)
    /** LostBattle.lossReward, kampania: pełny „wysiłek” (≥ 4 min) i ok. 70 zabitych. */
    private fun campaignLoss(stage: Int, mul: Float = 1f) = ((5 + stage * 2) + minOf(20, 70 / 8)) * mul.toDouble()
    private fun endless(wave: Int) = wave * 3 + (wave * 8) / 10

    // ---------------------------------------------------------------- założenia gracza
    private val minutesNow = doubleArrayOf( // BalanceReport.MedalPace.minutesNow (oczekiwany czas, z porażkami)
        15.9, 8.8, 8.8, 9.2, 8.8, 8.7, 10.2, 13.5, 11.8, 12.4, 13.5, 13.1,
        11.5, 17.0, 18.4, 15.7, 15.2, 15.6,
    )
    private val starsNow = doubleArrayOf(
        1.37, 1.53, 1.63, 1.77, 1.71, 1.88, 1.39, 1.50, 1.70, 1.79, 1.83, 1.76,
        1.62, 1.48, 1.51, 1.47, 1.78, 1.93,
    )
    /** d37 „realny gracz”: 19–27 Σ 129 min, 28–36 Σ 160 min (po K1). */
    private fun stageMinutes(i: Int) = when {
        i < minutesNow.size -> minutesNow[i]
        i < 27 -> 129.0 / 9
        else -> 160.0 / 9
    }
    private fun stageStars(i: Int) = if (i < starsNow.size) starsNow[i] else 1.6
    private fun winRate(i: Int) = when {
        i < 6 -> 0.85
        i < 18 -> 0.70
        i < 27 -> 0.72
        else -> 0.52
    }

    private class Params(
        val minutesPerDay: Double = 45.0,
        val hours: Int = 20,
        val dailyChallengeWin: Double = 0.55,
        val dailyMissionsClaimed: Double = 2.0,
        val weeklyMissionsClaimed: Double = 2.0,
        val endlessEveryDays: Int = 3,
        val hardMinutesMul: Double = 1.6,
        /** Co ile dni gracz robi przerwę ≥ 3 dni (powitanie po przerwie). */
        val breakEvery: Int = 14,
    )

    private class Row(
        val hour: Int,
        val earned: DoubleArray,
        val spent: Double,
        val balance: Double,
        val levels: Int,
        val purchases: Int,
        val longestWait: Double,
        val nextCost: Int,
        val stage: String,
        val achievements: Int,
    )

    private enum class Src(val label: String) {
        CAMPAIGN("Kampania (1. wygrane + porażki)"), HARD("Trudny (korony)"), LOGIN("Logowanie"),
        WELCOME("Powitanie po przerwie"), DAILY_MISSIONS("Misje dzienne"), WEEKLY_MISSIONS("Misje tygodniowe"),
        DAILY("Wyzwanie dnia"), WEEKLY("Oblężenie tygodnia"), ENDLESS("Endless"), ORDERS("Rozkazy sztabu"),
        ACHIEVEMENTS("Osiągnięcia"),
    }

    private inner class Model(val p: Params) {
        val defs = Research.defs
        val levels = IntArray(defs.size)
        var medals = 0.0
        var t = 0.0
        var lastPurchase = 0.0
        val purchaseTimes = ArrayList<Double>()
        val earned = Array(p.hours + 1) { DoubleArray(Src.values().size) }
        var spentByHour = DoubleArray(p.hours + 1)
        // Postęp
        var stage = 0 // następny etap Normalny
        var hardStage = 0
        var stageClock = 0.0
        val stars = DoubleArray(Campaign.stages.size)
        val orderStars = IntArray(Orders.COUNT)
        var wins = 0.0; var battles = 0.0; var kills = 0.0; var bestWave = 0
        var dailyDays = 0; var evolutions = 0.0; var turrets = 0.0
        var rain = 0; var wind = 0; var night = 0; var commander = 0
        val unlocked = HashSet<String>()
        val events = ArrayList<String>()

        fun hourOf(time: Double) = minOf(p.hours, (time / 60.0).toInt())
        fun add(src: Src, v: Double) {
            if (v <= 0.0) return
            medals += v
            earned[hourOf(t)][src.ordinal] += v
        }
        fun cheapest(): Int = defs.indices.filter { levels[it] < defs[it].max }
            .minWithOrNull(compareBy<Int> { defs[it].cost(levels[it]) }.thenBy { it }) ?: -1
        fun spend() {
            while (true) {
                val i = cheapest()
                if (i < 0) return
                val c = defs[i].cost(levels[i])
                if (c > medals) return
                medals -= c
                levels[i]++
                spentByHour[hourOf(t)] += c.toDouble()
                purchaseTimes += t
                if (defs[i].max == 1) events += "${f(t / 60.0, 1)} h: kupione ${defs[i].key} ($c)"
            }
        }
        fun battle(k: Double, won: Double, minutes: Double) {
            battles += 1; wins += won; kills += k; evolutions += 2.0 * minutes / 10.0; turrets += 3.0
            t += minutes
        }
        fun metric(m: AchievementMetric): Long? = when (m) {
            AchievementMetric.WINS -> wins.toLong()
            AchievementMetric.BATTLES -> battles.toLong()
            AchievementMetric.STARS -> stars.sum().toLong()
            AchievementMetric.STAGES -> stage.toLong()
            AchievementMetric.WAVES -> bestWave.toLong()
            AchievementMetric.KILLS -> kills.toLong()
            AchievementMetric.RESEARCH_LEVELS -> levels.sum().toLong()
            AchievementMetric.MAXED_RESEARCH -> defs.indices.count { levels[it] >= defs[it].max }.toLong()
            AchievementMetric.DAILY_DAYS -> dailyDays.toLong()
            AchievementMetric.EVOLUTIONS -> evolutions.toLong()
            AchievementMetric.TURRETS_BUILT -> turrets.toLong()
            AchievementMetric.RAIN_WINS -> rain.toLong()
            AchievementMetric.WIND_WINS -> wind.toLong()
            AchievementMetric.NIGHT_WINS -> night.toLong()
            AchievementMetric.COMMANDER_SUMMONED -> commander.toLong()
            AchievementMetric.HARD_CAMPAIGN -> hardStage.toLong()
            AchievementMetric.ORDERS_PERFECT -> orderStars.count { it >= 3 }.toLong()
            AchievementMetric.ORDERS_COMPLETED -> orderStars.count { it >= 1 }.toLong()
            AchievementMetric.ORDER_STARS -> orderStars.sum().toLong()
            else -> null // umiejętność / przypadek — poza modelem
        }
        fun achievements() {
            for (a in Achievements.all) {
                if (a.id in unlocked) continue
                val v = metric(a.metric) ?: continue
                if (v >= a.threshold) { unlocked += a.id; add(Src.ACHIEVEMENTS, a.medalReward.toDouble()) }
            }
        }
        val snapshot = IntArray(defs.size)
        fun after() {
            achievements(); spend()
            for (i in levels.indices) while (snapshot[i] < levels[i]) { levelHistory += t to i; snapshot[i]++ }
            val h = hourOf(t)
            stageAt[h] = (if (stage < Campaign.stages.size) "N $stage" else "N 36, T $hardStage") +
                ", R ${orderStars.count { it > 0 }}"
            unlockedAt[h] = unlocked.size
        }

        fun run(): List<Row> {
            val end = p.hours * 60.0
            var day = 0
            var streak = 0
            while (t < end) {
                day++
                val dayEnd = t + p.minutesPerDay
                val gap = day > 1 && (day - 1) % 7 == 0 // dzień przerwy po 6 dniach gry
                val longBreak = day > 1 && (day - 1) % p.breakEvery == 0
                streak = when { day == 1 -> 1; gap || longBreak -> maxOf(1, streak - 2); else -> minOf(7, streak + 1) }
                add(Src.LOGIN, Missions.loginRewardFor(streak, stars.sum().toInt()).toDouble())
                if (longBreak) add(Src.WELCOME, WelcomeBack.BONUS.toDouble())
                after()
                if (day >= 2) { // wyzwanie dnia: 1 próba rankingowa
                    val w = p.dailyChallengeWin
                    add(Src.DAILY, w * (minOf(90, 18_000 / 350) + 10) + (1 - w) * (minOf(90, 2_500 / 350) + 10))
                    dailyDays++; battle(60.0, w, 8.0); after()
                }
                if (day >= 3 && day % 7 == 3) { // Oblężenie tygodnia: mediana fali 12 (d37), brąz w ok. 60% tygodni
                    val wave = 12
                    add(Src.WEEKLY, ((wave * 3 + wave * 8 / 10) / 2).toDouble() + 0.6 * Weekly.medalReward(0))
                    battle(wave * 8.0, 0.0, 6.0); after()
                }
                ordersAttempt(); after()
                if (day >= 3 && day % p.endlessEveryDays == 0) {
                    val wave = minOf(50, 18 + levels.sum() / 3)
                    bestWave = maxOf(bestWave, wave)
                    add(Src.ENDLESS, endless(wave).toDouble())
                    battle(wave * 8.0, 0.0, minOf(25.0, wave * 0.42)); after()
                }
                while (t < dayEnd && t < end) campaignStep(dayEnd)
                add(Src.DAILY_MISSIONS, p.dailyMissionsClaimed * dailyMissionReward(false))
                if (day % 7 == 0) add(Src.WEEKLY_MISSIONS, p.weeklyMissionsClaimed * dailyMissionReward(true))
                after()
                t = maxOf(t, dayEnd)
            }
            return rows()
        }

        fun dailyMissionReward(weekly: Boolean): Double {
            val types = MissionType.values()
            val avgBonus = types.sumOf { it.bonus * it.weight }.toDouble() / types.sumOf { it.weight }
            val s = stars.sum().toInt()
            return if (weekly) 34 + avgBonus * 4 + s / 3 else 10 + avgBonus * 2 + s / 12
        }

        fun ordersAttempt() {
            val campaignStars = IntArray(stars.size) { if (stars[it] > 0) 1 else 0 }
            val target = Orders.all.firstOrNull {
                Orders.unlocked(it, campaignStars, orderStars) && orderStars[it.index] < 2
            } ?: Orders.all.firstOrNull { Orders.unlocked(it, campaignStars, orderStars) && orderStars[it.index] < 3 }
                ?: return
            val old = orderStars[target.index]
            val new = if (old < 2) 2 else if (unlocked.size % 3 == 0) 3 else old // ok. co 3. próba daje 3★
            add(Src.ORDERS, Orders.medalsFor(target, old, new).toDouble())
            orderStars[target.index] = new
            battle(60.0, 0.8, 7.0)
        }

        fun campaignStep(dayEnd: Double) {
            val n = Campaign.stages.size
            val normal = stage < n
            if (!normal && hardStage >= n) { t = dayEnd; return }
            val i = if (normal) stage else hardStage
            val pWin = if (normal) winRate(i) else winRate(i) * 0.7
            val need = (if (normal) stageMinutes(i) else stageMinutes(i) * p.hardMinutesMul) + 1.5 / pWin
            val left = dayEnd - t
            if (stageClock + left < need) { stageClock += left; t = dayEnd; return }
            t += need - stageClock
            stageClock = 0.0
            val losses = 1.0 / pWin - 1.0
            battles += 1 + losses; wins += 1; kills += 70 * (1 + losses); evolutions += 2 * (1 + losses); turrets += 3 * (1 + losses)
            val st = Campaign.stages[i]
            when (st.weather) { Weather.RAIN -> rain++; Weather.WIND -> wind++; Weather.NIGHT -> night++; else -> {} }
            if (i >= Achievements.LATE_STAGE_INDEX) commander = 1
            if (normal) {
                val s = stageStars(i)
                add(Src.CAMPAIGN, campaignWin(i, s, s) + losses * campaignLoss(i))
                stars[i] = s
                stage++
                if (stage % 6 == 0 || stage == n) events += "${f(t / 60.0, 1)} h: ukończony etap $stage (Normalny)"
            } else {
                val mul = CampaignDifficulty.HARD.medalMultiplier
                val s = stageStars(i)
                val newStars = 0.25
                add(Src.HARD, campaignWin(i, s, newStars, mul) + losses * campaignLoss(i, mul))
                hardStage++
                if (hardStage % 6 == 0) events += "${f(t / 60.0, 1)} h: korona nr $hardStage (Trudny)"
            }
            after()
        }

        fun rows(): List<Row> {
            val out = ArrayList<Row>()
            var balance = 0.0
            // Saldo i poziomy liczone ponownie z historii zakupów (koszty w kolejności zakupu).
            val sortedTimes = purchaseTimes
            for (h in 0 until p.hours) {
                val e = earned[h]
                balance += e.sum() - spentByHour[h]
                val inHour = sortedTimes.filter { it >= h * 60.0 && it < (h + 1) * 60.0 }
                var wait = 0.0
                val prev = sortedTimes.lastOrNull { it < h * 60.0 } ?: 0.0
                val pts = listOf(prev) + inHour + listOf((h + 1) * 60.0)
                for (k in 1 until pts.size) wait = maxOf(wait, pts[k] - pts[k - 1])
                val lv = levelsAt((h + 1) * 60.0)
                out += Row(h + 1, e, spentByHour[h], balance, lv.sum(), inHour.size, wait,
                    nextCostAt((h + 1) * 60.0), stageAt[h] ?: "", unlockedAt[h] ?: 0)
            }
            return out
        }
        val levelHistory = ArrayList<Pair<Double, Int>>()
        fun levelsAt(time: Double): IntArray {
            val lv = IntArray(defs.size)
            for ((tt, i) in levelHistory) if (tt < time) lv[i]++
            return lv
        }
        fun nextCostAt(time: Double): Int {
            val lv = levelsAt(time)
            return defs.indices.filter { lv[it] < defs[it].max }.minOfOrNull { defs[it].cost(lv[it]) } ?: 0
        }
        val stageAt = HashMap<Int, String>()
        val unlockedAt = HashMap<Int, Int>()
    }

    @Test
    fun curve() {
        on()
        val sb = StringBuilder()
        sb.append("# Krzywa medali — model przeciętnego gracza (audyt 73)\n\n")
        val total = Research.defs.sumOf { d -> (0 until d.max).sumOf { d.cost(it) } }
        sb.append("Suma kosztów wszystkich badań: **$total** medali (${Research.defs.sumOf { it.max }} poziomów).\n\n")
        sb.append("| Badanie | Poziomy | Koszty: pierwszy / ostatni | Suma | Efekt na poziom |\n|---|---:|---|---:|---|\n")
        val effect = mapOf("r_elite" to "odblokowanie elity", "r_rally" to "odblokowanie zrywu",
            "r_income" to "+6% dochodu", "r_unitDmg" to "+4% obrażeń", "r_unitHp" to "+4% HP",
            "r_startGold" to "+20 złota na start (×skala epoki)", "r_baseHp" to "+8% HP bazy",
            "r_turretDmg" to "+6% obrażeń wież", "r_train" to "+5% tempa szkolenia",
            "r_xp" to "+5% XP", "r_cap" to "+1 limit armii (12→18)", "r_cd" to "−5% odnowienia zdolności",
            "r_slots" to "+1 slot wieży (2→4)")
        for (d in Research.defs) sb.append("| ${d.key} | ${d.max} | ${d.cost(0)} / ${d.cost(d.max - 1)} | " +
            "${(0 until d.max).sumOf { d.cost(it) }} | ${effect[d.key]} |\n")

        for ((name, params) in listOf("45 min/dzień (główny)" to Params(), "30 min/dzień" to Params(minutesPerDay = 30.0),
            "90 min/dzień" to Params(minutesPerDay = 90.0))) {
            val m = Model(params)
            val rows = m.run()
            sb.append("\n## Profil: $name\n\n")
            sb.append("| h | Kamp. | Trudny | Login+powit. | Misje d/t | Wyzw. dnia | Tydz. | Endless | Rozkazy | Osiągn. | Σ przychód | Wydane | Saldo | Poziomy | Zakupy | Najdł. czekanie [min] | Nast. koszt | Postęp | Osiągn. (szt.) |\n")
            sb.append("|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---:|\n")
            var cum = 0.0
            for (r in rows) {
                val e = r.earned
                fun s(vararg x: Src) = f(x.sumOf { e[it.ordinal] })
                cum += e.sum()
                sb.append("| ${r.hour} | ${s(Src.CAMPAIGN)} | ${s(Src.HARD)} | ${s(Src.LOGIN, Src.WELCOME)} | " +
                    "${s(Src.DAILY_MISSIONS, Src.WEEKLY_MISSIONS)} | ${s(Src.DAILY)} | ${s(Src.WEEKLY)} | ${s(Src.ENDLESS)} | " +
                    "${s(Src.ORDERS)} | ${s(Src.ACHIEVEMENTS)} | ${f(e.sum())} | ${f(r.spent)} | ${f(r.balance)} | ${r.levels} | " +
                    "${r.purchases} | ${f(r.longestWait)} | ${r.nextCost} | ${r.stage} | ${r.achievements} |\n")
            }
            val totals = DoubleArray(Src.values().size)
            for (r in rows) for (i in totals.indices) totals[i] += r.earned[i]
            val sum = totals.sum()
            sb.append("\nUdział źródeł w 20 h (Σ ${f(sum)}): ")
            sb.append(Src.values().filter { totals[it.ordinal] > 0 }.sortedByDescending { totals[it.ordinal] }
                .joinToString("; ") { "${it.label} ${f(totals[it.ordinal])} (${f(100 * totals[it.ordinal] / sum)}%)" })
            sb.append("\n\nZdarzenia: ").append(m.events.joinToString("; ")).append("\n")
            val modeled = Achievements.all.filter { m.metric(it.metric) != null }
            val skipped = Achievements.all.filter { m.metric(it.metric) == null }
            sb.append("\nOsiągnięcia w modelu: ${m.unlocked.size}/${modeled.size} (z ${Achievements.all.size}); " +
                "poza modelem (umiejętność/przypadek) ${skipped.size} szt. za ${skipped.sumOf { it.medalReward }} medali; " +
                "wszystkie razem ${Achievements.all.sumOf { it.medalReward }}.\n")
        }

        // Medale na minutę w powtarzalnych źródłach.
        sb.append("\n## Medale na minutę gry (źródła powtarzalne)\n\n")
        sb.append("| Źródło | Medale / próba | Czas próby [min] | Medale / min |\n|---|---:|---:|---:|\n")
        fun line(n: String, m: Double, min: Double) { sb.append("| $n | ${f(m)} | ${f(min, 1)} | ${f(m / min, 1)} |\n") }
        for (i in intArrayOf(5, 11, 17, 26, 35)) {
            val med = stageMinutes(i) * winRate(i) // mediana wygranej ≈ oczek. × p (przybliżenie)
            line("Powtórka etapu ${i + 1} na 3★ (Normalny, p=${winRate(i)})", winRate(i) * campaignWin(i, 3.0, 0.0) +
                (1 - winRate(i)) * campaignLoss(i), med)
            line("Powtórka etapu ${i + 1} na 3★ (Łatwy, p≈0,9)", 0.9 * campaignWin(i, 3.0, 0.0, 0.75f) +
                0.1 * campaignLoss(i, 0.75f), med * 0.85)
        }
        for (w in intArrayOf(15, 25, 40, 53)) line("Endless do fali $w", endless(w).toDouble(), w * 0.42)
        for (preset in 0..3) line("Potyczka rankingowa, preset $preset (wygrana)", 15.0 + preset * 14, 8.0)
        line("Wyzwanie dnia (1×/dzień, wygrana)", (minOf(90, 18_000 / 350) + 10).toDouble(), 8.0)
        line("Oblężenie tygodnia (fala 12, 1×/tydz. z brązem)", ((12 * 3 + 9) / 2 + 25).toDouble(), 6.0)

        val out = File("build/balance").apply { mkdirs() }
        File(out, "economy-curve.md").writeText(sb.toString())
        println(sb)
    }

    // ---------------------------------------------------------------- pomiar: czy badania czuć
    @Test
    fun researchFeel() {
        on()
        val runs = System.getenv("ECONOMY_RUNS")?.toIntOrNull() ?: 24
        val extra = System.getenv("ECONOMY_EXTRA")?.toIntOrNull() ?: 500
        val stages = (System.getenv("ECONOMY_STAGES") ?: "12,28").split(",").map { it.trim().toInt() - 1 }
        // Budżet „bazowy” przed etapem: cheapest-first z budżetem z gpt-200 (medale-czas).
        val baseBudget = mapOf(11 to 1442, 27 to 5474)
        val sb = StringBuilder("# Czy badania czuć? (audyt 73, N=$runs, +$extra medali w jedno badanie)\n\n")
        for (si in stages) {
            val budget = baseBudget[si] ?: 2000
            val base = spendCheapest(IntArray(Research.defs.size), budget)
            sb.append("## Etap ${si + 1} (baza: $budget medali cheapest-first = ${base.sum()} poziomów: ")
                .append(Research.defs.indices.filter { base[it] > 0 }.joinToString(", ") { "${Research.defs[it].key} ${base[it]}" })
                .append(")\n\n| Wariant | Poziomy wariantu | Wydane | Wygrane | Δ pp | Mediana wygranej [min] | Śr. ★ |\n|---|---|---:|---:|---:|---:|---:|\n")
            val variants = ArrayList<Triple<String, IntArray, Int>>()
            variants += Triple("baza", base, 0)
            variants += Triple("+$extra cheapest-first", spendCheapest(base.copyOf(), extra).let { it }, extra)
            for (k in Research.defs.indices) {
                val lv = base.copyOf()
                var left = extra
                var spent = 0
                while (lv[k] < Research.defs[k].max && Research.defs[k].cost(lv[k]) <= left) {
                    val c = Research.defs[k].cost(lv[k]); left -= c; spent += c; lv[k]++
                }
                if (spent == 0) { sb.append("| ${Research.defs[k].key} | max albo > $extra | 0 | — | — | — | — |\n"); continue }
                variants += Triple(Research.defs[k].key, lv, spent)
            }
            var baseWin = 0.0
            for ((name, lv, spent) in variants) {
                var w = 0; val mins = ArrayList<Double>(); var starSum = 0
                for (trial in 0 until runs) {
                    val seed = 73_000_000L + si * 10_007L + trial * 997L
                    val b = Battle(Campaign.config(Campaign.stages[si], Research.bonuses(lv), seed))
                    b.playerAi = Ai(b, 0)
                    var steps = 0
                    val max = (30f * 60f / Battle.STEP).toInt()
                    while (!b.over && steps < max) { b.step(); b.fx.count = 0; steps++ }
                    if (b.over && b.winner == 0) { w++; mins += b.time / 60.0; starSum += b.stars() }
                }
                val rate = 100.0 * w / runs
                if (name == "baza") baseWin = rate
                val diff = Research.defs.indices.filter { lv[it] != base[it] }
                    .joinToString(", ") { "${Research.defs[it].key} ${base[it]}→${lv[it]}" }
                val med = mins.sorted().let { if (it.isEmpty()) Double.NaN else it[it.size / 2] }
                sb.append("| $name | ${diff.ifEmpty { "—" }} | $spent | ${f(rate)}% | ${f(rate - baseWin)} | ${f(med, 1)} | " +
                    "${if (w == 0) "—" else f(starSum.toDouble() / w, 2)} |\n")
            }
            sb.append("\n")
        }
        val out = File("build/balance").apply { mkdirs() }
        File(out, "economy-research.md").writeText(sb.toString())
        println(sb)
    }

    private fun spendCheapest(levels: IntArray, budget: Int): IntArray {
        val defs = Research.defs
        var medals = budget
        while (true) {
            val idx = defs.indices.filter { levels[it] < defs[it].max }
                .minWithOrNull(compareBy<Int> { defs[it].cost(levels[it]) }.thenBy { it }) ?: break
            val c = defs[idx].cost(levels[idx])
            if (c > medals) break
            medals -= c; levels[idx]++
        }
        return levels
    }
}
