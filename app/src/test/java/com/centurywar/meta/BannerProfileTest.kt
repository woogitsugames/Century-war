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
import java.nio.file.Files

/** Zapis starych profili, warunki kosmetyczne i trwałe znaczniki lokalnych wyników. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BannerProfileTest {
    private fun profile(): Profile =
        Profile(File(Files.createTempDirectory("cw-banner").toFile(), "profile.json"))

    @Test
    fun oldSaveDefaultsAndUnlocksEarnedStars() {
        val original = profile()
        original.stars[0] = 3
        original.stars[1] = 3
        original.stars[2] = 3
        original.stars[3] = 3
        val old = JSONObject(original.toJson()).apply {
            remove("selectedBanner")
            remove("unlockedBanners")
            remove("dailyChallengeStreak")
            remove("bestDailyChallengeStreak")
            remove("lastDailyChallengeStreakDay")
            optJSONObject("boards")?.let { boards ->
                boards.keys().forEach { board ->
                    val list = boards.getJSONArray(board)
                    for (i in 0 until list.length()) list.getJSONObject(i).remove("b")
                }
            }
        }
        val loaded = profile()
        loaded.fromJson(old.toString())
        assertEquals(0, loaded.selectedBanner)
        assertEquals(0, loaded.bestDailyChallengeStreak)
        assertTrue(loaded.bannerUnlocked(0))
        assertTrue(loaded.bannerUnlocked(1))
        assertFalse(loaded.bannerUnlocked(2))
    }

    @Test
    fun unlocksForStarsAchievementsAndFourteenDaysArePermanent() {
        val p = profile()
        p.stars.fill(3)
        p.unlockedAchievements.addAll(listOf("win_10", "wave_25", "orbital", "without_turrets"))
        assertTrue(p.refreshBanners())
        for (i in 0..8) assertTrue("Sztandar $i", p.bannerUnlocked(i))
        assertFalse(p.bannerUnlocked(9))
        for (day in 1000L..1013L) assertTrue(p.recordRankedDailyStreak(day))
        assertEquals(14, p.bestDailyChallengeStreak)
        for (i in 0 until BannerCatalog.COUNT) assertTrue("Sztandar $i", p.bannerUnlocked(i))
        assertTrue(p.selectBanner(11))
        p.stars.fill(0)
        p.unlockedAchievements.clear()
        assertFalse(p.refreshBanners())
        assertTrue(p.bannerUnlocked(11))
        assertEquals(11, p.selectedBanner)
    }

    @Test
    fun dailyStreakRejectsDuplicatesAndResetsAfterMissingDay() {
        val p = profile()
        assertFalse(p.recordRankedDailyStreak(-1L))
        assertTrue(p.recordRankedDailyStreak(10L))
        assertFalse(p.recordRankedDailyStreak(10L))
        assertTrue(p.recordRankedDailyStreak(11L))
        assertEquals(2, p.dailyChallengeStreak)
        assertTrue(p.recordRankedDailyStreak(15L))
        assertEquals(1, p.dailyChallengeStreak)
        assertEquals(2, p.bestDailyChallengeStreak)
        assertFalse(p.recordRankedDailyStreak(14L))
        assertFalse(p.bannerUnlocked(9))
    }

    @Test
    fun selectionAndLocalScoreMarkerSurviveDiskAndReplayRemoval() {
        val file = File(Files.createTempDirectory("cw-banners-scores").toFile(), "profile.json")
        val p = Profile(file)
        p.stars.fill(3)
        assertTrue(p.selectBanner(2))
        p.submit(Profile.BOARD_ENDLESS, 1500, 1000L, "replay_001")
        assertTrue(p.selectBanner(4))
        p.submit(Profile.BOARD_ENDLESS, 2000, 1001L, "replay_002")
        p.save()
        Profile.awaitHintWrites()
        val restored = Profile(file)
        restored.load()
        assertEquals(4, restored.selectedBanner)
        assertEquals(2, restored.top(Profile.BOARD_ENDLESS)[1].bannerId)
        assertEquals(4, restored.top(Profile.BOARD_ENDLESS)[0].bannerId)
        assertTrue(restored.clearReplayReferences(listOf("replay_001")))
        assertEquals(2, restored.top(Profile.BOARD_ENDLESS)[1].bannerId)
        assertEquals(null, restored.top(Profile.BOARD_ENDLESS)[1].replay)
    }

    @Test
    fun corruptedOptionalValuesAreBoundedAndLockedChoiceRejected() {
        val p = profile()
        val corrupted = JSONObject(p.toJson())
            .put("selectedBanner", 100000)
            .put("unlockedBanners", 0)
            .put("dailyChallengeStreak", -1000)
            .put("bestDailyChallengeStreak", -100)
            .put("lastDailyChallengeStreakDay", Long.MAX_VALUE)
        p.fromJson(corrupted.toString())
        assertEquals(0, p.selectedBanner)
        assertEquals(1, p.unlockedBanners)
        assertEquals(0, p.dailyChallengeStreak)
        assertEquals(0, p.bestDailyChallengeStreak)
        assertEquals(100000L, p.lastDailyChallengeStreakDay)
        assertFalse(p.selectBanner(11))
        val high = JSONObject(p.toJson()).put("bestDailyChallengeStreak", Int.MAX_VALUE)
        p.fromJson(high.toString())
        assertEquals(100000, p.bestDailyChallengeStreak)
        assertTrue(p.bannerUnlocked(11))
        val all = JSONObject(p.toJson())
            .put("selectedBanner", 100000)
            .put("unlockedBanners", Int.MAX_VALUE)
        p.fromJson(all.toString())
        assertEquals(11, p.selectedBanner)
        assertEquals(BannerCatalog.VALID_MASK, p.unlockedBanners)
    }

    @Test
    fun resetClearsOnlyCosmeticProgressWithOtherProfileReset() {
        val p = profile()
        p.stars.fill(3)
        assertTrue(p.selectBanner(4))
        p.recordRankedDailyStreak(100L)
        p.reset()
        assertEquals(0, p.selectedBanner)
        assertEquals(1, p.unlockedBanners)
        assertEquals(0, p.dailyChallengeStreak)
        assertEquals(0, p.bestDailyChallengeStreak)
        assertEquals(-1L, p.lastDailyChallengeStreakDay)
    }
}
