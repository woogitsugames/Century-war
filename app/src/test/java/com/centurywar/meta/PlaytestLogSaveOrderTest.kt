package com.centurywar.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch

/**
 * D49: `playtest.json` zapisują dwa wątki (gra: przypomnienie, ekrany; `crash-io`: liczniki
 * awarii z d48). Plik po wszystkich zapisach musi odpowiadać stanowi po wszystkich zmianach,
 * a nie starszej migawce dołożonej do kolejki później.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaytestLogSaveOrderTest {
    private val day = 24L * 3600L * 1000L
    private val t0 = 20_402L * day + 12L * 3600L * 1000L

    @Test
    fun concurrentSavesLeaveNewestStateOnDisk() {
        repeat(20) { round ->
            val file = File(Files.createTempDirectory("cw-playtest-order").toFile(), PlaytestLog.FILE_NAME)
            val log = PlaytestLog(file)
            log.load()
            log.startSession(t0, 0L)
            val start = CountDownLatch(1)
            val crashIo = Thread {
                start.await()
                for (i in 1..200) {
                    log.countCrashes(listOf(Pair(t0 + i, i % 3 == 0)))
                    log.saveAsync()
                }
            }.apply { priority = Thread.MIN_PRIORITY }
            val gameThread = Thread {
                start.await()
                for (i in 0 until 200) {
                    log.screenVisited(i % PlaytestLog.SCREEN_NAMES.size)
                    if (i == 150) log.reminderShown(TesterReportReminder.mask(TesterReportReminder.DAY_FIRST))
                    log.saveAsync()
                }
            }
            crashIo.start(); gameThread.start()
            start.countDown()
            crashIo.join(); gameThread.join()
            assertTrue(log.awaitWrites(5_000L))
            assertEquals("runda $round", log.toJson(), file.readText(Charsets.UTF_8))
            val reread = PlaytestLog(file).also { it.load() }
            assertEquals(PlaytestLog.REMINDER_DAY3, reread.reminders and PlaytestLog.REMINDER_DAY3)
            assertEquals(200, reread.crashes + reread.anrs)
        }
    }
}
