package com.centurywar.meta

import com.centurywar.engine.L
import com.centurywar.game.Campaign
import com.centurywar.game.Defs
import com.centurywar.game.Mode
import com.centurywar.game.Modifier
import com.centurywar.game.Research
import com.centurywar.game.Weather
import com.centurywar.game.Weekly
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** R2-01: kolejność i treść „następnego celu”. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NextGoalTest {
    private val now = 1_793_461_200_000L
    private var savedLang = 0

    @Before fun lang() { savedLang = L.lang; L.setCode("pl") }
    @After fun restore() { L.lang = savedLang }

    private fun profile(): Profile {
        val f = File.createTempFile("next-goal-", ".json").apply { delete() }
        return Profile(f).apply { tutorialDone = true }
    }

    private fun completeAll(p: Profile) = Campaign.stages.indices.forEach { p.stars[it] = 1 }

    private fun withPendingMission(p: Profile) {
        Missions(p).refresh(now)
        val item = p.missionState.daily.first()
        item.progress = item.target
    }

    @Test
    fun priorityMissionsThenResearchThenStage() {
        val p = profile()
        p.stars[0] = 3
        p.medals = 10_000
        withPendingMission(p)
        assertEquals(NextGoalKind.MISSIONS, NextGoals.pick(p, now, Mode.CAMPAIGN).kind)
        assertEquals("Odbierz nagrody misji: 1", NextGoals.pick(p, now, Mode.CAMPAIGN).text)
        // Menu ma plakietkę misji: wybiera następny cel w kolejności.
        assertEquals(NextGoalKind.RESEARCH, NextGoals.pick(p, now, null, skipMissions = true).kind)
        p.missionState.daily.first().claimed = true
        assertEquals(NextGoalKind.RESEARCH, NextGoals.pick(p, now, Mode.CAMPAIGN).kind)
        p.medals = 0
        val stage = NextGoals.pick(p, now, Mode.CAMPAIGN)
        assertEquals(NextGoalKind.STAGE, stage.kind)
        assertEquals(1, stage.arg)
        assertTrue(stage.text, stage.text.startsWith("Następny etap 2: "))
    }

    @Test
    fun missionsFromAnotherDayDoNotCount() {
        val p = profile()
        withPendingMission(p)
        assertEquals(NextGoalKind.STAGE, NextGoals.pick(p, now + 2 * Missions.DAY_MILLIS, null).kind)
    }

    @Test
    fun eliteFirstThenCheapestResearch() {
        val p = profile()
        val elite = Research.defs.first { it.key == "r_elite" }
        p.medals = elite.cost(0)
        val goal = NextGoals.pick(p, now, Mode.CAMPAIGN)
        assertEquals(NextGoalKind.RESEARCH, goal.kind)
        assertEquals(Research.defs.indexOf(elite), goal.arg)
        assertTrue(goal.text, goal.text.contains(L["r_elite"]) && goal.text.contains("120 medali"))
        // Elita kupiona: najtańsze dostępne badanie (Rezerwy, 30 medali).
        p.levels["r_elite"] = 1
        val cheapest = Research.defs.filter { it.key != "r_elite" }.minByOrNull { it.cost(0) }!!
        val g2 = NextGoals.pick(p, now, Mode.CAMPAIGN)
        assertEquals(Research.defs.indexOf(cheapest), g2.arg)
        // Za mało medali na wszystko: brak badania.
        p.medals = cheapest.cost(0) - 1
        assertEquals(NextGoalKind.STAGE, NextGoals.pick(p, now, Mode.CAMPAIGN).kind)
    }

    @Test
    fun stageNoveltyModifierWeatherOrEras() {
        // Etap 5: modyfikator (szybkie jednostki) ma pierwszeństwo przed deszczem.
        val s4 = Campaign.stages[4]
        assertEquals(Modifier.FAST_UNITS, s4.modifiers.first())
        assertEquals(Weather.RAIN, s4.weather)
        assertEquals(L[Modifier.FAST_UNITS.key], NextGoals.stageNovelty(4))
        // Etap 14: bez modyfikatorów, za to pogoda.
        val s13 = Campaign.stages[13]
        assertTrue(s13.modifiers.isEmpty() && s13.weather != Weather.CLEAR)
        assertEquals(L[s13.weather.key], NextGoals.stageNovelty(13))
        // Etap 7: ani modyfikatora, ani pogody: zakres epok.
        val s6 = Campaign.stages[6]
        assertTrue(s6.modifiers.isEmpty() && s6.weather == Weather.CLEAR)
        assertEquals(L.f("eras_range", L[Defs.eraKeys[s6.startEra]], L[Defs.eraKeys[s6.maxEra]]),
            NextGoals.stageNovelty(6))
        val p = profile()
        for (i in 0 until 4) p.stars[i] = 2
        val goal = NextGoals.pick(p, now, Mode.CAMPAIGN)
        assertEquals("Następny etap 5: " + L[Modifier.FAST_UNITS.key], goal.text)
    }

    @Test
    fun weeklyTierOnlyInEndlessOrAfterCampaign() {
        val p = profile()
        val week = Missions.utcWeek(now)
        // Kampania nieukończona i wyniki kampanii: etap, nie tydzień.
        assertEquals(NextGoalKind.STAGE, NextGoals.pick(p, now, Mode.CAMPAIGN).kind)
        completeAll(p)
        assertEquals(NextGoalKind.WEEKLY_TIER, NextGoals.pick(p, now, null).kind)
        assertEquals("Oblężenie tygodnia: 10 fal do progu Brąz", NextGoals.pick(p, now, null).text)
        p.claimWeekly(week, Weekly.threshold(0), week)
        val silver = NextGoals.pick(p, now, null)
        assertEquals(1, silver.arg)
        assertEquals("Oblężenie tygodnia: 20 fal do progu Srebro", silver.text)
        p.claimWeekly(week, Weekly.threshold(2), week)
        // Wszystko zdobyte: brak celu.
        val none = NextGoals.pick(p, now, null)
        assertEquals(NextGoalKind.NONE, none.kind)
        assertEquals("", none.text)
    }

    @Test
    fun weeklyTierInEndlessContextBeforeCampaignEnds() {
        val p = profile()
        p.stars[0] = 1
        // Wyniki Oblężenia tygodnia, ale etap 2 czeka: etap ma pierwszeństwo.
        assertEquals(NextGoalKind.STAGE, NextGoals.pick(p, now, Mode.ENDLESS).kind)
        completeAll(p)
        assertEquals(NextGoalKind.WEEKLY_TIER, NextGoals.pick(p, now, Mode.ENDLESS).kind)
    }

    @Test
    fun everyLanguageBuildsGoalTexts() {
        val p = profile()
        p.medals = 10_000
        for (code in arrayOf("pl", "en", "de", "es", "fr", "it", "pt", "uk", "nl", "tr")) {
            L.setCode(code)
            for (key in arrayOf("goal179_missions", "goal179_research", "goal179_stage", "goal179_weekly"))
                assertTrue("$code $key", L[key].isNotBlank() && L[key] != key)
            val text = NextGoals.pick(p, now, null).text
            assertTrue("$code: $text", text.isNotBlank() && !text.contains('%'))
        }
    }
}
