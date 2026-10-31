package com.centurywar.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CrashReportsTest {
    private fun tempDir(): File {
        val file = File.createTempFile("cw-crash-", ".tmp")
        check(file.delete() && file.mkdirs())
        return file
    }

    @Test
    fun rotatesByAgeAndPreservesUnrelatedFiles() {
        val dir = tempDir()
        try {
            val store = CrashStore(dir)
            for (i in 0 until 12) {
                val file = File(dir, "fatal-" + (100000 + i) + ".txt")
                file.writeText("log " + i)
                assertTrue(file.setLastModified(1_000L + i * 1_000L))
            }
            File(dir, "notes.txt").writeText("nie ruszaj")
            store.rotate()
            assertEquals(10, dir.listFiles()!!.count { it.name.startsWith("fatal-") })
            assertFalse(File(dir, "fatal-100000.txt").exists())
            assertFalse(File(dir, "fatal-100001.txt").exists())
            assertTrue(File(dir, "fatal-100011.txt").exists())
            assertTrue(File(dir, "notes.txt").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun writesAtMostTenReportsAndRejectsOtherPaths() {
        val dir = tempDir()
        try {
            val store = CrashStore(dir)
            for (i in 0 until 15) store.write(i % 2 == 0, "awaria " + i)
            assertEquals(10, dir.listFiles()!!.count { it.name.endsWith(".txt") })
            val newest = store.newest()
            assertNotNull(newest)
            assertNotNull(store.read(newest!!.name))
            assertNotNull(store.newest(fatalOnly = true))
            assertNull(store.read("../profile.json"))
            assertNull(store.read("profile.json"))
            assertNull(store.read("caught-123.txt/../profile.json"))
            val longFile = store.write(true, "X".repeat(CrashStore.MAX_CHARS + 500))
            assertEquals(CrashStore.MAX_CHARS, store.read(longFile.name)!!.length)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun reportOmitsExceptionMessagesAndPersonalIdentifiers() {
        val exception = IllegalStateException("MyNick secret-player-id-123")
        val report = CrashReportText.build(
            version = "0.4.0",
            model = "Pixel test",
            android = "16",
            screen = "BattleScreen",
            events = "- 5 ms temu: ekran BattleScreen\n",
            exception = exception,
        )
        assertTrue(report.contains("java.lang.IllegalStateException"))
        assertTrue(report.contains("BattleScreen"))
        assertTrue(report.contains("Pixel test"))
        assertTrue(report.contains("Stos wywołań"))
        assertFalse(report.contains("MyNick"))
        assertFalse(report.contains("secret-player-id-123"))
    }

    @Test
    fun eventRingKeepsOnlyThirtyAndReusesFixedStorage() {
        val ring = CrashEventRing()
        ring.add(CrashEventRing.START)
        for (i in 1..300) ring.add(CrashEventRing.BATTLE, i)
        val text = ring.snapshot()
        assertEquals(30, text.lines().count { it.startsWith("- ") })
        assertFalse(text.contains("etap 270\n"))
        assertTrue(text.contains("etap 300\n"))
        val old = ring.snapshot()
        ring.add(CrashEventRing.SCREEN, screen = "SettingsScreen")
        val recent = ring.snapshot()
        assertEquals(30, recent.lines().count { it.startsWith("- ") })
        assertTrue(recent.contains("SettingsScreen"))
        assertTrue(old.contains("etap 300"))
    }
}
