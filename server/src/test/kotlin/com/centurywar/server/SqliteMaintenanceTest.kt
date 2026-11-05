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
        // Na VPS operator przekazuje plik przez stdin, bez udostępniania katalogu hosta.
        Files.newInputStream(backup).use { stream ->
            assertEquals(0, runRestore(
                listOf("restore", "-"), db, StringBuilder(), StringBuilder(), stream,
            ))
        }
        SqliteStore("jdbc:sqlite:$db").use { store ->
            assertEquals(1L, store.countScores())
        }
    }

    /** Schemat 2 dokładnie tak, jak tworzył go serwer sprzed R5 (bez dzienników i statusu zgłoszeń). */
    private fun createSchemaV2(db: Path, player: String, publicId: String) {
        java.sql.DriverManager.getConnection("jdbc:sqlite:$db").use { c ->
            c.createStatement().use { s ->
                for (sql in listOf(
                    "CREATE TABLE players (player_id TEXT PRIMARY KEY NOT NULL, nick TEXT NOT NULL, " +
                        "updated_at INTEGER NOT NULL, secret_hash TEXT, public_id TEXT)",
                    "CREATE TABLE blocked_players (player_id TEXT PRIMARY KEY NOT NULL, blocked_at INTEGER NOT NULL)",
                    "CREATE TABLE scores (player_id TEXT NOT NULL REFERENCES players(player_id), " +
                        "board TEXT NOT NULL, day INTEGER NOT NULL, score INTEGER NOT NULL CHECK (score >= 0), " +
                        "seed INTEGER NOT NULL, game_version TEXT NOT NULL, ticks INTEGER NOT NULL, " +
                        "replay_sha256 TEXT NOT NULL, verified_at INTEGER NOT NULL, " +
                        "research_sum INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(player_id, board, day))",
                    "CREATE INDEX idx_scores_board_time ON scores(board, verified_at, score)",
                    "CREATE TABLE used_nonces (nonce TEXT PRIMARY KEY NOT NULL, expires_at INTEGER NOT NULL)",
                    "CREATE TABLE score_submit_quotas (day INTEGER NOT NULL, quota_kind TEXT NOT NULL, " +
                        "quota_key TEXT NOT NULL, requests INTEGER NOT NULL CHECK(requests > 0), " +
                        "PRIMARY KEY(day, quota_kind, quota_key))",
                    "CREATE TABLE schema_version (id INTEGER PRIMARY KEY CHECK (id = 1), version INTEGER NOT NULL)",
                    "INSERT INTO schema_version(id, version) VALUES (1, 2)",
                    "CREATE UNIQUE INDEX idx_players_public_id ON players(public_id)",
                    "CREATE TABLE nick_reports (report_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "player_id TEXT NOT NULL, public_id TEXT NOT NULL, reported_nick TEXT NOT NULL, " +
                        "reason TEXT NOT NULL, reported_at INTEGER NOT NULL)",
                    "INSERT INTO players VALUES ('$player', 'Stary', 1800000000, NULL, '$publicId')",
                    "INSERT INTO scores VALUES ('$player', 'endless', 20833, 90, 42, '0.8.8', 900, 'h', 1800000000, 0)",
                    "INSERT INTO nick_reports(player_id, public_id, reported_nick, reason, reported_at) " +
                        "VALUES ('$player', '$publicId', 'Stary', 'offensive', 1800000000)",
                )) s.execute(sql)
            }
        }
    }

    private fun schemaVersion(db: Path): Int = java.sql.DriverManager.getConnection("jdbc:sqlite:$db").use { c ->
        c.createStatement().use { s ->
            s.executeQuery("SELECT version FROM schema_version WHERE id = 1").use { rs -> rs.next(); rs.getInt(1) }
        }
    }

    @Test fun migratesSchemaTwoToThreeKeepingDataAndRefusesNewerSchema() {
        val db = directory.resolve("v2.db")
        val player = UUID.randomUUID().toString()
        val ids = PublicPlayerIds(ByteArray(32) { 3 })
        createSchemaV2(db, player, ids.of(player))
        val now = 1_800_000_000_000L
        SqliteStore("jdbc:sqlite:$db", ids).use { store ->
            assertEquals(90L, store.top("endless", "all", now).single().score)
            val report = store.recentReports().single()
            assertEquals(null, report.resolvedAt)
            assertEquals(true, store.resolveReport(report.id, now))
            assertTrue(store.deletionLogSince(0L).isEmpty())
            assertTrue(store.recentAdminLog().isEmpty())
            assertEquals(1, store.findPlayersByNick("stary").size)
            store.deletePlayer(player, SqliteStore.SOURCE_API, now)
            assertEquals(1, store.deletionLogSince(0L).size)
        }
        assertEquals(3, schemaVersion(db))
        // Ponowne otwarcie nie migruje drugi raz i zachowuje dzienniki.
        SqliteStore("jdbc:sqlite:$db", ids).use { store -> assertEquals(1, store.deletionLogSince(0L).size) }
        assertEquals(3, schemaVersion(db))

        java.sql.DriverManager.getConnection("jdbc:sqlite:$db").use { c ->
            c.createStatement().use { it.execute("UPDATE schema_version SET version = 4 WHERE id = 1") }
        }
        val error = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            SqliteStore("jdbc:sqlite:$db", ids).close()
        }
        assertEquals("unsupported_schema_version", error.message)
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

    @Test fun repeatedRestoresKeepOnlyNewestRescueDirectories() {
        val db = directory.resolve("rotate.db")
        val backup = directory.resolve("rotate-backup.sqlite")
        val now = 1_800_000_000_000L
        SqliteStore("jdbc:sqlite:$db").use { store ->
            store.save(input(UUID.randomUUID().toString(), 10L), VerifiedReplay("endless", 10L, 900), now, "h")
            assertEquals(0, runAdmin(listOf("backup", backup.toString()), store, StringBuilder(), StringBuilder()))
        }
        // Obcy katalog i plik o podobnej nazwie nie mogą zostać usunięte przez rotację.
        val unrelated = Files.createDirectories(directory.resolve("cw-other-keep"))
        val lookalike = Files.writeString(directory.resolve("cw-pre-restore-note.txt"), "x")
        val rescues = mutableListOf<Path>()
        repeat(DatabaseMaintenance.PRE_RESTORE_KEEP + 2) { i ->
            val output = StringBuilder()
            assertEquals(0, runRestore(listOf("restore", backup.toString()), db, output, StringBuilder()))
            val rescue = Path.of(output.lines().first { it.startsWith("previous-files:") }
                .substringAfter("previous-files: ").trim())
            // Jednoznaczna kolejność mimo zgrubnej rozdzielczości mtime w systemie plików.
            Files.setLastModifiedTime(rescue,
                java.nio.file.attribute.FileTime.fromMillis(now + i * 1_000L))
            rescues.add(rescue)
        }
        val left = Files.list(directory).use { stream ->
            stream.filter {
                it.fileName.toString().startsWith("cw-pre-restore-") && Files.isDirectory(it)
            }.toList().toSet()
        }
        assertEquals(DatabaseMaintenance.PRE_RESTORE_KEEP, left.size)
        assertEquals(rescues.takeLast(DatabaseMaintenance.PRE_RESTORE_KEEP).toSet(), left)
        assertTrue(Files.isDirectory(unrelated))
        assertTrue(Files.isRegularFile(lookalike))
    }
}
