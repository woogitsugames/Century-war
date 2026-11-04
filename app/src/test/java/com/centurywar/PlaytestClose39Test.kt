package com.centurywar

import android.os.Looper
import com.centurywar.meta.PlaytestLog
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * D39 (audyt): dziennik testu ma własny wątek zapisu. MainActivity.onDestroy zamyka go jak
 * historię bitew; inaczej każde wyjście i ponowne otwarcie gry w tym samym procesie zostawiało
 * kolejny bezczynny wątek „century-playtest”.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaytestClose39Test {

    @Test fun destroyClosesPlaytestWriter() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (!activity.gameReady && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("Gra uruchomiona", activity.gameReady)
        val log = activity.playtestLog
        assertNotNull(log)
        assertFalse(log!!.closed)
        val snapshots = activity.snapshotStore
        assertNotNull(snapshots)
        assertFalse(snapshots!!.isClosed)
        controller.pause().stop().destroy()
        assertTrue("onDestroy zamyka wątek dziennika", log.closed)
        // D41: wątek zrzutu bitwy („snapshot-io”) zamykany tak samo.
        assertTrue("onDestroy zamyka wątek zrzutu", snapshots.isClosed)
    }

    @Test fun closeKeepsQueuedWriteAndLaterCallsAreSafe() {
        val file = File(Files.createTempDirectory("cw-playtest39").toFile(), PlaytestLog.FILE_NAME)
        val log = PlaytestLog(file)
        log.load()
        log.startSession(1_000_000L, 0L)
        log.saveAsync()
        log.close()
        // Zapis zlecony przed zamknięciem kończy się mimo zamknięcia.
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!file.isFile && System.nanoTime() < end) Thread.sleep(5)
        assertEquals(1, JSONObject(file.readText()).getInt("sessions"))
        // Po zamknięciu: bez wyjątku i bez bariery.
        log.saveAsync()
        assertFalse(log.awaitWrites(100L))
    }
}
