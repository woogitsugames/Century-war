package com.centurywar.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/** Profil używa org.json z Androida, więc testy idą przez Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileTest {

    private fun tmpFile(): File = File(Files.createTempDirectory("cw").toFile(), "profile.json")

    @Test
    fun personalBestSurvivesRename() {
        val p = Profile(tmpFile())
        p.name = "Alfa"
        assertTrue(p.submit(Profile.BOARD_ENDLESS, 1000, 1))
        p.name = "Beta"
        assertFalse("słabszy wynik po zmianie nazwy to nie rekord", p.submit(Profile.BOARD_ENDLESS, 500, 2))
        assertTrue(p.submit(Profile.BOARD_ENDLESS, 1500, 3))
    }

    @Test
    fun weeklyTiersAreClaimedOnceAndSurviveSave() {
        val f = tmpFile()
        val p = Profile(f)
        assertEquals(0, p.claimWeekly(301L, 9, 301L))
        assertEquals(25, p.claimWeekly(301L, 10, 301L))
        assertEquals(0, p.claimWeekly(301L, 19, 301L))
        assertEquals(60, p.claimWeekly(301L, 20, 301L))
        assertEquals(120, p.claimWeekly(301L, 30, 301L))
        assertEquals(0, p.claimWeekly(301L, 40, 301L))
        assertEquals(7, p.weeklyClaimed)
        p.save()
        val loaded = Profile(f).also { it.load() }
        assertEquals(301L, loaded.weeklyWeek)
        assertEquals(7, loaded.weeklyClaimed)
        assertEquals(0, loaded.claimWeekly(301L, 35, 301L))
        // Opóźniona bitwa poprzedniego tygodnia nie daje progów ponownie.
        loaded.syncWeekly(302L)
        assertEquals(0, loaded.claimWeekly(301L, 35, 302L))
        assertEquals(25, loaded.claimWeekly(302L, 10, 302L))
        loaded.save()
        val reload = Profile(f).also { it.load() }
        assertEquals(7, reload.weeklyClaimedFor(301L))
        assertEquals(1, reload.weeklyClaimedFor(302L))
    }

    @Test
    fun weeklyBoardsKeepOnlyLastEightWeeks() {
        val p = Profile(tmpFile())
        for (w in 200L..212L) p.submit(Profile.weeklyBoard(w), w * 10L, w * 7L)
        p.syncWeekly(212L)
        for (w in 200L..204L) assertTrue(p.top(Profile.weeklyBoard(w)).isEmpty())
        for (w in 205L..212L) assertEquals(1, p.top(Profile.weeklyBoard(w)).size)
        p.save()
        val loaded = Profile(tmpFile())
        // Załadowanie starego JSON nie może wyzerować starego typu rankingu.
        loaded.fromJson(p.toJson())
        assertEquals(8, loaded.boards.keys.count { it.startsWith("weekly_") })
        assertEquals(0, loaded.claimWeekly(203L, 30, 212L))
    }

    @Test
    fun weeklyStateDefaultsAndMasksAreClamped() {
        val f = tmpFile()
        f.writeText("""{"weeklyWeek":-99,"weeklyClaimed":255,"weeklyClaims":{"3":255,"-9":7}}""")
        val old = Profile(f).also { it.load() }
        assertEquals(-1L, old.weeklyWeek)
        assertEquals(0, old.weeklyClaimed)
        assertEquals(7, old.weeklyClaimedFor(3L))
        assertEquals(0, old.weeklyClaimedFor(-9L))

        f.writeText("""{"weeklyWeek":999,"weeklyClaimed":255}""")
        val loaded = Profile(f).also { it.load() }
        assertEquals(999L, loaded.weeklyWeek)
        assertEquals(7, loaded.weeklyClaimed)
        loaded.syncWeekly(1000L)
        assertEquals(0, loaded.weeklyClaimed)
        assertEquals(7, loaded.weeklyClaimedFor(999L))

        f.writeText("""{"medals":7}""")
        val legacy = Profile(f).also { it.load() }
        assertEquals(-1L, legacy.weeklyWeek)
        assertEquals(0, legacy.weeklyClaimed)
        assertEquals(7, legacy.medals)
    }

    @Test
    fun concurrentSavesLeaveValidFile() {
        val f = tmpFile()
        val p = Profile(f).apply { medals = 777; name = "x".repeat(16) }
        repeat(30) { p.submit(Profile.BOARD_SKIRMISH, it.toLong() * 1000, 1) }
        val threads = List(4) { Thread { repeat(150) { p.save() } } }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        val q = Profile(f).also { it.load() }
        assertEquals(777, q.medals)
        assertEquals(20, q.top(Profile.BOARD_SKIRMISH).size)
    }

    @Test
    fun loadClampsOutOfRangeValues() {
        val f = tmpFile()
        f.writeText("""{"medals":-5,"skirmishDiff":9,"quality":7,"fpsCap":45,"stars":[5,-1,2]}""")
        val p = Profile(f).also { it.load() }
        assertEquals(0, p.medals)
        assertEquals(3, p.skirmishDiff)
        assertEquals(0, p.quality)
        assertEquals(0, p.fpsCap)
        assertEquals(3, p.stars[0])
        assertEquals(0, p.stars[1])
        assertEquals(2, p.stars[2])
    }


    @Test
    fun remindersRoundTripAndOldProfilesGetDefaults() {
        val f = tmpFile()
        f.writeText("""{"wins":2,"notifyHour":99,"notifications":false}""")
        val invalid = Profile(f).also { it.load() }
        assertEquals(23, invalid.notifyHour)
        assertFalse(invalid.notifications)
        invalid.notifyHour = 7
        invalid.save()
        val loaded = Profile(f).also { it.load() }
        assertEquals(7, loaded.notifyHour)
        assertFalse(loaded.notifications)

        f.writeText("""{"wins":1}""")
        val old = Profile(f).also { it.load() }
        assertTrue(old.notifications)
        assertEquals(18, old.notifyHour)

        f.writeText("""{"notifyHour":-50}""")
        assertEquals(0, Profile(f).also { it.load() }.notifyHour)
    }

    @Test
    fun migrationKeepsLegacyStarsAndLeaderboard() {
        val f = tmpFile()
        val legacy = IntArray(32) { (it % 3) + 1 }
        val json = """{"v":1,"medals":117,"stars":[${legacy.joinToString(",")}],
            "boards":{"campaign":[{"n":"Komendant","s":1234,"d":17}]}}"""
        f.writeText(json)
        val p = Profile(f).also { it.load() }
        assertEquals(36, p.stars.size)
        assertEquals(legacy[7], p.stars[7])
        for (i in 8..11) assertEquals(0, p.stars[i])
        for (i in 8 until 32) assertEquals("Stary etap ${i + 1}", legacy[i], p.stars[i + 4])
        assertEquals(legacy.sum(), p.totalStars())
        assertEquals(1234L, p.top(Profile.BOARD_CAMPAIGN).single().score)
        p.save()
        val reloaded = Profile(f).also { it.load() }
        assertEquals(p.stars.toList(), reloaded.stars.toList())
        assertEquals(legacy.sum(), reloaded.totalStars())
        assertEquals(117, reloaded.medals)
        assertEquals(1234L, reloaded.top(Profile.BOARD_CAMPAIGN).single().score)
    }

    @Test
    fun loadFallsBackToTempFileWhenMainIsMissing() {
        val f = tmpFile()
        Profile(f).apply { medals = 321; save() }
        assertTrue(f.renameTo(File(f.parentFile, f.name + ".tmp")))
        val p = Profile(f).also { it.load() }
        assertEquals(321, p.medals)
    }

    @Test
    fun musicVolumeDefaultsPersistsAndClamps() {
        val f = tmpFile()
        val p = Profile(f)
        assertEquals(0.5f, p.musicVolume, 0.0001f)
        p.musicVolume = 0.73f
        p.save()
        assertEquals(0.73f, Profile(f).also { it.load() }.musicVolume, 0.0001f)
        f.writeText("""{"musicVolume":5}""")
        assertEquals(1f, Profile(f).also { it.load() }.musicVolume, 0.0001f)
        f.writeText("""{"musicVolume":-2}""")
        assertEquals(0f, Profile(f).also { it.load() }.musicVolume, 0.0001f)
        f.writeText("""{"musicVolume":"NaN"}""")
        assertEquals(0.5f, Profile(f).also { it.load() }.musicVolume, 0.0001f)
        f.writeText("""{"name":"Stary profil"}""")
        assertEquals(0.5f, Profile(f).also { it.load() }.musicVolume, 0.0001f)
    }
}
