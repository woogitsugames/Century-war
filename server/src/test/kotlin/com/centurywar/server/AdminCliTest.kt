package com.centurywar.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class AdminCliTest {
    private val now = 1_800_000_000_000L

    @Test fun recentDeleteBlockAndCsvWorkWithoutWebPanel() {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            val player = UUID.randomUUID().toString()
            val input = ScoreSubmission(
                board = "endless", player = player, nick = "Gracz", gameVersion = "0.8.13",
                seed = 42L, replay = "irrelevant", score = 150L, research = List(13) { 0 },
            )
            store.save(input, VerifiedReplay("endless", 150L, 900), now, "hash-abc")

            val recent = StringBuilder()
            assertEquals(0, runAdmin(listOf("recent", "5"), store, recent, StringBuilder()))
            assertTrue(recent.contains(player))
            assertTrue(recent.contains("Gracz"))
            assertTrue(recent.contains("endless"))

            val blockedOutput = StringBuilder()
            assertEquals(0, runAdmin(listOf("block", player), store, blockedOutput, StringBuilder()))
            assertEquals("updated\n", blockedOutput.toString())
            assertTrue(store.isBlocked(player))
            assertTrue(store.top("endless", "all", now).isEmpty())

            val csv = StringBuilder()
            assertEquals(0, runAdmin(listOf("export-csv"), store, csv, StringBuilder()))
            assertEquals(2, csv.toString().lines().count { it.isNotBlank() })
            assertTrue(csv.contains("\"true\""))
            assertTrue(csv.contains("\"hash-abc\""))

            assertEquals(0, runAdmin(listOf("unblock", player), store, StringBuilder(), StringBuilder()))
            assertFalse(store.isBlocked(player))
            assertEquals(0, runAdmin(
                listOf("delete-score", player, "endless", todayUtc(now).toString()),
                store, StringBuilder(), StringBuilder()
            ))
            assertTrue(store.top("endless", "all", now).isEmpty())
            assertEquals(1, runAdmin(
                listOf("delete-score", player, "endless", todayUtc(now).toString()),
                store, StringBuilder(), StringBuilder()
            ))
            assertEquals(0, store.exportCsv(StringBuilder()))
        }
    }

    private fun SqliteStore.add(player: String, nick: String, atMillis: Long = now) = save(
        ScoreSubmission(
            board = "endless", player = player, nick = nick, gameVersion = "0.8.8",
            seed = 42L, replay = "irrelevant", score = 150L, research = List(13) { 0 },
        ),
        VerifiedReplay("endless", 150L, 900), atMillis, "hash-$nick",
    )

    private fun run(store: SqliteStore, vararg args: String): Triple<Int, String, String> {
        val out = StringBuilder()
        val err = StringBuilder()
        val code = runAdmin(args.toList(), store, out, err, now = { now })
        return Triple(code, out.toString(), err.toString())
    }

    @Test fun findNickIgnoresCaseNormalizesAndExplainsFilteredNicks() {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            val kowalski = UUID.randomUUID().toString()
            val lukasz = UUID.randomUUID().toString()
            val hidden = UUID.randomUUID().toString()
            store.add(kowalski, "Kowalski")
            store.add(lukasz, "Łukasz 99")
            store.add(hidden, "kurwa")

            val (code, out, _) = run(store, "find-nick", "kOWAL")
            assertEquals(0, code)
            assertTrue(out.contains(kowalski))
            assertTrue(out.contains("Kowalski"))
            assertTrue(out.contains("\t1\t"))
            assertFalse(out.contains(lukasz))
            // Normalizacja filtru: bez polskich znaków, z odstępem w zapytaniu.
            assertTrue(run(store, "find-nick", "lukasz").second.contains(lukasz))
            assertTrue(run(store, "find-nick", "ŁUKASZ", "99").second.contains(lukasz))

            // Nick zastąpiony przez filtr: w bazie i rankingu jest „Dowódca-XXXX”.
            val shown = NickModeration.fallbackNick(hidden)
            val (filteredCode, _, hint) = run(store, "find-nick", "kurwa")
            assertEquals(1, filteredCode)
            assertTrue(hint.contains("Dowódca-XXXX"))
            assertTrue(hint.contains("not_found"))
            val byShown = run(store, "find-nick", shown.uppercase())
            assertEquals(0, byShown.first)
            assertTrue(byShown.second.contains(hidden))

            assertEquals(1, run(store, "find-nick", "nikt-taki").first)
            assertEquals(2, run(store, "find-nick").first)
        }
    }

    @Test fun deletePlayerRemovesEverythingAndBlockUnlessKept() {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            val first = UUID.randomUUID().toString()
            val second = UUID.randomUUID().toString()
            val other = UUID.randomUUID().toString()
            store.add(first, "Pierwszy")
            store.add(second, "Drugi")
            store.add(other, "Inny")
            store.reportNick(store.publicIdFor(first)!!, "offensive", now)
            store.blockPlayer(first)
            store.blockPlayer(second)

            val deleted = run(store, "delete-player", first)
            assertEquals(0, deleted.first)
            assertEquals("deleted\nblock_removed\n", deleted.second)
            assertEquals(null, store.publicIdFor(first))
            assertFalse(store.isBlocked(first))
            assertTrue(store.recentReports(unresolvedOnly = false).isEmpty())
            val missing = run(store, "delete-player", first)
            assertEquals(1, missing.first)
            assertTrue(missing.third.contains("not_found"))

            val kept = run(store, "delete-player", second, "--keep-block")
            assertEquals(0, kept.first)
            assertEquals("deleted\n", kept.second)
            assertTrue(store.isBlocked(second))
            assertEquals(null, store.publicIdFor(second))

            assertEquals(2, run(store, "delete-player", "not-uuid").first)
            assertEquals(2, run(store, "delete-player", other, "--force").first)
            assertEquals(listOf(store.publicIdFor(other)), store.top("endless", "all", now).map { it.player })
            assertEquals(2, store.deletionLogSince(0L).size)
        }
    }

    @Test fun reportsShowUnresolvedByDefaultAndResolveReportMarksThem() {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            val target = UUID.randomUUID().toString()
            store.add(target, "Celownik")
            val id = store.publicIdFor(target)!!
            store.reportNick(id, "offensive", now)
            val reportId = store.recentReports().single().id

            assertTrue(run(store, "reports").second.contains("offensive"))
            val resolved = run(store, "resolve-report", reportId.toString())
            assertEquals(0 to "resolved\n", resolved.first to resolved.second)
            assertFalse(run(store, "reports").second.contains("offensive"))
            val all = run(store, "reports", "--all", "10").second
            assertTrue(all.contains("offensive"))
            assertTrue(all.contains("\t" + now / 1000 + "\n"))
            assertEquals("unchanged\n", run(store, "resolve-report", reportId.toString()).second)
            assertEquals(1, run(store, "resolve-report", "999").first)
            assertEquals(2, run(store, "resolve-report", "abc").first)
            assertEquals(2, run(store, "reports", "--all", "--all").first)
        }
    }

    @Test fun adminLogRecordsActionsWithoutRawUuid() {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            val player = UUID.randomUUID().toString()
            store.add(player, "Gracz")
            run(store, "block", player)
            run(store, "block", player) // bez zmiany: bez wpisu
            run(store, "unblock", player)
            run(store, "delete-score", player, "endless", todayUtc(now).toString())
            store.add(player, "Gracz")
            run(store, "delete-player", player)

            val (code, out, _) = run(store, "log")
            assertEquals(0, code)
            val actions = out.lines().drop(1).filter { it.isNotBlank() }.map { it.split('\t')[1] }
            assertEquals(listOf("delete-player", "delete-score", "unblock", "block"), actions)
            assertFalse(out.contains(player))
            assertTrue(out.contains(store.subjectHash(player)))
            assertEquals(2, run(store, "log", "0").first)
            assertEquals(1, run(store, "log", "1").second.lines().count { it.isNotBlank() } - 1)
        }
    }

    @Test fun wrongArgumentsDoNotModifyDatabase() {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            val bad = StringBuilder()
            assertEquals(2, runAdmin(listOf("delete-score", "not-uuid", "endless", "1"), store, StringBuilder(), bad))
            assertTrue(bad.contains("UUID"))
            assertEquals(2, runAdmin(listOf("recent", "201"), store, StringBuilder(), StringBuilder()))
            assertEquals(2, runAdmin(listOf("export-csv", "file.csv"), store, StringBuilder(), StringBuilder()))
            assertEquals(2, runAdmin(listOf("unknown"), store, StringBuilder(), StringBuilder()))
            val help = StringBuilder()
            assertEquals(0, runAdmin(listOf("help"), store, help, StringBuilder()))
            assertTrue(help.contains("export-csv"))
            assertTrue(help.contains("block"))
        }
    }
}
