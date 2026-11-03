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
    fun processExitsDoNotEvictJavaCrashesAndReportPrefersJavaCrash() {
        val dir = tempDir()
        try {
            val store = CrashStore(dir)
            store.write(true, "awaria Javy", eventTime = 1_000_000L)
            store.write(false, "złapany błąd", eventTime = 2_000_000L)
            // Pięć nowszych zakończeń procesu (np. brak pamięci) nie wypiera awarii Javy.
            for (i in 0 until 5) store.write(false, "brak pamięci " + i, exit = true, eventTime = 3_000_000L + i * 1000L)
            assertEquals(2, dir.listFiles()!!.count { !it.name.startsWith("exit-") })
            assertEquals(CrashStore.MAX_EXIT_LOGS, dir.listFiles()!!.count { it.name.startsWith("exit-") })
            // „Zgłoś problem” wybiera awarię Javy, nie nowsze zakończenie procesu.
            assertTrue(store.newest()!!.name.startsWith("caught-"))
            assertTrue(store.newest(fatalOnly = true)!!.name.startsWith("fatal-"))
            // ANR (exit-fatal) liczy się jak awaria w pytaniu po starcie.
            val anr = store.write(true, "ANR", exit = true, eventTime = 4_000_000L)
            assertEquals(anr.name, store.newest(fatalOnly = true)!!.name)
            assertNotNull(store.read(anr.name))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun exitReportIsUsedOnlyWhenNothingElseExists() {
        val dir = tempDir()
        try {
            val store = CrashStore(dir)
            val exit = store.write(false, "brak pamięci", exit = true)
            assertEquals(exit.name, store.newest()!!.name)
            assertNull(store.newest(fatalOnly = true))
            // Stare nazwy plików nadal są czytane.
            File(dir, "fatal-123.txt").writeText("stara awaria")
            assertEquals("stara awaria", store.read("fatal-123.txt"))
            assertEquals("fatal-123.txt", store.newest()!!.name)
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
