package com.centurywar.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class SaveGuard154Test {
    @get:Rule val folder = TemporaryFolder()

    @Test fun interruptedTempCannotReplaceCompleteSave() {
        val dir = folder.newFolder()
        val file = File(dir, "progress.json")
        val before = "poprawny-stary".toByteArray()
        val after = "poprawny-nowy".toByteArray()
        SaveGuard.atomicWrite(file, before)

        // Urwany zapis nie dotyka pliku głównego.
        File(dir, "progress.json.tmp").writeBytes("urwany".toByteArray())
        assertArrayEquals(before, file.readBytes())
        SaveGuard.atomicWrite(file, after)
        assertArrayEquals(after, file.readBytes())
        assertFalse(File(dir, "progress.json.tmp").exists())

        SaveGuard.writeBackup(file, after)
        assertArrayEquals(after, File(dir, "progress.json.bak").readBytes())
    }

    @Test fun unwritableDirectoryDoesNotDamagePreviousDataAndWarnsOnce() {
        SaveGuard.resetWarningForTests()
        val dir = folder.newFolder()
        val previous = File(dir, "profile.json")
        SaveGuard.atomicWrite(previous, "medale=20".toByteArray())
        val notDirectory = folder.newFile("regular-file")
        var rejected = false
        try {
            SaveGuard.atomicWrite(File(notDirectory, "profile.json"), "nowe".toByteArray())
        } catch (_: IOException) {
            rejected = true
        }
        assertTrue("Katalog będący plikiem musi przerwać zapis", rejected)
        assertArrayEquals("medale=20".toByteArray(), previous.readBytes())

        SaveGuard.report("profile", IOException("Dysk pełny"))
        assertTrue(SaveGuard.takeWarning())
        SaveGuard.report("history", IOException("Brak uprawnień"))
        assertFalse(SaveGuard.takeWarning())
        SaveGuard.resetWarningForTests()
    }
}
