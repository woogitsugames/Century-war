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
        Profile.awaitHintWrites()

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
        file.writeText("""{"v":1,"name":"Gracz","medals":99,"battles":7,"wins":5,"totalKills":20}""")
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
    fun badAchievementJsonIsClampedAndUnknownIdsIgnored() {
        val file = tempProfileFile()
        file.writeText("""{"battles":-5,"wins":15,"totalKills":-12,
            "achievementCounters":{"WINS":-9,"BARRAGES_CAST":20,"UNKNOWN":13},
            "unlockedAchievements":["win_1","invented"],
            "achievementDailySeeds":[20000,-2],
            "achievementDoctrineMask":2147483647}""")
        val p = Profile(file)
        p.load()
        assertEquals(0, p.battles)
        assertEquals(0, p.wins)
        assertEquals(0L, p.totalKills)
        assertEquals(0L, p.achievementCounters["WINS"])
        assertEquals(20L, p.achievementCounters["BARRAGES_CAST"])
        assertFalse(p.achievementCounters.containsKey("UNKNOWN"))
        assertEquals(setOf("win_1"), p.unlockedAchievements)
        assertEquals(setOf(20000L), p.achievementDailySeeds)
        assertEquals(0x1FF, p.achievementDoctrineMask)
    }

    @Test
    fun newAchievementsPersistAfterSaveAndOldProfilesDefaultToZero() {
        val file = tempProfileFile()
        val p = Profile(file)
        p.achievementCounters[AchievementMetric.STEAM_VARIANTS.name] = 3L
        p.achievementCounters[AchievementMetric.SILICON_VARIANTS.name] = 1L
        p.achievementCounters[AchievementMetric.BOSS_SHIELD.name] = 1L
        p.achievementCounters[AchievementMetric.BARRAGE_EIGHT.name] = 1L
        p.achievementCounters[AchievementMetric.WEEKLY_GOLD.name] = 1L
        p.achievementCounters[AchievementMetric.REPLAY_RECORD.name] = 1L
        p.unlockedAchievements.addAll(setOf("steam_twins", "boss_shield",
            "barrage_eight", "weekly_gold", "replay_record"))
        p.save()
        Profile.awaitHintWrites()

        val reloaded = Profile(file)
        reloaded.load()
        assertEquals(2L, Achievements.progress(reloaded, Achievements.byId("steam_twins")!!))
        assertEquals(1L, Achievements.progress(reloaded, Achievements.byId("silicon_twins")!!))
        for (id in listOf("boss_shield", "barrage_eight", "weekly_gold", "replay_record"))
            assertEquals(1L, Achievements.progress(reloaded, Achievements.byId(id)!!))
        assertEquals(p.unlockedAchievements, reloaded.unlockedAchievements)

        val oldFile = tempProfileFile()
        oldFile.writeText("""{"v":1,"medals":100,"battles":2,"wins":1}""")
        val old = Profile(oldFile)
        old.load()
        assertEquals(0L, Achievements.progress(old, Achievements.byId("steam_twins")!!))
        assertEquals(0L, Achievements.progress(old, Achievements.byId("weekly_gold")!!))
        assertFalse(Achievements.isUnlocked(old, "replay_record"))
    }

    @Test
    fun newAchievementCountersAreClampedOnLoad() {
        val file = tempProfileFile()
        file.writeText("""{"achievementCounters":{
            "STEAM_VARIANTS":999,"SILICON_VARIANTS":-20,
            "BOSS_SHIELD":42,"BARRAGE_EIGHT":24,
            "WEEKLY_GOLD":111,"CUSTOM_THREE":11,"REPLAY_RECORD":33
        }}""")
        val p = Profile(file)
        p.load()
        assertEquals(3L, p.achievementCounters[AchievementMetric.STEAM_VARIANTS.name])
        assertEquals(0L, p.achievementCounters[AchievementMetric.SILICON_VARIANTS.name])
        for (key in listOf("BOSS_SHIELD", "BARRAGE_EIGHT", "WEEKLY_GOLD",
            "CUSTOM_THREE", "REPLAY_RECORD"))
            assertEquals(1L, p.achievementCounters[key])
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
