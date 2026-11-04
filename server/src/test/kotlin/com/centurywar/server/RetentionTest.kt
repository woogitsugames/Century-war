package com.centurywar.server

import com.centurywar.game.Weekly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class RetentionTest {
    private val now = 1_800_000_000_000L
    private val dayMs = 86_400_000L
    private val today = todayUtc(now)
    private val policy = RetentionPolicy(periodDays = 90, reportDays = 180, inactiveDays = 730, logDays = 400)

    private fun input(player: String, board: String = "endless", day: Long? = null) = ScoreSubmission(
        board = board, player = player, nick = "Gracz", gameVersion = "0.8.8",
        seed = 42L, replay = "test-only", score = 100L, research = List(13) { 0 }, day = day,
    )

    private fun player() = UUID.randomUUID().toString()

    private fun SqliteStore.daily(player: String, day: Long) =
        save(input(player, "daily", day), VerifiedReplay("daily", 100L, 900), now, "d$day")

    private fun SqliteStore.weekly(player: String, week: Long) =
        save(input(player, "weekly", week), VerifiedReplay("weekly_$week", 100L, 900), now, "w$week")

    private fun SqliteStore.endless(player: String, atMillis: Long) =
        save(input(player), VerifiedReplay("endless", 100L, 900), atMillis, "e$atMillis")

    @Test fun dailyAndWeeklyExpireByChallengeDateWhilePermanentBoardsStay() {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            val a = player()
            store.daily(a, today - 91)
            store.daily(player(), today - 90)
            val cutoffDay = today - 90
            // Tydzień zawierający dzień graniczny zostaje, choć zaczął się wcześniej (liczymy od końca).
            val keptWeek = Weekly.weekNumber(cutoffDay)
            store.weekly(a, keptWeek - 1)
            store.weekly(a, keptWeek)
            store.endless(a, now)

            val result = store.purgeExpired(now, policy)
            assertEquals(1, result.dailyScores)
            assertEquals(1, result.weeklyScores)
            assertEquals(0, result.inactivePlayers)
            assertEquals(listOf(100L), store.top("daily", "all", now).map { it.score })
            assertTrue(store.top("weekly_${keptWeek - 1}", "all", now).isEmpty())
            assertEquals(1, store.top("weekly_$keptWeek", "all", now).size)
            assertEquals(1, store.top("endless", "all", now).size)
            // Gracz zostaje: miał świeży wynik, a wyniki tablic dnia nie usuwają konta.
            assertTrue(store.publicIdFor(a) != null)
        }
    }

    @Test fun inactivePlayerIsDeletedCompletelyWithLogEntryAndActiveOneStays() {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            val inactive = player()
            val active = player()
            store.endless(inactive, now - 731 * dayMs)
            store.save(input(inactive, "campaign"), VerifiedReplay("campaign_3", 30L, 900), now - 731 * dayMs, "c")
            store.endless(active, now - 729 * dayMs)
            store.blockPlayer(inactive)
            assertTrue(store.reportNick(store.publicIdFor(inactive)!!, "offensive", now - 10 * dayMs))

            val result = store.purgeExpired(now, policy)
            assertEquals(1, result.inactivePlayers)
            assertEquals(null, store.publicIdFor(inactive))
            assertTrue(store.top("campaign_3", "all", now).isEmpty())
            assertEquals(listOf(store.publicIdFor(active)), store.top("endless", "all", now).map { it.player })
            assertTrue(store.recentReports(unresolvedOnly = false).isEmpty())
            // Świeża blokada zostaje (zapobiega powrotowi pod tym samym UUID).
            assertTrue(store.isBlocked(inactive))
            val log = store.deletionLogSince(0L)
            assertEquals(listOf(DeletionLogEntry(store.subjectHash(inactive), now / 1000, "retention")), log)
        }
    }

    @Test fun oldBlockWithoutAccountExpiresWithInactivity() {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            val blocked = player()
            store.blockPlayer(blocked)
            // blocked_at to bieżący czas; patrzymy z przyszłości o ponad okres nieaktywności.
            val later = System.currentTimeMillis() + 731 * dayMs
            assertEquals(1, store.purgeExpired(later, policy).expiredBlocks)
            assertFalse(store.isBlocked(blocked))
        }
    }

    @Test fun reportsLogsQuotasAndNoncesExpire() {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            val p = player()
            store.endless(p, now)
            val id = store.publicIdFor(p)!!
            store.reportNick(id, "old", now - 181 * dayMs)
            store.reportNick(id, "new", now - 179 * dayMs)
            store.logAdmin("block", p, "updated", now - 401 * dayMs)
            store.logAdmin("block", p, "updated", now - 10 * dayMs)
            assertTrue(store.consumeScoreSubmissionQuota("inst", "203.0.113.1", now - dayMs, 10, 10))
            assertTrue(store.consumeNonce("n1", now / 1000 - 700))

            val result = store.purgeExpired(now, policy)
            assertEquals(1, result.nickReports)
            assertEquals(1, result.adminLog)
            assertEquals(2, result.quotas)
            assertEquals(1, result.nonces)
            assertEquals(listOf("new"), store.recentReports().map { it.reason })
            assertEquals(1, store.recentAdminLog().size)
        }
    }

    @Test fun zeroDisablesEachStep() {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            val old = player()
            store.daily(old, today - 400)
            store.endless(old, now - 2000 * dayMs)
            store.reportNick(store.publicIdFor(old)!!, "old", now - 1000 * dayMs)
            val off = RetentionPolicy(periodDays = 0, reportDays = 0, inactiveDays = 0, logDays = 0)
            val result = store.purgeExpired(now, off)
            assertEquals(0, result.personalRows)
            assertEquals(2L, store.countScores())
            assertEquals(1, store.recentReports().size)
        }
    }

    @Test fun dryRunReportsCountsWithoutDeleting() {
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            store.daily(player(), today - 100)
            store.endless(player(), now - 800 * dayMs)
            val preview = store.purgeExpired(now, policy, dryRun = true)
            assertTrue(preview.dryRun)
            assertEquals(1, preview.dailyScores)
            assertEquals(1, preview.inactivePlayers)
            assertEquals(2L, store.countScores())
            assertTrue(store.deletionLogSince(0L).isEmpty())

            val out = StringBuilder()
            assertEquals(0, runAdmin(listOf("purge", "--dry-run"), store, out, StringBuilder(), policy, { now }))
            assertTrue(out.startsWith("dry-run "))
            assertTrue(out.contains("daily_scores=1"))
            assertEquals(2L, store.countScores())
            assertTrue(store.recentAdminLog().isEmpty())

            val real = StringBuilder()
            assertEquals(0, runAdmin(listOf("purge"), store, real, StringBuilder(), policy, { now }))
            assertTrue(real.contains("inactive_players=1"))
            assertEquals(0L, store.countScores())
            assertEquals("purge", store.recentAdminLog().single().action)
            assertEquals(2, runAdmin(listOf("purge", "--force"), store, StringBuilder(), StringBuilder()))
        }
    }

    @Test fun environmentValuesAreValidated() {
        assertEquals(RetentionPolicy(), RetentionPolicy.fromEnv { null })
        val custom = RetentionPolicy.fromEnv {
            mapOf("CW_RETENTION_PERIOD_DAYS" to "30", "CW_RETENTION_INACTIVE_DAYS" to "0")[it]
        }
        assertEquals(RetentionPolicy(periodDays = 30, inactiveDays = 0), custom)
        assertThrows(IllegalArgumentException::class.java) {
            RetentionPolicy.fromEnv { if (it == "CW_RETENTION_REPORT_DAYS") "-1" else null }
        }
        assertThrows(IllegalArgumentException::class.java) {
            RetentionPolicy.fromEnv { if (it == "CW_RETENTION_LOG_DAYS") "rok" else null }
        }
    }

    @Test fun schedulerRunsAndSurvivesErrors() {
        val lines = ArrayList<String>()
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            store.daily(player(), today - 200)
            RetentionScheduler(store, policy, { now }, { synchronized(lines) { lines.add(it) } }).use { task ->
                task.runOnce()
            }
            assertTrue(lines.single().startsWith("retention: daily_scores=1"))
            assertEquals("purge", store.recentAdminLog().single().action)
        }
        val closed = SqliteStore("jdbc:sqlite::memory:").also { it.close() }
        RetentionScheduler(closed, policy, { now }, { lines.add(it) }).use { it.runOnce() }
        assertTrue(lines.last().startsWith("retention_failed"))
    }
}
