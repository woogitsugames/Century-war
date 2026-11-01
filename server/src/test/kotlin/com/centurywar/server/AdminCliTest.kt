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
                board = "endless", player = player, nick = "Gracz", gameVersion = "0.5.0",
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
