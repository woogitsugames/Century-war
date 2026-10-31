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
    fun legacyProfileMigratesBranchPreferences() {
        val f = tmpFile()
        f.writeText("""{"v":1,"medals":100,"stars":[2,1]}""")
        val old = Profile(f).also { it.load() }
        assertEquals(0, old.preferredVariant(4))
        assertEquals(0, old.preferredVariant(6))
        old.rememberVariant(4, 1)
        old.rememberVariant(6, 1)
        val fresh = Profile(f).also { it.load() }
        assertEquals(100, fresh.medals)
        assertEquals(1, fresh.preferredVariant(4))
        assertEquals(1, fresh.preferredVariant(6))
        f.writeText("""{"v":2,"steamPreference":900,"siliconPreference":-20}""")
        val invalid = Profile(f).also { it.load() }
        assertEquals(1, invalid.preferredVariant(4))
        assertEquals(0, invalid.preferredVariant(6))
    }

    @Test
    fun loadFallsBackToTempFileWhenMainIsMissing() {
        val f = tmpFile()
        Profile(f).apply { medals = 321; save() }
        assertTrue(f.renameTo(File(f.parentFile, f.name + ".tmp")))
        val p = Profile(f).also { it.load() }
        assertEquals(321, p.medals)
    }
}
