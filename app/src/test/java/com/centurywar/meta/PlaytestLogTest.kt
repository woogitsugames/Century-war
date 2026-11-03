package com.centurywar.meta

import com.centurywar.game.SaveGuard
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

/** R2-04: lokalny dziennik testu: dni, sesje, wydajność, odczyt i zapis. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaytestLogTest {
    private val day = 24L * 3600L * 1000L
    private val min = 60L * 1000L
    /** 10.11.2026 12:00 UTC. */
    private val t0 = 20_402L * day + 12L * 3600L * 1000L

    private fun file(): File = File(Files.createTempDirectory("cw-playtest").toFile(), PlaytestLog.FILE_NAME)

    @Test
    fun daysAndSessions() {
        val log = PlaytestLog(file())
        log.load()
        log.startSession(t0, 0L)
        assertEquals(1, log.sessions)
        assertEquals(1, log.daysPlayed)
        assertEquals(20_402L, log.firstDay)

        // 5 minut w tle: ta sama sesja.
        log.paused(t0 + 10 * min)
        log.resumed(t0 + 15 * min, 0L)
        assertEquals(1, log.sessions)
        // 31 minut w tle: nowa sesja.
        log.paused(t0 + 20 * min)
        log.resumed(t0 + 51 * min, 0L)
        assertEquals(2, log.sessions)
        assertEquals("Ten sam dzień UTC", 1, log.daysPlayed)
        assertEquals("Czas na pierwszym planie: 10 + 5 min", 15L * 60L, log.foregroundSeconds)

        // Zmiana dnia UTC (12 godzin później, po północy).
        log.paused(t0 + 60 * min)
        log.resumed(t0 + 12L * 3600L * 1000L + 5 * min, 0L)
        assertEquals(3, log.sessions)
        assertEquals(2, log.daysPlayed)
        assertEquals(20_403L, log.lastDay)
        // Powrót z cofniętym zegarem nie dolicza dnia.
        log.paused(t0 + 13L * 3600L * 1000L)
        log.resumed(t0 + 13L * 3600L * 1000L + min, 0L)
        log.battleSettled(false, true, false, 50L, true, t0 - 3 * day)
        assertEquals(2, log.daysPlayed)
        // Kolejne uruchomienie gry to zawsze nowa sesja.
        log.startSession(t0 + 2 * day, 0L)
        assertEquals(4, log.sessions)
        assertEquals(3, log.daysPlayed)
    }

    @Test
    fun battlesStreaksAndMilestones() {
        val log = PlaytestLog(file())
        log.startSession(t0, 0L)
        log.battleSettled(true, false, false, 100L, false, t0)
        log.battleSettled(true, false, true, 150L, false, t0)
        log.battleSettled(false, false, false, 180L, false, t0)
        assertEquals("Porażka poza kampanią nie zmienia serii kampanii", 2, log.lossStreak)
        log.battleSettled(true, true, false, 240L, true, t0)
        assertEquals(0, log.lossStreak)
        assertEquals(2, log.maxLossStreak)
        assertEquals(1, log.surrenders)
        assertEquals(240L, log.firstWinSeconds)
        assertEquals(240L, log.tutorialSeconds)
        log.battleSettled(true, true, false, 500L, true, t0)
        assertEquals("Pierwsza wygrana liczy się raz", 240L, log.firstWinSeconds)

        // Stary profil: czasów sprzed dziennika nie znamy.
        val old = PlaytestLog(file())
        old.load()
        old.adoptProfile(tutorialDone = true, wins = 3)
        assertEquals(PlaytestLog.BEFORE_LOG, old.tutorialSeconds)
        assertEquals(PlaytestLog.BEFORE_LOG, old.firstWinSeconds)
    }

    @Test
    fun perfKeepsOnlyFiveSessions() {
        val log = PlaytestLog(file())
        for (s in 0 until 7) {
            log.startSession(t0 + s * day, s * 100L)
            log.perfSample(10f + s, s, s * 100L + 7L, s % 3)
            log.perfSample(5f, 0, s * 100L + 9L, 0)
        }
        val perf = log.perfSessions()
        assertEquals(PlaytestLog.PERF_SESSIONS, perf.size)
        assertEquals("Najnowsza sesja pierwsza", 16f, perf[0].worstP95Ms, 0.01f)
        assertEquals(6, perf[0].maxReduction)
        assertEquals("Zgubione kroki od początku sesji", 9L, perf[0].droppedSteps)
        assertEquals(12f, perf[4].worstP95Ms, 0.01f)
    }

    @Test
    fun saveAndLoadInTempDirectory() {
        val f = file()
        val log = PlaytestLog(f)
        log.load()
        assertTrue(log.fresh)
        log.startSession(t0, 0L)
        log.screenVisited(PlaytestLog.SCREEN_CODEX)
        log.screenVisited(PlaytestLog.SCREEN_WEEKLY)
        log.screenVisited(-1)
        log.battleSettled(true, false, true, 30L, false, t0)
        log.perfSample(21.5f, 2, 3L, 1)
        log.paused(t0 + 5 * min)
        log.saveAsync()
        assertTrue(log.awaitWrites(5_000L))
        assertTrue(f.isFile)

        val copy = PlaytestLog(f)
        copy.load()
        assertFalse(copy.fresh)
        assertEquals(1, copy.sessions)
        assertEquals(1, copy.daysPlayed)
        assertEquals(1, copy.surrenders)
        assertEquals(1, copy.lossStreak)
        assertEquals((1 shl PlaytestLog.SCREEN_CODEX) or (1 shl PlaytestLog.SCREEN_WEEKLY), copy.screensVisited)
        assertEquals(300L, copy.foregroundSeconds)
        val perf = copy.perfSessions()
        assertEquals(1, perf.size)
        assertEquals(21.5f, perf[0].worstP95Ms, 0.01f)
        assertEquals(2, perf[0].maxReduction)
        // Kolejne uruchomienie dopisuje sesję wydajności obok wczytanej.
        copy.startSession(t0 + day, 0L)
        assertEquals(2, copy.perfSessions().size)
        assertEquals(2, copy.sessions)
        assertEquals(2, copy.daysPlayed)
        copy.close(); log.close()
    }

    @Test
    fun corruptFileStartsFromZeroWithoutWarning() {
        SaveGuard.resetWarningForTests()
        for (content in listOf("{nie json", "", JSONObject().put("v", 99).toString(),
                JSONObject().put("v", 1).put("firstDay", 5).toString())) {
            val f = file().apply { writeText(content) }
            val log = PlaytestLog(f)
            log.load()
            assertTrue(content, log.fresh)
            assertEquals(0, log.sessions)
            assertEquals(0, log.daysPlayed)
            assertEquals(-1L, log.firstDay)
            assertEquals(PlaytestLog.NOT_YET, log.tutorialSeconds)
            assertEquals(0, log.perfSessions().size)
        }
        assertFalse("Uszkodzony dziennik nie ostrzega gracza", SaveGuard.takeWarning())
    }
}
