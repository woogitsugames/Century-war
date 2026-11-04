package com.centurywar.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import java.time.LocalDate
import java.util.UUID

class DeletionLogTest {
    @TempDir lateinit var directory: Path

    private val now = 1_800_000_000_000L
    private val dayMs = 86_400_000L
    private val key = ByteArray(32) { (it * 7 + 1).toByte() }
    private val playerSecret = "b2".repeat(32)

    private fun input(player: String) = ScoreSubmission(
        board = "endless", player = player, nick = "Gracz", gameVersion = "0.8.8",
        seed = 42L, replay = "test-only", score = 100L, research = List(13) { 0 },
    )

    private fun SqliteStore.add(player: String, atMillis: Long = now) =
        save(input(player), VerifiedReplay("endless", 100L, 900), atMillis, "h", installSecretHash(playerSecret))

    /** Wszystkie wartości z `deletion_log` i `admin_log`, aby sprawdzić brak surowego UUID. */
    private fun logValues(db: Path): List<String> = DriverManager.getConnection("jdbc:sqlite:$db").use { c ->
        val values = ArrayList<String>()
        for (table in listOf("deletion_log", "admin_log")) {
            c.createStatement().use { s ->
                s.executeQuery("SELECT * FROM $table").use { rs ->
                    while (rs.next()) for (i in 1..rs.metaData.columnCount) values.add(rs.getString(i) ?: "")
                }
            }
        }
        values
    }

    @Test fun apiCliAndRetentionLogOnlyHashNeverRawUuid() {
        val db = directory.resolve("log.db")
        val viaApi = UUID.randomUUID().toString()
        val viaCli = UUID.randomUUID().toString()
        val viaRetention = UUID.randomUUID().toString()
        SqliteStore("jdbc:sqlite:$db", PublicPlayerIds(key)).use { store ->
            store.add(viaApi)
            store.add(viaCli)
            store.add(viaRetention, now - 800 * dayMs)
            assertTrue(store.deleteOwnedPlayer(viaApi, installSecretHash(playerSecret), now))
            // Powtórne usunięcie (idempotentne API) nie dubluje wpisu.
            assertTrue(store.deleteOwnedPlayer(viaApi, installSecretHash(playerSecret), now))
            assertEquals(0, runAdmin(listOf("delete-player", viaCli), store, StringBuilder(), StringBuilder(),
                now = { now }))
            store.purgeExpired(now, RetentionPolicy())

            val log = store.deletionLogSince(0L)
            assertEquals(
                listOf(viaApi to "api", viaCli to "operator", viaRetention to "retention")
                    .map { (player, source) -> DeletionLogEntry(store.subjectHash(player), now / 1000, source) },
                log,
            )
            // Skrót nie jest publicznym ID z rankingu (osobna domena HMAC).
            assertNotEquals(PublicPlayerIds(key).of(viaApi), store.subjectHash(viaApi))
        }
        val values = logValues(db)
        assertTrue(values.isNotEmpty())
        for (player in listOf(viaApi, viaCli, viaRetention)) {
            assertFalse(values.any { it.contains(player) }, "Surowy UUID w dzienniku: $player")
        }
    }

