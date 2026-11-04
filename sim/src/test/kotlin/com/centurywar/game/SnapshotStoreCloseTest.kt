package com.centurywar.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * D41: wątek „snapshot-io” z [SnapshotStore] jest zamykany (MainActivity.onDestroy) bez utraty
 * zleconych zapisów; wywołania po zamknięciu wykonują się na miejscu i nie rzucają wyjątków.
 */
class SnapshotStoreCloseTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun ioThreads(): Int = Thread.getAllStackTraces().keys.count { it.name == "snapshot-io" && it.isAlive }

    private fun waitThreadsAtMost(limit: Int): Boolean {
        val end = System.nanoTime() + 5_000_000_000L
        while (ioThreads() > limit && System.nanoTime() < end) Thread.sleep(5)
        return ioThreads() <= limit
    }

    @Test
    fun closeWithoutUseCreatesNoThread() {
        val store = SnapshotStore(tmp.newFolder().resolve("battle.snap"))
        store.close()
        assertTrue(store.isClosed)
        assertFalse(store.ioStarted)
        assertTrue(store.awaitWrites(100L))
        assertFalse("close() nie tworzy wątku", store.ioStarted)
    }

    @Test
    fun queuedWritesFinishAfterCloseAndThreadEnds() {
        val before = ioThreads()
        val file = tmp.newFolder().resolve("battle.snap")
        val store = SnapshotStore(file)
        var last = ByteArray(0)
        for (i in 1..20) {
            last = ByteArray(64) { (it + i).toByte() }
            store.saveAsync(last, 7L, byteArrayOf(i.toByte()))
        }
        store.close()
        assertTrue("zadania z kolejki kończą się", store.awaitWrites(10_000L))
        assertArrayEquals(last, file.readBytes())
        assertArrayEquals(byteArrayOf(20), store.metaFile.readBytes())
        assertTrue("wątek snapshot-io kończy się po close()", waitThreadsAtMost(before))
    }

    @Test
    fun callsAfterCloseRunInlineWithoutThrowing() {
        val before = ioThreads()
        val file = tmp.newFolder().resolve("battle.snap")
        val store = SnapshotStore(file)
        val a = ByteArray(32) { 1 }
        val b = ByteArray(32) { 2 }
        store.saveAsync(a, 5L)
        store.close()
        // Zapis po zamknięciu (np. wywołanie zwrotne po onDestroy) nie przepada,
        // a starszy zapis z kolejki go nie nadpisuje.
        store.saveAsync(b, 5L)
        assertTrue(store.awaitWrites(10_000L))
        assertArrayEquals(b, file.readBytes())
        assertTrue(store.saveNow(a, 6L))
        assertArrayEquals(a, file.readBytes())
        // Usunięcie rozliczonej bitwy po zamknięciu nadal usuwa pliki.
        assertTrue(store.deleteSettledAsync(6L))
        assertFalse(file.exists())
        store.saveAsync(b, 8L)
        assertTrue(file.exists())
        store.deleteAsync()
        assertFalse(file.exists())
        store.saveAsync(b, 9L)
        store.delete()
        assertFalse(file.exists())
        store.close()
        assertTrue(waitThreadsAtMost(before))
    }
}
