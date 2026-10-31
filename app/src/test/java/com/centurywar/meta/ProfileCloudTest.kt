package com.centurywar.meta

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProfileCloudTest {

    private fun freshProfile(): Profile {
        val file = File.createTempFile("century-profile-", ".json")
        file.deleteOnExit()
        return Profile(file)
    }

    /** Zapis w chmurze niesie też osiągnięcia, misje i ustawienia przypomnień (scalone gałęzie 02/03/15). */
    @Test
    fun jsonRoundTripPreservesMetaProgress() {
        val source = freshProfile()
        source.battles = 3; source.wins = 2
        source.unlockedAchievements.add("win_1")
        source.achievementCounters[AchievementMetric.TURRETS_BUILT.name] = 7L
        source.achievementDailySeeds.add(20000L)
        source.achievementDoctrineMask = 5
        source.notifications = false
        source.notifyHour = 21
        Missions(source).refresh(20_000L * Missions.DAY_MILLIS + 1000L)
        val json = source.toJson()
        val target = freshProfile()
        target.fromJson(json)
        assertTrue("win_1" in target.unlockedAchievements)
        assertEquals(7L, target.achievementCounters[AchievementMetric.TURRETS_BUILT.name])
        assertTrue(20000L in target.achievementDailySeeds)
        assertEquals(5, target.achievementDoctrineMask)
        assertFalse(target.notifications)
        assertEquals(21, target.notifyHour)
        assertEquals(3, target.missionState.daily.size)
        assertEquals(source.missionState.daily.map { it.id }, target.missionState.daily.map { it.id })
        assertEquals(json, target.toJson())
    }

    @Test
    fun jsonRoundTripPreservesResearchBoardsAndSettings() {
        val source = freshProfile()
        source.name = "Testowy dowódca"
        source.medals = 173
        source.levels["r_income"] = 3
        source.stars[0] = 3
        source.stars[7] = 2
        source.bestWave = 18
        source.lastRankedDaily = 1234
        source.tutorialDone = true
        source.quality = 2
        source.fpsCap = 30
        source.vibration = false
        source.sound = false
        source.showFps = true
        source.lang = "en"
        source.skirmishDiff = 3
        source.battles = 9
        source.wins = 6
        source.totalKills = 4567
        source.playSeconds = 7890
        source.submit(Profile.BOARD_ENDLESS, 5555, 1234)

        val copy = freshProfile()
        copy.fromJson(source.toJson())

        assertEquals(source.name, copy.name)
        assertEquals(173, copy.medals)
        assertEquals(3, copy.level("r_income"))
        assertEquals(3, copy.stars[0])
        assertEquals(2, copy.stars[7])
        assertEquals(18, copy.bestWave)
        assertEquals(1234L, copy.lastRankedDaily)
        assertTrue(copy.tutorialDone)
        assertEquals(2, copy.quality)
        assertEquals(30, copy.fpsCap)
        assertFalse(copy.vibration)
        assertFalse(copy.sound)
        assertTrue(copy.showFps)
        assertEquals("en", copy.lang)
        assertEquals(3, copy.skirmishDiff)
        assertEquals(9, copy.battles)
        assertEquals(6, copy.wins)
        assertEquals(4567L, copy.totalKills)
        assertEquals(7890L, copy.playSeconds)
        assertEquals(5555L, copy.top(Profile.BOARD_ENDLESS).first().score)
    }

    @Test
    fun progressValueCountsStarsSpentAndAvailableMedalsAndBattles() {
        val source = freshProfile()
        source.stars[0] = 2
        source.medals = 30
        source.battles = 7
        source.levels["r_income"] = 2 // 40 + 55 wydanych medali
        assertEquals(332L, Profile.progressValue(source.toJson()))
    }

    @Test
    fun preferenceUsesGreaterProgressAndKeepsLocalOnTie() {
        val local = freshProfile().apply { battles = 5 }
        val cloud = freshProfile().apply { battles = 6 }
        assertTrue(Profile.preferCloud(local.toJson(), cloud.toJson()))
        assertFalse(Profile.preferCloud(cloud.toJson(), local.toJson()))
        assertFalse(Profile.preferCloud(local.toJson(), local.toJson()))
    }

    @Test
    fun importReplacesStaleLevelsAndBoards() {
        val old = freshProfile()
        old.levels["r_income"] = 6
        old.stars[9] = 2
        old.submit(Profile.BOARD_ENDLESS, 10, 1)

        val clean = freshProfile()
        clean.stars[0] = 1
        old.fromJson(clean.toJson())

        assertEquals(0, old.level("r_income"))
        assertEquals(0, old.stars[9])
        assertEquals(1, old.stars[0])
        assertTrue(old.top(Profile.BOARD_ENDLESS).isEmpty())
    }

    @Test
    fun malformedSnapshotCannotPartiallyOverwriteProfile() {
        val local = freshProfile()
        local.name = "Lokalny"
        local.medals = 101
        local.stars[0] = 3
        assertFalse(Profile.isValidJson("niepoprawny JSON"))
        assertTrue(Profile.isValidJson(local.toJson()))
        try {
            local.fromJson("{\"name\":\"Zmieniony\",\"boards\":{\"endless\":\"bledne\"}}")
            fail("Niepoprawne tablice powinny przerwać import")
        } catch (_: Exception) {
            assertEquals("Lokalny", local.name)
            assertEquals(101, local.medals)
            assertEquals(3, local.stars[0])
        }
    }

    @Test
    fun localSaveAndLoadKeepCurrentFormat() {
        val file = File.createTempFile("century-save-", ".json")
        try {
            val profile = Profile(file)
            profile.stars[1] = 3
            profile.save()
            assertTrue(file.readText().contains("\"v\":" + Profile.CURRENT_VERSION))
            val loaded = Profile(file)
            loaded.load()
            assertEquals(3, loaded.stars[1])
        } finally {
            file.delete()
        }
    }
}
