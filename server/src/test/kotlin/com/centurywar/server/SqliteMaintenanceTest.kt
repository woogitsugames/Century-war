package com.centurywar.server

import com.centurywar.game.SimVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class SqliteMaintenanceTest {
    @TempDir lateinit var directory: Path

    private fun input(player: String, score: Long) = ScoreSubmission(
        board = "endless", player = player, nick = "Gracz",
        gameVersion = SimVersion.PROTOCOL, seed = 42L,
        replay = "test-only", score = score, research = List(13) { 0 },
    )

    @Test fun vacuumIntoCapturesOnlineStateAndRestoreRequiresStoppedServer() {
        val db = directory.resolve("leaderboards.db")
        // Apostrof w ścieżce wymaga poprawnego cytowania literału VACUUM INTO.
        val backup = directory.resolve("external/backup's-copy.sqlite")
        val firstPlayer = UUID.randomUUID().toString()
        val secondPlayer = UUID.randomUUID().toString()
        val now = 1_800_000_000_000L

        DatabaseMaintenance.acquireLock(db).use {
            SqliteStore("jdbc:sqlite:$db").use { store ->
                store.save(input(firstPlayer, 120L), VerifiedReplay("endless", 120L, 900), now, "hash1")
                val output = StringBuilder()
                assertEquals(0, runAdmin(listOf("backup", backup.toString()), store, output, StringBuilder()))
                assertTrue(output.contains("backup:"))
                assertTrue(Files.size(backup) > 0L)
                store.save(input(secondPlayer, 200L), VerifiedReplay("endless", 200L, 900), now, "hash2")
                assertEquals(2L, store.countScores())

                val errors = StringBuilder()
                assertEquals(1, runRestore(listOf("restore", backup.toString()), db, StringBuilder(), errors))
                assertTrue(errors.contains("najpierw zatrzymaj"))
                assertEquals(2L, store.countScores())

                val existingBackupError = StringBuilder()
                assertEquals(1, runAdmin(
                    listOf("backup", backup.toString()), store, StringBuilder(), existingBackupError,
                ))
                assertTrue(existingBackupError.contains("już istnieje"))
            }
        }

        val restoreOutput = StringBuilder()
        assertEquals(0, runRestore(listOf("restore", backup.toString()), db, restoreOutput, StringBuilder()))
        assertTrue(restoreOutput.contains("restored:"))
        assertTrue(restoreOutput.contains("previous-files:"))
        SqliteStore("jdbc:sqlite:$db").use { store ->
            assertEquals(1L, store.countScores())
            assertEquals(1, store.top("endless", "all", now).size)
            assertEquals(120L, store.top("endless", "all", now).single().score)
        }
        val rescue = restoreOutput.lines().first { it.startsWith("previous-files:") }
            .substringAfter("previous-files: ").trim()
        assertTrue(Files.isRegularFile(Path.of(rescue).resolve("leaderboards.db")))
    }

    @Test fun invalidBackupDoesNotReplaceExistingDatabase() {
        val db = directory.resolve("live.db")
        val invalid = directory.resolve("not-sqlite.sqlite")
        val now = 1_800_000_000_000L
        val player = UUID.randomUUID().toString()
        SqliteStore("jdbc:sqlite:$db").use { store ->
            store.save(input(player, 70L), VerifiedReplay("endless", 70L, 900), now, "hash3")
        }
        Files.writeString(invalid, "not a valid SQLite database")
        val errors = StringBuilder()
        assertEquals(1, runRestore(listOf("restore", invalid.toString()), db, StringBuilder(), errors))
        assertTrue(errors.contains("Błąd przywracania"))
        SqliteStore("jdbc:sqlite:$db").use { store ->
            assertEquals(1L, store.countScores())
            assertFalse(store.top("endless", "all", now).isEmpty())
        }
        assertEquals(2, runRestore(listOf("restore"), db, StringBuilder(), StringBuilder()))
        assertEquals(1, runRestore(listOf("restore", db.toString()), db, StringBuilder(), StringBuilder()))
    }
}
