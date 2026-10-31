package com.centurywar.meta

import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.game.Role
import com.centurywar.game.Weather
import com.centurywar.game.Replay
import com.centurywar.engine.L
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MissionsTest {
    private fun day(date: String): Long = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun profile(): Profile {
        val file = File.createTempFile("century-missions-", ".json")
        file.delete()
        return Profile(file)
    }

    private fun identity(items: List<MissionItem>): List<String> =
        items.map { "${it.id}/${it.type}/${it.target}/${it.reward}/${it.modifier}" }

    @Test
    fun sameUtcDayProducesSameMissions() {
        val now = day("2026-10-28")
        val a = profile()
        val b = profile()
        a.stars[0] = 3; b.stars[0] = 3
        val x = Missions.refresh(a, now)
        val y = Missions.refresh(b, now + Missions.DAY_MILLIS - 1)
        assertEquals(3, x.daily.size)
        assertEquals(3, x.weekly.size)
        assertEquals(identity(x.daily), identity(y.daily))
        assertEquals(identity(x.weekly), identity(y.weekly))
        assertEquals(6, (x.daily + x.weekly).map { it.type }.distinct().size)
        assertTrue(MissionType.values().size >= 20)
    }

    @Test
    fun dailyAndWeeklyRolloverUseUtcMonday() {
        val p = profile()
        val m = Missions(p)
        m.refresh(day("2026-10-27")) // wtorek
        val daily = identity(m.state.daily)
        val weekly = identity(m.state.weekly)
        m.refresh(day("2026-10-28")) // środa
        assertNotEquals(daily, identity(m.state.daily))
        assertEquals(weekly, identity(m.state.weekly))
        val next = identity(m.state.daily)
        m.refresh(day("2026-11-01")) // niedziela
        assertEquals(weekly, identity(m.state.weekly))
        m.refresh(day("2026-11-02")) // poniedziałek
        assertNotEquals(weekly, identity(m.state.weekly))
        assertNotEquals(next, identity(m.state.daily))
    }

    @Test
    fun timezoneChangeAndClockRollbackCannotRepeatReward() {
        val now = day("2026-10-31")
        val p = profile()
        val m = Missions(p)
        m.refresh(now)
        val daily = identity(m.state.daily)
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))
            assertEquals(daily, identity(m.refresh(now + 43_200_000L).daily))
            val first = m.claimLogin(now + 43_200_000L)
            assertTrue(first > 0)
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(0, m.claimLogin(now + 43_200_000L))
            assertEquals(first, p.medals)
        } finally {
            TimeZone.setDefault(original)
        }
        m.refresh(now + Missions.DAY_MILLIS)
        assertEquals(2, m.state.loginStreak)
        val future = identity(m.state.daily)
        assertEquals(0, m.claimLogin(now))
        assertEquals(future, identity(m.state.daily))
        assertEquals(2, m.state.loginStreak)
    }

    @Test
    fun loginStreakDecreasesByTwoAfterGapAndStopsAtSeven() {
        val p = profile()
        val m = Missions(p)
        val start = day("2026-09-01")
        val payouts = ArrayList<Int>()
        for (i in 0 until 9) {
            m.refresh(start + i * Missions.DAY_MILLIS)
            payouts.add(m.claimLogin(start + i * Missions.DAY_MILLIS))
        }
        assertEquals(7, m.state.loginStreak)
        assertEquals(payouts[6], payouts[7])
        assertEquals(payouts[7], payouts[8])
        assertTrue(payouts[0] < payouts[1] && payouts[1] < payouts[2])
        m.refresh(start + 12 * Missions.DAY_MILLIS)
        assertEquals(5, m.state.loginStreak)
        m.refresh(start + 13 * Missions.DAY_MILLIS)
        assertEquals(6, m.state.loginStreak)
        m.refresh(start + 20 * Missions.DAY_MILLIS)
        assertEquals(4, m.state.loginStreak)
        m.refresh(start + 27 * Missions.DAY_MILLIS)
        assertEquals(2, m.state.loginStreak)
        m.refresh(start + 34 * Missions.DAY_MILLIS)
        assertEquals(1, m.state.loginStreak)
    }

    @Test
    fun missionClaimIsIdempotentAndPersists() {
        val now = day("2026-10-31")
        val file = File.createTempFile("century-mission-save-", ".json").apply { delete() }
        val p = Profile(file)
        val m = Missions(p)
        val first = m.refresh(now).daily.first()
        first.progress = first.target
        val reward = m.claim(first.id, now)
        assertTrue(reward > 0)
        assertEquals(reward, p.medals)
        assertEquals(0, m.claim(first.id, now))
        val restored = Profile(file).apply { load() }
        val other = Missions(restored)
        assertTrue(other.state.daily.first().claimed)
        assertEquals(0, other.claim(first.id, now))
        assertEquals(reward, restored.medals)
    }

    @Test
    fun oldProfileWithoutMissionFieldsStillLoads() {
        val now = day("2026-10-31")
        val file = File.createTempFile("century-legacy-", ".json")
        file.writeText("""{"name":"Archiwum","medals":77,"wins":4}""")
        val p = Profile(file).apply { load() }
        assertEquals(77, p.medals)
        assertEquals(0, p.missionState.daily.size)
        Missions.refresh(p, now)
        assertEquals(3, p.missionState.daily.size)
        assertEquals(77, p.medals)
        Profile.awaitHintWrites()
        val saved = JSONObject(file.readText())
        assertEquals(3, saved.getJSONObject("missions").getJSONArray("daily").length())
    }

    @Test
    fun researchPurchaseIsRecordedAutomatically() {
        val p = profile()
        val m = Missions(p)
        val start = day("2026-10-01")
        var today = start
        var found = false
        for (i in 0..90) {
            today = start + i * Missions.DAY_MILLIS
            m.refresh(today)
            if ((m.state.daily + m.state.weekly).any { it.type == MissionType.RESEARCH }) {
                found = true
                break
            }
        }
        assertTrue("Pula losowa powinna uwzględnić badania", found)
        val active = (m.state.daily + m.state.weekly).filter { it.type == MissionType.RESEARCH }
        p.medals = 100_000
        val def = Profile.research.first { it.key == "r_income" }
        assertTrue(p.buy(def, today))
        assertTrue(active.all { it.progress == 1 })
        assertEquals(active.size, (m.state.daily + m.state.weekly).count { it.type == MissionType.RESEARCH && it.progress == 1 })
    }

    @Test
    fun battleCountersCompleteOnlyMatchingGoals() {
        val start = day("2026-10-01")
        val p = profile()
        val m = Missions(p)
        var today = start
        var item: MissionItem? = null
        for (i in 0..120) {
            today = start + i * Missions.DAY_MILLIS
            m.refresh(today)
            item = (m.state.daily + m.state.weekly).firstOrNull { it.type == MissionType.HEAVY }
            if (item != null) break
        }
        assertTrue(item != null)
        val battle = Battle(BattleConfig(Mode.SKIRMISH, 44L, 1.3f))
        battle.player.trainedByRole[Role.HEAVY.ordinal] = 4
        battle.player.trainedByRole[Role.ELITE.ordinal] = 10
        battle.player.kills = 55
        battle.winner = 0
        m.onBattleEnd(battle, today)
        assertEquals(4, item!!.progress)
        assertFalse(item.completed && item.claimed)
    }

    @Test
    fun onlyNewCampaignStarsAdvanceStarMission() {
        val p = profile()
        val m = Missions(p)
        val start = day("2026-10-01")
        var current = start
        var starMission: MissionItem? = null
        for (i in 0..120) {
            current = start + i * Missions.DAY_MILLIS
            m.refresh(current)
            starMission = (m.state.daily + m.state.weekly).firstOrNull { it.type == MissionType.STARS }
            if (starMission != null) break
        }
        assertTrue("W puli powinna pojawić się misja gwiazdek", starMission != null)
        val battle = Battle(BattleConfig(Mode.CAMPAIGN, 77L, 1f, stageIndex = 0))
        battle.winner = 0
        m.onBattleEnd(battle, current, newStars = 0)
        assertEquals(0, starMission!!.progress)
        m.onBattleEnd(battle, current, newStars = 2)
        assertEquals(2, starMission.progress)
    }

    @Test
    fun loginRewardAndMalformedStateSurviveJson() {
        val f = File.createTempFile("century-login-", ".json").apply { delete() }
        val p = Profile(f)
        val m = Missions(p)
        val today = day("2026-10-31")
        m.refresh(today)
        val reward = m.claimLogin(today)
        assertTrue(reward > 0)
        val restored = Profile(f).apply { load() }
        assertEquals(reward, restored.missionState.loginRewardToday)
        assertEquals(0, Missions(restored).claimLogin(today))

        restored.missionState.fromJson(JSONObject("""{"day":9223372036854775807,"week":-9223372036854775807,"seen":9223372036854775807,"loginDay":9223372036854775807,"loginClaimed":9223372036854775807,"streak":55,"loginRewardToday":-10}"""))
        assertEquals(Long.MIN_VALUE, restored.missionState.dailyDay)
        assertEquals(Long.MIN_VALUE, restored.missionState.weeklyWeek)
        assertEquals(Long.MIN_VALUE, restored.missionState.lastSeenDay)
        assertEquals(Long.MIN_VALUE, restored.missionState.loginDay)
        assertEquals(0, restored.missionState.loginStreak)
        assertEquals(0, restored.missionState.loginRewardToday)
    }

    @Test
    fun lockedGoalsAreOmittedAndNewGoalsKeepLowerWeight() {
        val start = day("2026-09-01")
        val beginner = profile()
        val first = Missions(beginner)
        repeat(40) { i ->
            val state = first.refresh(start + i * Missions.DAY_MILLIS)
            val types = (state.daily + state.weekly).map { it.type }
            assertFalse(MissionType.WEATHER_WINS in types)
            assertFalse(MissionType.REPLAYS_WATCHED in types)
            assertFalse(MissionType.ELITES in types)
            assertFalse(MissionType.RALLIES in types)
        }

        val advanced = profile()
        for (i in 0..3) advanced.stars[i] = 1
        advanced.battles = 1
        val m = Missions(advanced)
        val rare = setOf(
            MissionType.WEATHER_WINS, MissionType.WEEKLY_WAVES,
            MissionType.CUSTOM_SKIRMISH, MissionType.ENDLESS_BOSSES,
            MissionType.UNIT_UPGRADES, MissionType.REPLAYS_WATCHED,
            MissionType.ENDLESS_CARDS,
        )
        val seen = mutableSetOf<MissionType>()
        var commonCount = 0
        var newCount = 0
        repeat(140) { i ->
            val state = m.refresh(start + i * Missions.DAY_MILLIS)
            val items = state.daily + state.weekly
            assertEquals(6, items.map { it.type }.distinct().size)
            for (item in items) {
                seen.add(item.type)
                if (item.type in rare) newCount++ else commonCount++
            }
        }
        assertTrue("Każdy nowy typ powinien być losowalny: $seen", seen.containsAll(rare))
        assertTrue("Podstawowe misje muszą dominować", commonCount > newCount * 3)
    }

    @Test
    fun weatherCustomSkirmishAndUnitUpgradesCountOnlyMatchingEvents() {
        val now = day("2026-10-31")
        val m = Missions(profile())
        m.refresh(now)
        val weather = MissionItem("weather", MissionType.WEATHER_WINS, 2, 10)
        val custom = MissionItem("custom", MissionType.CUSTOM_SKIRMISH, 2, 10)
        val upgrades = MissionItem("upgrades", MissionType.UNIT_UPGRADES, 4, 10)
        m.state.daily.clear()
        m.state.daily.addAll(listOf(weather, custom, upgrades))

        val clear = Battle(BattleConfig(Mode.CAMPAIGN, 1L, 1f))
        clear.winner = 0
        m.onBattleEnd(clear, now, persist = false)
        assertEquals(0, weather.progress)
        val rain = Battle(BattleConfig(Mode.CAMPAIGN, 2L, 1f, weather = Weather.RAIN))
        rain.winner = 0
        m.onBattleEnd(rain, now, persist = false)
        assertEquals(1, weather.progress)
        val loss = Battle(BattleConfig(Mode.CAMPAIGN, 3L, 1f, weather = Weather.NIGHT))
        m.onBattleEnd(loss, now, persist = false)
        assertEquals(1, weather.progress)

        val preset = Battle(BattleConfig(Mode.SKIRMISH, 4L, 1.3f))
        m.onBattleEnd(preset, now, persist = false)
        assertEquals(0, custom.progress)
        val own = Battle(BattleConfig(Mode.SKIRMISH, 5L, 1.3f, startEra = 1, maxEra = 3))
        m.onBattleEnd(own, now, persist = false)
        assertEquals(1, custom.progress)

        val actions = Replay.Log(0, listOf(
            Replay.Command(0, Replay.Kind.UNIT_UPGRADE, 0),
            Replay.Command(0, Replay.Kind.UNIT_UPGRADE, 1),
            Replay.Command(0, Replay.Kind.BARRAGE),
        ))
        m.onBattleEnd(clear, now, persist = false, playerCommands = actions)
        assertEquals(2, upgrades.progress)
        m.onBattleEnd(clear, now, persist = false, playerCommands = actions)
        assertEquals(4, upgrades.progress)
    }

    @Test
    fun weeklyWaveBossAndCardProgressDoesNotMixModesOrOldWeeks() {
        val now = day("2026-10-31")
        val m = Missions(profile())
        m.refresh(now)
        val waves = MissionItem("waves", MissionType.WEEKLY_WAVES, 20, 30)
        val bosses = MissionItem("bosses", MissionType.ENDLESS_BOSSES, 3, 30)
        val cards = MissionItem("cards", MissionType.ENDLESS_CARDS, 4, 30)
        m.state.weekly.clear()
        m.state.weekly.addAll(listOf(waves, bosses, cards))
        val week = Missions.utcWeek(now)
        val previous = Battle(BattleConfig(Mode.ENDLESS, 1L, 1f, weekly = week - 1))
        previous.wave = 27
        m.onBattleEnd(previous, now, persist = false)
        assertEquals(0, waves.progress)
        val regular = Battle(BattleConfig(Mode.ENDLESS, 2L, 1f))
        regular.wave = 40
        m.onBattleEnd(regular, now, persist = false)
        assertEquals(0, waves.progress)

        val current = Battle(BattleConfig(Mode.ENDLESS, 3L, 1f, weekly = week))
        current.wave = 12
        current.bossesDefeated = 2
        val log = Replay.Log(0, listOf(
            Replay.Command(0, Replay.Kind.CARD, 0),
            Replay.Command(0, Replay.Kind.CARD, 1),
            Replay.Command(0, Replay.Kind.UNIT_UPGRADE, 0),
        ))
        m.onBattleEnd(current, now, persist = false, playerCommands = log)
        assertEquals(12, waves.progress)
        assertEquals(2, bosses.progress)
        assertEquals(2, cards.progress)
        current.wave = 9
        m.onBattleEnd(current, now, persist = false)
        assertEquals(12, waves.progress)
        assertEquals(3, bosses.progress) // Licznik nie może przekroczyć celu.
    }

    @Test
    fun replayViewAndUnknownSavedTypePreserveExistingProgress() {
        val now = day("2026-10-31")
        val p = profile()
        val m = Missions(p)
        m.refresh(now)
        val watched = MissionItem("replay", MissionType.REPLAYS_WATCHED, 2, 12)
        m.state.daily[0] = watched
        m.onReplayWatched(now, persist = false)
        assertEquals(1, watched.progress)
        m.onReplayWatched(now, persist = false)
        m.onReplayWatched(now, persist = false)
        assertEquals(2, watched.progress)

        val known = m.state.daily[1]
        known.progress = 1.coerceAtMost(known.target)
        val json = m.state.toJson()
        json.put("daily", JSONArray()
            .put(known.toJson())
            .put(JSONObject().put("id", "future").put("type", "UNKNOWN_FUTURE")
                .put("goal", 2).put("reward", 10))
            .put(m.state.daily[2].toJson()))
        val restored = profile()
        restored.missionState.fromJson(json)
        assertEquals(2, restored.missionState.daily.size)
        assertEquals(known.id, restored.missionState.daily[0].id)
        assertEquals(known.progress, restored.missionState.daily[0].progress)
        val after = Missions(restored).refresh(now)
        assertEquals(3, after.daily.size)
        assertTrue(after.daily.any { it.id == known.id && it.progress == known.progress })
    }

    @Test
    fun newMissionNamesUsePolishPluralAndWaveNumber() {
        val previous = L.lang
        try {
            L.lang = L.PL
            assertEquals("Obejrzyj 2 powtórki",
                MissionTexts.label(MissionItem("replay", MissionType.REPLAYS_WATCHED, 2, 10)))
            assertEquals("Obejrzyj 5 powtórek",
                MissionTexts.label(MissionItem("replay", MissionType.REPLAYS_WATCHED, 5, 10)))
            assertEquals("Dotrzyj do fali 20 w oblężeniu tygodnia",
                MissionTexts.label(MissionItem("waves", MissionType.WEEKLY_WAVES, 20, 10)))
        } finally {
            L.lang = previous
        }
    }
}
