package com.centurywar.meta

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** R2-02: licznik porażek z rzędu na etapie kampanii (stan lokalny profilu). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CampaignLossStreakTest {
    private fun file() = File.createTempFile("loss-streak-", ".json").apply { delete() }

    @Test
    fun streakCountsLossesOnSameStageAndResets() {
        val p = Profile(file())
        p.recordCampaignOutcome(10, won = false)
        p.recordCampaignOutcome(10, won = false)
        assertEquals(2, p.lossStreakOn(10))
        // Wygrana na innym etapie nie zeruje serii etapu 11.
        p.recordCampaignOutcome(3, won = true)
        assertEquals(2, p.lossStreakOn(10))
        p.recordCampaignOutcome(10, won = true)
        assertEquals(0, p.lossStreakOn(10))
        p.recordCampaignOutcome(10, won = false)
        p.recordCampaignOutcome(11, won = false)
        assertEquals(0, p.lossStreakOn(10))
        assertEquals(1, p.lossStreakOn(11))
    }

    @Test
    fun earlySurrenderDoesNotCount() {
        val p = Profile(file())
        p.recordCampaignOutcome(5, won = false)
        p.recordCampaignOutcome(5, won = false, counts = false)
        assertEquals(1, p.lossStreakOn(5))
        p.recordCampaignOutcome(6, won = false, counts = false)
        assertEquals(1, p.lossStreakOn(5))
        assertEquals(0, p.lossStreakOn(6))
    }

    @Test
    fun settledBattleCountsOnce() {
        val p = Profile(file())
        val id = 77L
        assertTrue(p.settleBattle(id) { p.recordCampaignOutcome(4, won = false) })
        assertFalse(p.settleBattle(id) { p.recordCampaignOutcome(4, won = false) })
        assertEquals(1, p.lossStreakOn(4))
        Profile.awaitHintWrites()
    }

    @Test
    fun savedLocallyButNotInCloud() {
        val f = file()
        val p = Profile(f)
        p.recordCampaignOutcome(12, won = false)
        p.recordCampaignOutcome(12, won = false)
        val json = JSONObject(p.toJson())
        assertEquals(12, json.getInt("campaignLossStreakStage"))
        assertEquals(2, json.getInt("campaignLossStreak"))
        val cloud = JSONObject(p.toCloudJson())
        assertFalse(cloud.has("campaignLossStreakStage"))
        assertFalse(cloud.has("campaignLossStreak"))
        // Wczytanie snapshotu z chmury nie kasuje lokalnej serii.
        p.fromJson(p.toCloudJson())
        assertEquals(2, p.lossStreakOn(12))
        val f2 = file().apply { writeText(p.toJson()) }
        val other = Profile(f2).apply { load() }
        assertEquals(2, other.lossStreakOn(12))
        p.reset()
        assertEquals(0, p.lossStreakOn(12))
        assertEquals(-1, p.campaignLossStreakStage)
    }

    @Test
    fun oldProfileWithoutKeysLoadsDefaults() {
        val p = Profile(file())
        val json = JSONObject(p.toJson())
        json.remove("campaignLossStreakStage")
        json.remove("campaignLossStreak")
        val q = Profile(file().apply { writeText(json.toString()) }).apply { load() }
        assertEquals(-1, q.campaignLossStreakStage)
        assertEquals(0, q.campaignLossStreak)
        // Uszkodzone wartości sprowadzone do zakresu.
        json.put("campaignLossStreakStage", 999).put("campaignLossStreak", -5)
        val r = Profile(file().apply { writeText(json.toString()) }).apply { load() }
        assertTrue(r.campaignLossStreakStage in -1 until r.stars.size)
        assertTrue(r.campaignLossStreak >= 0)
        q.fromJson(json.toString())
        assertTrue(q.campaignLossStreakStage in -1 until q.stars.size)
        assertTrue(q.campaignLossStreak >= 0)
    }
}