    @Test fun replayAfterRestoringOlderCopyDeletesExactlyLoggedPlayers() {
        val live = directory.resolve("live.db")
        val backup = directory.resolve("backup.sqlite")
        val deletedByApi = UUID.randomUUID().toString()
        val deletedByCli = UUID.randomUUID().toString()
        val kept = UUID.randomUUID().toString()
        val deletedBeforeBackup = UUID.randomUUID().toString()
        SqliteStore("jdbc:sqlite:$live", PublicPlayerIds(key)).use { store ->
            for (p in listOf(deletedByApi, deletedByCli, kept, deletedBeforeBackup)) store.add(p)
            store.deletePlayer(deletedBeforeBackup, SqliteStore.SOURCE_API, now - 3 * dayMs)
            store.backupTo(backup)
            // Po kopii: dwa usunięcia, których odtworzona kopia nie zna.
            assertTrue(store.deleteOwnedPlayer(deletedByApi, installSecretHash(playerSecret), now))
            assertEquals(0, runAdmin(listOf("delete-player", deletedByCli), store, StringBuilder(), StringBuilder(),
                now = { now }))
        }
        // „Odtworzenie”: serwer pracuje teraz na starszej kopii; nowsza baza to katalog ratunkowy.
        val restored = directory.resolve("restored.db")
        java.nio.file.Files.copy(backup, restored)
        SqliteStore("jdbc:sqlite:$restored", PublicPlayerIds(key)).use { store ->
            assertTrue(store.publicIdFor(deletedByApi) != null)
            assertTrue(store.publicIdFor(deletedByCli) != null)
            val since = LocalDate.ofEpochDay(todayUtc(now) - 1).toString()

            // Bez --from bieżąca (odtworzona) baza nie zna nowych usunięć.
            val noop = StringBuilder()
            assertEquals(0, runAdmin(listOf("replay-deletions", "--since", since), store, noop, StringBuilder()))
            assertTrue(noop.contains("deleted_players: 0"))

            val out = StringBuilder()
            assertEquals(0, runAdmin(
                listOf("replay-deletions", "--since", since, "--from", live.toString()),
                store, out, StringBuilder(),
            ))
            assertTrue(out.contains("entries: 2"), out.toString())
            assertTrue(out.contains("deleted_players: 2"), out.toString())
            assertEquals(null, store.publicIdFor(deletedByApi))
            assertEquals(null, store.publicIdFor(deletedByCli))
            assertEquals(listOf(store.publicIdFor(kept)), store.top("endless", "all", now).map { it.player })
            // Wpisy trafiły do dziennika odtworzonej bazy; powtórka jest idempotentna.
            val logSize = store.deletionLogSince(0L).size
            val again = StringBuilder()
            assertEquals(0, runAdmin(
                listOf("replay-deletions", "--since", since, "--from", live.toString()),
                store, again, StringBuilder(),
            ))
            assertTrue(again.contains("deleted_players: 0"))
            assertEquals(logSize, store.deletionLogSince(0L).size)
            assertEquals("replay-deletions", store.recentAdminLog(1).single().action)

            assertEquals(2, runAdmin(listOf("replay-deletions"), store, StringBuilder(), StringBuilder()))
            assertEquals(2, runAdmin(listOf("replay-deletions", "--since", since, "--since", since), store,
                StringBuilder(), StringBuilder()))
            assertEquals(2, runAdmin(listOf("replay-deletions", "--since", "wczoraj"), store,
                StringBuilder(), StringBuilder()))
            assertEquals(1, runAdmin(listOf("replay-deletions", "--since", since, "--from",
                directory.resolve("brak.db").toString()), store, StringBuilder(), StringBuilder()))
        }
    }

    /** Pełna ścieżka z instrukcji: `admin restore` zostawia katalog ratunkowy, z którego czytamy dziennik. */
    @Test fun replayFromRescueDirectoryLeftByAdminRestore() {
        val db = directory.resolve("leaderboards.db")
        val backup = directory.resolve("copy.sqlite")
        val gone = UUID.randomUUID().toString()
        val stays = UUID.randomUUID().toString()
        SqliteStore("jdbc:sqlite:$db", PublicPlayerIds(key)).use { store ->
            store.add(gone)
            store.add(stays)
            store.backupTo(backup)
            assertTrue(store.deleteOwnedPlayer(gone, installSecretHash(playerSecret), now))
        }
        val restoreOut = StringBuilder()
        assertEquals(0, runRestore(listOf("restore", backup.toString()), db, restoreOut, StringBuilder()))
        val rescue = restoreOut.lines().first { it.startsWith("previous-files:") }
            .substringAfter("previous-files: ").trim()
        SqliteStore("jdbc:sqlite:$db", PublicPlayerIds(key)).use { store ->
            assertTrue(store.publicIdFor(gone) != null)
            val out = StringBuilder()
            assertEquals(0, runAdmin(listOf(
                "replay-deletions", "--since", LocalDate.ofEpochDay(todayUtc(now)).toString(),
                "--from", Path.of(rescue).resolve("leaderboards.db").toString(),
            ), store, out, StringBuilder()), out.toString())
            assertTrue(out.contains("deleted_players: 1"))
            assertEquals(null, store.publicIdFor(gone))
            assertTrue(store.publicIdFor(stays) != null)
        }
    }
}
