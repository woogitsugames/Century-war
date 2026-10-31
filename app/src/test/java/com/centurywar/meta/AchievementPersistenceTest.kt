package com.centurywar.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AchievementPersistenceTest {
    private fun tempProfileFile(): File {
        val file = File.createTempFile("century-achievements-state", ".json")
        file.delete()
        file.deleteOnExit()
        return file
    }

    @Test
    fun roundTripKeepsAllAchievementState() {
        val file = tempProfileFile()
        val p = Profile(file)
        p.medals = 543
        p.achievementCounters[AchievementMetric.BARRAGES_CAST.name] = 97L
        p.achievementCounters[AchievementMetric.DAILY_DAYS.name] = 2L
        p.unlockedAchievements.add("win_1")
        p.unlockedAchievements.add("daily_1")
        p.achievementDailySeeds.addAll(listOf(20000L, 20002L))
        p.achievementDoctrineMask = 0b100101
        p.save()

        val loaded = Profile(file)
        loaded.load()
        assertEquals(543, loaded.medals)
        assertEquals(97L, loaded.achievementCounters[AchievementMetric.BARRAGES_CAST.name])
        assertEquals(2L, loaded.achievementCounters[AchievementMetric.DAILY_DAYS.name])
        assertEquals(setOf("win_1", "daily_1"), loaded.unlockedAchievements)
        assertEquals(setOf(20000L, 20002L), loaded.achievementDailySeeds)
        assertEquals(0b100101, loaded.achievementDoctrineMask)
    }

    @Test
    fun oldProfileWithoutAchievementKeysStartsAtZero() {
        val file = tempProfileFile()
        file.writeText("""{"v":1,"name":"Gracz","medals":99,"wins":5,"totalKills":20}""")
        val p = Profile(file)
        p.load()
        assertEquals(99, p.medals)
        assertEquals(5, p.wins)
        assertTrue(p.achievementCounters.isEmpty())
        assertTrue(p.unlockedAchievements.isEmpty())
        assertTrue(p.achievementDailySeeds.isEmpty())
        assertEquals(0, p.achievementDoctrineMask)
    }

    @Test
    fun resetClearsUnlocksAndCounters() {
        val file = tempProfileFile()
        val p = Profile(file)
        p.achievementCounters[AchievementMetric.DAILY_DAYS.name] = 7
        p.unlockedAchievements.add("daily_7")
        p.achievementDailySeeds.add(20001L)
        p.achievementDoctrineMask = 1
        p.reset()

        val loaded = Profile(file)
        loaded.load()
        assertFalse("daily_7" in loaded.unlockedAchievements)
        assertTrue(loaded.achievementCounters.isEmpty())
        assertTrue(loaded.achievementDailySeeds.isEmpty())
        assertEquals(0, loaded.achievementDoctrineMask)
    }
}
