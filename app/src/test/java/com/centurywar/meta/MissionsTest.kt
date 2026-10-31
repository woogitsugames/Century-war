package com.centurywar.meta

import com.centurywar.game.Battle
import com.centurywar.game.BattleConfig
import com.centurywar.game.Mode
import com.centurywar.game.Role
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
}