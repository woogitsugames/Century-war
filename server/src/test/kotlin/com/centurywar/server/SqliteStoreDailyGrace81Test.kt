package com.centurywar.server

import com.centurywar.game.Research
import com.centurywar.game.SimVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class SqliteStoreDailyGrace81Test {
    @Test fun yesterdayScoreDoesNotBlockTodaysScore() {
        val day = 21_000L
        val midnight = (day + 1L) * 86_400_000L
        val player = UUID.randomUUID().toString()
        val previous = ScoreSubmission(
            board = "daily", player = player, nick = "Dowodca",
            gameVersion = SimVersion.PROTOCOL,
            day = day, seed = day, replay = "verified",
            score = 100L, research = List(Research.defs.size) { 0 },
        )
        val current = previous.copy(day = day + 1L, seed = day + 1L, score = 200L)
        SqliteStore("jdbc:sqlite::memory:").use { store ->
            assertTrue(store.save(previous, VerifiedReplay("daily", 100L, 900),
                midnight + 1000L, "previous").inserted)
            assertTrue(store.save(current, VerifiedReplay("daily", 200L, 900),
                midnight + 1500L, "current").inserted)
            assertFalse(store.save(previous, VerifiedReplay("daily", 100L, 900),
                midnight + 2000L, "duplicate").inserted)
            val todayTop = store.top("daily", "day", midnight + 3000L)
            assertEquals(1, todayTop.size)
            assertEquals(200L, todayTop.single().score)
            assertEquals(200L, store.top("daily", "all", midnight + 3000L).single().score)
        }
    }
}
