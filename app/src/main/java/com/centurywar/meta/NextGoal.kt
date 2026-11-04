package com.centurywar.meta

import com.centurywar.engine.L
import com.centurywar.game.Campaign
import com.centurywar.game.Defs
import com.centurywar.game.Mode
import com.centurywar.game.Research
import com.centurywar.game.ResearchDef
import com.centurywar.game.Weather
import com.centurywar.game.Weekly

/** R2-01: rodzaj „następnego celu” (wyniki bitwy i menu). */
// R10-02: HARD_STAGE dopisany na końcu (ikony celu są indeksowane przez ordinal).
internal enum class NextGoalKind { MISSIONS, RESEARCH, STAGE, WEEKLY_TIER, NONE, HARD_STAGE }

/** Cel z gotowym tekstem; [arg] to liczba misji, indeks badania, etapu albo progu (−1 = brak). */
internal class NextGoal(val kind: NextGoalKind, val text: String, val arg: Int = -1)

/**
 * R2-01: jeden najbliższy, konkretny cel gracza. Czysta logika bez Androida; wołana przy
 * rozliczeniu bitwy, przy layout(), po zmianie języka i co 10 s w menu, nigdy w każdej klatce.
 * Kolejność: odbiór misji → badanie, na które stać → następny etap → próg Oblężenia tygodnia
 * → (R10-02, po komplecie gwiazdek) pierwszy etap bez korony Trudnego.
 */
internal object NextGoals {
    val NONE = NextGoal(NextGoalKind.NONE, "")

    private val ELITE: ResearchDef = Research.defs.first { it.key == "r_elite" }

    /**
     * [skipMissions]: menu ma już czerwoną plakietkę misji, więc nie dubluje jej celem.
     */
    fun pick(p: Profile, nowMillis: Long, context: Mode?, skipMissions: Boolean = false): NextGoal {
        if (!skipMissions) {
            val claims = pendingClaims(p, nowMillis)
            if (claims > 0) return NextGoal(NextGoalKind.MISSIONS, L.f("goal179_missions", claims), claims)
        }
        val research = affordableResearch(p)
        if (research >= 0) {
            val def = Research.defs[research]
            return NextGoal(NextGoalKind.RESEARCH,
                L.f("goal179_research", L[def.key], L.plural("medals_n", def.cost(p.level(def.key)).toLong())), research)
        }
        val stage = p.unlockedStage()
        if (stage in p.stars.indices && p.stars[stage] == 0) {
            return NextGoal(NextGoalKind.STAGE, L.f("goal179_stage", stage + 1, stageNovelty(stage)), stage)
        }
        val campaignDone = p.stars.isNotEmpty() && p.stars.all { it > 0 }
        if (context == Mode.ENDLESS || campaignDone) {
            val week = Missions.utcWeek(nowMillis)
            val mask = p.weeklyClaimedFor(week)
            for (i in 0 until Weekly.TIER_COUNT) {
                if (mask and (1 shl i) != 0) continue
                return NextGoal(NextGoalKind.WEEKLY_TIER,
                    L.f("goal179_weekly", Weekly.threshold(i), L[TIER_KEYS[i]]), i)
            }
        }
        if (campaignDone) {
            for (i in p.stars.indices) {
                if (p.hardWon(i)) continue
                return NextGoal(NextGoalKind.HARD_STAGE, L.f("hard189_goal", i + 1, stageNovelty(i)), i)
            }
        }
        return NONE
    }

    private val TIER_KEYS = arrayOf("weekly_bronze", "weekly_silver", "weekly_gold")

    /** Ukończone i nieodebrane misje bieżącego dnia i tygodnia (jak plakietka w menu). */
    fun pendingClaims(p: Profile, nowMillis: Long): Int {
        val s = p.missionState
        var count = 0
        if (s.dailyDay == Missions.utcDay(nowMillis))
            for (item in s.daily) if (item.completed && !item.claimed) count++
        if (s.weeklyWeek == Missions.utcWeek(nowMillis))
            for (item in s.weekly) if (item.completed && !item.claimed) count++
        return count
    }

    /** Indeks w [Research.defs]: elita (gdy stać), potem najtańsze dostępne badanie; −1 = brak. */
    fun affordableResearch(p: Profile): Int {
        if (p.level(ELITE.key) == 0 && p.canBuy(ELITE)) return Research.defs.indexOf(ELITE)
        var best = -1
        var bestCost = Int.MAX_VALUE
        for (i in Research.defs.indices) {
            val def = Research.defs[i]
            if (!p.canBuy(def)) continue
            val cost = def.cost(p.level(def.key))
            if (cost < bestCost) { best = i; bestCost = cost }
        }
        return best
    }

    /** Pierwsza nowość etapu: modyfikator, pogoda albo zakres epok. */
    fun stageNovelty(index: Int): String {
        val s = Campaign.stages[index]
        val mod = s.modifiers.firstOrNull()
        if (mod != null) return L[mod.key]
        if (s.weather != Weather.CLEAR) return L[s.weather.key]
        return L.f("eras_range", L[Defs.eraKeys[s.startEra]], L[Defs.eraKeys[s.maxEra]])
    }
}
