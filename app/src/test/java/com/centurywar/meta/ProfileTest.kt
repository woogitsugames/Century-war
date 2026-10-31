package com.centurywar.meta

import com.centurywar.engine.Palette
import com.centurywar.engine.Ui
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
    fun replayIdIsOptionalBackwardCompatibleAndSurvivesJson() {
        val f = tmpFile()
        val board = Profile.BOARD_ENDLESS
        // Pełny profil v3 (ścisły odczyt) z wpisem rankingu sprzed pola "r".
        Profile(f).save()
        val json = org.json.JSONObject(f.readText())
        json.put("boards", org.json.JSONObject("""{"endless":[{"n":"Stary","s":90,"d":12}]}"""))
        f.writeText(json.toString())
        val old = Profile(f).also { it.load() }
        assertEquals(null, old.top(board).single().replay)
        assertTrue(old.submit(board, 200L, 13L, "battle_001"))
        old.save()
        val again = Profile(f).also { it.load() }
        assertEquals("battle_001", again.top(board).first().replay)
        assertEquals(null, again.top(board).last().replay)
        // ID jest wyłącznie nazwą pliku, nie może zawierać ścieżek.
        again.submit(board, 100L, 14L, "../invalid")
        assertEquals(null, again.top(board).first { it.score == 100L }.replay)
    }

    @Test
    fun standardPalettePreservesLegacyColors() {
        Palette.select(Palette.STANDARD)
        assertEquals(0xFF408CEB.toInt(), Ui.PLAYER)
        assertEquals(0xFFDC463C.toInt(), Ui.ENEMY)
        assertEquals(0xFF76E278.toInt(), Ui.GOOD)
        assertEquals(0xFFF05C50.toInt(), Ui.BAD)
        assertEquals(0xEE1A2030.toInt(), Ui.PANEL)
        assertEquals(0xFF1A1620.toInt(), Ui.OUTLINE)
        assertEquals(0xFF409646.toInt(), Ui.BTN_GREEN)
        Palette.select(Palette.DEUTAN_PROTAN)
        assertTrue(Ui.PLAYER != Ui.ENEMY)
        assertTrue(Ui.GOOD != Ui.BAD)
        Palette.select(Palette.TRITAN)
        assertTrue(Ui.PLAYER != Ui.ENEMY)
        Palette.select(Palette.HIGH_CONTRAST)
        assertEquals(0xFA080C16.toInt(), Ui.PANEL)
        Palette.select(Palette.STANDARD)
    }

    @Test
    fun accessibilityRoundTripAndLegacyDefaults() {
        val f = tmpFile()
        val p = Profile(f)
        assertEquals(0, p.palette)
        assertEquals(100, p.textScale)
        assertFalse(p.teamMarkers)
        assertFalse(p.reduceShake)
        assertFalse(p.reduceFlash)
        p.palette = 3
        p.textScale = 130
        p.teamMarkers = true
        p.reduceShake = true
        p.reduceFlash = true
        p.save()
        val restored = Profile(f).also { it.load() }
        assertEquals(3, restored.palette)
        assertEquals(130, restored.textScale)
        assertTrue(restored.teamMarkers)
        assertTrue(restored.reduceShake)
        assertTrue(restored.reduceFlash)

        f.writeText("""{"v":1,"name":"stary profil"}""")
        val legacy = Profile(f).also { it.load() }
        assertEquals(0, legacy.palette)
        assertEquals(100, legacy.textScale)
        assertFalse(legacy.teamMarkers)
        assertFalse(legacy.reduceShake)
        assertFalse(legacy.reduceFlash)
    }

    @Test
    fun accessibilityClampsUntrustedSettings() {
        val f = tmpFile()
        f.writeText("""{"palette":99,"textScale":999,"teamMarkers":true,"reduceShake":true,"reduceFlash":true}""")
        val high = Profile(f).also { it.load() }
        assertEquals(3, high.palette)
        assertEquals(130, high.textScale)
        assertTrue(high.teamMarkers)
        assertTrue(high.reduceShake)
        assertTrue(high.reduceFlash)

        f.writeText("""{"palette":-8,"textScale":-40}""")
        val low = Profile(f).also { it.load() }
        assertEquals(0, low.palette)
        assertEquals(100, low.textScale)
        assertFalse(low.teamMarkers)
        low.textScale = 115
        assertEquals(115, low.textScale)
        low.textScale = 999
        assertEquals(130, low.textScale)
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

    @Test
    fun screenShakeDefaultsPersistsAndClamps() {
        val f = tmpFile()
        val p = Profile(f)
        assertEquals(2, p.screenShake)
        p.screenShake = 1
        p.save()
        assertEquals(1, Profile(f).also { it.load() }.screenShake)

        // Zapis v3 sprzed dodania ustawienia nadal musi być poprawny.
        val old = org.json.JSONObject(p.toJson())
        old.remove("screenShake")
        f.writeText(old.toString())
        assertEquals(2, Profile(f).also { it.load() }.screenShake)

        old.put("screenShake", -5)
        f.writeText(old.toString())
        assertEquals(0, Profile(f).also { it.load() }.screenShake)
        old.put("screenShake", 100)
        f.writeText(old.toString())
        assertEquals(2, Profile(f).also { it.load() }.screenShake)
    }

}
