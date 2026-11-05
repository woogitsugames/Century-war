package com.centurywar.crash

import com.centurywar.engine.Game
import com.centurywar.engine.Host
import com.centurywar.engine.Quality
import com.centurywar.game.Mode
import com.centurywar.meta.PlaytestLog
import com.centurywar.meta.Profile
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
import java.nio.file.Files
import java.util.Random

/**
 * D48 (R13 TOP 5 poz. 1): Raport v2 tylko w wersjach testowych: losowy kod testera,
 * `Report format: 2`, awarie i ANR z lokalnego dziennika awarii, mapa 28 dni i badania.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaytestReportV2Test {
    private val day = 86_400_000L
    /** 10.11.2026 12:00 UTC. */
    private val t0 = 20_402L * day + 12L * 3600L * 1000L

    private val host = object : Host {
        override fun vibrate(ms: Long) {}
        override fun requestText(title: String, current: String, onResult: (String) -> Unit) {}
        override fun applyRenderScale(scale: Float) {}
        override fun applyFrameRate(fps: Int) {}
        override fun exit() {}
    }

    private fun dir(): File = Files.createTempDirectory("cw-d48").toFile()

    private fun game(d: File, test: Boolean, now: Long = t0): Game {
        val profile = Profile(File(d, "profile.json"))
        profile.tutorialDone = true
        val g = Game(host, profile, Quality.LOW).apply {
            density = 2f
            resize(1600, 720, 1600, 720)
            testerSurveyEnabled = test
            clock = { now }
        }
        g.playtest = PlaytestLog(File(d, PlaytestLog.FILE_NAME)).also { it.load() }
        return g
    }

    @Test
    fun testerCodeIsShortRandomStableAndValidated() {
        val f = File(dir(), PlaytestLog.FILE_NAME)
        val log = PlaytestLog(f)
        log.startSession(t0, 0L)
        val code = log.ensureTesterCode(Random(7))
        assertEquals(PlaytestLog.TESTER_CODE_LENGTH, code.length)
        assertTrue(code.all { it in PlaytestLog.TESTER_CODE_CHARS })
        for (bad in "0O1IL2Z5S6G8B") assertFalse("mylący znak $bad", bad in PlaytestLog.TESTER_CODE_CHARS)
        assertEquals("raz wylosowany", code, log.ensureTesterCode(Random(99)))
        // Różne ziarna dają różne kody (losowy, nie z urządzenia).
        val other = PlaytestLog(File(dir(), PlaytestLog.FILE_NAME)).ensureTesterCode(Random(8))
        assertNotEquals(code, other)
        SaveGuardHelper.write(f, log.toJson())
        val again = PlaytestLog(f).also { it.load() }
        assertEquals(code, again.testerCode)
        // Zły kod w pliku: odrzucony, reszta dziennika zostaje.
        val o = JSONObject(log.toJson()).put("code", "O0I1")
        SaveGuardHelper.write(f, o.toString())
        val broken = PlaytestLog(f).also { it.load() }
        assertEquals("", broken.testerCode)
        assertEquals(1, broken.sessions)
    }

    @Test
    fun codeOnlyInTestBuildAndNeverInProfile() {
        val d = dir()
        val g = game(d, test = true)
        g.playtestStarted(t0)
        val code = g.playtest!!.testerCode
        assertTrue(PlaytestLog.validTesterCode(code))
        val text = PlaytestReportText.build("0.1.0 (1)", "Acme", "15", PlaytestReportText.collect(g))
        assertTrue(text.contains("\nReport format: 2\nTester code: $code\nCrashes: 0\nANRs: 0\n\n[Tester survey]"))
        // Kod nie trafia do profilu (ani jego wersji chmurowej, która powstaje z toJson).
        assertFalse(g.profile.toJson().contains(code))
        assertFalse(g.profile.toJson().contains("testerCode"))

        val prod = game(dir(), test = false)
        prod.playtestStarted(t0)
        assertEquals("", prod.playtest!!.testerCode)
        val v1 = PlaytestReportText.build("1.0.0 (1)", "Acme", "15", PlaytestReportText.collect(prod))
        for (label in listOf("Report format", "Tester code", "Crashes:", "ANRs:", "Day map", "Research levels"))
            assertFalse(label, v1.contains(label))
    }

    @Test
    fun crashesAndAnrsCountedOnceSinceLogStart() {
        val d = dir()
        val store = CrashStore(File(d, "crash"))
        val log = PlaytestLog(File(d, PlaytestLog.FILE_NAME))
        log.startSession(t0, 0L)
        store.write(true, "java", eventTime = t0 - 3 * day) // sprzed dziennika
        store.write(true, "Przyczyna: ANR (6)\n", exit = true, eventTime = t0 + 1000L)
        store.write(true, "Przyczyna: CRASH_NATIVE (5)\n", exit = true, eventTime = t0 + 2000L)
        store.write(true, "java", eventTime = t0 + 3000L)
        store.write(false, "caught", eventTime = t0 + 4000L) // złapany błąd: nie awaria
        store.write(false, "Przyczyna: LOW_MEMORY (3)\n", exit = true, eventTime = t0 + 5000L)
        assertTrue(log.countCrashes(store.fatalEvents()))
        assertEquals(2, log.crashes)
        assertEquals(1, log.anrs)
        assertFalse("drugi odczyt nic nie dolicza", log.countCrashes(store.fatalEvents()))
        store.write(true, "Przyczyna: ANR (6)\n", exit = true, eventTime = t0 + 9000L)
        assertTrue(log.countCrashes(store.fatalEvents()))
        assertEquals(2, log.anrs)
        val f = File(d, "copy.json")
        SaveGuardHelper.write(f, log.toJson())
        val again = PlaytestLog(f).also { it.load() }
        assertEquals(2, again.crashes)
        assertFalse("znacznik przetrwał zapis", again.countCrashes(store.fatalEvents()))
    }

    @Test
    fun dayMapCoversLast28DaysAndMigratesOldLog() {
        val log = PlaytestLog(File(dir(), PlaytestLog.FILE_NAME))
        log.startSession(t0, 0L)
        log.startSession(t0 + 2 * day, 0L)
        log.startSession(t0 + 3 * day, 0L)
        log.startSession(t0 + 40 * day, 0L) // przerwa dłuższa niż 28 dni
        log.startSession(t0 + 41 * day, 0L)
        val today = 20_402L + 43
        val map = log.dayMap(today)
        assertEquals(PlaytestLog.DAY_MAP_DAYS, map.length)
        assertEquals("0".repeat(24) + "1100", map)
        val early = PlaytestLog(File(dir(), PlaytestLog.FILE_NAME))
        early.startSession(t0, 0L)
        early.startSession(t0 + 2 * day, 0L)
        assertEquals("0".repeat(25) + "101", early.dayMap(20_404L))
        // Dziennik sprzed d48 (bez dayBits): znane tylko pierwszy i ostatni dzień.
        val o = JSONObject(early.toJson()).apply { remove("dayBits") }
        val f = File(dir(), PlaytestLog.FILE_NAME)
        SaveGuardHelper.write(f, o.toString())
        val old = PlaytestLog(f).also { it.load() }
        assertEquals("0".repeat(25) + "101", old.dayMap(20_404L))
        assertEquals("0".repeat(28), PlaytestLog(File(dir(), "x.json")).dayMap(20_404L))
    }

    @Test
    fun researchLevelsAndDayMapInReportWithinLimit() {
        val g = game(dir(), test = true, now = t0 + day)
        g.playtestStarted(t0)
        g.playtest!!.startSession(t0 + day, 0L)
        g.profile.medals = 1234
        g.profile.recordBattle(Mode.CAMPAIGN, true, 60)
        val text = PlaytestReportText.build("0.1.0 (1)", "Acme", "15", PlaytestReportText.collect(g))
        assertTrue(text, Regex("\\nResearch levels: 0 of \\d+, medals 1234\\n").containsMatchIn(text))
        assertTrue(text, text.contains("\nDay map (28 days, oldest first, 1 = played): " + "0".repeat(26) + "11\n"))
        assertTrue(text.length <= PlaytestReportText.MAX_CHARS)
        assertTrue(text.trimEnd().endsWith("Battle speed x1/2: no"))
    }
}

/** Zapis pliku w teście bez wątku I/O dziennika. */
private object SaveGuardHelper {
    fun write(f: File, text: String) {
        f.parentFile?.mkdirs()
        f.writeText(text, Charsets.UTF_8)
    }
}
